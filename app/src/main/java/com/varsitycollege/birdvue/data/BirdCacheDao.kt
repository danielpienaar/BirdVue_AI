package com.varsitycollege.birdvue.data


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BirdCacheDao {

    @Query("SELECT * FROM bird_cache WHERE birdName = :birdName")
    suspend fun getBirdByName(birdName: String): BirdCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Replace if entry already exists
    suspend fun insertBird(bird: BirdCacheEntry)

    @Query("DELETE FROM bird_cache WHERE birdName = :birdName")
    suspend fun deleteBirdByName(birdName: String)

    // Query to get the count of entries
    @Query("SELECT COUNT(*) FROM bird_cache")
    suspend fun getCount(): Int

    // Query to get the oldest entry (lowest timestamp)
    @Query("SELECT * FROM bird_cache ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestEntry(): BirdCacheEntry?

    // Query to delete entries older than a specific timestamp
    @Query("DELETE FROM bird_cache WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    // Query to get all entries (useful for debugging or other purposes)
    @Query("SELECT * FROM bird_cache ORDER BY timestamp DESC")
    suspend fun getAllEntries(): List<BirdCacheEntry>

}