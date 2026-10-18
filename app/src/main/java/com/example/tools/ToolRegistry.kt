package com.example.tools


object ToolRegistry {

    /**
     * Compact agent-facing API. The orchestrator exposes these stable function names;
     * each agent owns its operation-specific validation and implementation.
     */
    /**
     * The LLM sees simple, individual functions rather than broad agent/router tools.
     * Only capabilities with an executor implementation are advertised here.
     */
    fun getAgentTools(): List<ToolDefinition> = listOf(
        ToolDefinition("toggle_torch", "Turn the phone flashlight on, off, or toggle it.", listOf(
            ToolParameter("state", "string", "on, off, or toggle")
        ), requiredPermissions = listOf("CAMERA")),
        ToolDefinition("set_brightness", "Set screen brightness. May request Modify System Settings permission.", listOf(
            ToolParameter("level", "number", "Brightness percentage from 0 to 100")
        )),
        ToolDefinition("set_volume", "Set music/media volume.", listOf(
            ToolParameter("level", "number", "Volume percentage from 0 to 100")
        )),
        ToolDefinition("toggle_wifi", "Open Wi-Fi settings because Android may restrict direct Wi-Fi toggling for ordinary apps.", listOf(
            ToolParameter("state", "string", "on or off")
        )),
        ToolDefinition("toggle_bluetooth", "Open Bluetooth settings because direct toggling may be restricted by Android.", listOf(
            ToolParameter("state", "string", "on or off")
        )),
        ToolDefinition("toggle_mobile_data", "Open mobile network settings; ordinary apps generally cannot toggle mobile data directly.", listOf(
            ToolParameter("state", "string", "on or off")
        )),
        ToolDefinition("open_app", "Open one installed app by its displayed name.", listOf(
            ToolParameter("app_name", "string", "Installed app name")
        )),
        ToolDefinition("close_app", "Return to the Android home screen. Android does not allow force-stopping arbitrary apps.", emptyList()),
        ToolDefinition("call", "Call a saved contact by exact name or a phone number. Ambiguous contacts must be clarified.", listOf(
            ToolParameter("name", "string", "Saved contact name or phone number")
        )),
        ToolDefinition("open_camera", "Open the phone camera app.", emptyList()),
        ToolDefinition("vibrate", "Vibrate the phone for a short duration.", listOf(
            ToolParameter("duration_ms", "number", "Duration in milliseconds, from 1 to 5000")
        )),
        ToolDefinition("send_sms", "Open the SMS composer with a recipient and message. The user must review and tap Send; do not claim the SMS was sent.", listOf(
            ToolParameter("number", "string", "Recipient phone number"),
            ToolParameter("message", "string", "SMS body")
        )),
        ToolDefinition("user_perma_memory", "Save a durable user fact or list saved facts. Use action save with key/value, or action list.", listOf(
            ToolParameter("action", "string", "save or list"),
            ToolParameter("key", "string", "Short memory key", false),
            ToolParameter("value", "string", "Fact to remember", false)
        )),
        ToolDefinition("web_search", "Search the live web with Tavily and return source evidence for answering the user's question.", listOf(
            ToolParameter("query", "string", "Question or search terms")
        )),
        ToolDefinition("play_music", "Play a specifically requested song from local music files. Do not substitute an unrelated song.", listOf(
            ToolParameter("query", "string", "Song title or artist")
        )),
        ToolDefinition("play", "Resume or play existing media.", emptyList()),
        ToolDefinition("pause", "Pause current media playback.", emptyList()),
        ToolDefinition("stop", "Stop current media playback.", emptyList()),
        ToolDefinition("next", "Skip to the next media track.", emptyList()),
        ToolDefinition("previous", "Go to the previous media track.", emptyList()),
        ToolDefinition("open_system_settings", "Open a relevant Android settings screen.", listOf(
            ToolParameter("type", "string", "wifi, bluetooth, sound, display, apps, battery, mobile_data, or general")
        )),
        ToolDefinition("schedule_task", "Create a reminder for a future time or delay.", listOf(
            ToolParameter("description", "string", "Reminder description"),
            ToolParameter("delay_minutes", "number", "Minutes from now", false),
            ToolParameter("action_type", "string", "Usually REMINDER", false)
        )),
        ToolDefinition("get_task_status", "Check pending reminders or a specific task.", listOf(
            ToolParameter("task_id", "string", "Optional task ID", false)
        )),
        ToolDefinition("cancel_scheduled_task", "Cancel a scheduled reminder.", listOf(
            ToolParameter("task_id", "string", "Task ID")
        )),
        ToolDefinition("create_workspace_note", "Save a text note in SIA's workspace.", listOf(
            ToolParameter("title", "string", "Note title"),
            ToolParameter("content", "string", "Note content")
        )),
        ToolDefinition("export_app_apk", "Open SIA's APK export/share flow.", emptyList()),
        ToolDefinition("list_all_commands", "List the capabilities available in this build.", emptyList())
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
            )        ),
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
}
