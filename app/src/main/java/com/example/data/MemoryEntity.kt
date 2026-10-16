package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "permanent_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val key: String, // e.g. "language_preference", "user_habit", "favorite_music"
    val content: String, // natural language statement
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
