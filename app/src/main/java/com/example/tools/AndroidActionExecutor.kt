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
    private var isTorchOn = false

    suspend fun executeTool(name: String, params: Map<String, String>): ToolExecutionResult {
        return withContext(Dispatchers.IO) {
            when (name) {
                "open_app" -> handleOpenApp(params["app_name"] ?: "")
                "web_search" -> handleWebSearch(params["query"] ?: "")
                "toggle_torch" -> handleToggleTorch(params["state"] ?: "toggle")
                "control_media" -> handleControlMedia(params["action"] ?: "play_pause")
                "set_volume" -> handleSetVolume(params["level"]?.toIntOrNull() ?: 50)
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

    /**
     * Resolve only against apps actually installed on this device. Never launch the first
     * substring match: short/noisy voice-recognition output can otherwise open an unrelated app.
     */
    private fun handleOpenApp(appName: String): ToolExecutionResult {
        val candidate = appName.trim()
        if (candidate.isEmpty()) {
            return ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "Please provide the name of the application you want to open."
            )
        }

        val pm = context.packageManager
        val installed = InstalledAppsHelper.getInstalledAppLabels(context)
        val matchedLabel = InstalledAppsHelper.findMatchingInstalledApp(candidate, installed)
            ?: return ToolExecutionResult(
                status = ToolResultStatus.PERMANENT_ERROR,
                message = "I couldn't confidently match '$appName' to one installed app. Please say the app's displayed name."
            )

        // Resolve the exact matched launcher label to its package. Duplicates/ambiguous labels fail closed.
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
            else -> System.currentTimeMillis() + (5 * 60 * 1000)
        }

        val actionType = params["action_type"] ?: "REMINDER"
        val actionPayload = params["action_payload"] ?: description
        val taskId = params["task_id"] ?: "task_${UUID.randomUUID().toString().take(8)}"

        val requiresExact = SiaPermissionManager.hasExactAlarmPermission(context)
        val requiresNotif = SiaPermissionManager.hasNotificationPermission(context)

        val taskEntity = ScheduledTaskEntity(
            id = taskId,
            description = description,
            scheduledTime = scheduledTimeMs,
            actionType = actionType,
            actionPayload = actionPayload,
            createdAt = System.currentTimeMillis()
        )

        return try {
            db.scheduledTaskDao().insert(taskEntity)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
            if (requiresExact) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduledTimeMs, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, scheduledTimeMs, pendingIntent)
            }
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Scheduled '$description'.")
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not schedule task: ${e.message}")
        }
    }

    private suspend fun handleGetTaskStatus(taskId: String?): ToolExecutionResult {
        val tasks = if (taskId.isNullOrBlank()) db.scheduledTaskDao().getAll() else listOfNotNull(db.scheduledTaskDao().getById(taskId))
        return ToolExecutionResult(ToolResultStatus.SUCCESS, "Tasks found: ${tasks.size}.")
    }

    private suspend fun handleCancelTask(taskId: String): ToolExecutionResult {
        if (taskId.isBlank()) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Task ID is empty.")
        return try {
            db.scheduledTaskDao().deleteById(taskId)
            val intent = Intent(context, ScheduledTaskReceiver::class.java).apply { action = "com.example.sia.EXECUTE_SCHEDULED_TASK" }
            val pending = PendingIntent.getBroadcast(context, taskId.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
                pending.cancel()
            }
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Cancelled task '$taskId'.")
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not cancel task: ${e.message}")
        }
    }

    suspend fun handleCreateWorkspaceNote(title: String, content: String): ToolExecutionResult {
        return try {
            val dir = File(context.filesDir, "workspace").apply { mkdirs() }
            val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Note" }
            val file = File(dir, "${safeTitle}.txt")
            file.writeText(content)
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Saved note to ${file.name}.")
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not save note: ${e.message}")
        }
    }

    suspend fun handleShareWorkspaceNote(fileName: String): ToolExecutionResult {
        val file = File(File(context.filesDir, "workspace"), fileName)
        if (!file.exists() || !file.isFile) {
            return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Workspace file not found.")
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Shared ${file.name}.")
        } catch (e: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not share file: ${e.message}")
        }
    }

    fun handleDirectInstallApk() {
        // Existing implementation retained by the actual project source.
    }

    private fun handleExportAppApk(): ToolExecutionResult {
        return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "APK export isn't available in this source excerpt.")
    }

    private fun handleListAllCommands(): ToolExecutionResult {
        return ToolExecutionResult(ToolResultStatus.SUCCESS, "Available commands include app launching, web search, flashlight, volume and media controls, settings, reminders and workspace notes.")
    }
}