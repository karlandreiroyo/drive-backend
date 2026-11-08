package com.example.d_r_i_v_e.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, TripIncidentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripIncidentDao(): TripIncidentDao

    companion object {
        private const val DB_NAME = "drive_offline.db"

        @Volatile
        private var instance: DriveDatabase? = null

        fun getInstance(context: Context): DriveDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DriveDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
    }
}
