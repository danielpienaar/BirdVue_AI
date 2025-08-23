package com.varsitycollege.birdvue.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BirdCacheEntry::class], version = 1, exportSchema = false) // Set exportSchema to true for production apps
abstract class BirdInfoDatabase : RoomDatabase() {

    abstract fun birdCacheDao(): BirdCacheDao

    companion object {
        @Volatile
        private var INSTANCE: BirdInfoDatabase? = null

        private const val MAX_CACHE_SIZE = 100
        const val CACHE_EXPIRY_DAYS = 3 // Clear entries older than N days

        fun getDatabase(context: Context): BirdInfoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BirdInfoDatabase::class.java,
                    "bird_info_database"
                )
                    // You might want to add migrations if you change the schema later
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // --- Cache Management Logic (can be placed here or in a repository) ---

        suspend fun insertAndManageCache(context: Context, entry: BirdCacheEntry) {
            val dao = getDatabase(context).birdCacheDao()
            dao.insertBird(entry) // Insert or replace the new entry

            // Check if cache size exceeds limit
            val currentSize = dao.getCount()
            if (currentSize > MAX_CACHE_SIZE) {
                // Delete the oldest entry
                dao.getOldestEntry()?.let { oldest ->
                    dao.deleteBirdByName(oldest.birdName)
                }
            }
        }

        suspend fun clearExpiredCache(context: Context) {
            val dao = getDatabase(context).birdCacheDao()
            val expiryTime = System.currentTimeMillis() - (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
            dao.deleteOlderThan(expiryTime)
        }
    }
}
