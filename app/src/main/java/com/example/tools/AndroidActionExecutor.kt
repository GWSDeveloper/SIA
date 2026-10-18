package com.example.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import com.example.data.ScheduledTaskEntity
import com.example.memory.MemoryManager
import com.example.data.SiaDatabase
import com.example.permissions.SiaPermissionManager
import com.example.scheduler.ScheduledTaskReceiver
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AndroidActionExecutor(private val context: Context) {

    private val db = SiaDatabase.getDatabase(context)
    private val memoryManager = MemoryManager(context)
    private var isTorchOn = false
    private val webSearchAgent = WebSearchAgent(context)
    private val contactAgent = ContactAgent(context)
    private val localMusicPlayer = LocalMusicPlayer(context)
    private val mediaSessionAgent = MediaSessionAgent(context)

    suspend fun executeTool(name: String, params: Map<String, String>): ToolExecutionResult {
        return withContext(Dispatchers.IO) {
            when (name) {
                // Individual LLM-facing tools delegate to existing Android implementations.
                "toggle_torch" -> handleToggleTorch(params["state"] ?: "toggle")
                "set_brightness" -> handleSetBrightness(params["level"]?.toIntOrNull() ?: 50)
                "set_volume" -> handleSetVolume(params["level"]?.toIntOrNull() ?: 50)
                "toggle_wifi" -> handleDeviceAgent(mapOf("action" to "wifi_${params["state"]?.lowercase(Locale.ROOT) ?: "on"}"))
                "toggle_bluetooth" -> handleDeviceAgent(mapOf("action" to "bluetooth_${params["state"]?.lowercase(Locale.ROOT) ?: "on"}"))
                "toggle_mobile_data" -> handleDeviceAgent(mapOf("action" to "mobile_data_${params["state"]?.lowercase(Locale.ROOT) ?: "on"}"))
                "open_app" -> handleOpenApp(params["app_name"] ?: "")
                "close_app" -> handleAppAgent(mapOf("action" to "close"))
                "open_camera" -> handleOpenApp("Camera")
                "vibrate" -> handleVibrate(params["duration_ms"]?.toLongOrNull() ?: 250L)
                "send_sms" -> handleSendSms(params["number"] ?: "", params["message"] ?: "")
                "user_perma_memory" -> handlePermanentMemory(params)
                "call" -> {
                    val target = params["name"]?.trim().orEmpty()
                    if (target.any(Char::isLetter)) contactAgent.callContact(target)
                    else contactAgent.callSpecificNumber(target)
                }
                "play_music" -> handleMediaAgent(mapOf("action" to "play", "query" to (params["query"] ?: "")))
                "play", "pause", "stop", "next", "previous" -> handleControlMedia(name)
                "device_agent" -> handleDeviceAgent(params)
                "app_agent" -> handleAppAgent(params)
                "contact_agent" -> if (params["action"] == "call_number") contactAgent.callSpecificNumber(params["number"] ?: "") else contactAgent.callContact(params["name"] ?: "")
                "web_agent", "web_search" -> webSearchAgent.search(params["query"] ?: "")
                "media_agent" -> handleMediaAgent(params)
                "reminder_agent" -> handleReminderAgent(params)
                "control_media" -> handleControlMedia(params["action"] ?: "play_pause")
                "open_system_settings" -> handleOpenSettings(params["type"] ?: "general")
                "create_reminder", "schedule_task" -> handleScheduleTask(params)
                "get_task_status" -> handleGetTaskStatus(params["task_id"])
                "cancel_scheduled_task" -> handleCancelTask(params["task_id"] ?: "")
                "create_workspace_note" -> handleCreateWorkspaceNote(
                    params["title"] ?: "Note_${System.currentTimeMillis()}",
                    params["content"] ?: ""
                )
                "export_app_apk", "install_on_phone", "download_apk" -> handleExportAppApk()
                "share_workspace_note" -> handleShareWorkspaceNote(params["file_name"] ?: "")
                "list_all_commands" -> handleListAllCommands()
                else -> ToolExecutionResult(
                    status = ToolResultStatus.UNSUPPORTED,
                    message = "Command '$name' is not recognized or supported by SIA on this device."
                )
            }
        }
    }

    private fun handleVibrate(durationMs: Long): ToolExecutionResult {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            ?: return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Vibration hardware is unavailable.")
        val duration = durationMs.coerceIn(1L, 5000L)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Phone vibrated for $duration ms.")
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not vibrate the phone: ${e.message ?: "unknown error"}")
        }
    }

    private fun handleSendSms(number: String, message: String): ToolExecutionResult {
        val recipient = number.trim()
        val body = message.trim()
        if (recipient.isBlank() || body.isBlank()) {
            return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Both recipient number and SMS message are required.")
        }
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient)}")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                ToolResultStatus.USER_CONFIRMATION_REQUIRED,
                "SMS composer opened for $recipient. The message has NOT been sent; the user must review it and tap Send."
            )
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not open the SMS composer: ${e.message ?: "unknown error"}")
        }
    }

    private suspend fun handlePermanentMemory(params: Map<String, String>): ToolExecutionResult {
        return when (params["action"]?.lowercase(Locale.ROOT)) {
            "save" -> {
                val key = params["key"]?.trim().orEmpty()
                val value = params["value"]?.trim().orEmpty()
                if (key.isBlank() || value.isBlank()) {
                    ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Saving a memory requires both key and value.")
                } else {
                    memoryManager.addPermanentMemory(key, value)
                    ToolExecutionResult(ToolResultStatus.SUCCESS, "Saved permanent memory for '$key'.")
                }
            }
            "list" -> {
                val memories = memoryManager.getPermanentMemoryText()
                ToolExecutionResult(
                    ToolResultStatus.SUCCESS,
                    if (memories.isBlank()) "No permanent memories are saved." else memories
                )
            }
            else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Use user_perma_memory with action save or list.")
        }
    }

    private fun handleDeviceAgent(params: Map<String, String>): ToolExecutionResult {
        val rawAction = params["action"]?.lowercase(Locale.ROOT).orEmpty()
        val action = rawAction.removeSuffix("_on").removeSuffix("_off")
        val state = when {
            rawAction.endsWith("_on") -> "on"
            rawAction.endsWith("_off") -> "off"
            else -> params["state"]?.lowercase(Locale.ROOT).orEmpty()
        }
        return when (action) {
            "torch", "flashlight" -> handleToggleTorch(state.ifBlank { "toggle" })
            "volume", "set_volume" -> handleSetVolume(params["level"]?.toIntOrNull() ?: 50)
            "brightness", "set_brightness" -> handleSetBrightness(params["level"]?.toIntOrNull() ?: if (state == "max" || state == "full") 100 else 50)
            "wifi", "bluetooth", "mobile_data", "data", "hotspot" -> {
                val setting = when (action) {
                    "wifi" -> "wifi"
                    "bluetooth" -> "bluetooth"
                    "hotspot" -> "hotspot"
                    else -> "mobile_data"
                }
                val result = handleOpenSettings(setting)
                if (result.status == ToolResultStatus.SUCCESS) ToolExecutionResult(
                    ToolResultStatus.USER_CONFIRMATION_REQUIRED,
                    "Android opened the $setting settings screen. This Android version restricts direct toggling by ordinary apps, so SIA cannot claim it changed the switch."
                ) else result
            }
            else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "This device action is not supported.")
        }
    }

    private fun handleAppAgent(params: Map<String, String>): ToolExecutionResult {
        return when (params["action"]?.lowercase(Locale.ROOT)) {
            "open" -> handleOpenApp(params["app_name"] ?: "")
            "close", "home" -> try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolExecutionResult(ToolResultStatus.SUCCESS, "Returned to the home screen. Android does not allow SIA to force-stop arbitrary apps.")
            } catch (_: Exception) {
                ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not return to the home screen.")
            }
            else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Use app_agent with action open or close.")
        }
    }

    private fun handleMediaAgent(params: Map<String, String>): ToolExecutionResult {
        val action = params["action"]?.lowercase(Locale.ROOT).orEmpty()
        val query = params["query"]?.trim().orEmpty()
        if (action == "play" && query.isBlank()) {
            // A requested song title must be present before starting playback. Otherwise the
            // active media session may resume an unrelated track and falsely satisfy the request.
            return ToolExecutionResult(
                ToolResultStatus.PERMANENT_ERROR,
                "A specific song query is required to play a named track. No track was started."
            )
        }
        if (action in setOf("play", "resume") && query.isNotBlank()) {
            return localMusicPlayer.control("play", query)
        }
        val sessionAction = if (action == "control_existing") params["media_action"] ?: "play_pause" else action
        val sessionResult = mediaSessionAgent.control(sessionAction)
        if (sessionResult?.status == ToolResultStatus.SUCCESS) return sessionResult
        if (action == "control_existing") {
            if (sessionResult?.status == ToolResultStatus.PERMISSION_REQUIRED) {
                try {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
                return sessionResult
            }
            return sessionResult ?: ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "No active media session was found.")
        }

        val localResult = when (action) {
            "play", "resume", "pause", "stop", "next", "previous" -> localMusicPlayer.control(action, params["query"] ?: "")
            else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Use media_agent with a supported playback action.")
        }
        if (localResult.status == ToolResultStatus.SUCCESS) return localResult
        if (sessionResult?.status == ToolResultStatus.PERMISSION_REQUIRED) {
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
            return sessionResult
        }
        return localResult
    }

    private suspend fun handleReminderAgent(params: Map<String, String>): ToolExecutionResult {
        return when (params["action"]?.lowercase(Locale.ROOT)) {
            "create" -> handleScheduleTask(params)
            "list", "status" -> handleGetTaskStatus(params["task_id"])
            "cancel" -> handleCancelTask(params["task_id"] ?: "")
            else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Use reminder_agent with create, list, or cancel.")
        }
    }

    private fun handleOpenApp(appName: String): ToolExecutionResult {
        val candidate = appName.trim()
        if (candidate.isEmpty()) {
            return ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Please provide the name of the application you want to open."
            )
        }

        // Resolve only against the actual installed launcher labels. Never launch the first
        // substring match: speech-recognition errors can otherwise open an unrelated app.
        val pm = context.packageManager
        val installedLabels = InstalledAppsHelper.getInstalledAppLabels(context)
        val matchedLabel = InstalledAppsHelper.findMatchingInstalledApp(candidate, installedLabels)
            ?: return ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "I couldn't confidently match '$appName' to one installed app. Please say the app's displayed name."
            )

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val matches = try {
            pm.queryIntentActivities(launcherIntent, 0).filter { info ->
                runCatching { info.loadLabel(pm).toString().trim() }.getOrNull()
                    ?.equals(matchedLabel, ignoreCase = true) == true
            }.distinctBy { it.activityInfo.packageName }
        } catch (_: Exception) {
            emptyList()
        }

        if (matches.size != 1) {
            return ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "More than one app matches '$matchedLabel', or its launcher isn't available. Please clarify the app."
            )
        }

        return try {
            val packageName = matches.single().activityInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
                ?: return ToolExecutionResult(
                    status = ToolResultStatus.PERMANENT_ERROR,
                    message = "The app '$matchedLabel' is installed but doesn't provide a launch screen."
                )
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Opening $matchedLabel.")
        } catch (e: Exception) {
            ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Could not open '$matchedLabel': ${e.message ?: "Android rejected the launch"}"
            )
        }
    }

    private fun handleWebSearch(query: String): ToolExecutionResult {
        if (query.isBlank()) {
            return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Search query is empty.")
        }
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(
                status = ToolResultStatus.SUCCESS,
                message = "Searching web for '$query'."
            )
        } catch (_: Exception) {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(browserIntent)
                ToolExecutionResult(
                    status = ToolResultStatus.SUCCESS,
                    message = "Opened browser for '$query'."
                )
            } catch (e: Exception) {
                ToolExecutionResult(
                    status = ToolResultStatus.PERMANENT_ERROR,
                    message = "Could not open browser: ${e.message}"
                )
            }
        }
    }

    private fun handleToggleTorch(state: String): ToolExecutionResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Camera hardware not available.")

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "No camera with flashlight found.")

            val targetState = when (state.lowercase(Locale.ROOT)) {
                "on" -> true
                "off" -> false
                else -> !isTorchOn
            }

            cameraManager.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            val stateText = if (targetState) "ON" else "OFF"
            return ToolExecutionResult(
                status = ToolResultStatus.SUCCESS,
                message = "Flashlight turned $stateText."
            )
        } catch (e: Exception) {
            return ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Unable to toggle torch: ${e.message}"
            )
        }
    }

    private fun handleControlMedia(action: String): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Audio service unavailable.")

        val keyCode = when (action.lowercase(Locale.ROOT)) {
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }

        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

        return ToolExecutionResult(
            status = ToolResultStatus.SUCCESS,
            message = "Media control '$action' sent."
        )
    }

    private fun handleSetBrightness(level: Int): ToolExecutionResult {
        val percent = level.coerceIn(0, 100)
        return try {
            if (!Settings.System.canWrite(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "BRIGHTNESS_PERMISSION_REQUIRED: Allow SIA to modify system settings, then repeat the brightness command.")
            }
            val value = (percent * 255 / 100).coerceIn(1, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Screen brightness set to $percent percent.")
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not change screen brightness. Check SIA's Modify System Settings permission.")
        }
    }

    private fun handleSetVolume(levelPercent: Int): ToolExecutionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Audio service unavailable.")

        val clamped = levelPercent.coerceIn(0, 100)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (maxVolume * (clamped / 100.0f)).toInt()

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, AudioManager.FLAG_SHOW_UI)
        return ToolExecutionResult(
            status = ToolResultStatus.SUCCESS,
            message = "Music volume set to $clamped%."
        )
    }

    private fun handleOpenSettings(type: String): ToolExecutionResult {
        val action = when (type.lowercase(Locale.ROOT)) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "hotspot" -> "android.settings.TETHER_SETTINGS"
            "mobile_data" -> Settings.ACTION_NETWORK_OPERATOR_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "apps", "application" -> Settings.ACTION_APPLICATION_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "date", "time" -> Settings.ACTION_DATE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ToolExecutionResult(
                status = ToolResultStatus.SUCCESS,
                message = "Opened $type settings."
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Could not open $type settings: ${e.message}"
            )
        }
    }

    private suspend fun handleScheduleTask(params: Map<String, String>): ToolExecutionResult {
        val description = params["description"] ?: params["task_description"] ?: "Reminder"
        val delayMinutes = params["delay_minutes"]?.toLongOrNull()
        val timeMsParam = params["scheduled_time_ms"]?.toLongOrNull()

        val scheduledTimeMs = when {
            timeMsParam != null && timeMsParam > System.currentTimeMillis() -> timeMsParam
            delayMinutes != null && delayMinutes > 0 -> System.currentTimeMillis() + (delayMinutes * 60 * 1000)
            else -> System.currentTimeMillis() + (5 * 60 * 1000) // Default 5 mins if unspecified
        }

        val actionType = params["action_type"] ?: "REMINDER"
        val actionPayload = params["action_payload"] ?: description
        val taskId = params["task_id"] ?: "task_${UUID.randomUUID().toString().take(8)}"

        // Permission check
        val requiresExact = SiaPermissionManager.hasExactAlarmPermission(context)
        val requiresNotif = SiaPermissionManager.hasNotificationPermission(context)

        val taskEntity = ScheduledTaskEntity(
            id = taskId,
            description = description,
            scheduledTimeMs = scheduledTimeMs,
            timeZone = Locale.getDefault().country,
            recurrence = params["recurrence"] ?: "ONE_TIME",
            actionType = actionType,
            actionPayload = actionPayload,
            requiredPermissions = if (!requiresNotif) "POST_NOTIFICATIONS" else "",
            status = "PENDING",
            attemptCount = 0,
            notificationMessage = "⏰ SIA: $description"
        )

        db.siaDao().insertScheduledTask(taskEntity)

        // Schedule Alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = "com.example.sia.EXECUTE_SCHEDULED_TASK"
            putExtra("task_id", taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormatted = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(scheduledTimeMs))

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (requiresExact) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledTimeMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            scheduledTimeMs,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTimeMs, pendingIntent)
                }
            } catch (e: Exception) {
                return ToolExecutionResult(
                    status = ToolResultStatus.RETRYABLE_ERROR,
                    message = "Task saved in SIA, but alarm trigger warning: ${e.message}"
                )
            }
        }

        val notifWarning = if (!requiresNotif) " (Note: Notification permission is off; enable it so SIA can alert you!)" else ""

        return ToolExecutionResult(
            status = ToolResultStatus.SUCCESS,
            message = "Scheduled '$description' for $timeFormatted.$notifWarning",
            data = mapOf("task_id" to taskId, "scheduled_time" to timeFormatted)
        )
    }

    private suspend fun handleGetTaskStatus(taskId: String?): ToolExecutionResult {
        return if (!taskId.isNullOrBlank()) {
            val task = db.siaDao().getScheduledTaskById(taskId)
            if (task != null) {
                val formattedTime = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date(task.scheduledTimeMs))
                ToolExecutionResult(
                    status = ToolResultStatus.SUCCESS,
                    message = "Task '${task.description}' is ${task.status}. Scheduled for $formattedTime (Attempts: ${task.attemptCount}/3)."
                )
            } else {
                ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Task with ID '$taskId' not found.")
            }
        } else {
            val pending = db.siaDao().getPendingTasks()
            if (pending.isEmpty()) {
                ToolExecutionResult(ToolResultStatus.SUCCESS, "You have no pending scheduled tasks.")
            } else {
                val summary = pending.joinToString("\n") { task ->
                    val t = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(task.scheduledTimeMs))
                    "• ${task.description} at $t [${task.status}]"
                }
                ToolExecutionResult(ToolResultStatus.SUCCESS, "Pending tasks:\n$summary")
            }
        }
    }

    private suspend fun handleCancelTask(taskId: String): ToolExecutionResult {
        if (taskId.isBlank()) {
            return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Task ID is required to cancel.")
        }
        val task = db.siaDao().getScheduledTaskById(taskId)
            ?: return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No task found with ID '$taskId'.")

        db.siaDao().updateTaskStatus(taskId, "CANCELLED")

        // Cancel alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = "com.example.sia.EXECUTE_SCHEDULED_TASK"
            putExtra("task_id", taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }

        return ToolExecutionResult(
            status = ToolResultStatus.SUCCESS,
            message = "Cancelled task '${task.description}'."
        )
    }

    private fun handleCreateWorkspaceNote(title: String, content: String): ToolExecutionResult {
        return try {
            val workspaceDir = File(context.filesDir, "workspace").apply {
                if (!exists()) mkdirs()
            }
            val sanitized = title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val fileName = if (sanitized.endsWith(".txt")) sanitized else "$sanitized.txt"
            val file = File(workspaceDir, fileName)
            file.writeText(content)
            ToolExecutionResult(
                status = ToolResultStatus.SUCCESS,
                message = "Created file '$fileName' in SIA Workspace.",
                data = mapOf("file_name" to fileName, "path" to file.absolutePath)
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Failed to create note: ${e.message}"
            )
        }
    }

    fun handleExportAppApk(): ToolExecutionResult {
        return try {
            val appInfo = context.applicationInfo
            val sourceApk = File(appInfo.sourceDir)
            if (!sourceApk.exists()) {
                return ToolExecutionResult(
                    status = ToolResultStatus.PERMANENT_ERROR,
                    message = "Could not locate installed APK source file."
                )
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val targetApk = File(exportDir, "SIA_v2_Assistant.apk")
            sourceApk.copyTo(targetApk, overwrite = true)

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(Intent.EXTRA_SUBJECT, "SIA v2 Assistant APK")
                putExtra(Intent.EXTRA_TEXT, "Here is the SIA v2 Personal AI Assistant APK by FAST. Open and tap Install.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(sendIntent, "Install / Share SIA v2 APK").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            ToolExecutionResult(
                status = ToolResultStatus.SUCCESS,
                message = "SIA v2 APK prepared successfully. Share sheet opened to save or install on your phone.",
                data = mapOf("apk_path" to targetApk.absolutePath)
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Failed to export APK: ${e.message}"
            )
        }
    }

    fun handleDirectInstallApk(): ToolExecutionResult {
        return try {
            val appInfo = context.applicationInfo
            val sourceApk = File(appInfo.sourceDir)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val targetApk = File(exportDir, "SIA_v2_Assistant.apk")
            sourceApk.copyTo(targetApk, overwrite = true)

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetApk
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)

            ToolExecutionResult(
                status = ToolResultStatus.SUCCESS,
                message = "Package installer opened for SIA v2 APK."
            )
        } catch (e: Exception) {
            handleExportAppApk()
        }
    }

    fun handleShareWorkspaceNote(fileName: String): ToolExecutionResult {
        return try {
            val workspaceDir = File(context.filesDir, "workspace")
            val targetFile = File(workspaceDir, fileName)
            if (!targetFile.exists()) {
                return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Note '$fileName' not found in workspace.")
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, targetFile.readText())
                putExtra(Intent.EXTRA_SUBJECT, targetFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share ${targetFile.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Sharing '${targetFile.name}'.")
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Failed to share note: ${e.message}")
        }
    }

    private fun handleListAllCommands(): ToolExecutionResult {
        val tools = ToolRegistry.getAgentTools()
        val text = tools.joinToString("\n\n") { tool ->
            val perms = if (tool.requiredPermissions.isEmpty()) "None" else tool.requiredPermissions.joinToString(", ")
            "• ${tool.name}: ${tool.description} (Permissions: $perms)"
        }
        return ToolExecutionResult(
            status = ToolResultStatus.SUCCESS,
            message = "SIA Supported Capabilities:\n$text"
        )
    }
}
