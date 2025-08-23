package com.varsitycollege.birdvue.api

import com.google.gson.annotations.SerializedName

data class VerifyBirdImageResponse(
    @SerializedName("ok")
    val isOk: Boolean?, // Using isOk for Kotlin boolean convention

    @SerializedName("label")
    val label: String?, // Will be "Bird" or null

    @SerializedName("confidence")
    val confidence: Double?,

    @SerializedName("message")
    val message: String?,

    @SerializedName("error")
    val error: String? // e.g., "not_bird" or null
)
