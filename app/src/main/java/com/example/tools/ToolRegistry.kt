package com.example.tools

import com.example.provider.SingleToolCall
import java.util.Locale

object ToolRegistry {

    /**
     * Compact agent-facing API. The orchestrator exposes these stable function names;
     * each agent owns its operation-specific validation and implementation.
     */
    fun getAgentTools(): List<ToolDefinition> = listOf(
        ToolDefinition("device_agent", "Control torch and volume. Wi-Fi, Bluetooth, mobile data, hotspot, and brightness may open Android Settings because direct toggles are restricted. Torch needs Camera permission. Discover supported operations with list_all_commands.", listOf(
            ToolParameter("action", "string", "torch_on, torch_off, wifi_on/off, bluetooth_on/off, mobile_data_on/off, hotspot_on/off, volume, brightness"),
            ToolParameter("state", "string", "Desired state on/off/toggle when relevant", false),
            ToolParameter("level", "number", "Volume percentage from 0 to 100", false)
        )),
        ToolDefinition("app_agent", "Open an installed app or return to the launcher. Android does not permit third-party force-stopping arbitrary apps.", listOf(
            ToolParameter("action", "string", "open or close"),
            ToolParameter("app_name", "string", "Installed app name when action is open", false)
        )),
        ToolDefinition("contact_agent", "Requires Contacts access; direct calling also needs Call Phone permission. Find an exact saved contact name and call it. Never call if the name is ambiguous; ask the user to choose.", listOf(
            ToolParameter("action", "string", "call"),
            ToolParameter("name", "string", "Exact contact display name")
        )),
        ToolDefinition("web_agent", "Search the live web using the Tavily API key saved in Settings and return source excerpts for answer synthesis. Do not open Google or a browser.", listOf(
            ToolParameter("action", "string", "search"),
            ToolParameter("query", "string", "Question or search terms")
        )),
        ToolDefinition("media_agent", "Play music stored locally on this phone. Local playback needs audio permission; controlling another media app needs SIA Notification Access. Online streaming is not supported in this version.", listOf(
            ToolParameter("action", "string", "play, pause, resume, stop, next, previous, or control_existing"),
            ToolParameter("media_action", "string", "Existing app media key: play_pause, play, pause, next, previous", false),
            ToolParameter("query", "string", "Optional song title or artist to match in local files", false)
        )),
        ToolDefinition("reminder_agent", "Create, inspect, or cancel persistent reminders. Notification permission is needed for alerts; exact alarm access affects timing precision.", listOf(
            ToolParameter("action", "string", "create, list, or cancel"),
            ToolParameter("description", "string", "Reminder description", false),
            ToolParameter("delay_minutes", "number", "Minutes from now", false),
            ToolParameter("task_id", "string", "Existing reminder ID for cancellation or status", false)
        )),
        ToolDefinition("list_all_commands", "Discover supported operations, permissions and platform limitations.", emptyList()),
        ToolDefinition("create_workspace_note", "Save a note in the SIA workspace.", listOf(
            ToolParameter("title", "string", "Note title"),
            ToolParameter("content", "string", "Note contents")
        )),
        ToolDefinition("export_app_apk", "Open the installed SIA APK export/share flow.", emptyList())
    )

    fun getAllTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "open_app",
            description = "Launch an installed Android application by name (e.g. YouTube, Camera, WhatsApp, Spotify, Chrome, Settings).",
            parameters = listOf(
                ToolParameter("app_name", "string", "The name of the application to open")
            )
        ),
        ToolDefinition(
            name = "web_search",
            description = "Search the live web using the configured Tavily API and return source excerpts for answer synthesis.",
            parameters = listOf(
                ToolParameter("query", "string", "The search query string")
            )
        ),
        ToolDefinition(
            name = "toggle_torch",
            description = "Control the device flashlight (turn ON, turn OFF, or TOGGLE).",
            parameters = listOf(
                ToolParameter("state", "string", "Desired state: 'on', 'off', or 'toggle'")
            ),
            requiredPermissions = listOf("CAMERA")
        ),
        ToolDefinition(
            name = "control_media",
            description = "Control music or media playback on the device.",
            parameters = listOf(
                ToolParameter("action", "string", "Playback action: 'play_pause', 'play', 'pause', 'next', 'previous'")
            )
        ),
        ToolDefinition(
            name = "set_volume",
            description = "Adjust music and media volume percentage from 0 to 100.",
            parameters = listOf(
                ToolParameter("level", "number", "Target volume level percentage from 0 to 100")
            )
        ),
        ToolDefinition(
            name = "open_system_settings",
            description = "Open specific Android system settings (wifi, bluetooth, sound, display, apps, battery, general).",
            parameters = listOf(
                ToolParameter("type", "string", "Settings category: 'wifi', 'bluetooth', 'sound', 'display', 'apps', 'battery'")
            )
        ),
        ToolDefinition(
            name = "schedule_task",
            description = "Schedule a task or reminder for a future time or delay.",
            parameters = listOf(
                ToolParameter("description", "string", "What needs to be done or reminded"),
                ToolParameter("delay_minutes", "number", "Delay in minutes from now (e.g. 10 for 10 minutes later)", false),
                ToolParameter("action_type", "string", "Action type (e.g. 'REMINDER', 'TOGGLE_TORCH', 'WEB_SEARCH')", false)
            ),
            requiredPermissions = listOf("SCHEDULE_EXACT_ALARM", "POST_NOTIFICATIONS")
        ),
        ToolDefinition(
            name = "get_task_status",
            description = "Query pending or scheduled future tasks.",
            parameters = listOf(
                ToolParameter("task_id", "string", "Optional specific task ID to check", false)
            )
        ),
        ToolDefinition(
            name = "cancel_scheduled_task",
            description = "Cancel a previously scheduled task by ID.",
            parameters = listOf(
                ToolParameter("task_id", "string", "The task ID to cancel")
            )
        ),
        ToolDefinition(
            name = "create_workspace_note",
            description = "Create or save a quick note or text file into the SIA internal workspace.",
            parameters = listOf(
                ToolParameter("title", "string", "Note or file title"),
                ToolParameter("content", "string", "The note content or text")
            )
        ),
        ToolDefinition(
            name = "export_app_apk",
            description = "Download, export, share, or install the SIA v2 APK directly onto the Android phone.",
            parameters = emptyList()
        ),
        ToolDefinition(
            name = "list_all_commands",
            description = "Discover all available capabilities, tools, and permissions supported by SIA.",
            parameters = emptyList()
        )
    )

    sealed class CommandResolution {
        open class LocalActions(val actions: List<SingleToolCall>) : CommandResolution() {
            override fun equals(other: Any?): Boolean = other is LocalActions && actions == other.actions
            override fun hashCode(): Int = actions.hashCode()
            override fun toString(): String = "LocalActions(actions=$actions)"
        }
        class Resolved(val action: SingleToolCall) : LocalActions(listOf(action)) {
            override fun toString(): String = "Resolved(action=$action)"
        }
        class MultiAction(actions: List<SingleToolCall>) : LocalActions(actions) {
            override fun toString(): String = "MultiAction(actions=$actions)"
        }
        object NeedsAiAgent : CommandResolution() {
            override fun toString(): String = "NeedsAiAgent"
        }

        val allActions: List<SingleToolCall>
            get() = when (this) {
                is LocalActions -> actions
                is NeedsAiAgent -> emptyList()
            }
    }

    private val INVALID_APP_NAMES = setOf(
        "kardo", "kar do", "karo", "karna", "karein", "kijiye", "kar dena", "dena", "do",
        "app", "application", "open", "kholo", "close", "band", "on", "off", "chalu",
        "please", "na", "to", "hi", "bhi", "ab", "turant", "yeh", "woh", "it", "this", "that",
        "kuch", "sab", "koi", "mera", "meri", "mujhe", "hum", "batao", "sunao", "bhai", "zara"
    )

    private val KNOWN_APP_ALIASES = mapOf(
        "youtube" to "youtube",
        "yt" to "youtube",
        "whatsapp" to "whatsapp",
        "chrome" to "chrome",
        "browser" to "chrome",
        "spotify" to "spotify",
        "camera" to "camera",
        "maps" to "maps",
        "google maps" to "maps",
        "gmail" to "gmail",
        "calculator" to "calculator",
        "clock" to "clock",
        "alarm" to "clock",
        "photos" to "photos",
        "gallery" to "gallery",
        "settings" to "settings",
        "instagram" to "instagram",
        "insta" to "instagram",
        "chatgpt" to "chatgpt",
        "chat gpt" to "chatgpt",
        // Common Hindi/Devanagari-accented SpeechRecognizer mishearings of "ChatGPT".
        // Kept as an explicit, conservative allowlist (not fuzzy matching) so we only
        // resolve known STT mistranscriptions and never silently guess unrelated apps.
        "chat gupt" to "chatgpt",
        "chatgupt" to "chatgpt",
        "chat gupta" to "chatgpt",
        "चैट जीपीटी" to "chatgpt",
        "चैट गुप्त" to "chatgpt"
    )

    private val CONVERSATIONAL_MARKERS = listOf(
        "mood off", "dimag kharab", "kya kar rahi", "kya chal raha", "kaisi ho", "kaise ho",
        "who are you", "what is", "kaun ho", "namaste", "hello", "hi", "batao", "samjhao",
        "explain", "kuch sunao", "kuch batao", "bore ho", "help me with", "aaj mood",
        "baat karo", "kya haal", "how are you", "joke sunao", "story sunao", "kahani sunao",
        "ke baare mein", "ke bare mein", "tell me about"
    )

    /**
     * Resolves user utterance using a deterministic, fast semantic intent model.
     * Returns Resolved (single action) or MultiAction (all actions preserved) ONLY if every requested
     * operation is supported by the existing AndroidActionExecutor.
     * Incomplete, ambiguous, unsupported (e.g. mobile data toggle), or conversational utterances return NeedsAiAgent.
     */
    fun resolveCommand(userInput: String): CommandResolution {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty()) return CommandResolution.NeedsAiAgent

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Guard against conversational/emotional expressions when no conjunction is present
        val hasConjunction = lower.contains(" aur ") || lower.contains(" and ") || lower.contains(" phir ") || lower.contains(" then ")
        if (!hasConjunction && CONVERSATIONAL_MARKERS.any { lower.contains(it) }) {
            return CommandResolution.NeedsAiAgent
        }

        // 2. Split compound sentences on conjunctions: "aur", "and", "phir", "then", commas
        val splitRegex = Regex("""(?:,\s*and\s*|,\s*aur\s*|\s+and\s+|\s+aur\s+|\s*,\s*|\s+phir\s+|\s+then\s+)""", RegexOption.IGNORE_CASE)
        val clauses = trimmed.split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (clauses.isEmpty()) return CommandResolution.NeedsAiAgent

        // Single clause resolution
        if (clauses.size == 1) {
            val single = matchSingleClause(clauses[0])
            return if (single != null) {
                CommandResolution.Resolved(single)
            } else {
                CommandResolution.NeedsAiAgent
            }
        }

        // Multi-clause resolution: ALL clauses must be supported local actions
        val actions = mutableListOf<SingleToolCall>()
        for (clause in clauses) {
            val matched = matchSingleClause(clause)
            if (matched == null) {
                // If even one clause is conversational or requires complex AI reasoning,
                // forward the entire request to the AI agent without dropping earlier actions!
                return CommandResolution.NeedsAiAgent
            }
            if (!actions.any { it.toolName == matched.toolName && it.toolParams == matched.toolParams }) {
                actions.add(matched)
            }
        }

        return if (actions.size == 1) {
            CommandResolution.Resolved(actions[0])
        } else if (actions.size > 1) {
            CommandResolution.MultiAction(actions)
        } else {
            CommandResolution.NeedsAiAgent
        }
    }

    /**
     * Preserved helper for legacy callers and tests. Delegates to semantic resolveCommand.
     */
    fun matchLocalIntent(userInput: String): List<SingleToolCall> {
        return resolveCommand(userInput).allActions
    }

    /**
     * Semantic clause matcher based on operation concepts and recognized targets.
     */
    private fun matchSingleClause(clause: String): SingleToolCall? {
        val raw = clause.trim().lowercase(Locale.ROOT)
            .removeSuffix(".").removeSuffix("!").removeSuffix("?").trim()

        if (raw.isEmpty()) return null

        // Conversational guard for this clause
        if (CONVERSATIONAL_MARKERS.any { raw.contains(it) }) {
            return null
        }

        // Reject unsupported hardware toggles (e.g. mobile data toggle requires root/system signature)
        if (raw.contains("mobile data") || raw.contains("cellular data") || raw.contains("data network") || raw.contains("airplane mode")) {
            return null
        }

        // 1. Target: Torch / Flashlight
        val isTorchTarget = raw.contains("torch") || raw.contains("flashlight") || raw.contains("flash") ||
                raw.contains("torchlight") || raw.contains("टॉर्च") || raw.contains("फ्लैशलाइट")
        if (isTorchTarget) {
            val onKeywords = listOf("on", "open", "chalu", "jalao", "turn on", "start", "enable", "jala", "activate")
            val offKeywords = listOf("off", "close", "band", "bujhao", "turn off", "stop", "disable", "bujha", "deactivate")

            val isOn = onKeywords.any { raw.contains(it) }
            val isOff = offKeywords.any { raw.contains(it) }

            if (isOff) {
                return SingleToolCall("toggle_torch", mapOf("state" to "off"))
            }
            if (isOn) {
                return SingleToolCall("toggle_torch", mapOf("state" to "on"))
            }
            return SingleToolCall("toggle_torch", mapOf("state" to "toggle"))
        }

        // 2. Target: System Settings (WiFi, Bluetooth, Sound, Display, Battery)
        if (raw.contains("wifi") || raw.contains("wi-fi") || raw.contains("वाईफाई")) {
            return SingleToolCall("open_system_settings", mapOf("type" to "wifi"))
        }
        if (raw.contains("bluetooth") || raw.contains("ब्लूटूथ")) {
            return SingleToolCall("open_system_settings", mapOf("type" to "bluetooth"))
        }
        if (raw.contains("sound setting") || raw.contains("volume setting")) {
            return SingleToolCall("open_system_settings", mapOf("type" to "sound"))
        }
        if (raw.contains("display setting") || raw.contains("brightness setting")) {
            return SingleToolCall("open_system_settings", mapOf("type" to "display"))
        }
        if (raw.contains("battery setting")) {
            return SingleToolCall("open_system_settings", mapOf("type" to "battery"))
        }

        // 3. Target: Volume
        val volRegex = Regex("""(?:volume|sound|aawaaz)\s*(?:to|set\s*to|karo|kar\s*do|badhao)?\s*(\d{1,3})%?""")
        val volMatch = volRegex.find(raw)
        if (volMatch != null) {
            val level = volMatch.groupValues[1]
            return SingleToolCall("set_volume", mapOf("level" to level))
        }
        if (raw.contains("mute") || raw.contains("silent") || raw.contains("volume zero") || raw == "volume off") {
            return SingleToolCall("set_volume", mapOf("level" to "0"))
        }

        // 4. Target: Media Playback
        // Capture named-song requests before the generic "play music" fallback.
        // STT often emits Hindi in Devanagari, so remove common command filler in both scripts
        // and pass only the likely title to the media agent.
        val asksForNamedSong = (raw.contains("music") || raw.contains("song") || raw.contains("gana") ||
            raw.contains("gaana") || raw.contains("गाना") || raw.contains("गाने") ||
            raw.contains("म्यूजिक") || raw.contains("सॉन्ग") || raw.contains("प्ले") ||
            raw.contains("बजाओ") || raw.contains("चलाओ")) &&
            (raw.contains("play") || raw.contains("bajao") || raw.contains("chalao") ||
             raw.contains("bajaa") || raw.contains("प्ले") || raw.contains("बजाओ") ||
             raw.contains("चलाओ") || raw.contains("सुना"))
        if (asksForNamedSong) {
            val title = extractSongQuery(raw)
            if (title.isNotBlank()) return SingleToolCall("media_agent", mapOf("action" to "play", "query" to title))
        }
        if (raw.contains("next song") || raw.contains("next track") || raw.contains("agla gana") || raw.contains("play next")) {
            return SingleToolCall("media_agent", mapOf("action" to "next"))
        }
        if (raw.contains("previous song") || raw.contains("pichla gana") || raw.contains("play previous") || raw.contains("prev song")) {
            return SingleToolCall("media_agent", mapOf("action" to "previous"))
        }
        if (raw.contains("pause music") || raw.contains("gana roko") || raw == "pause" || raw == "stop music") {
            return SingleToolCall("media_agent", mapOf("action" to "pause"))
        }
        if (raw.contains("play music") || raw.contains("gana bajao") || raw == "play" || raw == "resume") {
            return SingleToolCall("media_agent", mapOf("action" to "play"))
        }

        // 5. Target: APK Export / Install
        if (raw.contains("download apk") || raw.contains("install on phone") || raw.contains("install on my phone") ||
            raw.contains("export apk") || raw.contains("share apk") || raw.contains("install apk") ||
            raw.contains("install app") || raw.contains("phone me install") || raw.contains("phone par install")
        ) {
            return SingleToolCall("export_app_apk", emptyMap())
        }

        // 6. Target: Reminder / Scheduling
        if (raw.contains("remind me") || raw.contains("schedule reminder") || raw.contains("yaad dilana")) {
            val reminderRegex = Regex("""(?:remind me|schedule reminder|yaad dilana)\s*(?:in\s*(\d+)\s*(?:min|minute|minutes))?\s*(?:to\s*|for\s*)?(.*)""")
            val remMatch = reminderRegex.find(raw)
            val minutes = remMatch?.groupValues?.getOrNull(1)?.ifEmpty { "5" } ?: "5"
            val desc = remMatch?.groupValues?.getOrNull(2)?.trim()?.ifEmpty { "Reminder" } ?: "Reminder"
            return SingleToolCall("schedule_task", mapOf(
                "description" to desc,
                "delay_minutes" to minutes,
                "action_type" to "REMINDER"
            ))
        }

        // Web search is intentionally routed through the LLM and web_agent so retrieved
        // source material can be synthesized into an answer instead of opening Google.
        val searchPrefixes = listOf("search for ", "search ", "google ", "dhundo ", "find ")
        if (searchPrefixes.any { raw.startsWith(it) }) return null

        // 8. Target: Command Discovery
        if (raw.contains("what can you do") || raw.contains("list commands") || raw == "help" || raw.contains("capabilities")) {
            return SingleToolCall("list_all_commands", emptyMap())
        }

        // 9. Target: Open Application (Known App Aliases + validated targets)
        val appCall = matchAppOpenIntent(raw)
        if (appCall != null) {
            return appCall
        }

        return null
    }

    private fun extractSongQuery(raw: String): String {
        val commandWords = listOf(
            "please", "play", "music", "song", "gana", "gaana", "bajao", "bajaa", "chalao", "chala",
            "sunao", "suna", "named", "name", "se", "ko", "ka", "ki", "ke", "wala", "wali", "waala",
            "kar", "karo", "do", "de", "dena", "isko", "use", "usko", "mere", "mera", "meri",
            "the", "on", "my", "now", "please", "गाना", "गाने", "म्यूजिक", "सॉन्ग", "प्ले", "बजाओ",
            "चलाओ", "सुनाओ", "सुन", "करो", "कर", "दो", "दे", "देना", "इसे", "इसे", "उसे", "उसको",
            "नाम", "से", "वाला", "वाली", "का", "की", "के", "मेरा", "मेरी", "म्यूज़िक", "गाना", "होगा", "होगी", "है", "हो", "करना", "करदो", "कर दो"
        )
        val devanagariCleaned = raw.replace(Regex("[,!?।]+"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in commandWords }
            .joinToString(" ")
        return devanagariCleaned.trim()
    }

    private fun matchAppOpenIntent(raw: String): SingleToolCall? {
        val openVerbs = listOf(
            "open", "kholo", "khol do", "kholdo", "khol dena",
            "launch", "start", "run", "chalao", "chala do", "chalado", "chala dena",
            "खोलो", "खोल दो", "चलाओ", "चला दो", "ओपन"
        )
        val hasOpenVerb = openVerbs.any { raw.contains(it) }
        if (!hasOpenVerb) return null

        // Strip common polite and Hindi/Hinglish command suffixes/fillers and open verbs
        var clean = raw
            .replace(Regex("""\b(please|zara|bhai|kar dena|kardo|kar do|karo|karna|karein|kijiye|chala dena|chalado|chala do|chalao|khol dena|kholdo|khol do|kholo|open|launch|start|run|app|application|meri|mera)\b"""), " ")
            .replace(Regex("""(खोलो|खोल दो|चलाओ|चला दो|कर दो|करो|चला देना|ओपन)"""), " ")
            .trim()
        clean = clean.replace(Regex("""\s+"""), " ").trim()

        // Check against known application aliases first
        for ((alias, canonical) in KNOWN_APP_ALIASES) {
            if (raw.contains(Regex("""\b$alias\b""")) || clean.equals(alias, ignoreCase = true)) {
                return SingleToolCall("open_app", mapOf("app_name" to canonical))
            }
        }

        // If not a predefined alias, extract clean target candidate
        val candidate = clean
            .removeSuffix(" app")
            .removeSuffix(" application")
            .trim()

        if (candidate.isBlank() || candidate in INVALID_APP_NAMES) {
            return null
        }

        // Reject candidates containing device action keywords to prevent false app matches
        if (candidate.contains("torch") || candidate.contains("wifi") || candidate.contains("volume") || candidate.contains("data")) {
            return null
        }

        val words = candidate.split(" ")
        if (words.size <= 2) {
            return SingleToolCall("open_app", mapOf("app_name" to candidate))
        }

        return null
    }
}
