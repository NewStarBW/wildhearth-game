package com.example.data.local

import com.example.model.SkinConfig
import com.example.model.SkinPalettes
import com.example.model.WorldSavePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalRepository(
    private val db: WildHearthDatabase
) {
    private val profileDao = db.survivorProfileDao()
    private val saveDao = db.worldSaveDao()
    private val skinDao = db.skinPresetDao()
    private val friendDao = db.friendDao()

    val observeProfile: Flow<SurvivorProfileEntity?> = profileDao.observeProfile()
    val observeAllSaves: Flow<List<WorldSaveEntity>> = saveDao.observeAllSaves()
    val observeAllSkins: Flow<List<SkinConfig>> = skinDao.observeAllSkins().map { list ->
        list.map { it.toModel() }
    }
    val observeFriends: Flow<List<FriendEntity>> = friendDao.observeFriends()

    suspend fun ensureDefaultSkins() {
        val defaults = SkinPalettes.defaultPresets.map { skin ->
            SkinPresetEntity(
                id = skin.id,
                name = skin.name,
                archetype = skin.archetype,
                hatStyle = skin.hatStyle,
                coatColorHex = skin.coatColorHex,
                hairColorHex = skin.hairColorHex,
                skinToneHex = skin.skinToneHex,
                accessory = skin.accessory,
                updatedAt = 1000L
            )
        }
        skinDao.insertDefaults(defaults)
    }

    suspend fun saveLocalProfile(
        nickname: String,
        email: String,
        activeSkinId: String = "preset_scholar"
    ): SurvivorProfileEntity {
        val existing = profileDao.getProfileOnce()
        val entity = SurvivorProfileEntity(
            id = "local_survivor",
            localPlayerId = existing?.localPlayerId ?: "srv_${UUID.randomUUID().toString().take(8)}",
            nickname = nickname.trim(),
            email = email.trim(),
            activeSkinId = existing?.activeSkinId ?: activeSkinId,
            updatedAt = System.currentTimeMillis()
        )
        profileDao.upsertProfile(entity)
        return entity
    }

    suspend fun setActiveSkin(skinId: String) {
        val existing = profileDao.getProfileOnce() ?: return
        profileDao.upsertProfile(existing.copy(activeSkinId = skinId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveSkinPreset(skin: SkinConfig) {
        skinDao.upsertSkin(
            SkinPresetEntity(
                id = skin.id,
                name = skin.name,
                archetype = skin.archetype,
                hatStyle = skin.hatStyle,
                coatColorHex = skin.coatColorHex,
                hairColorHex = skin.hairColorHex,
                skinToneHex = skin.skinToneHex,
                accessory = skin.accessory,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSkinPreset(skinId: String) {
        skinDao.deleteSkin(skinId)
    }

    suspend fun getSkinById(skinId: String): SkinConfig? = skinDao.getSkinById(skinId)?.toModel()

    suspend fun autoSaveWorld(
        slotId: String,
        worldName: String,
        gameMode: String,
        dayCount: Int,
        timePhase: String,
        health: Int,
        hunger: Int,
        sanity: Int,
        payload: WorldSavePayload
    ) {
        saveDao.upsertSave(
            WorldSaveEntity(
                id = slotId,
                worldName = worldName,
                gameMode = gameMode,
                dayCount = dayCount,
                timePhase = timePhase,
                health = health,
                hunger = hunger,
                sanity = sanity,
                payloadJson = payload.toJson(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun loadWorldSave(slotId: String): WorldSaveEntity? = saveDao.getSaveById(slotId)

    suspend fun deleteWorldSave(slotId: String) = saveDao.deleteSave(slotId)

    suspend fun addOrUpdateFriend(
        friendId: String,
        nickname: String,
        email: String,
        lastIp: String = "",
        lastPort: Int = 0,
        skin: SkinConfig = SkinConfig()
    ) {
        friendDao.upsertFriend(
            FriendEntity(
                friendId = friendId,
                nickname = nickname,
                email = email,
                lastIp = lastIp,
                lastPort = lastPort,
                skinJson = skin.toJson(),
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteFriend(friendId: String) = friendDao.deleteFriend(friendId)
}

private fun SkinPresetEntity.toModel() = SkinConfig(
    id = id,
    name = name,
    archetype = archetype,
    hatStyle = hatStyle,
    coatColorHex = coatColorHex,
    hairColorHex = hairColorHex,
    skinToneHex = skinToneHex,
    accessory = accessory
)
