package com.varsitycollege.birdvue.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bird_cache")
data class BirdCacheEntry(
    @PrimaryKey val birdName: String,
    val description: String,
    val timestamp: Long // cache expiry
)
