package com.varsitycollege.birdvue.data

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val text: String,
    val sender: SenderType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SenderType {
    USER, AI
}
