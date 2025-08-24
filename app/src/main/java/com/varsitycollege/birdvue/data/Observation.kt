package com.varsitycollege.birdvue.data

import android.os.Parcelable // Import Parcelable
import kotlinx.parcelize.Parcelize // Import Parcelize

@Parcelize
data class Observation(
    val id: String ?= null,
    val birdName: String ?= null,
    val date: String ?= null,
    val photo: String ?= null,
    val details: String ?= null,
    val lat: Double ?= null,
    val lng: Double ?= null,
    val location: String ?= null,
    val likes: Int ?= null,
    val comments: List<Comment> ?= null,
    val userId: String ?= null,
    var userName: String ?= null
) : Parcelable

