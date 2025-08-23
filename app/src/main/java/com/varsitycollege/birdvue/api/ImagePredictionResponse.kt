package com.varsitycollege.birdvue.api // Or wherever you keep your API-related data classes

import com.google.gson.annotations.SerializedName

// Data class for each item in the "topK" array
data class TopKItem(
    @SerializedName("label") // Matches the JSON key "label"
    val label: String?,

    @SerializedName("score") // Matches the JSON key "score"
    val score: Double?
)

// Main data class for the entire prediction response
data class ImagePredictionResponse(
    @SerializedName("confidence") // Matches the JSON key "confidence"
    val confidence: Double?,

    @SerializedName("predicted_class") // Matches the JSON key "predicted_class"
    val predictedClass: String?, // Using camelCase for Kotlin convention, mapped by @SerializedName

    @SerializedName("topK") // Matches the JSON key "topK"
    val topK: List<TopKItem>? // A list of TopKItem objects
)
