package com.example

import com.example.model.CharacterArchetype
import com.example.model.GameMode
import com.example.model.HatStyle
import com.example.model.SkinConfig
import com.example.model.WorldSavePayload
import com.example.network.QrCodeHelper
import com.example.network.ScannedQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GameAndQrUnitTest {

    @Test
    fun roomQrUri_roundTrip_preservesHostAndSkin() {
        val skin = SkinConfig(
            id = "custom_1",
            name = "Night Hunter",
            archetype = CharacterArchetype.VALKYRIE.name,
            hatStyle = HatStyle.VIKING_HELM.name,
            coatColorHex = "#8E3B2E"
        )
        val uri = QrCodeHelper.buildRoomQrUri(
            ip = "192.168.1.42",
            port = 51234,
            voicePort = 51235,
            roomName = "林间篝火",
            gameMode = GameMode.LAN_PVP,
            hostId = "srv_abc",
            hostNickname = "Lina",
            hostEmail = "lina@example.com",
            hostSkin = skin
        )
        val parsed = QrCodeHelper.parseQrContent(uri)
        assertTrue(parsed is ScannedQrPayload.RoomInvite)
        val room = parsed as ScannedQrPayload.RoomInvite
        assertEquals("192.168.1.42", room.ip)
        assertEquals(51234, room.port)
        assertEquals(51235, room.voicePort)
        assertEquals(GameMode.LAN_PVP, room.gameMode)
        assertEquals("lina@example.com", room.hostEmail)
        assertEquals("Night Hunter", room.hostSkin.name)
    }

    @Test
    fun worldSavePayload_jsonRoundTrip_preservesDayAndInventory() {
        val payload = WorldSavePayload(
            seed = 999L,
            worldName = "极夜营地",
            gameMode = GameMode.OFFLINE_SOLO.name,
            dayCount = 7,
            health = 82,
            hunger = 64,
            sanity = 91
        )
        val restored = WorldSavePayload.fromJson(payload.toJson())
        assertEquals(7, restored?.dayCount)
        assertEquals(82, restored?.health)
        assertEquals("极夜营地", restored?.worldName)
    }
}
