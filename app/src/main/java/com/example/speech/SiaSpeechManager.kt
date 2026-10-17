package com.example.speech

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.permissions.SiaPermissionManager
import com.example.preferences.SiaPreferences
import com.example.preferences.VoiceGender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import com.example.tools.InstalledAppsHelper
import com.example.tools.ToolRegistry

enum class AssistantVisualState {
    IDLE,
    PREPARING,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    PAUSED,
    ERROR
}

data class TtsVoiceInfo(
    val name: String,
    val displayName: String,
    val locale: Locale,
    val languageLabel: String,
    val genderLabel: String?,
    val isNetworkConnectionRequired: Boolean,
    val isInstalled: Boolean,
    val qualityLabel: String
)

class SiaSpeechManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onRecognitionError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs = SiaPreferences.getInstance(context)
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var speechRecognizer: SpeechRecognizer? = null

    private var activeMediaPlayer: MediaPlayer? = null
    private var currentSarvamJob: Job? = null
    private var currentAudioFile: File? = null

    private val _visualState = MutableStateFlow(AssistantVisualState.IDLE)
    val visualState: StateFlow<AssistantVisualState> = _visualState.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _hasHindiVoice = MutableStateFlow(false)
    val hasHindiVoice: StateFlow<Boolean> = _hasHindiVoice.asStateFlow()

    private val _isUsingSarvam = MutableStateFlow(false)
    val isUsingSarvam: StateFlow<Boolean> = _isUsingSarvam.asStateFlow()

    private val _sarvamStatusMessage = MutableStateFlow("")
    val sarvamStatusMessage: StateFlow<String> = _sarvamStatusMessage.asStateFlow()

    private val _ttsDiagnosticMessage = MutableStateFlow("Initializing speech engine...")
    val ttsDiagnosticMessage: StateFlow<String> = _ttsDiagnosticMessage.asStateFlow()

    private val _lastSpokenText = MutableStateFlow("")
    val lastSpokenText: StateFlow<String> = _lastSpokenText.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceInfo>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceInfo>> = _availableVoices.asStateFlow()

    // Audio level reactivity (0.0 to 1.0) for live visualizer
    private val _rmsAudioLevel = MutableStateFlow(0f)
    val rmsAudioLevel: StateFlow<Float> = _rmsAudioLevel.asStateFlow()

    private var isCurrentlyListening = false
    var isSessionActive: Boolean = false
        private set
    private var consecutiveSilenceCount = 0
    private var activeSpeechSessionId: Long = 0L
    private var activeSpeechDoneCallback: (() -> Unit)? = null

    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    } catch (_: Exception) {
        null
    }

    init {
        resetSpeechRecognizer()
    }

    fun startSession() {
        isSessionActive = true
        consecutiveSilenceCount = 0
        startListening()
    }

    fun pauseSession(notifyUser: Boolean = true) {
        if (!isSessionActive) return
        stopCurrentAudioPlayback()
        tts?.stop()
        stopListeningInternal()
        _visualState.value = AssistantVisualState.PAUSED

        val wakePhrase = prefs.getWakeWordPhrase()
        val pauseMsg = "Listening paused. Say '$wakePhrase' or tap to resume."
        _lastSpokenText.value = pauseMsg

        if (notifyUser && prefs.isAutoSpeakEnabled()) {
            speak(pauseMsg) {
                if (isSessionActive && _visualState.value == AssistantVisualState.PAUSED) {
                    startListeningForWakeWord()
                }
            }
        } else {
            startListeningForWakeWord()
        }
    }

    fun resumeSession() {
        isSessionActive = true
        consecutiveSilenceCount = 0
        stopCurrentAudioPlayback()
        tts?.stop()
        _visualState.value = AssistantVisualState.LISTENING
        startListening()
    }

    fun stopSession() {
        isSessionActive = false
        consecutiveSilenceCount = 0
        stopSpeaking()
        stopListeningInternal()
        _visualState.value = AssistantVisualState.IDLE
    }

    private fun resetSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            } catch (e: Exception) {
                Log.e("SiaSpeechManager", "Failed to create SpeechRecognizer", e)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (_visualState.value != AssistantVisualState.PAUSED) {
                    _visualState.value = AssistantVisualState.LISTENING
                }
                isCurrentlyListening = true
            }

            override fun onBeginningOfSpeech() {
                if (_visualState.value != AssistantVisualState.PAUSED) {
                    _visualState.value = AssistantVisualState.LISTENING
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (_visualState.value != AssistantVisualState.PAUSED) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _rmsAudioLevel.value = normalized
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (_visualState.value != AssistantVisualState.PAUSED) {
                    _visualState.value = AssistantVisualState.THINKING
                }
                _rmsAudioLevel.value = 0f
                isCurrentlyListening = false
            }

            override fun onError(error: Int) {
                isCurrentlyListening = false
                _rmsAudioLevel.value = 0f

                // In PAUSED mode, quietly re-arm wake word detection without raising loud errors
                if (_visualState.value == AssistantVisualState.PAUSED) {
                    if (isSessionActive) {
                        mainHandler.postDelayed({
                            if (isSessionActive && _visualState.value == AssistantVisualState.PAUSED) {
                                startListeningForWakeWord()
                            }
                        }, 500)
                    }
                    return
                }

                // In active continuous session:
                if (isSessionActive && _visualState.value == AssistantVisualState.LISTENING) {
                    if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                        consecutiveSilenceCount++
                        if (consecutiveSilenceCount < 3) {
                            mainHandler.postDelayed({
                                if (isSessionActive && _visualState.value == AssistantVisualState.LISTENING) {
                                    startListening()
                                }
                            }, 300)
                            return
                        } else {
                            // After prolonged inactivity, automatically pause listening to preserve battery
                            pauseSession(notifyUser = false)
                            onRecognitionError("Listening paused due to silence. Say '${prefs.getWakeWordPhrase()}' or tap to resume.")
                            return
                        }
                    } else if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        resetSpeechRecognizer()
                        mainHandler.postDelayed({
                            if (isSessionActive && _visualState.value == AssistantVisualState.LISTENING) {
                                startListening()
                            }
                        }, 400)
                        return
                    }
                }

                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Tap or speak to retry."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Tap to retry."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue in voice recognition."
                    SpeechRecognizer.ERROR_CLIENT -> "Voice recognition reset. Tap to speak."
                    else -> "Recognition stopped ($error)."
                }

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    isSessionActive = false
                    _visualState.value = AssistantVisualState.ERROR
                } else {
                    _visualState.value = AssistantVisualState.IDLE
                }
                onRecognitionError(message)
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                _rmsAudioLevel.value = 0f
                val rawMatches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                val text = selectBestRecognitionHypothesis(rawMatches)

                // 1. If currently in PAUSED state, filter exclusively for the wake phrase
                if (_visualState.value == AssistantVisualState.PAUSED) {
                    if (text.isNotBlank()) {
                        val wakePhrase = prefs.getWakeWordPhrase().trim().lowercase(Locale.ROOT)
                        val lower = text.lowercase(Locale.ROOT)
                        if (lower.contains(wakePhrase)) {
                            // Wake phrase recognized while paused!
                            consecutiveSilenceCount = 0
                            val cleanCommand = lower.substringAfter(wakePhrase).trim().removePrefix(",").trim()
                            if (cleanCommand.isNotBlank()) {
                                _visualState.value = AssistantVisualState.THINKING
                                onSpeechRecognized(cleanCommand)
                            } else {
                                _visualState.value = AssistantVisualState.LISTENING
                                speak("Haanji, boliye?") {
                                    if (isSessionActive && _visualState.value != AssistantVisualState.PAUSED) {
                                        startListening()
                                    }
                                }
                            }
                            return
                        }
                    }
                    // Non-wake speech while paused is quietly ignored
                    mainHandler.postDelayed({
                        if (isSessionActive && _visualState.value == AssistantVisualState.PAUSED) {
                            startListeningForWakeWord()
                        }
                    }, 500)
                    return
                }

                // 2. Active Session processing
                if (text.isNotBlank()) {
                    val stopPhrase = prefs.getStopPhrase().trim().lowercase(Locale.ROOT)
                    val lower = text.lowercase(Locale.ROOT)

                    // Check configured STOP phrase: pauses session, never closes SIA
                    if (stopPhrase.isNotBlank() && lower.contains(stopPhrase)) {
                        pauseSession(notifyUser = true)
                        return
                    }

                    // Check configured WAKE phrase when spoken while already active
                    val wakePhrase = prefs.getWakeWordPhrase().trim().lowercase(Locale.ROOT)
                    if (wakePhrase.isNotBlank() && (lower == wakePhrase || lower.startsWith(wakePhrase))) {
                        val cleanCommand = lower.removePrefix(wakePhrase).trim().removePrefix(",").trim()
                        if (cleanCommand.isNotBlank()) {
                            consecutiveSilenceCount = 0
                            _visualState.value = AssistantVisualState.THINKING
                            onSpeechRecognized(cleanCommand)
                        } else {
                            speak("Haanji, boliye?") {
                                if (isSessionActive && _visualState.value != AssistantVisualState.PAUSED) {
                                    startListening()
                                }
                            }
                        }
                        return
                    }

                    // Normal command
                    consecutiveSilenceCount = 0
                    _visualState.value = AssistantVisualState.THINKING
                    onSpeechRecognized(text)
                } else {
                    if (isSessionActive && _visualState.value == AssistantVisualState.LISTENING) {
                        consecutiveSilenceCount++
                        if (consecutiveSilenceCount < 3) {
                            mainHandler.postDelayed({
                                if (isSessionActive && _visualState.value == AssistantVisualState.LISTENING) {
                                    startListening()
                                }
                            }, 300)
                        } else {
                            pauseSession(notifyUser = false)
                            onRecognitionError("Listening paused due to silence. Say '${prefs.getWakeWordPhrase()}' or tap to resume.")
                        }
                    } else {
                        _visualState.value = AssistantVisualState.IDLE
                        onRecognitionError("No speech recognized. Tap to try again.")
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank() && _visualState.value != AssistantVisualState.PAUSED) {
                    _lastSpokenText.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening() }
            return
        }

        if (!SiaPermissionManager.hasRecordAudioPermission(context)) {
            if (!isSessionActive) {
                _visualState.value = AssistantVisualState.ERROR
            }
            onRecognitionError("Microphone permission is required to listen.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            if (!isSessionActive) {
                _visualState.value = AssistantVisualState.IDLE
            }
            onRecognitionError("Voice recognition engine not found on this device. Please use keyboard chat.")
            return
        }

        if (isCurrentlyListening) {
            return
        }

        if (_visualState.value == AssistantVisualState.SPEAKING) {
            stopSpeaking()
        }

        if (_visualState.value != AssistantVisualState.LISTENING) {
            _visualState.value = AssistantVisualState.PREPARING
        }

        val langPref = prefs.getLanguagePreference()
        // Hinglish defaults to en-IN so speech results are more likely to be Roman text.
        // Explicit Hindi mode remains hi-IN; explicit English mode remains en-IN.
        val primaryLang = when {
            langPref.equals("Hindi", ignoreCase = true) -> "hi-IN"
            langPref.equals("English", ignoreCase = true) -> "en-IN"
            else -> "en-IN"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Dynamic language switching & detection where supported
            try {
                putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
            } catch (_: Exception) {}

            // Dynamic biasing strings for installed app names
            try {
                val appLabels = InstalledAppsHelper.getInstalledAppLabels(context)
                if (appLabels.isNotEmpty()) {
                    putExtra("android.speech.extra.BIASING_STRINGS", appLabels.toTypedArray())
                }
            } catch (_: Exception) {}
        }

        try {
            resetSpeechRecognizer()
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isCurrentlyListening = false
            _visualState.value = AssistantVisualState.IDLE
            onRecognitionError("Failed to start voice listener: ${e.message}")
        }
    }

    private fun startListeningForWakeWord() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListeningForWakeWord() }
            return
        }

        if (!isSessionActive || _visualState.value != AssistantVisualState.PAUSED) return
        if (!SiaPermissionManager.hasRecordAudioPermission(context)) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        if (isCurrentlyListening) return

        val langPref = prefs.getLanguagePreference()
        val primaryLang = when {
            langPref.contains("Hindi", ignoreCase = true) -> "hi-IN"
            langPref.contains("English", ignoreCase = true) -> "en-IN"
            else -> "hi-IN"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLang)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            resetSpeechRecognizer()
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            isCurrentlyListening = false
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isCurrentlyListening = false
        _rmsAudioLevel.value = 0f
    }

    fun stopListening() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopListening() }
            return
        }
        stopListeningInternal()
        if (_visualState.value == AssistantVisualState.LISTENING || _visualState.value == AssistantVisualState.PREPARING) {
            _visualState.value = AssistantVisualState.IDLE
        }
    }

    fun setVisualState(state: AssistantVisualState) {
        _visualState.value = state
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                setupTtsEngine(engine)
                _isTtsReady.value = true
            }
        }
    }

    private fun setupTtsEngine(engine: TextToSpeech) {
        val voicesList = mutableListOf<TtsVoiceInfo>()
        var foundHindi = false

        try {
            val systemVoices = engine.voices
            if (systemVoices != null && systemVoices.isNotEmpty()) {
                for (voice in systemVoices) {
                    val lang = voice.locale.language.lowercase(Locale.ROOT)
                    val country = voice.locale.country.uppercase(Locale.ROOT)
                    val isHindi = lang == "hi"
                    val isIndianEnglish = lang == "en" && country == "IN"
                    val isStandardEnglish = lang == "en" && (country == "US" || country == "GB" || country.isEmpty())

                    if (isHindi) foundHindi = true

                    if (isHindi || isIndianEnglish || isStandardEnglish) {
                        val quality = if (voice.quality >= Voice.QUALITY_VERY_HIGH) "Neural HD" else if (voice.quality >= Voice.QUALITY_HIGH) "High" else "Standard"
                        val notInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
                        val requiresNetwork = voice.isNetworkConnectionRequired

                        val detectedGender = detectGenuineVoiceGender(voice)

                        val langLabel = when {
                            isHindi -> "Hindi (India)"
                            isIndianEnglish -> "Indian English"
                            else -> "English (${if (country.isNotBlank()) country else "US"})"
                        }

                        val cleanId = voice.name.substringAfterLast("/").substringAfterLast("-").take(14)
                        val display = if (detectedGender != null) {
                            "$langLabel • $detectedGender ($quality • $cleanId)"
                        } else {
                            "$langLabel ($quality • $cleanId)"
                        }

                        voicesList.add(
                            TtsVoiceInfo(
                                name = voice.name,
                                displayName = display,
                                locale = voice.locale,
                                languageLabel = langLabel,
                                genderLabel = detectedGender,
                                isNetworkConnectionRequired = requiresNetwork,
                                isInstalled = !notInstalled,
                                qualityLabel = quality
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        _availableVoices.value = voicesList
        _hasHindiVoice.value = foundHindi

        if (foundHindi) {
            _ttsDiagnosticMessage.value = "Genuine Hindi voice pack is installed and active on this device."
            engine.setLanguage(Locale.forLanguageTag("hi-IN"))
        } else {
            val enInResult = engine.setLanguage(Locale.forLanguageTag("en-IN"))
            if (enInResult == TextToSpeech.LANG_MISSING_DATA || enInResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            _ttsDiagnosticMessage.value = "Hindi voice pack not found on device. Install voice data in Android Text-to-Speech settings for authentic Hindi pronunciation."
        }

        applySelectedVoiceAndPitch(engine)

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _visualState.value = AssistantVisualState.SPEAKING
            }
            override fun onDone(utteranceId: String?) {
                val utteranceSessionId = utteranceId?.substringAfter("sia_utterance_")?.substringBefore("_")?.toLongOrNull() ?: activeSpeechSessionId
                mainHandler.post {
                    onSpeechPlaybackCompleted(utteranceSessionId)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val utteranceSessionId = utteranceId?.substringAfter("sia_utterance_")?.substringBefore("_")?.toLongOrNull() ?: activeSpeechSessionId
                mainHandler.post {
                    onSpeechPlaybackFailed(utteranceSessionId, "TTS engine error")
                }
            }
        })
    }

    /**
     * Unified speech-completion contract for both Sarvam AI streaming and native Android TTS.
     * Transitions from SPEAKING to LISTENING automatically when the continuous session is active.
     * Never restarts listening when the session is PAUSED or CLOSED.
     */
    fun onSpeechPlaybackCompleted(sessionId: Long) {
        // Protect against stale completion callbacks from previous speech sessions
        if (sessionId != activeSpeechSessionId) {
            return
        }

        val cb = activeSpeechDoneCallback
        activeSpeechDoneCallback = null
        cb?.invoke()

        if (isSessionActive && _visualState.value != AssistantVisualState.PAUSED) {
            _visualState.value = AssistantVisualState.LISTENING
            startListening()
        } else if (_visualState.value != AssistantVisualState.PAUSED) {
            _visualState.value = AssistantVisualState.IDLE
        }
    }

    fun onSpeechPlaybackFailed(sessionId: Long, error: String) {
        if (sessionId != activeSpeechSessionId) {
            return
        }

        val cb = activeSpeechDoneCallback
        activeSpeechDoneCallback = null
        cb?.invoke()

        if (isSessionActive && _visualState.value != AssistantVisualState.PAUSED) {
            _visualState.value = AssistantVisualState.LISTENING
            startListening()
        } else if (_visualState.value != AssistantVisualState.PAUSED) {
            _visualState.value = AssistantVisualState.IDLE
        }
    }

    private fun detectGenuineVoiceGender(voice: Voice): String? {
        val features = voice.features ?: emptySet()
        val nameLower = voice.name.lowercase(Locale.ROOT)

        if (features.contains("#female") || features.contains("gender=female") || nameLower.contains("female")) {
            return "Female"
        }
        if (features.contains("#male") || features.contains("gender=male") || (nameLower.contains("male") && !nameLower.contains("female"))) {
            return "Male"
        }
        // Google Text-to-Speech standard naming convention
        if (nameLower.contains("-x-hia") || nameLower.contains("-x-hic") || nameLower.contains("-x-ena") || nameLower.contains("-x-enc")) {
            return "Female"
        }
        if (nameLower.contains("-x-hid") || nameLower.contains("-x-hie") || nameLower.contains("-x-end") || nameLower.contains("-x-ene")) {
            return "Male"
        }
        return null
    }

    private fun applySelectedVoiceAndPitch(engine: TextToSpeech, preferHindi: Boolean = false) {
        val selectedVoiceName = prefs.getSelectedVoiceName()

        if (selectedVoiceName.isNotBlank()) {
            try {
                val match = engine.voices?.firstOrNull { it.name == selectedVoiceName }
                if (match != null) {
                    engine.voice = match
                }
            } catch (_: Exception) {}
        } else {
            try {
                val systemVoices = engine.voices.orEmpty()
                if (systemVoices.isNotEmpty()) {
                    val hindiVoices = systemVoices.filter { it.locale.language.equals("hi", ignoreCase = true) }
                    val femaleHindiVoices = hindiVoices.filter { detectGenuineVoiceGender(it) == "Female" }
                    val candidates = when {
                        femaleHindiVoices.isNotEmpty() -> femaleHindiVoices
                        hindiVoices.isNotEmpty() -> hindiVoices
                        else -> systemVoices.filter {
                            it.locale.language.equals("en", ignoreCase = true) &&
                                it.locale.country.equals("IN", ignoreCase = true)
                        }
                    }
                    val bestVoice = candidates.sortedWith(
                        compareByDescending<Voice> { it.quality >= Voice.QUALITY_VERY_HIGH }
                            .thenByDescending { it.quality >= Voice.QUALITY_HIGH }
                            .thenByDescending { it.isNetworkConnectionRequired }
                            .thenByDescending { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                    ).firstOrNull()
                    if (bestVoice != null) {
                        engine.voice = bestVoice
                        engine.language = bestVoice.locale
                    } else {
                        engine.setLanguage(Locale.forLanguageTag("hi-IN"))
                    }
                } else {
                    engine.setLanguage(Locale.forLanguageTag("hi-IN"))
                }
            } catch (_: Exception) {
                try { engine.setLanguage(Locale.forLanguageTag("hi-IN")) } catch (_: Exception) {}
            }
        }

        engine.setPitch(prefs.getSpeechPitch())
        engine.setSpeechRate(prefs.getSpeechRate())
    }

    fun applyVoiceSettings() {
        tts?.let { engine ->
            applySelectedVoiceAndPitch(engine)
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!prefs.isAutoSpeakEnabled() || text.isBlank()) {
            onDone?.invoke()
            return
        }

        val cleanText = cleanSpeechText(text)
        if (cleanText.isBlank()) {
            onDone?.invoke()
            return
        }

        // Increment session ID to immediately invalidate any in-flight playback/synthesis from previous calls
        val currentSessionId = ++activeSpeechSessionId
        stopCurrentAudioPlayback()
        tts?.stop()

        _lastSpokenText.value = cleanText
        activeSpeechDoneCallback = onDone

        val sarvamKey = prefs.getSarvamApiKey()
        val sarvamEnabled = prefs.isSarvamTtsEnabled()

        if (sarvamEnabled && sarvamKey.isNotBlank()) {
            _isUsingSarvam.value = true
            _visualState.value = AssistantVisualState.PREPARING

            val chunks = splitIntoSpeechChunks(cleanText, maxChunkSize = 750)
            val speaker = prefs.getSarvamSpeaker()
            val model = prefs.getSarvamModel()
            val pace = prefs.getSarvamPace()

            currentSarvamJob = managerScope.launch {
                var failedChunkIndex: Int? = null

                for ((index, chunk) in chunks.withIndex()) {
                    if (!isActive || currentSessionId != activeSpeechSessionId) return@launch

                    _visualState.value = AssistantVisualState.PREPARING
                    val result = SarvamTtsClient.synthesizeSpeechToFile(
                        context = context,
                        apiKey = sarvamKey,
                        text = chunk,
                        speaker = speaker,
                        model = model,
                        targetLanguageCode = "hi-IN",
                        pace = pace
                    )

                    if (!isActive || currentSessionId != activeSpeechSessionId) return@launch

                    when (result) {
                        is SarvamTtsResult.Success -> {
                            _sarvamStatusMessage.value = "Sarvam AI Active (${index + 1}/${chunks.size})"
                            val playSuccess = playAudioFileSuspend(result.audioFile, currentSessionId)
                            if (!playSuccess && isActive && currentSessionId == activeSpeechSessionId) {
                                Log.w("SiaSpeechManager", "MediaPlayer playback failed on chunk $index. Falling back to Android TTS for remaining chunks.")
                                failedChunkIndex = index
                                break
                            }
                        }
                        is SarvamTtsResult.Error -> {
                            Log.w("SiaSpeechManager", "Sarvam TTS failed on chunk $index: ${result.message}. Falling back to Android TTS for remaining chunks.")
                            _sarvamStatusMessage.value = "Sarvam error: ${result.message} (Fallback active)"
                            _isUsingSarvam.value = false
                            failedChunkIndex = index
                            break
                        }
                    }
                }

                if (!isActive || currentSessionId != activeSpeechSessionId) return@launch

                if (failedChunkIndex != null) {
                    // Fallback policy: only speak the remaining unplayed chunks with Android TTS
                    val remainingText = chunks.subList(failedChunkIndex, chunks.size).joinToString(" ")
                    if (remainingText.isNotBlank()) {
                        speakWithAndroidTtsFallback(remainingText, currentSessionId)
                    } else {
                        onSpeechPlaybackCompleted(currentSessionId)
                    }
                } else {
                    // All Sarvam audio chunks have finished playing: trigger unified completion contract
                    onSpeechPlaybackCompleted(currentSessionId)
                }
            }
        } else {
            _isUsingSarvam.value = false
            speakWithAndroidTtsFallback(cleanText, currentSessionId)
        }
    }

    private fun speakWithAndroidTtsFallback(cleanText: String, sessionId: Long) {
        val preparedText = HindiSpeechTextPreparer.prepareForSpeech(cleanText)
        val containsHindi = preparedText.any { it in '\u0900'..'\u097F' } || HindiSpeechTextPreparer.isHindiOrHinglish(cleanText)

        _lastSpokenText.value = preparedText
        _visualState.value = AssistantVisualState.SPEAKING

        tts?.let { engine ->
            applySelectedVoiceAndPitch(engine, preferHindi = containsHindi)
            val utteranceId = "sia_utterance_${sessionId}_${System.currentTimeMillis()}"
            engine.speak(preparedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } ?: run {
            onSpeechPlaybackCompleted(sessionId)
        }
    }

    private suspend fun playAudioFileSuspend(audioFile: File, sessionId: Long): Boolean = suspendCancellableCoroutine { continuation ->
        if (sessionId != activeSpeechSessionId) {
            try { audioFile.delete() } catch (_: Exception) {}
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        val player = MediaPlayer()
        activeMediaPlayer = player
        currentAudioFile = audioFile
        _visualState.value = AssistantVisualState.SPEAKING

        val hasResumed = AtomicBoolean(false)

        fun safeResume(result: Boolean) {
            if (hasResumed.compareAndSet(false, true)) {
                try {
                    player.setOnPreparedListener(null)
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                    player.release()
                } catch (_: Exception) {}

                if (activeMediaPlayer === player) {
                    activeMediaPlayer = null
                }
                try {
                    audioFile.delete()
                } catch (_: Exception) {}
                if (currentAudioFile === audioFile) {
                    currentAudioFile = null
                }

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }

        player.setOnPreparedListener { mp ->
            try {
                if (sessionId == activeSpeechSessionId) {
                    mp.start()
                } else {
                    safeResume(false)
                }
            } catch (_: Exception) {
                safeResume(false)
            }
        }

        player.setOnCompletionListener {
            safeResume(true)
        }

        player.setOnErrorListener { _, what, extra ->
            Log.w("SiaSpeechManager", "MediaPlayer error ($what, $extra)")
            safeResume(false)
            true
        }

        continuation.invokeOnCancellation {
            safeResume(false)
        }

        try {
            player.setDataSource(audioFile.absolutePath)
            player.prepareAsync()
        } catch (_: Exception) {
            safeResume(false)
        }
    }

    private fun cleanupActivePlayer() {
        try {
            activeMediaPlayer?.setOnPreparedListener(null)
            activeMediaPlayer?.setOnCompletionListener(null)
            activeMediaPlayer?.setOnErrorListener(null)
            if (activeMediaPlayer?.isPlaying == true) {
                activeMediaPlayer?.stop()
            }
            activeMediaPlayer?.reset()
            activeMediaPlayer?.release()
        } catch (_: Exception) {}
        activeMediaPlayer = null

        try {
            currentAudioFile?.delete()
        } catch (_: Exception) {}
        currentAudioFile = null
    }

    private fun stopCurrentAudioPlayback() {
        currentSarvamJob?.cancel()
        currentSarvamJob = null
        cleanupActivePlayer()
    }

    private fun selectBestRecognitionHypothesis(matches: List<String>): String {
        if (matches.isEmpty()) return ""
        val first = matches[0].trim()
        if (matches.size == 1) return first

        // If top match is conversational, preserve it exactly as spoken
        val lowerFirst = first.lowercase(Locale.ROOT)
        if (lowerFirst.contains("batao") || lowerFirst.contains("samjhao") || lowerFirst.contains("ke baare mein") || lowerFirst.contains("who are you") || lowerFirst.contains("tell me")) {
            return first
        }

        // 1. If first match resolves cleanly as a supported local action, use it
        val firstRes = ToolRegistry.resolveCommand(first)
        if (firstRes is ToolRegistry.CommandResolution.LocalActions) {
            return first
        }

        // 2. Check if an alternative hypothesis resolves cleanly as a supported local action
        for (i in 1 until matches.size) {
            val alt = matches[i].trim()
            val altRes = ToolRegistry.resolveCommand(alt)
            if (altRes is ToolRegistry.CommandResolution.LocalActions) {
                return alt
            }
        }

        // 3. If there is an app opening verb and installed apps can disambiguate the candidate
        val openVerbs = listOf("open", "kholo", "khol do", "chalao", "chala do", "launch", "start", "run")
        if (openVerbs.any { lowerFirst.contains(it) }) {
            val installedApps = InstalledAppsHelper.getInstalledAppLabels(context)
            if (installedApps.isNotEmpty()) {
                for (candidateUtterance in matches) {
                    val candidateApp = candidateUtterance
                        .replace(Regex("""\b(please|zara|bhai|kar dena|kardo|kar do|karo|karna|karein|kijiye|chala dena|chalado|chala do|chalao|khol dena|kholdo|khol do|kholo|open|launch|start|run|app|application)\b""", RegexOption.IGNORE_CASE), " ")
                        .trim()
                    val matchedApp = InstalledAppsHelper.findMatchingInstalledApp(candidateApp, installedApps)
                    if (matchedApp != null) {
                        return "Open $matchedApp"
                    }
                }
            }
        }

        return first
    }

    fun testVoice(sampleText: String = "Namaste! Main SIA hoon, aapki personal AI assistant. Kaise help kar sakti hoon?") {
        speak(sampleText)
    }

    fun openTtsSystemSettings(context: Context) {
        val intents = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        try {
            val fallback = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }

    fun stopSpeaking() {
        ++activeSpeechSessionId
        stopCurrentAudioPlayback()
        tts?.stop()
        if (_visualState.value == AssistantVisualState.SPEAKING || _visualState.value == AssistantVisualState.PREPARING) {
            _visualState.value = AssistantVisualState.IDLE
        }
    }

    fun destroy() {
        try {
            stopSpeaking()
            managerScope.cancel()
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
            toneGenerator?.release()
            SarvamTtsClient.cleanOldAudioCache(context.cacheDir)
        } catch (_: Exception) {}
    }

    companion object {
        fun cleanSpeechText(text: String): String {
            if (text.isBlank()) return ""

            // 1. Remove markdown code blocks first (e.g. ```json ... ```)
            val withoutCodeBlocks = text.replace(Regex("""```(?:[a-zA-Z0-9_-]+)?[\s\S]*?```"""), "")

            // 2. Strip JSON tool calls (including nested braces and brackets)
            val sb = StringBuilder()
            var i = 0
            val n = withoutCodeBlocks.length
            while (i < n) {
                if (withoutCodeBlocks[i] == '{' || withoutCodeBlocks[i] == '[') {
                    val openChar = withoutCodeBlocks[i]
                    val closeChar = if (openChar == '{') '}' else ']'
                    var depth = 1
                    var j = i + 1
                    var inString = false
                    var escape = false
                    while (j < n && depth > 0) {
                        val c = withoutCodeBlocks[j]
                        if (escape) {
                            escape = false
                        } else if (c == '\\') {
                            escape = true
                        } else if (c == '"') {
                            inString = !inString
                        } else if (!inString) {
                            if (c == openChar) depth++
                            else if (c == closeChar) depth--
                        }
                        j++
                    }
                    if (depth == 0) {
                        val candidate = withoutCodeBlocks.substring(i, j)
                        if (candidate.contains("\"tool\"") || candidate.contains("'tool'") || candidate.contains("\"action\"")) {
                            i = j
                            continue
                        }
                    }
                }
                sb.append(withoutCodeBlocks[i])
                i++
            }

            // 3. Remove markdown symbols and trim
            return sb.toString()
                .replace(Regex("""[*#_`~]"""), "")
                .trim()
        }

        /**
         * Splits text into coherent sentences/clauses for sequential TTS playback.
         * Respects punctuation (period, exclamation, question mark, Hindi purna viram, newline),
         * ensuring no chunk exceeds maxChunkSize and original sentence order is strictly preserved.
         */
        fun splitIntoSpeechChunks(text: String, maxChunkSize: Int = 750): List<String> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            if (trimmed.length <= maxChunkSize) return listOf(trimmed)

            // Split on sentence-ending boundaries: period, question mark, exclamation, Hindi danda/purna viram (।), newline
            val sentenceRegex = Regex("""(?<=[.!?।\n])\s+""")
            val rawSentences = trimmed.split(sentenceRegex).map { it.trim() }.filter { it.isNotEmpty() }

            val chunks = mutableListOf<String>()
            val currentChunk = StringBuilder()

            for (sentence in rawSentences) {
                if (sentence.length > maxChunkSize) {
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk.clear()
                    }
                    val subParts = splitLongSentence(sentence, maxChunkSize)
                    chunks.addAll(subParts)
                } else {
                    if (currentChunk.isNotEmpty() && currentChunk.length + sentence.length + 1 > maxChunkSize) {
                        chunks.add(currentChunk.toString().trim())
                        currentChunk.clear()
                    }
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append(" ")
                    }
                    currentChunk.append(sentence)
                }
            }

            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())
            }

            return chunks.filter { it.isNotBlank() }
        }

        private fun splitLongSentence(sentence: String, maxChunkSize: Int): List<String> {
            val result = mutableListOf<String>()
            val words = sentence.split(" ")
            val sb = StringBuilder()
            for (w in words) {
                if (sb.isNotEmpty() && sb.length + w.length + 1 > maxChunkSize) {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(w)
            }
            if (sb.isNotEmpty()) {
                result.add(sb.toString().trim())
            }
            return result
        }
    }
}
