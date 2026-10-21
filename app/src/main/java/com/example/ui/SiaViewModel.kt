package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConversationEntity
import com.example.data.SiaDatabase
import com.example.memory.MemoryManager
import com.example.preferences.SiaPreferences
import com.example.provider.LlmCallResult
import com.example.provider.PromptContext
import com.example.provider.PromptMessage
import com.example.provider.ProviderFactory
import com.example.provider.SingleToolCall
import com.example.speech.AssistantVisualState
import com.example.speech.HindiSpeechTextPreparer
import com.example.speech.SiaSpeechManager
import com.example.tools.AndroidActionExecutor
import com.example.tools.ToolExecutionResult
import com.example.tools.ToolExecutionCoordinator
import com.example.tools.ToolRegistry
import com.example.tools.ToolResultStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class MainScreenTab {
    WORKSPACE,
    AI_AGENT,
    SETTINGS
}

enum class AgentMode {
    ANIMATION,
    CHAT
}

class SiaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SiaDatabase.getDatabase(application)
    private val dao = db.siaDao()
    val preferences = SiaPreferences.getInstance(application)
    val memoryManager = MemoryManager(application)
    private val actionExecutor = AndroidActionExecutor(application)
    @Volatile private var pendingContactChoices: List<Pair<String, String>> = emptyList()

    // Current navigation tab
    private val _currentTab = MutableStateFlow(MainScreenTab.AI_AGENT)
    val currentTab: StateFlow<MainScreenTab> = _currentTab.asStateFlow()

    // Agent mode: ANIMATION vs CHAT
    private val _agentMode = MutableStateFlow(AgentMode.ANIMATION)
    val agentMode: StateFlow<AgentMode> = _agentMode.asStateFlow()

    // Continuous conversation stream
    val conversationMessages: StateFlow<List<ConversationEntity>> = dao.getAllMessagesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Visual state for the animated orb
    private val _visualState = MutableStateFlow(AssistantVisualState.IDLE)
    val visualState: StateFlow<AssistantVisualState> = _visualState.asStateFlow()

    // Current status text
    private val _statusText = MutableStateFlow("Tap the orb to speak or type below")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Last spoken / displayed assistant response
    private val _lastAssistantReply = MutableStateFlow("")
    val lastAssistantReply: StateFlow<String> = _lastAssistantReply.asStateFlow()

    // Text field input
    private val _currentInputText = MutableStateFlow("")
    val currentInputText: StateFlow<String> = _currentInputText.asStateFlow()

    // Speech manager
    val speechManager: SiaSpeechManager = SiaSpeechManager(
        context = application,
        onSpeechRecognized = { text ->
            processUserMessage(text, isFromVoice = true)
        },
        onRecognitionError = { err ->
            _statusText.value = err
            _visualState.value = AssistantVisualState.IDLE
        }
    )

    val isUsingSarvam: StateFlow<Boolean> = speechManager.isUsingSarvam

    init {
        // Sync speech manager visual state to view model
        viewModelScope.launch {
            speechManager.visualState.collect { state ->
                if (_visualState.value != AssistantVisualState.EXECUTING) {
                    _visualState.value = state
                    when (state) {
                        AssistantVisualState.PREPARING -> {
                            if (speechManager.isUsingSarvam.value) {
                                _statusText.value = "Generating Sarvam voice stream..."
                            } else {
                                _statusText.value = "Preparing microphone..."
                            }
                        }
                        AssistantVisualState.LISTENING -> _statusText.value = "Listening now! Speak in Hindi, Hinglish, or English..."
                        AssistantVisualState.THINKING -> _statusText.value = "Processing request..."
                        AssistantVisualState.SPEAKING -> _statusText.value = "Speaking response..."
                        AssistantVisualState.PAUSED -> {
                            val wake = preferences.getWakeWordPhrase()
                            _statusText.value = "Listening paused. Say '$wake' or tap orb to resume"
                        }
                        AssistantVisualState.IDLE -> {
                            if (_statusText.value.contains("Listening") || _statusText.value.contains("Preparing") || _statusText.value.contains("Generating")) {
                                _statusText.value = "Ready for next command"
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun setTab(tab: MainScreenTab) {
        _currentTab.value = tab
    }

    fun setAgentMode(mode: AgentMode) {
        _agentMode.value = mode
    }

    fun setInputText(text: String) {
        _currentInputText.value = text
    }

    fun startVoiceInteraction() {
        _statusText.value = "Starting assistant session..."
        speechManager.startSession()
    }

    fun pauseVoiceInteraction() {
        _statusText.value = "Session paused."
        speechManager.pauseSession(notifyUser = true)
    }

    fun resumeVoiceInteraction() {
        _statusText.value = "Resuming listening..."
        speechManager.resumeSession()
    }

    fun stopVoiceInteraction() {
        speechManager.stopSession()
        _statusText.value = "Ready"
    }

    fun submitTextMessage() {
        val text = _currentInputText.value.trim()
        if (text.isNotBlank()) {
            _currentInputText.value = ""
            processUserMessage(text, isFromVoice = false)
        }
    }

    fun processUserMessage(userInput: String, isFromVoice: Boolean) {
        if (userInput.isBlank()) return

        // Resolve a number choice directly after SIA asks which saved contact number to call.
        val pendingChoices = pendingContactChoices
        if (pendingChoices.isNotEmpty()) {
            val requestedDigits = userInput.filter(Char::isDigit)
            val lowerChoice = userInput.lowercase(Locale.ROOT)
            val selected = when {
                requestedDigits.length >= 7 -> pendingChoices.firstOrNull { (_, number) ->
                    val digits = number.filter(Char::isDigit)
                    digits == requestedDigits || digits.endsWith(requestedDigits) || requestedDigits.endsWith(digits)
                }
                listOf("second", "2nd", "number two", "doosra", "dusra", "सेकंड", "दूसरा", "दूसरे").any { lowerChoice.contains(it) } -> pendingChoices.getOrNull(1)
                listOf("first", "1st", "number one", "pehla", "पहला", "पहली", "फर्स्ट").any { lowerChoice.contains(it) } -> pendingChoices.firstOrNull()
                else -> null
            }
            if (selected != null) {
                pendingContactChoices = emptyList()
                viewModelScope.launch {
                    dao.insertMessage(ConversationEntity(sender = "user", text = userInput, timestamp = System.currentTimeMillis()))
                    val result = actionExecutor.executeTool("contact_agent", mapOf("action" to "call_number", "number" to selected.second))
                    val reply = when (result.status) {
                        ToolResultStatus.SUCCESS -> "Theek hai, " + selected.first + " ko call laga rahi hoon."
                        ToolResultStatus.USER_CONFIRMATION_REQUIRED -> "Dialer khol diya hai, call karne ke liye Call button daba dena."
                        else -> "Ye number contact mein nahi mila. Ek baar phir se number bol de."
                    }
                    handleConversationalReply(reply, isFromVoice)
                }
                return
            }
        }

        viewModelScope.launch {
            _visualState.value = AssistantVisualState.THINKING
            _statusText.value = "Processing request..."

            // 1. Record user message in continuous thread
            dao.insertMessage(
                ConversationEntity(
                    sender = "user",
                    text = userInput,
                    timestamp = System.currentTimeMillis()
                )
            )
            memoryManager.persistJsonSnapshot()

            // Use the unified LLM planner for every turn; local phrase parsing must not
            // consume a compound request or bypass web research and conversational intent.
            // 3. Construct AI prompt context for selected LLM provider
            val assistantName = preferences.getAssistantName()
            val permanentMemory = memoryManager.getPermanentMemoryText()
            val rollingSummary = memoryManager.getRollingSummaryText()

            val systemPrompt = com.example.provider.SiaSystemPrompt.build(assistantName, preferences.getUserName())

            // The current user message was inserted into Room above. Exclude that same
            // trailing entry from history so providers receive it only as currentUserMessage.
            val recentHistory = dao.getRecentMessages(11).reversed()
                .dropLastWhile { it.sender == "user" && it.text == userInput }
                .takeLast(10)
                .map {
                    PromptMessage(
                        role = if (it.sender == "user") "user" else "assistant",
                        content = it.text
                    )
                }



            val promptContext = PromptContext(
                systemPrompt = systemPrompt,
                permanentMemory = permanentMemory,
                rollingSummary = rollingSummary,
                recentMessages = recentHistory,
                currentUserMessage = userInput,
                availableTools = ToolRegistry.getAgentTools()
            )

            val provider = ProviderFactory.createProvider(getApplication())
            val responseResult = provider.generateResponse(promptContext)

            if (responseResult.isSuccess) {
                val llmOutput: LlmCallResult = responseResult.getOrThrow()
                if (llmOutput.toolCalls.isNotEmpty() || llmOutput.directive == com.example.provider.TurnDirective.CONTINUE) {
                    executeMultiActions(llmOutput.toolCalls, isFromVoice, userInput, explicitSpokenText = llmOutput.textReply, planningContext = promptContext)
                } else {
                    // Normal informational conversation without tool calls
                    handleConversationalReply(llmOutput.textReply, isFromVoice)
                }
            } else {
                val error = responseResult.exceptionOrNull()?.message ?: "Unknown error"
                _visualState.value = AssistantVisualState.ERROR
                val errMsg = "Error: $error"
                _statusText.value = errMsg
                _lastAssistantReply.value = errMsg

                dao.insertMessage(
                    ConversationEntity(
                        sender = "assistant",
                        text = errMsg,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Keep technical provider errors visible in the UI, but never speak them aloud.
            }

        }
    }

    /**
     * Executes multiple actions sequentially, validates results, and generates a natural spoken confirmation.
     */
    private suspend fun executeMultiActions(
        toolCalls: List<SingleToolCall>,
        isFromVoice: Boolean,
        originalUserText: String,
        explicitSpokenText: String?,
        planningContext: PromptContext
    ) {
        _visualState.value = AssistantVisualState.EXECUTING
        _statusText.value = "Executing requested actions..."
        val initialOutput = LlmCallResult(
            textReply = explicitSpokenText.orEmpty(),
            toolCalls = toolCalls
        )
        val outcome = ToolExecutionCoordinator(
            getApplication(),
            actionExecutor,
            customToolHandler = { toolName, _ ->
                if (toolName == "stop_listening") {
                    speechManager.stopSession()
                    ToolExecutionResult(ToolResultStatus.SUCCESS, "SIA voice listening stopped by user request.")
                } else {
                    ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "No handler is available for tool $toolName.")
                }
            }
        ).execute(
            initialOutput = initialOutput,
            planningContext = planningContext,
            originalUserText = originalUserText
        )
        val executionResults = outcome.executionResults.toMutableList()
        var finalModelText = outcome.finalOutput.textReply

        if (outcome.roundLimitReached) {
            finalModelText = if (isHindiOrHinglish(originalUserText)) {
                "Kaafi steps execute ho gaye, lekin task poora confirm nahi hua. Main success assume nahi karungi."
            } else {
                "I reached the safe action limit before the task was fully verified, so I can’t claim it is complete."
            }
        }

        // Search is not a user-facing answer. If the model only acknowledges searching,
        // give it up to two synthesis-only retries with the retrieved evidence and no tools,
        // so it cannot loop into duplicate side effects or claim it answered without facts.
        val hasSuccessfulWebSearch = executionResults.any {
            it.first.toolName == "web_search" && it.second.status == ToolResultStatus.SUCCESS
        }
        fun needsWebAnswer(text: String): Boolean {
            val value = text.trim()
            return value.isBlank() ||
                value.equals("Web search kar liya.", ignoreCase = true) ||
                value.equals("Web search kar liya", ignoreCase = true) ||
                value.equals("Searched the web.", ignoreCase = true) ||
                value.equals("Search complete.", ignoreCase = true) ||
                value.equals("Main sun rahi hoon.", ignoreCase = true)
        }
        if (hasSuccessfulWebSearch && needsWebAnswer(finalModelText)) {
            val evidence = executionResults.filter {
                it.first.toolName == "web_search" && it.second.status == ToolResultStatus.SUCCESS
            }.joinToString("\n\n") { it.second.message }
            for (attempt in 0 until 2) {
                if (!needsWebAnswer(finalModelText)) break
                val synthesisContext = PromptContext(
                    systemPrompt = planningContext.systemPrompt +
                        "\nYou are synthesizing a web answer, not executing tools. Use only the evidence below. Answer the original question directly with specific facts and sources where available. If the evidence does not contain the answer, clearly say it could not be verified. Never say only that you searched. Do not output tool calls.",
                    permanentMemory = planningContext.permanentMemory,
                    rollingSummary = planningContext.rollingSummary,
                    recentMessages = planningContext.recentMessages,
                    currentUserMessage = "Original user request: $originalUserText\n\nRetrieved web evidence:\n$evidence\n\nGive the actual answer now, or clearly state that the evidence is insufficient.",
                    availableTools = emptyList()
                )
                val synthesis = ProviderFactory.createProvider(getApplication()).generateResponse(synthesisContext)
                if (synthesis.isSuccess) {
                    val candidate = synthesis.getOrThrow()
                    if (candidate.toolCalls.isEmpty() && !needsWebAnswer(candidate.textReply)) {
                        finalModelText = candidate.textReply
                        break
                    }
                }
            }
        }

        val webIssue = executionResults.firstOrNull {
            it.first.toolName == "web_search" && it.second.status != ToolResultStatus.SUCCESS
        }
        if (webIssue != null && executionResults.none { it.second.status == ToolResultStatus.SUCCESS }) {
            val detail = webIssue.second.message
            val reply = when {
                detail.startsWith("TAVILY_KEY_MISSING:") -> "Web search ke liye Settings mein Tavily key add kar de."
                detail.contains("HTTP 401") || detail.contains("HTTP 403") || detail.contains("rejected the API key", true) -> "Tavily key accept nahi hui. Settings mein key dobara check kar; key ko chat mein share mat karna."
                detail.contains("HTTP 400") -> "Tavily request reject hui. Error detail: " + detail.take(180)
                detail.contains("HTTP 429") || detail.contains("request limit", true) -> "Tavily ki request limit hit hui lagti hai. Thodi der baad try kar."
                detail.contains("HTTP 5") -> "Tavily service mein temporary problem hai. Thodi der baad try kar."
                detail.contains("could not reach", true) -> "Search service tak connection nahi ho raha. Internet check kar."
                else -> "Search fail hui: " + detail.take(180)
            }
            handleConversationalReply(reply, isFromVoice)
            return
        }

        // Surface ambiguous contact matches as a clarification, without exposing technical errors.
        val ambiguousContactResult = executionResults.firstOrNull {
            it.first.toolName == "call" && it.second.message.startsWith("AMBIGUOUS_CONTACTS:")
        }
        if (ambiguousContactResult != null) {
            val rawOptions = ambiguousContactResult.second.message.substringAfter("Ask which one to call: ", "")
            pendingContactChoices = rawOptions.split(";").mapNotNull { item ->
                val parts = item.trim().split(Regex(":\\s*"), limit = 2)
                if (parts.size == 2 && parts[1].filter(Char::isDigit).length >= 7) parts[0].trim() to parts[1].trim() else null
            }
        }

        val contactIssue = executionResults.firstOrNull {
            it.first.toolName == "call" && it.second.status != ToolResultStatus.SUCCESS
        }
        if (contactIssue != null && executionResults.none { it.second.status == ToolResultStatus.SUCCESS }) {
            val detail = contactIssue.second.message
            val clarification = if (detail.startsWith("AMBIGUOUS_CONTACTS:")) {
                val context = PromptContext(
                    systemPrompt = "You are SIA, a casual, friendly female assistant. Ask the user which exact contact/number they mean using the provided options. Do not call anyone. Keep it brief.",
                    permanentMemory = "",
                    rollingSummary = "",
                    recentMessages = emptyList(),
                    currentUserMessage = detail,
                    availableTools = emptyList()
                )
                val phrased = ProviderFactory.createProvider(getApplication()).generateResponse(context)
                if (phrased.isSuccess && phrased.getOrThrow().toolCalls.isEmpty() && phrased.getOrThrow().textReply.isNotBlank()) phrased.getOrThrow().textReply
                else "Is naam ke multiple numbers mile hain. Kisko call karun? " + detail.substringAfter("Ask which one to call: ")
            } else when {
                detail.startsWith("CALL_CONFIRMATION_REQUIRED:") || detail.startsWith("CALL_PERMISSION_REQUIRED:") -> "Dialer khol diya hai. Call abhi nahi lagi hai—number check karke Call button dabana."
                detail.startsWith("CONTACT_PERMISSION_REQUIRED:") -> "Contacts access allow kar de, phir main contact dhoondh kar call laga dungi."
                detail.startsWith("No exact contact match") -> "Is exact naam ka contact nahi mila. Naam ek baar check kar de."
                detail.startsWith("MUSIC_PERMISSION_REQUIRED:") -> "Local music chalane ke liye phone ki audio permission allow kar de."
                detail.startsWith("No local song matched") -> "Ye gaana local music mein nahi mila. Title ya artist ka naam check kar le."
                detail.startsWith("BRIGHTNESS_PERMISSION_REQUIRED:") -> "Brightness badalne ke liye SIA ko Modify System Settings permission de, phir dobara bolna."
                detail.startsWith("MEDIA_ACCESS_REQUIRED:") -> "Existing music app control karne ke liye Notification Access mein SIA ko allow kar de."
                detail.startsWith("TAVILY_KEY_MISSING:") -> "Web search ke liye Settings mein Tavily API key add kar de."
                isHindiOrHinglish(originalUserText) -> "Ye kaam abhi nahi ho paaya. Ek baar permission ya settings check kar le."
                else -> "I couldn't complete that action. Please check the required permission or setting."
            }
            handleConversationalReply(clarification, isFromVoice)
            return
        }

        // Determine language style from original query
        val isHindiHinglish = isHindiOrHinglish(originalUserText) || preferences.getLanguagePreference().contains("Hindi") || preferences.getLanguagePreference().contains("Hinglish")

        // Synthesize natural confirmation
        val successResults = executionResults.filter { it.second.status == ToolResultStatus.SUCCESS }
        val failedResults = executionResults.filter { it.second.status != ToolResultStatus.SUCCESS }

        val hasNamedMusicPlay = executionResults.any { (call, _) ->
            call.toolName == "play_music" && !call.toolParams["query"].isNullOrBlank()
        }
        val hasWebSearch = executionResults.any { it.first.toolName == "web_search" }
        val genericWebAcknowledgement = finalModelText.isBlank() || finalModelText.contains("web search kar liya", ignoreCase = true) ||
            finalModelText.contains("search complete", ignoreCase = true) ||
            finalModelText.contains("search kar li", ignoreCase = true)
        val spokenConfirmation: String = if (failedResults.isEmpty() && hasNamedMusicPlay) {
            // Playback confirmation must come from the executor's actual track result, not the LLM.
            synthesizeSuccessConfirmation(successResults, isHindiHinglish)
        } else if (hasWebSearch && genericWebAcknowledgement) {
            if (isHindiHinglish) {
                "Search results mil gaye, lekin main unse tumhare sawaal ka reliable jawab verify nahi kar paayi. Main guess nahi karungi."
            } else {
                "I retrieved search results, but couldn't verify a reliable answer to your question from them. I won't guess."
            }
        } else if (failedResults.isEmpty()) {
            if (!finalModelText.isNullOrBlank() && !finalModelText.contains("```") && !finalModelText.contains("{")) {
                finalModelText
            } else {
                synthesizeSuccessConfirmation(successResults, isHindiHinglish)
            }
        } else if (successResults.isNotEmpty()) {
            synthesizePartialSuccessConfirmation(successResults, failedResults, isHindiHinglish)
        } else {
            synthesizeFailureConfirmation(failedResults, isHindiHinglish)
        }

        _lastAssistantReply.value = spokenConfirmation
        _statusText.value = spokenConfirmation

        // Create clean action badge summary (e.g. "Opened YouTube, Turned on torch")
        val badgesSummary = executionResults.joinToString(" • ") { (call, res) ->
            formatHumanReadableActionBadge(call, res)
        }

        val overallStatus = when {
            failedResults.isEmpty() -> "SUCCESS"
            successResults.isEmpty() -> failedResults.first().second.status.name
            else -> "PARTIAL_SUCCESS"
        }

        // Save cleanly in Room DB without ANY raw JSON
        dao.insertMessage(
            ConversationEntity(
                sender = "assistant",
                text = spokenConfirmation,
                timestamp = System.currentTimeMillis(),
                toolName = toolCalls.joinToString(", ") { it.toolName },
                toolInput = toolCalls.joinToString("; ") { "${it.toolName}:${it.toolParams}" },
                toolStatus = overallStatus,
                toolResult = badgesSummary,
                isSpoken = isFromVoice
            )
        )

        _visualState.value = AssistantVisualState.IDLE

        if (preferences.isAutoSpeakEnabled()) {
            speechManager.speak(spokenConfirmation)
        }
    }

    private fun handleConversationalReply(cleanReply: String, isFromVoice: Boolean) {
        val reply = cleanReply.ifBlank { "Mujhe is baar usable response nahi mila. Ek baar dobara try karte hain." }
        _visualState.value = AssistantVisualState.IDLE
        _statusText.value = "Ready"
        _lastAssistantReply.value = reply

        viewModelScope.launch {
            dao.insertMessage(
                ConversationEntity(
                    sender = "assistant",
                    text = reply,
                    timestamp = System.currentTimeMillis(),
                    isSpoken = isFromVoice
                )
            )
            memoryManager.persistJsonSnapshot()
            memoryManager.checkAndSummarizeIfNeeded()
        }

        if (preferences.isAutoSpeakEnabled()) {
            speechManager.speak(reply)
        }
    }

    private fun isHindiOrHinglish(text: String): Boolean {
        return HindiSpeechTextPreparer.isHindiOrHinglish(text)
    }

    private fun synthesizeSuccessConfirmation(
        successList: List<Pair<SingleToolCall, ToolExecutionResult>>,
        isHindiHinglish: Boolean
    ): String {
        val phrases = successList.map { (call, result) ->
            val level = call.toolParams["level"] ?: "50"
            val requestedName = call.toolParams["app_name"] ?: "app"
            val actualTrack = Regex("""Now playing local track: (.+?) by (.+?)\.""")
                .find(result.message)?.groupValues?.getOrNull(1)
            val calledName = result.message.substringAfter("Call started for ", "")
                .substringBefore(".").ifBlank { call.toolParams["name"] ?: "contact" }

            if (isHindiHinglish) {
                when (call.toolName) {
                    "toggle_torch" -> if (call.toolParams["state"] == "off") "torch band kar di" else "torch on kar di"
                    "set_brightness" -> "brightness $level percent kar di"
                    "set_volume" -> "volume $level percent kar diya"
                    "open_app" -> "$requestedName launch karne ki request bhej di"
                    "close_app" -> "home screen par aa gayi"
                    "open_camera" -> "camera khol diya"
                    "vibrate" -> "phone vibrate kar diya"
                    "call" -> "$calledName ko call laga di"
                    "play_music" -> actualTrack?.let { "\"$it\" play kar diya" }
                        ?: "requested gaane ka playback result verify nahi ho paaya"
                    "play" -> "music playback resume kar diya"
                    "pause" -> "music pause kar diya"
                    "stop" -> "music playback rok diya"
                    "next" -> "agla track chala diya"
                    "previous" -> "pichhla track chala diya"
                    "web_search" -> "search results mil gaye"
                    "list_music" -> "local music ki list mil gayi"
                    "user_perma_memory" -> if (call.toolParams["action"] == "list") "saved memory details nikaal li" else "memory save kar di"
                    "schedule_task" -> "reminder schedule kar diya"
                    "get_task_status" -> "scheduled task ka status check kar liya"
                    "cancel_scheduled_task" -> "scheduled task cancel kar diya"
                    "create_workspace_note" -> "note save kar diya"
                    "export_app_apk" -> "SIA APK export flow khol diya"
                    "list_all_commands" -> "available commands ki list nikaal di"
                    "stop_listening" -> "voice listening band kar di"
                    else -> "requested action complete kar diya"
                }
            } else {
                when (call.toolName) {
                    "toggle_torch" -> if (call.toolParams["state"] == "off") "turned off the flashlight" else "turned on the flashlight"
                    "set_brightness" -> "set brightness to $level percent"
                    "set_volume" -> "set volume to $level percent"
                    "open_app" -> "sent a launch request for $requestedName"
                    "close_app" -> "returned to the home screen"
                    "open_camera" -> "opened the camera"
                    "vibrate" -> "vibrated the phone"
                    "call" -> "started a call to $calledName"
                    "play_music" -> actualTrack?.let { "played $it" }
                        ?: "could not verify the requested track"
                    "play" -> "resumed media playback"
                    "pause" -> "paused media playback"
                    "stop" -> "stopped media playback"
                    "next" -> "skipped to the next track"
                    "previous" -> "returned to the previous track"
                    "web_search" -> "retrieved web search results"
                    "list_music" -> "retrieved the local music list"
                    "user_perma_memory" -> if (call.toolParams["action"] == "list") "retrieved saved memories" else "saved a memory"
                    "schedule_task" -> "scheduled a reminder"
                    "get_task_status" -> "checked the scheduled task status"
                    "cancel_scheduled_task" -> "cancelled the scheduled task"
                    "create_workspace_note" -> "saved a workspace note"
                    "export_app_apk" -> "opened the SIA APK export flow"
                    "list_all_commands" -> "retrieved the available command list"
                    "stop_listening" -> "stopped voice listening"
                    else -> "completed the requested action"
                }
            }
        }

        return if (isHindiHinglish) {
            when (phrases.size) {
                0 -> "Kaam ka result confirm nahi ho paaya."
                1 -> "Haan, ${phrases[0]}."
                2 -> "Haan, ${phrases[0]} aur ${phrases[1]}."
                else -> "Haan, ${phrases.dropLast(1).joinToString(", ")}, aur ${phrases.last()}."
            }
        } else {
            when (phrases.size) {
                0 -> "I couldn't verify the action result."
                1 -> "Done — ${phrases[0]}."
                2 -> "Done — ${phrases[0]} and ${phrases[1]}."
                else -> "Done — ${phrases.dropLast(1).joinToString(", ")}, and ${phrases.last()}."
            }
        }
    }

    private fun synthesizePartialSuccessConfirmation(
        successList: List<Pair<SingleToolCall, ToolExecutionResult>>,
        failedList: List<Pair<SingleToolCall, ToolExecutionResult>>,
        isHindiHinglish: Boolean
    ): String {
        val succText = successList.joinToString(", ") { formatHumanReadableActionBadge(it.first, it.second) }
        return if (isHindiHinglish) {
            "Complete: $succText. Failed: " + failedList.joinToString("; ") { "${formatHumanReadableActionBadge(it.first, it.second)} — ${it.second.message.take(180)}" }
        } else {
            "Completed: $succText. Failed: " + failedList.joinToString("; ") { "${formatHumanReadableActionBadge(it.first, it.second)} — ${it.second.message.take(180)}" }
        }
    }

    private fun synthesizeFailureConfirmation(
        failedList: List<Pair<SingleToolCall, ToolExecutionResult>>,
        isHindiHinglish: Boolean
    ): String {
        val first = failedList.firstOrNull()
        val message = first?.second?.message.orEmpty()
        if (message.startsWith("TAVILY_KEY_MISSING:")) return "Web search ke liye Settings mein Tavily API key add kar de."
        if (message.startsWith("Tavily rejected the API key")) return "Tavily key sahi nahi lag rahi. Settings mein key check kar le."
        if (message.startsWith("MUSIC_PERMISSION_REQUIRED:")) return "Local music chalane ke liye audio permission allow kar de."
        if (message.startsWith("No local song matched")) return "Ye gaana local music mein nahi mila. Title ya artist ka naam ek baar check kar le."
        if (message.startsWith("BRIGHTNESS_PERMISSION_REQUIRED:")) return "Brightness badalne ke liye SIA ko Modify System Settings permission de, phir dobara bolna."
        if (message.startsWith("MEDIA_ACCESS_REQUIRED:")) return "Existing music app control karne ke liye Notification Access mein SIA ko allow kar de."
        if (message.startsWith("CONTACT_PERMISSION_REQUIRED:")) return "Contacts access allow kar de, phir main call laga dungi."
        if (message.startsWith("CALL_PERMISSION_REQUIRED:")) return "Number dialer mein khol diya hai. Call karne ke liye Call button daba dena."
        if (message.startsWith("Android opened the")) return "Settings khol di hain. Wahan se is setting ko on ya off kar de."
        return if (isHindiHinglish) {
            "Ye kaam abhi nahi ho paaya, bhai. Ek baar permission ya setting check kar le."
        } else {
            "I couldn't complete that action. Please check the required permission or setting."
        }
    }

    private fun formatHumanReadableActionBadge(call: SingleToolCall, res: ToolExecutionResult): String {
        return when (call.toolName) {
            "open_app" -> "Launch requested: ${call.toolParams["app_name"] ?: "App"}"
            "toggle_torch" -> if (call.toolParams["state"] == "off") "Flashlight Off" else "Flashlight On"
            "set_volume" -> "Volume ${call.toolParams["level"] ?: "50"}%"
            "control_media" -> "Media: ${call.toolParams["action"] ?: "Play"}"
            "schedule_task" -> "Reminder Scheduled"
            "web_search" -> "Web Search"
            "export_app_apk" -> "Exported SIA APK"
            "share_workspace_note" -> "Shared Workspace Note"
            else -> call.toolName.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
    }

    fun exportApk() {
        actionExecutor.handleExportAppApk()
    }

    fun directInstallApk() {
        actionExecutor.handleDirectInstallApk()
    }

    fun shareWorkspaceFile(fileName: String) {
        actionExecutor.handleShareWorkspaceNote(fileName)
    }

    fun speakMessage(text: String) {
        speechManager.speak(text)
    }

    fun clearConversation() {
        viewModelScope.launch {
            dao.clearAllMessages()
            _statusText.value = "Conversation history cleared."
            _lastAssistantReply.value = ""
        }
    }

    override fun onCleared() {
        speechManager.destroy()
        super.onCleared()
    }
}
