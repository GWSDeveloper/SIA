package com.example.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LlmProviderType(val displayName: String, val defaultModel: String, val baseUrl: String) {
    GEMINI("Google Gemini", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
    GROQ("Groq", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1"),
    OPENROUTER("OpenRouter", "google/gemini-2.5-flash", "https://openrouter.ai/api/v1"),
    NIM("NVIDIA NIM", "meta/llama-3.1-70b-instruct", "https://integrate.api.nvidia.com/v1"),
    LOCAL_OFFLINE("Local On-Device Engine", "local-rule-engine", "")
}

enum class VoiceGender(val displayName: String) {
    FEMALE("Female"),
    MALE("Male")
}

class SiaPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sia_user_preferences", Context.MODE_PRIVATE)

    private val _assistantName = MutableStateFlow(getAssistantName())
    val assistantName: StateFlow<String> = _assistantName.asStateFlow()

    private val _selectedProvider = MutableStateFlow(getSelectedProvider())
    val selectedProvider: StateFlow<LlmProviderType> = _selectedProvider.asStateFlow()

    private val _modelName = MutableStateFlow(getModelName())
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _autoSpeak = MutableStateFlow(isAutoSpeakEnabled())
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _languagePreference = MutableStateFlow(getLanguagePreference())
    val languagePreference: StateFlow<String> = _languagePreference.asStateFlow()

    private val _persistentNotificationEnabled = MutableStateFlow(isPersistentNotificationEnabled())
    val persistentNotificationEnabled: StateFlow<Boolean> = _persistentNotificationEnabled.asStateFlow()

    private val _voiceGender = MutableStateFlow(getVoiceGender())
    val voiceGender: StateFlow<VoiceGender> = _voiceGender.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow(getSelectedVoiceName())
    val selectedVoiceName: StateFlow<String> = _selectedVoiceName.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(isWakeWordEnabled())
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    fun getUserName(): String = prefs.getString("user_name", "")?.trim().orEmpty()

    fun setUserName(name: String) { prefs.edit().putString("user_name", name.trim()).apply() }

    fun getAssistantName(): String = prefs.getString("assistant_name", "SIA") ?: "SIA"

    fun setAssistantName(name: String) {
        val trimmed = name.trim().ifEmpty { "SIA" }
        prefs.edit().putString("assistant_name", trimmed).apply()
        _assistantName.value = trimmed
    }

    fun getSelectedProvider(): LlmProviderType {
        val name = prefs.getString("provider_type", LlmProviderType.GEMINI.name)
        return try {
            LlmProviderType.valueOf(name ?: LlmProviderType.GEMINI.name)
        } catch (_: Exception) {
            LlmProviderType.GEMINI
        }
    }

    fun setSelectedProvider(provider: LlmProviderType) {
        val previous = getSelectedProvider()
        if (!prefs.contains("model_name_${previous.name}") && prefs.contains("model_name")) {
            prefs.edit().putString("model_name_${previous.name}", prefs.getString("model_name", previous.defaultModel)).apply()
        }
        prefs.edit().putString("provider_type", provider.name).apply()
        _selectedProvider.value = provider
        _modelName.value = getModelName(provider)
    }

    fun getModelName(provider: LlmProviderType = getSelectedProvider()): String {
        val key = "model_name_${provider.name}"
        if (prefs.contains(key)) return prefs.getString(key, provider.defaultModel) ?: provider.defaultModel
        if (provider == getSelectedProvider() && prefs.contains("model_name")) {
            return prefs.getString("model_name", provider.defaultModel) ?: provider.defaultModel
        }
        return provider.defaultModel
    }

    fun setModelName(model: String, provider: LlmProviderType = getSelectedProvider()) {
        val trimmed = model.trim().ifEmpty { provider.defaultModel }
        prefs.edit().putString("model_name_${provider.name}", trimmed).apply()
        if (provider == getSelectedProvider()) {
            prefs.edit().putString("model_name", trimmed).apply()
            _modelName.value = trimmed
        }
    }

    fun getApiKey(provider: LlmProviderType): String {
        val customKey = prefs.getString("api_key_${provider.name}", "") ?: ""
        if (customKey.isNotBlank()) return customKey
        if (provider == LlmProviderType.GEMINI) {
            val buildConfigKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (_: Exception) {
                ""
            }
            if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
                return buildConfigKey
            }
        }
        return ""
    }

    fun setApiKey(provider: LlmProviderType, key: String) {
        prefs.edit().putString("api_key_${provider.name}", key.trim()).apply()
    }

    fun isAutoSpeakEnabled(): Boolean = prefs.getBoolean("auto_speak", true)

    fun setAutoSpeakEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_speak", enabled).apply()
        _autoSpeak.value = enabled
    }

    fun getLanguagePreference(): String =
        prefs.getString("language_pref", "Hinglish (Hindi + English)") ?: "Hinglish (Hindi + English)"

    fun setLanguagePreference(language: String) {
        prefs.edit().putString("language_pref", language).apply()
        _languagePreference.value = language
    }

    fun isPersistentNotificationEnabled(): Boolean =
        prefs.getBoolean("persistent_notification_enabled", true)

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("persistent_notification_enabled", enabled).apply()
        _persistentNotificationEnabled.value = enabled
    }

    // Voice Gender (Female vs Male)
    fun getVoiceGender(): VoiceGender {
        val name = prefs.getString("voice_gender", VoiceGender.FEMALE.name)
        return try {
            VoiceGender.valueOf(name ?: VoiceGender.FEMALE.name)
        } catch (_: Exception) {
            VoiceGender.FEMALE
        }
    }

    fun setVoiceGender(gender: VoiceGender) {
        prefs.edit().putString("voice_gender", gender.name).apply()
        _voiceGender.value = gender
    }

    // Specific selected voice identifier
    fun getSelectedVoiceName(): String = prefs.getString("selected_voice_name", "") ?: ""

    fun setSelectedVoiceName(voiceName: String) {
        prefs.edit().putString("selected_voice_name", voiceName).apply()
        _selectedVoiceName.value = voiceName
    }

    fun getSpeechPitch(): Float {
        return prefs.getFloat("speech_pitch", 1.0f)
    }

    fun setSpeechPitch(pitch: Float) {
        prefs.edit().putFloat("speech_pitch", pitch).apply()
    }

    fun getSpeechRate(): Float = prefs.getFloat("speech_rate", 1.0f)

    fun setSpeechRate(rate: Float) {
        prefs.edit().putFloat("speech_rate", rate).apply()
    }

    // Hands-Free Wake Word settings
    fun isWakeWordEnabled(): Boolean = prefs.getBoolean("wake_word_enabled", false)

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
        _wakeWordEnabled.value = enabled
    }

    fun getWakeWordPhrase(): String = prefs.getString("wake_word_phrase", "Hey SIA") ?: "Hey SIA"

    fun setWakeWordPhrase(phrase: String) {
        prefs.edit().putString("wake_word_phrase", phrase.trim().ifEmpty { "Hey SIA" }).apply()
    }

    fun getStopPhrase(): String = prefs.getString("stop_phrase", "Stop listening") ?: "Stop listening"

    fun setStopPhrase(phrase: String) {
        prefs.edit().putString("stop_phrase", phrase.trim().ifEmpty { "Stop listening" }).apply()
    }

    // Gnani AI speech-to-text configuration
    fun getGnaniApiKey(): String = prefs.getString("gnani_stt_api_key", "") ?: ""

    fun setGnaniApiKey(key: String) {
        prefs.edit().putString("gnani_stt_api_key", key.trim()).apply()
    }

    // Sarvam AI Text-to-Speech configuration
    fun isSarvamTtsEnabled(): Boolean = prefs.getBoolean("sarvam_tts_enabled", false)

    fun setSarvamTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sarvam_tts_enabled", enabled).apply()
    }

    fun getSarvamApiKey(): String {
        val customKey = prefs.getString("sarvam_api_key", "") ?: ""
        if (customKey.isNotBlank()) return customKey
        val buildConfigKey = try {
            BuildConfig.SARVAM_API_KEY
        } catch (_: Exception) {
            ""
        }
        if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "MY_SARVAM_API_KEY") {
            return buildConfigKey
        }
        return ""
    }

    fun setSarvamApiKey(key: String) {
        prefs.edit().putString("sarvam_api_key", key.trim()).apply()
    }

    // Tavily web search configuration
    fun getTavilyApiKey(): String = prefs.getString("tavily_api_key", "") ?: ""

    fun setTavilyApiKey(key: String) {
        prefs.edit().putString("tavily_api_key", key.trim()).apply()
    }

    fun getSarvamSpeaker(): String =
        prefs.getString("sarvam_speaker", "suman_hi_companion") ?: "suman_hi_companion"

    fun setSarvamSpeaker(speaker: String) {
        prefs.edit().putString("sarvam_speaker", speaker.trim().ifEmpty { "suman_hi_companion" }).apply()
    }

    fun getSarvamModel(): String =
        prefs.getString("sarvam_model", "bulbul:v4-flash") ?: "bulbul:v4-flash"

    fun setSarvamModel(model: String) {
        prefs.edit().putString("sarvam_model", model.trim().ifEmpty { "bulbul:v4-flash" }).apply()
    }

    fun getSarvamPace(): Float = prefs.getFloat("sarvam_pace", 1.0f)

    fun setSarvamPace(pace: Float) {
        prefs.edit().putFloat("sarvam_pace", pace.coerceIn(0.5f, 2.0f)).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SiaPreferences? = null

        fun getInstance(context: Context): SiaPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SiaPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
