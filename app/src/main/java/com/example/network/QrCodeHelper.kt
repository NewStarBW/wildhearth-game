package com.example.network

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.model.GameMode
import com.example.model.SkinConfig
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder

sealed interface ScannedQrPayload {
    data class RoomInvite(
        val ip: String,
        val port: Int,
        val voicePort: Int,
        val roomName: String,
        val gameMode: GameMode,
        val hostId: String,
        val hostNickname: String,
        val hostEmail: String,
        val hostSkin: SkinConfig
    ) : ScannedQrPayload

    data class FriendCard(
        val friendId: String,
        val nickname: String,
        val email: String,
        val skin: SkinConfig
    ) : ScannedQrPayload
}

object QrCodeHelper {

    fun buildRoomQrUri(
        ip: String,
        port: Int,
        voicePort: Int,
        roomName: String,
        gameMode: GameMode,
        hostId: String,
        hostNickname: String,
        hostEmail: String,
        hostSkin: SkinConfig
    ): String {
        return buildString {
            append("wildhearth://connect?type=room")
            append("&ip=").append(enc(ip))
            append("&port=").append(port)
            append("&vport=").append(voicePort)
            append("&room=").append(enc(roomName))
            append("&mode=").append(enc(gameMode.name))
            append("&id=").append(enc(hostId))
            append("&nick=").append(enc(hostNickname))
            append("&email=").append(enc(hostEmail))
            append("&skin=").append(enc(hostSkin.toJson()))
        }
    }

    fun buildFriendCardQrUri(
        friendId: String,
        nickname: String,
        email: String,
        skin: SkinConfig
    ): String {
        return buildString {
            append("wildhearth://connect?type=friend")
            append("&id=").append(enc(friendId))
            append("&nick=").append(enc(nickname))
            append("&email=").append(enc(email))
            append("&skin=").append(enc(skin.toJson()))
        }
    }

    fun parseQrContent(raw: String): ScannedQrPayload? {
        return runCatching {
            val uri = Uri.parse(raw.trim())
            if (uri.scheme != "wildhearth") return null
            val type = uri.getQueryParameter("type") ?: return null
            val id = uri.getQueryParameter("id").orEmpty().ifBlank { "peer_${System.currentTimeMillis()}" }
            val nick = uri.getQueryParameter("nick").orEmpty().ifBlank { "荒原旅人" }
            val email = uri.getQueryParameter("email").orEmpty()
            val skin = uri.getQueryParameter("skin")?.let { SkinConfig.fromJson(it) } ?: SkinConfig()

            if (type == "room") {
                val ip = uri.getQueryParameter("ip").orEmpty()
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 0
                if (ip.isBlank() || port <= 0) return null
                val voicePort = uri.getQueryParameter("vport")?.toIntOrNull() ?: (port + 1)
                val roomName = uri.getQueryParameter("room").orEmpty().ifBlank { "${nick}的营地" }
                val modeName = uri.getQueryParameter("mode").orEmpty()
                val mode = GameMode.entries.firstOrNull { it.name == modeName } ?: GameMode.LAN_COOP
                ScannedQrPayload.RoomInvite(
                    ip = ip,
                    port = port,
                    voicePort = voicePort,
                    roomName = roomName,
                    gameMode = mode,
                    hostId = id,
                    hostNickname = nick,
                    hostEmail = email,
                    hostSkin = skin
                )
            } else if (type == "friend") {
                ScannedQrPayload.FriendCard(
                    friendId = id,
                    nickname = nick,
                    email = email,
                    skin = skin
                )
            } else {
                null
            }
        }.getOrNull()
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val darkColor = Color.parseColor("#111013")
        val lightColor = Color.parseColor("#EDE3D2")
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) darkColor else lightColor)
            }
        }
        return bitmap
    }

    suspend fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(image).await()
            barcodes.firstNotNullOfOrNull { it.rawValue }
        } finally {
            scanner.close()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
