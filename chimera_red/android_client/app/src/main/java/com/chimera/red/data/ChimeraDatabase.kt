package com.chimera.red.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LogEntity::class, NetworkEntity::class, BleDeviceEntity::class], version = 1)
abstract class ChimeraDatabase : RoomDatabase() {
    abstract fun dao(): ChimeraDao

    companion object {
        @Volatile
        private var INSTANCE: ChimeraDatabase? = null

        fun getDatabase(context: Context): ChimeraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChimeraDatabase::class.java,
                    "chimera_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
