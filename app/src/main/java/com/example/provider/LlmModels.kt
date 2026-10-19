package com.example.provider

import com.example.tools.ToolDefinition

data class PromptMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class PromptContext(
    val systemPrompt: String,
    val permanentMemory: String,
    val rollingSummary: String,
    val recentMessages: List<PromptMessage>,
    val currentUserMessage: String,
    val availableTools: List<ToolDefinition>
)

data class SingleToolCall(
    val toolName: String,
    val toolParams: Map<String, String> = emptyMap()
)

enum class TurnDirective { CONTINUE, FINAL }

data class LlmCallResult(
    val textReply: String,
    val toolCalls: List<SingleToolCall> = emptyList(),
    val directive: TurnDirective = TurnDirective.FINAL
) {
    // Backwards compatibility helper
    val toolCallName: String?
        get() = toolCalls.firstOrNull()?.toolName

    val toolCallParams: Map<String, String>
        get() = toolCalls.firstOrNull()?.toolParams ?: emptyMap()
}
