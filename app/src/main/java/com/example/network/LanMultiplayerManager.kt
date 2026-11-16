package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.model.GameMode
import com.example.model.RemotePlayerState
import com.example.model.SkinConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

@JsonClass(generateAdapter = true)
data class LanPacket(
    val type: String, // JOIN, STATE, PVP_HIT, CHAT
    val playerId: String = "",
    val nickname: String = "",
    val email: String = "",
    val x: Float = 1200f,
    val y: Float = 1200f,
    val facingRight: Boolean = true,
    val isAttacking: Boolean = false,
    val hp: Int = 100,
    val voicePort: Int = 0,
    val skinJson: String = "",
    val gameMode: String = GameMode.LAN_COOP.name,
    val campfireFuel: Float = -1f,
    val targetId: String = "",
    val damage: Int = 0,
    val message: String = ""
)

data class DiscoveredLanRoom(
    val serviceName: String,
    val roomName: String,
    val hostIp: String,
    val port: Int,
    val gameMode: GameMode
)

data class LanSessionState(
    val isHosting: Boolean = false,
    val isConnected: Boolean = false,
    val roomName: String = "",
    val gameMode: GameMode = GameMode.LAN_COOP,
    val hostIp: String = "",
    val hostPort: Int = 0,
    val localVoicePort: Int = 0,
    val peers: Map<String, RemotePlayerState> = emptyMap(),
    val peerVoiceEndpoints: Map<String, Pair<String, Int>> = emptyMap(),
    val discoveredRooms: List<DiscoveredLanRoom> = emptyList(),
    val statusText: String = "未连接局域网房间",
    val chatMessages: List<Pair<String, String>> = emptyList()
)

sealed interface LanGameEvent {
    data class PvpDamageReceived(val damage: Int, val attackerName: String) : LanGameEvent
    data class CampfireFuelSynced(val fuel: Float) : LanGameEvent
    data class PeerJoinedFriend(val player: RemotePlayerState, val ip: String, val port: Int) : LanGameEvent
}

class LanMultiplayerManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val packetAdapter = moshi.adapter(LanPacket::class.java)

    private val _sessionState = MutableStateFlow(LanSessionState())
    val sessionState: StateFlow<LanSessionState> = _sessionState.asStateFlow()

    private val _events = MutableSharedFlow<LanGameEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<LanGameEvent> = _events.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientWriter: BufferedWriter? = null
    private val hostClientWriters = ConcurrentHashMap<String, BufferedWriter>()
    private val hostClientSockets = ConcurrentHashMap<String, Socket>()
    private var acceptJob: Job? = null
    private var readJob: Job? = null

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun getLocalWifiIp(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface -> iface.inetAddresses.toList().map { iface.name to it } }
                .filter { (_, addr) -> addr is Inet4Address && !addr.isLoopbackAddress }
            candidates.firstOrNull { (name, _) -> name.startsWith("wlan") }?.second?.hostAddress
                ?: candidates.firstOrNull()?.second?.hostAddress
                ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    fun startHosting(
        roomName: String,
        gameMode: GameMode,
        localPlayerId: String,
        nickname: String,
        email: String,
        skin: SkinConfig,
        voicePort: Int
    ) {
        stopSession()
        val ip = getLocalWifiIp()
        try {
            val server = ServerSocket(0)
            serverSocket = server
            val port = server.localPort
            _sessionState.update {
                it.copy(
                    isHosting = true,
                    isConnected = true,
                    roomName = roomName,
                    gameMode = gameMode,
                    hostIp = ip,
                    hostPort = port,
                    localVoicePort = voicePort,
                    peers = emptyMap(),
                    peerVoiceEndpoints = emptyMap(),
                    statusText = "正在主持房间：$ip:$port"
                )
            }
            registerNsdService(roomName, gameMode, port)

            acceptJob = scope.launch {
                while (!server.isClosed) {
                    try {
                        val socket = server.accept()
                        handleIncomingHostClient(socket, localPlayerId, nickname, email, skin, voicePort, gameMode)
                    } catch (e: Exception) {
                        if (!server.isClosed) {
                            Log.w(TAG, "Server accept stopped: ${e.message}")
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hosting", e)
            _sessionState.update { it.copy(statusText = "创建房间失败: ${e.localizedMessage}") }
        }
    }

    private fun handleIncomingHostClient(
        socket: Socket,
        hostId: String,
        hostNick: String,
        hostEmail: String,
        hostSkin: SkinConfig,
        hostVoicePort: Int,
        gameMode: GameMode
    ) {
        scope.launch {
            val remoteIp = socket.inetAddress?.hostAddress.orEmpty()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
            var peerId = "peer_${socket.port}"
            hostClientSockets[peerId] = socket
            hostClientWriters[peerId] = writer

            // Send host info right away
            sendPacketToWriter(
                writer,
                LanPacket(
                    type = "JOIN",
                    playerId = hostId,
                    nickname = hostNick,
                    email = hostEmail,
                    voicePort = hostVoicePort,
                    skinJson = hostSkin.toJson(),
                    gameMode = gameMode.name
                )
            )

            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    val pkt = runCatching { packetAdapter.fromJson(line) }.getOrNull() ?: continue
                    if (pkt.playerId.isNotBlank() && pkt.playerId != peerId) {
                        hostClientSockets.remove(peerId)
                        hostClientWriters.remove(peerId)
                        peerId = pkt.playerId
                        hostClientSockets[peerId] = socket
                        hostClientWriters[peerId] = writer
                    }
                    processIncomingPacket(pkt, remoteIp, hostId)
                    // Relay packet to all other clients
                    hostClientWriters.forEach { (otherId, otherWriter) ->
                        if (otherId != peerId) {
                            sendPacketToWriter(otherWriter, pkt)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client disconnected: ${e.message}")
            } finally {
                hostClientWriters.remove(peerId)
                hostClientSockets.remove(peerId)?.closeQuietly()
                _sessionState.update { state ->
                    state.copy(
                        peers = state.peers - peerId,
                        peerVoiceEndpoints = state.peerVoiceEndpoints - peerId
                    )
                }
            }
        }
    }

    fun joinRoom(
        hostIp: String,
        hostPort: Int,
        localPlayerId: String,
        nickname: String,
        email: String,
        skin: SkinConfig,
        voicePort: Int
    ) {
        stopSession()
        _sessionState.update {
            it.copy(
                isHosting = false,
                isConnected = false,
                hostIp = hostIp,
                hostPort = hostPort,
                localVoicePort = voicePort,
                statusText = "正在连接 $hostIp:$hostPort ..."
            )
        }

        readJob = scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostIp, hostPort), 4000)
                clientSocket = socket
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                clientWriter = writer
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                _sessionState.update {
                    it.copy(
                        isConnected = true,
                        statusText = "已连入局域网房间 ($hostIp:$hostPort)"
                    )
                }

                sendPacketToWriter(
                    writer,
                    LanPacket(
                        type = "JOIN",
                        playerId = localPlayerId,
                        nickname = nickname,
                        email = email,
                        voicePort = voicePort,
                        skinJson = skin.toJson()
                    )
                )

                while (true) {
                    val line = reader.readLine() ?: break
                    val pkt = runCatching { packetAdapter.fromJson(line) }.getOrNull() ?: continue
                    processIncomingPacket(pkt, hostIp, localPlayerId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Join room failed", e)
                _sessionState.update {
                    it.copy(
                        isConnected = false,
                        statusText = "连接中断：${e.localizedMessage ?: "请确认处于同一 Wi-Fi"}"
                    )
                }
            }
        }
    }

    private suspend fun processIncomingPacket(pkt: LanPacket, senderIp: String, localPlayerId: String) {
        if (pkt.playerId == localPlayerId) return
        val skin = SkinConfig.fromJson(pkt.skinJson) ?: SkinConfig()
        when (pkt.type) {
            "JOIN", "STATE" -> {
                val remotePlayer = RemotePlayerState(
                    playerId = pkt.playerId,
                    nickname = pkt.nickname.ifBlank { "幸存者" },
                    email = pkt.email,
                    x = pkt.x,
                    y = pkt.y,
                    facingRight = pkt.facingRight,
                    isAttacking = pkt.isAttacking,
                    hp = pkt.hp,
                    skin = skin
                )
                val parsedMode = GameMode.entries.firstOrNull { it.name == pkt.gameMode }
                _sessionState.update { state ->
                    val updatedEndpoints = if (pkt.voicePort > 0 && senderIp.isNotBlank()) {
                        state.peerVoiceEndpoints + (pkt.playerId to (senderIp to pkt.voicePort))
                    } else state.peerVoiceEndpoints
                    state.copy(
                        gameMode = if (!state.isHosting && pkt.type == "JOIN" && parsedMode != null) parsedMode else state.gameMode,
                        peers = state.peers + (pkt.playerId to remotePlayer),
                        peerVoiceEndpoints = updatedEndpoints
                    )
                }
                if (pkt.type == "JOIN") {
                    _events.emit(LanGameEvent.PeerJoinedFriend(remotePlayer, senderIp, _sessionState.value.hostPort))
                }
                if (pkt.campfireFuel >= 0f && !_sessionState.value.isHosting) {
                    _events.emit(LanGameEvent.CampfireFuelSynced(pkt.campfireFuel))
                }
            }
            "PVP_HIT" -> {
                if (pkt.targetId == localPlayerId && pkt.damage > 0) {
                    _events.emit(LanGameEvent.PvpDamageReceived(pkt.damage, pkt.nickname))
                }
            }
            "CHAT" -> {
                if (pkt.message.isNotBlank()) {
                    _sessionState.update { state ->
                        state.copy(chatMessages = (state.chatMessages + (pkt.nickname to pkt.message)).takeLast(30))
                    }
                }
            }
        }
    }

    fun broadcastPlayerState(
        playerId: String,
        nickname: String,
        email: String,
        x: Float,
        y: Float,
        facingRight: Boolean,
        isAttacking: Boolean,
        hp: Int,
        skin: SkinConfig,
        campfireFuel: Float
    ) {
        val state = _sessionState.value
        if (!state.isConnected) return
        val pkt = LanPacket(
            type = "STATE",
            playerId = playerId,
            nickname = nickname,
            email = email,
            x = x,
            y = y,
            facingRight = facingRight,
            isAttacking = isAttacking,
            hp = hp,
            voicePort = state.localVoicePort,
            skinJson = skin.toJson(),
            campfireFuel = if (state.isHosting) campfireFuel else -1f
        )
        sendPacket(pkt)
    }

    fun sendPvpHit(attackerId: String, attackerName: String, targetId: String, damage: Int) {
        sendPacket(
            LanPacket(
                type = "PVP_HIT",
                playerId = attackerId,
                nickname = attackerName,
                targetId = targetId,
                damage = damage
            )
        )
    }

    fun sendChatMessage(playerId: String, nickname: String, message: String) {
        _sessionState.update { state ->
            state.copy(chatMessages = (state.chatMessages + ("我" to message)).takeLast(30))
        }
        sendPacket(
            LanPacket(
                type = "CHAT",
                playerId = playerId,
                nickname = nickname,
                message = message
            )
        )
    }

    private fun sendPacket(packet: LanPacket) {
        scope.launch {
            if (_sessionState.value.isHosting) {
                hostClientWriters.values.forEach { sendPacketToWriter(it, packet) }
            } else {
                clientWriter?.let { sendPacketToWriter(it, packet) }
            }
        }
    }

    private fun sendPacketToWriter(writer: BufferedWriter, packet: LanPacket) {
        runCatching {
            val json = packetAdapter.toJson(packet)
            synchronized(writer) {
                writer.write(json)
                writer.newLine()
                writer.flush()
            }
        }
    }

    fun startDiscovery() {
        stopDiscovery()
        val manager = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != SERVICE_TYPE) return
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val ip = serviceInfo.host?.hostAddress ?: return
                        val port = serviceInfo.port
                        val rawName = serviceInfo.serviceName
                        val isPvp = rawName.contains("[PVP]")
                        val cleanName = rawName.replace("[PVP]", "").replace("[COOP]", "").trim()
                        val room = DiscoveredLanRoom(
                            serviceName = rawName,
                            roomName = cleanName.ifBlank { "荒原营地" },
                            hostIp = ip,
                            port = port,
                            gameMode = if (isPvp) GameMode.LAN_PVP else GameMode.LAN_COOP
                        )
                        _sessionState.update { state ->
                            val filtered = state.discoveredRooms.filterNot { it.hostIp == ip && it.port == port }
                            state.copy(discoveredRooms = filtered + room)
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _sessionState.update { state ->
                    state.copy(discoveredRooms = state.discoveredRooms.filterNot { it.serviceName == service.serviceName })
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
    }

    private fun registerNsdService(roomName: String, mode: GameMode, port: Int) {
        val tag = if (mode == GameMode.LAN_PVP) "[PVP]" else "[COOP]"
        val info = NsdServiceInfo().apply {
            serviceName = "$tag $roomName".take(45)
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching {
            nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopSession() {
        acceptJob?.cancel()
        readJob?.cancel()
        registrationListener?.let { listener ->
            runCatching { nsdManager?.unregisterService(listener) }
        }
        registrationListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { clientSocket?.close() }
        clientSocket = null
        clientWriter = null
        hostClientSockets.values.forEach { it.closeQuietly() }
        hostClientSockets.clear()
        hostClientWriters.clear()
        _sessionState.update {
            it.copy(
                isHosting = false,
                isConnected = false,
                peers = emptyMap(),
                peerVoiceEndpoints = emptyMap(),
                statusText = "已离开局域网房间"
            )
        }
    }

    private fun Socket.closeQuietly() {
        runCatching { close() }
    }

    private companion object {
        const val TAG = "LanMultiplayer"
        const val SERVICE_TYPE = "_wildhearth._tcp."
    }
}
