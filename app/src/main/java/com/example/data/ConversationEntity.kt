package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_messages")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user", "assistant", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolStatus: String? = null, // "SUCCESS", "PERMISSION_REQUIRED", "FAILED", etc.
    val toolResult: String? = null,
    val isSpoken: Boolean = false
)
