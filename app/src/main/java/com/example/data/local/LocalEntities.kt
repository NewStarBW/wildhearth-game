package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "survivor_profile")
data class SurvivorProfileEntity(
    @PrimaryKey val id: String = "local_survivor",
    val localPlayerId: String,
    val nickname: String,
    val email: String,
    val activeSkinId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "world_saves")
data class WorldSaveEntity(
    @PrimaryKey val id: String,
    val worldName: String,
    val gameMode: String,
    val dayCount: Int,
    val timePhase: String,
    val health: Int,
    val hunger: Int,
    val sanity: Int,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "skin_presets")
data class SkinPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val archetype: String,
    val hatStyle: String,
    val coatColorHex: String,
    val hairColorHex: String,
    val skinToneHex: String,
    val accessory: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val friendId: String,
    val nickname: String,
    val email: String,
    val lastIp: String = "",
    val lastPort: Int = 0,
    val skinJson: String,
    val addedAt: Long = System.currentTimeMillis()
)
