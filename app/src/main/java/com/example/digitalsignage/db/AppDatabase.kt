package com.example.digitalsignage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.digitalsignage.model.CampaignFile
import com.example.digitalsignage.model.DefaultList

@Database(entities = [CampaignFile::class, DefaultList::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun signageDao(): SignageDoa

    companion object {
        private lateinit var instance: AppDatabase

        @JvmStatic
        fun getDatabase(context: Context): AppDatabase {
            if (::instance.isInitialized.not()) {
                synchronized(AppDatabase::class.java) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "signage-db"
                    ).build()
                }
            }
            return instance
        }

    }
}