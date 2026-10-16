package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        ScheduledTaskEntity::class,
        MemoryEntity::class,
        ConversationSummaryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SiaDatabase : RoomDatabase() {

    abstract fun siaDao(): SiaDao

    companion object {
        @Volatile
        private var INSTANCE: SiaDatabase? = null

        fun getDatabase(context: Context): SiaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SiaDatabase::class.java,
                    "sia_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
