package com.varsitycollege.birdvue.data

import android.os.Parcelable // Import Parcelable
import kotlinx.parcelize.Parcelize // Import Parcelize

@Parcelize
data class Comment(
    val id: String,
    val userId: String,
    val content: String
) : Parcelable
