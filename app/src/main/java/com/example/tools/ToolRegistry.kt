package com.example.tools


object ToolRegistry {

    /**
     * Simple individual functions exposed directly to the LLM.
     * Only tools backed by an executor implementation are listed.
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
        ToolDefinition("open_camera", "Open the phone camera app.", emptyList()),
        ToolDefinition("vibrate", "Vibrate the phone for a short duration.", listOf(
            ToolParameter("duration_ms", "number", "Duration in milliseconds, from 1 to 5000")
        )),
        ToolDefinition("send_sms", "Open the SMS composer with a recipient and message. The user must review and tap Send; do not claim the SMS was sent.", listOf(
            ToolParameter("number", "string", "Recipient phone number"),
            ToolParameter("message", "string", "SMS body")
        )),
        ToolDefinition("call", "Call a saved contact by exact name or a phone number. Ambiguous contacts must be clarified.", listOf(
            ToolParameter("name", "string", "Saved contact name or phone number")
        )),
        ToolDefinition("web_search", "Search the live web with Tavily and return source evidence for answering the user's question.", listOf(
            ToolParameter("query", "string", "Question or search terms")
        )),
        ToolDefinition("list_music", "List local music tracks, optionally filtered by title or artist, without changing playback.", listOf(
            ToolParameter("query", "string", "Optional title or artist filter", false)
        )),
        ToolDefinition("play_music", "Play a specifically requested song from local music files. Do not substitute an unrelated song.", listOf(
            ToolParameter("query", "string", "Song title or artist")
        )),
        ToolDefinition("play", "Resume or play existing media.", emptyList()),
        ToolDefinition("pause", "Pause current media playback.", emptyList()),
        ToolDefinition("stop", "Stop current media playback.", emptyList()),
        ToolDefinition("next", "Skip to the next media track.", emptyList()),
        ToolDefinition("previous", "Go to the previous media track.", emptyList()),
        ToolDefinition("user_perma_memory", "Save a durable user fact or list saved facts. Use action save with key/value, or action list.", listOf(
            ToolParameter("action", "string", "save or list"),
            ToolParameter("key", "string", "Short memory key", false),
            ToolParameter("value", "string", "Fact to remember", false)
        )),
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

    /**
     * Backwards-compatible alias. There is one source of truth for every LLM-facing tool.
     */
    fun getAllTools(): List<ToolDefinition> = getAgentTools()

}
