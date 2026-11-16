package com.example.network

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.sqrt

data class VoiceChatState(
    val isVoiceRunning: Boolean = false,
    val isMicEnabled: Boolean = false,
    val isSpeakerEnabled: Boolean = true,
    val localUdpPort: Int = 0,
    val micLevel: Float = 0f,
    val remoteSpeaking: Boolean = false
)

class LanVoiceChatManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _voiceState = MutableStateFlow(VoiceChatState())
    val voiceState: StateFlow<VoiceChatState> = _voiceState.asStateFlow()

    private var udpSocket: DatagramSocket? = null
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var peerEndpoints: List<Pair<String, Int>> = emptyList()

    fun updatePeers(endpoints: Collection<Pair<String, Int>>) {
        peerEndpoints = endpoints.toList()
    }

    fun startSocketAndPlayback(): Int {
        if (udpSocket != null && _voiceState.value.isVoiceRunning) {
            return _voiceState.value.localUdpPort
        }
        return try {
            val socket = DatagramSocket(0)
            udpSocket = socket
            val boundPort = socket.localPort
            val minPlayBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_BYTES * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track
            track.play()

            _voiceState.update {
                it.copy(isVoiceRunning = true, localUdpPort = boundPort)
            }

            playbackJob = scope.launch {
                val buf = ByteArray(FRAME_BYTES * 2)
                val packet = DatagramPacket(buf, buf.size)
                while (isActive && !socket.isClosed) {
                    try {
                        socket.receive(packet)
                        if (_voiceState.value.isSpeakerEnabled && packet.length > 0) {
                            track.write(packet.data, 0, packet.length)
                            _voiceState.update { it.copy(remoteSpeaking = true) }
                        }
                    } catch (_: Exception) {
                        if (socket.isClosed) break
                    }
                }
            }
            boundPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start voice socket", e)
            0
        }
    }

    @SuppressLint("MissingPermission")
    fun setMicEnabled(enabled: Boolean) {
        if (enabled == _voiceState.value.isMicEnabled) return
        if (!enabled) {
            stopMicCapture()
            return
        }

        if (udpSocket == null) {
            startSocketAndPlayback()
        }

        val minRecBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES * 4)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return
            }
            audioRecord = recorder
            recorder.startRecording()
            _voiceState.update { it.copy(isMicEnabled = true) }

            captureJob = scope.launch {
                val pcmBuffer = ByteArray(FRAME_BYTES)
                while (isActive && _voiceState.value.isMicEnabled) {
                    val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read > 0) {
                        val level = computeRmsLevel(pcmBuffer, read)
                        _voiceState.update { it.copy(micLevel = level) }
                        val socket = udpSocket ?: continue
                        val targets = peerEndpoints
                        for ((ip, port) in targets) {
                            if (port > 0 && ip.isNotBlank()) {
                                runCatching {
                                    val addr = InetAddress.getByName(ip)
                                    val packet = DatagramPacket(pcmBuffer, read, addr, port)
                                    socket.send(packet)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            stopMicCapture()
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        _voiceState.update { it.copy(isSpeakerEnabled = enabled, remoteSpeaking = false) }
    }

    private fun stopMicCapture() {
        captureJob?.cancel()
        captureJob = null
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        _voiceState.update { it.copy(isMicEnabled = false, micLevel = 0f) }
    }

    fun stopAll() {
        stopMicCapture()
        playbackJob?.cancel()
        playbackJob = null
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }
        audioTrack = null
        runCatching { udpSocket?.close() }
        udpSocket = null
        _voiceState.value = VoiceChatState()
    }

    private fun computeRmsLevel(bytes: ByteArray, length: Int): Float {
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toDouble()
            sumSq += sample * sample
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sumSq / count) / 8000.0
        return rms.toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "LanVoiceChat"
        const val SAMPLE_RATE = 16000
        const val FRAME_BYTES = 640 // 20ms mono 16-bit PCM
    }
}
