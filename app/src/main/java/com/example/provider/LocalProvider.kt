package com.example.provider

/**
 * Honest fallback used when no remote LLM provider is configured.
 * This deliberately does not parse commands or pretend to be an LLM.
 */
class LocalProvider : LlmProvider {
    override suspend fun generateResponse(context: PromptContext): Result<LlmCallResult> {
        return Result.failure(
            IllegalStateException(
                "No AI provider is configured. Add a valid provider API key in SIA Settings, or select a configured provider."
            )
        )
    }
}
