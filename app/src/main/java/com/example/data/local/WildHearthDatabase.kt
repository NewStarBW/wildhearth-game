package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SurvivorProfileEntity::class,
        WorldSaveEntity::class,
        SkinPresetEntity::class,
        FriendEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WildHearthDatabase : RoomDatabase() {
    abstract fun survivorProfileDao(): SurvivorProfileDao
    abstract fun worldSaveDao(): WorldSaveDao
    abstract fun skinPresetDao(): SkinPresetDao
    abstract fun friendDao(): FriendDao

    companion object {
        @Volatile
        private var instance: WildHearthDatabase? = null

        fun getInstance(context: Context): WildHearthDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WildHearthDatabase::class.java,
                    "wildhearth_offline.db"
                ).build().also { instance = it }
            }
        }
    }
}
