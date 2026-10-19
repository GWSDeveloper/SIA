package com.example.provider

import android.content.Context
import com.example.preferences.LlmProviderType
import com.example.preferences.SiaPreferences

private class FallbackProvider(private val providers: List<Pair<String, LlmProvider>>) : LlmProvider {
    override suspend fun generateResponse(context: PromptContext): Result<LlmCallResult> {
        val errors = mutableListOf<String>()
        for ((name, provider) in providers) {
            val result = provider.generateResponse(context)
            if (result.isSuccess) return result
            errors += "$name: ${result.exceptionOrNull()?.message ?: "request failed"}"
        }
        return Result.failure(IllegalStateException("All configured AI providers failed. " + errors.joinToString(" | ")))
    }
}

object ProviderFactory {
    fun createProvider(context: Context): LlmProvider {
        val prefs = SiaPreferences.getInstance(context)
        val selected = prefs.getSelectedProvider()
        val order = listOf(selected) + listOf(LlmProviderType.NIM, LlmProviderType.OPENROUTER, LlmProviderType.GROQ, LlmProviderType.GEMINI).filter { it != selected }
        val providers = order.distinct().mapNotNull { type ->
            val key = prefs.getApiKey(type)
            if (type == LlmProviderType.LOCAL_OFFLINE || key.isBlank()) return@mapNotNull null
            val model = prefs.getModelName(type)
            val provider: LlmProvider = when (type) {
                LlmProviderType.GEMINI -> GeminiProvider(key, model)
                LlmProviderType.GROQ -> OpenAiCompatibleProvider(key, type.baseUrl, model, "Groq")
                LlmProviderType.OPENROUTER -> OpenAiCompatibleProvider(key, type.baseUrl, model, "OpenRouter")
                LlmProviderType.NIM -> OpenAiCompatibleProvider(key, type.baseUrl, model, "NVIDIA NIM")
                LlmProviderType.LOCAL_OFFLINE -> return@mapNotNull null
            }
            type.displayName to provider
        }
        return if (providers.isEmpty()) LocalProvider() else FallbackProvider(providers)
    }
}
