package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_summaries")
data class ConversationSummaryEntity(
    @PrimaryKey
    val id: Int = 1,
    val summaryText: String,
    val lastSummarizedMessageId: Long,
    val updatedAtMs: Long = System.currentTimeMillis()
)
