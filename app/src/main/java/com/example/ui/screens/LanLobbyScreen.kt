package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.FriendEntity
import com.example.data.local.SurvivorProfileEntity
import com.example.model.GameMode
import com.example.model.SkinConfig
import com.example.network.LanSessionState
import com.example.network.QrCodeHelper
import com.example.network.ScannedQrPayload
import com.example.network.VoiceChatState
import com.example.ui.components.SurvivorCharacterPreview
import com.example.ui.theme.ForestMoss
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun LanLobbyScreen(
    profile: SurvivorProfileEntity?,
    activeSkin: SkinConfig,
    lanState: LanSessionState,
    voiceState: VoiceChatState,
    friends: List<FriendEntity>,
    initialMode: GameMode,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onHostRoom: (roomName: String, mode: GameMode) -> Unit,
    onJoinRoom: (ip: String, port: Int, mode: GameMode) -> Unit,
    onLeaveRoom: () -> Unit,
    onScannedQr: (ScannedQrPayload) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onToggleMic: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onEnterGameWorld: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var roomName by remember { mutableStateOf("${profile?.nickname ?: "营地"}的篝火") }
    var selectedMode by remember(initialMode) {
        mutableStateOf(if (initialMode == GameMode.OFFLINE_SOLO) GameMode.LAN_COOP else initialMode)
    }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showMyFriendQr by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose { onStopDiscovery() }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraScanner = true
        } else {
            feedbackMessage = "未授予相机权限，你仍可使用「从相册识别二维码」或自动发现加入"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMic(true)
        } else {
            feedbackMessage = "未授予麦克风权限，无法开启实时语音"
        }
    }

    val qrPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }.getOrNull()
                if (bitmap != null) {
                    val raw = QrCodeHelper.decodeQrFromBitmap(bitmap)
                    val parsed = raw?.let { QrCodeHelper.parseQrContent(it) }
                    if (parsed != null) {
                        onScannedQr(parsed)
                        feedbackMessage = when (parsed) {
                            is ScannedQrPayload.RoomInvite -> "已识别房间「${parsed.roomName}」，正在加入并添加好友！"
                            is ScannedQrPayload.FriendCard -> "已添加好友「${parsed.nickname}」(${parsed.email})！"
                        }
                    } else {
                        feedbackMessage = "未在图片中识别到有效的荒原篝火二维码"
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Session Banner + Voice Chat Control
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (lanState.isConnected) ForestMoss else MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("局域网联机 & 实时语音", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = lanState.statusText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showMyFriendQr = true },
                                modifier = Modifier.testTag("show_my_friend_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = "我的好友名片码",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Real-time Voice Controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (voiceState.isMicEnabled) {
                                        onToggleMic(false)
                                    } else {
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasMic) onToggleMic(true)
                                        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (voiceState.isMicEnabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .testTag("lobby_voice_mic_button")
                            ) {
                                Icon(
                                    imageVector = if (voiceState.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "开关麦克风",
                                    tint = if (voiceState.isMicEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (voiceState.isMicEnabled) "实时语音麦克风已开启" else "点击开启局域网实时语音",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { voiceState.micLevel },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }

                            IconButton(
                                onClick = { onToggleSpeaker(!voiceState.isSpeakerEnabled) },
                                modifier = Modifier.testTag("lobby_voice_speaker_button")
                            ) {
                                Icon(
                                    imageVector = if (voiceState.isSpeakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "扬声器静音",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        if (lanState.isConnected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onEnterGameWorld,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("enter_lan_game_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("进入联机世界 (${lanState.peers.size + 1}人)")
                                }
                                OutlinedButton(
                                    onClick = onLeaveRoom,
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("leave_lan_room_button")
                                ) {
                                    Text("退出房间")
                                }
                            }
                        }
                    }
                }
            }

            if (feedbackMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = feedbackMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Host Room & QR Code Display
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("创建同网络房间（生成联机二维码）", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = roomName,
                            onValueChange = { roomName = it.take(24) },
                            label = { Text("房间名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = selectedMode == GameMode.LAN_COOP,
                                onClick = { selectedMode = GameMode.LAN_COOP },
                                label = { Text("合作求生") }
                            )
                            FilterChip(
                                selected = selectedMode == GameMode.LAN_PVP,
                                onClick = { selectedMode = GameMode.LAN_PVP },
                                label = { Text("联机对战 (PvP)") }
                            )
                        }

                        Button(
                            onClick = { onHostRoom(roomName.ifBlank { "荒原营地" }, selectedMode) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("host_lan_room_button")
                        ) {
                            Text(if (lanState.isHosting) "刷新房间二维码" else "创建房间并生成二维码")
                        }

                        if (lanState.isHosting && lanState.hostIp.isNotBlank() && lanState.hostPort > 0) {
                            val qrUri = remember(lanState.hostIp, lanState.hostPort, lanState.roomName, lanState.gameMode, activeSkin) {
                                QrCodeHelper.buildRoomQrUri(
                                    ip = lanState.hostIp,
                                    port = lanState.hostPort,
                                    voicePort = voiceState.localUdpPort,
                                    roomName = lanState.roomName,
                                    gameMode = lanState.gameMode,
                                    hostId = profile?.localPlayerId ?: "host",
                                    hostNickname = profile?.nickname ?: "房主",
                                    hostEmail = profile?.email.orEmpty(),
                                    hostSkin = activeSkin
                                )
                            }
                            val qrBitmap = remember(qrUri) { QrCodeHelper.generateQrBitmap(qrUri, 420) }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "房间联机二维码",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                )
                                Text(
                                    text = "IP: ${lanState.hostIp}  端口: ${lanState.hostPort} · 同 Wi-Fi 好友扫码即可加入并互加好友",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Scan QR / Photo QR / Manual Join
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("扫码互联 / 扫码加好友", style = MaterialTheme.typography.titleMedium)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) showCameraScanner = true
                                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("scan_qr_camera_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("相机扫码")
                            }

                            OutlinedButton(
                                onClick = {
                                    qrPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("scan_qr_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("相册识码")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text("房主 IP") },
                                placeholder = { Text("192.168.1.10") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = manualPort,
                                onValueChange = { manualPort = it.filter { ch -> ch.isDigit() }.take(5) },
                                label = { Text("端口") },
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                            Button(
                                onClick = {
                                    val portNum = manualPort.toIntOrNull() ?: 0
                                    if (manualIp.isNotBlank() && portNum > 0) {
                                        onJoinRoom(manualIp.trim(), portNum, selectedMode)
                                    } else {
                                        feedbackMessage = "请输入有效的局域网 IP 和端口"
                                    }
                                },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("manual_join_button")
                            ) {
                                Text("连接")
                            }
                        }
                    }
                }
            }

            // NSD Discovered Rooms
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("同网络自动发现的房间 (${lanState.discoveredRooms.size})", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onStartDiscovery) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新局域网房间")
                    }
                }
            }

            if (lanState.discoveredRooms.isEmpty()) {
                item {
                    Text(
                        text = "暂未发现同 Wi-Fi 广播的房间，你也可以直接扫描房主的二维码加入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(lanState.discoveredRooms, key = { "${it.hostIp}:${it.port}" }) { room ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(room.roomName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${room.gameMode.label} · ${room.hostIp}:${room.port}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Button(onClick = { onJoinRoom(room.hostIp, room.port, room.gameMode) }) {
                                Text("加入")
                            }
                        }
                    }
                }
            }

            // Friends Roster
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("荒原好友名录 (${friends.size})", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (friends.isEmpty()) {
                item {
                    Text(
                        text = "与好友同网络联机或扫描对方的「好友名片码」，即可将对方永久加入好友列表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(friends, key = { it.friendId }) { friend ->
                    val skin = remember(friend.skinJson) { SkinConfig.fromJson(friend.skinJson) ?: SkinConfig() }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SurvivorCharacterPreview(skin = skin, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.nickname, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = friend.email.ifBlank { "局域网战友" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (friend.lastIp.isNotBlank() && friend.lastPort > 0) {
                                TextButton(onClick = { onJoinRoom(friend.lastIp, friend.lastPort, selectedMode) }) {
                                    Text("直连")
                                }
                            }
                            IconButton(onClick = { onRemoveFriend(friend.friendId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "移除好友")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMyFriendQr) {
        val myQrUri = remember(profile, activeSkin) {
            QrCodeHelper.buildFriendCardQrUri(
                friendId = profile?.localPlayerId ?: "local_id",
                nickname = profile?.nickname ?: "幸存者",
                email = profile?.email.orEmpty(),
                skin = activeSkin
            )
        }
        val myQrBitmap = remember(myQrUri) { QrCodeHelper.generateQrBitmap(myQrUri, 420) }
        AlertDialog(
            onDismissRequest = { showMyFriendQr = false },
            title = { Text("我的好友名片码") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        bitmap = myQrBitmap.asImageBitmap(),
                        contentDescription = "好友名片二维码",
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Text("${profile?.nickname ?: "幸存者"} · ${profile?.email.orEmpty()}", style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyFriendQr = false }) { Text("关闭") }
            }
        )
    }

    if (showCameraScanner) {
        CameraQrScannerDialog(
            onDismiss = { showCameraScanner = false },
            onQrDetected = { raw ->
                val parsed = QrCodeHelper.parseQrContent(raw)
                if (parsed != null) {
                    showCameraScanner = false
                    onScannedQr(parsed)
                    feedbackMessage = when (parsed) {
                        is ScannedQrPayload.RoomInvite -> "扫码成功！正在加入「${parsed.roomName}」并互加好友"
                        is ScannedQrPayload.FriendCard -> "已添加好友「${parsed.nickname}」！"
                    }
                }
            }
        )
    }
}

@Composable
private fun CameraQrScannerDialog(
    onDismiss: () -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    var alreadyMatched by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("对准房主或好友二维码")
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭扫码")
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().apply {
                                surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(640, 480))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { imageProxy ->
                                @androidx.camera.core.ExperimentalGetImage
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !alreadyMatched) {
                                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(input)
                                        .addOnSuccessListener { barcodes ->
                                            val code = barcodes.firstNotNullOfOrNull { it.rawValue }
                                            if (code != null && !alreadyMatched) {
                                                alreadyMatched = true
                                                onQrDetected(code)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
