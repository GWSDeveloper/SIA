package com.example.provider

import com.example.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalProvider : LlmProvider {

    override suspend fun generateResponse(context: PromptContext): Result<LlmCallResult> {
        return withContext(Dispatchers.Default) {
            val userText = context.currentUserMessage
            val matchedTools = ToolRegistry.matchLocalIntent(userText)

            if (matchedTools.isNotEmpty()) {
                val confirmations = matchedTools.map { call ->
                    when (call.toolName) {
                        "toggle_torch" -> if (call.toolParams["state"] == "off") "torch band kar di" else "torch on kar di"
                        "open_app" -> "${call.toolParams["app_name"] ?: "app"} open kar rahi hoon"
                        "set_volume" -> "volume ${call.toolParams["level"] ?: "50"}% par set kar diya"
                        "control_media" -> "media command execute kar diya"
                        "schedule_task" -> "reminder schedule kar diya"
                        "web_search" -> "web search kar rahi hoon"
                        "list_all_commands" -> "SIA commands list kar rahi hoon"
                        else -> "${call.toolName} execute kar rahi hoon"
                    }
                }

                val spokenConfirm = when (confirmations.size) {
                    1 -> "Haan, ${confirmations[0]}."
                    2 -> "Done! ${confirmations[0]}, aur ${confirmations[1]}."
                    else -> {
                        val allExceptLast = confirmations.dropLast(1).joinToString(", ")
                        "Done! $allExceptLast, aur ${confirmations.last()}."
                    }
                }

                return@withContext Result.success(
                    LlmCallResult(
                        textReply = spokenConfirm,
                        toolCalls = matchedTools
                    )
                )
            }

            // General conversational responses
            val lower = userText.lowercase()
            val reply = when {
                lower.contains("who are you") || lower.contains("kaun ho") || lower.contains("introduce") ->
                    "Namaste! Main hoon SIA, aapki personal Android AI assistant, developed by FAST. Main aapke phone ke controls, reminders, web search, aur everyday tasks me help kar sakti hoon!"
                lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                    "Hello! Kaise help kar sakti hoon aaj aapki?"
                lower.contains("how are you") || lower.contains("kaisi ho") ->
                    "Main badhiya hoon! Aap bataiye, phone me koi action perform karna hai ya reminder set karna hai?"
                lower.contains("thank") || lower.contains("shukriya") ->
                    "You're welcome! Anytime help chahiye ho toh bas bataiyega."
                else ->
                    "Main SIA hoon. Aap mujhe apps open karne, torch on/off karne, volume set karne, web search karne, ya reminders schedule karne ka command de sakte hain!"
            }

            Result.success(LlmCallResult(textReply = reply))
        }
    }
}
