package com.example.provider

import android.content.Context
import com.example.preferences.LlmProviderType
import com.example.preferences.SiaPreferences

object ProviderFactory {

    fun createProvider(context: Context): LlmProvider {
        val prefs = SiaPreferences.getInstance(context)
        val providerType = prefs.getSelectedProvider()
        val apiKey = prefs.getApiKey(providerType)
        val model = prefs.getModelName()

        return when (providerType) {
            LlmProviderType.GEMINI -> {
                if (apiKey.isBlank()) {
                    LocalProvider()
                } else {
                    GeminiProvider(apiKey, model)
                }
            }
            LlmProviderType.GROQ -> {
                if (apiKey.isBlank()) {
                    LocalProvider()
                } else {
                    OpenAiCompatibleProvider(
                        apiKey = apiKey,
                        baseUrl = LlmProviderType.GROQ.baseUrl,
                        modelName = model,
                        providerName = "Groq"
                    )
                }
            }
            LlmProviderType.OPENROUTER -> {
                if (apiKey.isBlank()) {
                    LocalProvider()
                } else {
                    OpenAiCompatibleProvider(
                        apiKey = apiKey,
                        baseUrl = LlmProviderType.OPENROUTER.baseUrl,
                        modelName = model,
                        providerName = "OpenRouter"
                    )
                }
            }
            LlmProviderType.NIM -> {
                if (apiKey.isBlank()) {
                    LocalProvider()
                } else {
                    OpenAiCompatibleProvider(
                        apiKey = apiKey,
                        baseUrl = LlmProviderType.NIM.baseUrl,
                        modelName = model,
                        providerName = "NVIDIA NIM"
                    )
                }
            }
            LlmProviderType.LOCAL_OFFLINE -> LocalProvider()
        }
    }
}
