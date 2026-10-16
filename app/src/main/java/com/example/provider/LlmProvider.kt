package com.example.provider

interface LlmProvider {
    suspend fun generateResponse(context: PromptContext): Result<LlmCallResult>
}
