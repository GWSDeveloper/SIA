package com.example.memory

import android.content.Context
import com.example.data.ConversationEntity
import com.example.data.ConversationSummaryEntity
import com.example.data.MemoryEntity
import com.example.data.SiaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MemoryManager(context: Context) {

    private val db = SiaDatabase.getDatabase(context)
    private val dao = db.siaDao()

    val permanentMemoriesFlow: Flow<List<MemoryEntity>> = dao.getAllMemoriesFlow()
    val rollingSummaryFlow: Flow<ConversationSummaryEntity?> = dao.getSummaryFlow()

    suspend fun getPermanentMemoryText(): String = withContext(Dispatchers.IO) {
        val memories = dao.getAllMemories()
        if (memories.isEmpty()) return@withContext ""
        memories.joinToString("\n") { "• ${it.key}: ${it.content}" }
    }

    suspend fun addPermanentMemory(key: String, content: String): Long = withContext(Dispatchers.IO) {
        dao.insertMemory(
            MemoryEntity(
                key = key.trim(),
                content = content.trim(),
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun updatePermanentMemory(id: Long, key: String, content: String) = withContext(Dispatchers.IO) {
        dao.updateMemory(
            MemoryEntity(
                id = id,
                key = key.trim(),
                content = content.trim(),
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deletePermanentMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        dao.deleteMemory(memory)
    }

    suspend fun clearPermanentMemory() = withContext(Dispatchers.IO) {
        dao.clearAllMemories()
    }

    suspend fun getRollingSummaryText(): String = withContext(Dispatchers.IO) {
        dao.getSummary()?.summaryText ?: ""
    }

    /**
     * Checks if conversation exceeds 15 messages; if so, produces a compact summary
     * of older context while preserving recent messages in active window.
     */
    suspend fun checkAndSummarizeIfNeeded() = withContext(Dispatchers.IO) {
        val count = dao.getMessageCount()
        if (count > 16) {
            val allMessages = dao.getRecentMessages(25).reversed()
            val olderMessages = allMessages.take(allMessages.size - 8)
            if (olderMessages.isNotEmpty()) {
                val summaryBullets = olderMessages.takeLast(6).map {
                    val sender = if (it.sender == "user") "User" else "SIA"
                    val snippet = it.text.take(60).replace("\n", " ")
                    val toolNote = if (!it.toolName.isNullOrBlank()) " [Action: ${it.toolName} -> ${it.toolStatus}]" else ""
                    "- $sender: $snippet$toolNote"
                }.joinToString("\n")

                val existing = dao.getSummary()?.summaryText ?: ""
                val merged = if (existing.isNotBlank()) {
                    "$existing\n$summaryBullets".takeLast(500)
                } else {
                    summaryBullets
                }

                dao.insertOrUpdateSummary(
                    ConversationSummaryEntity(
                        id = 1,
                        summaryText = merged,
                        lastSummarizedMessageId = olderMessages.last().id,
                        updatedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun initializeDefaultMemoriesIfEmpty() = withContext(Dispatchers.IO) {
        val existing = dao.getAllMemories()
        if (existing.isEmpty()) {
            addPermanentMemory(
                key = "Language Preference",
                content = "User prefers communicating in Hindi, Hinglish, or English naturally."
            )
            addPermanentMemory(
                key = "Interaction Style",
                content = "Prefers concise voice responses with clear visual action indicators."
            )
        }
    }
}
