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
                // Simple LLM-facing functions delegate to the existing tested action handlers.
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