package com.example.memory

import android.content.Context
import com.example.data.ConversationEntity
import com.example.data.ConversationSummaryEntity
import com.example.data.MemoryEntity
import com.example.data.SiaDatabase
import com.example.preferences.SiaPreferences
import com.example.provider.PromptContext
import com.example.provider.ProviderFactory
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MemoryManager(private val context: Context) {

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
     * AI compacts old conversation history and extracts stable profile facts separately.
     * Recent eight messages are kept. Old rows are deleted only after the summary is saved.
     */
    suspend fun checkAndSummarizeIfNeeded() = withContext(Dispatchers.IO) {
        val count = dao.getMessageCount()
        if (count <= 16) return@withContext
        val messages = dao.getRecentMessages(count.coerceAtMost(200)).reversed()
        if (messages.size <= 8) return@withContext
        val older = messages.dropLast(8)
        val existingSummary = dao.getSummary()?.summaryText.orEmpty()
        val transcript = older.joinToString("\n") { msg ->
            "${if (msg.sender == "user") "USER" else "SIA"}: ${msg.text}" +
                if (msg.toolName.isNullOrBlank()) "" else " [tool=${msg.toolName}; status=${msg.toolStatus}]"
        }.takeLast(24000)
        val currentProfile = dao.getAllMemories().joinToString("\n") { "${it.key}: ${it.content}" }
        val request = PromptContext(
            systemPrompt = "Return ONLY valid JSON with keys rolling_summary and permanent_profile. rolling_summary is a concise useful summary of conversation, preserving ongoing tasks, decisions, important entities and unresolved questions while removing chatter. permanent_profile is concise plain text containing ONLY stable user facts, name, communication style, enduring preferences, long-term interests and relationships explicitly stated. Never put conversation summaries, one-off tasks, temporary states, secrets, credentials, or guesses in permanent_profile. Preserve existing valid profile facts; do not invent anything.",
            permanentMemory = currentProfile,
            rollingSummary = existingSummary,
            recentMessages = emptyList(),
            currentUserMessage = "PREVIOUS SUMMARY:\n${existingSummary.ifBlank { "(none)" }}\n\nEXISTING PERMANENT PROFILE:\n${currentProfile.ifBlank { "(none)" }}\n\nOLDER MESSAGES:\n$transcript",
            availableTools = emptyList()
        )
        val result = ProviderFactory.createProvider(context).generateResponse(request)
        if (result.isFailure) return@withContext
        val raw = result.getOrThrow().textReply
        val json = raw.substringAfter("{", "").let { if (it.isBlank()) "" else "{$it" }
            .substringBeforeLast("}", "").let { if (it.isBlank()) "" else "$it}" }
        val parsed = try { JSONObject(json) } catch (_: Exception) { return@withContext }
        val summary = parsed.optString("rolling_summary").trim()
        val profile = parsed.optString("permanent_profile").trim()
        if (summary.isBlank()) return@withContext

        dao.insertOrUpdateSummary(ConversationSummaryEntity(
            id = 1, summaryText = summary, lastSummarizedMessageId = older.last().id,
            updatedAtMs = System.currentTimeMillis()
        ))
        if (profile.isNotBlank() && profile != currentProfile) {
            val existing = dao.getAllMemories().firstOrNull { it.key == "User Profile" }
            if (existing == null) dao.insertMemory(MemoryEntity(key = "User Profile", content = profile))
            else dao.updateMemory(existing.copy(content = profile, updatedAtMs = System.currentTimeMillis()))
        }
        dao.deleteMessagesUpTo(older.last().id)
        persistJsonSnapshot()
    }

    /** Persist summary plus the active transcript as readable JSON in app-private storage. */
    suspend fun persistJsonSnapshot() = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("summarized", dao.getSummary()?.summaryText.orEmpty())
        val array = org.json.JSONArray()
        dao.getRecentMessages(200).reversed().forEach { msg ->
            val item = JSONObject()
            item.put("role", msg.sender)
            item.put("content", msg.text)
            item.put("timestamp", msg.timestamp)
            if (!msg.toolName.isNullOrBlank()) item.put("tool", msg.toolName)
            if (!msg.toolStatus.isNullOrBlank()) item.put("tool_status", msg.toolStatus)
            array.put(item)
        }
        root.put("messages", array)
        context.openFileOutput("sia_chat_memory.json", Context.MODE_PRIVATE).use {
            it.write(root.toString(2).toByteArray(Charsets.UTF_8))
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
