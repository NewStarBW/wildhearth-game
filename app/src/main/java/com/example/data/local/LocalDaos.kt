package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SurvivorProfileDao {
    @Query("SELECT * FROM survivor_profile WHERE id = 'local_survivor' LIMIT 1")
    fun observeProfile(): Flow<SurvivorProfileEntity?>

    @Query("SELECT * FROM survivor_profile WHERE id = 'local_survivor' LIMIT 1")
    suspend fun getProfileOnce(): SurvivorProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: SurvivorProfileEntity)
}

@Dao
interface WorldSaveDao {
    @Query("SELECT * FROM world_saves ORDER BY updatedAt DESC")
    fun observeAllSaves(): Flow<List<WorldSaveEntity>>

    @Query("SELECT * FROM world_saves WHERE id = :id LIMIT 1")
    suspend fun getSaveById(id: String): WorldSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSave(save: WorldSaveEntity)

    @Query("DELETE FROM world_saves WHERE id = :id")
    suspend fun deleteSave(id: String)
}

@Dao
interface SkinPresetDao {
    @Query("SELECT * FROM skin_presets ORDER BY updatedAt DESC")
    fun observeAllSkins(): Flow<List<SkinPresetEntity>>

    @Query("SELECT * FROM skin_presets WHERE id = :id LIMIT 1")
    suspend fun getSkinById(id: String): SkinPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkin(skin: SkinPresetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(skins: List<SkinPresetEntity>)

    @Query("DELETE FROM skin_presets WHERE id = :id")
    suspend fun deleteSkin(id: String)
}

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY addedAt DESC")
    fun observeFriends(): Flow<List<FriendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFriend(friend: FriendEntity)

    @Query("DELETE FROM friends WHERE friendId = :friendId")
    suspend fun deleteFriend(friendId: String)
}
