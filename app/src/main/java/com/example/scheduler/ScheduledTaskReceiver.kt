package com.example.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ConversationEntity
import com.example.data.SiaDatabase
import com.example.permissions.SiaPermissionManager
import com.example.provider.PromptContext
import com.example.provider.ProviderFactory
import com.example.tools.AndroidActionExecutor
import com.example.tools.ToolRegistry
import com.example.tools.ToolResultStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processTask(context, taskId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processTask(context: Context, taskId: String) {
        val db = SiaDatabase.getDatabase(context)
        val task = db.siaDao().getScheduledTaskById(taskId) ?: return

        // Prevent duplicate execution
        if (task.status != "PENDING") {
            return
        }

        // Permission revalidation at execution time
        if (task.requiredPermissions.contains("POST_NOTIFICATIONS") && !SiaPermissionManager.hasNotificationPermission(context)) {
            db.siaDao().updateScheduledTask(
                task.copy(
                    status = "FAILED",
                    lastResult = "Missing Notification Permission at execution time."
                )
            )
            return
        }

        val currentAttempts = task.attemptCount + 1
        val executor = AndroidActionExecutor(context)

        var isSuccess = false
        var resultMessage = ""
        var notificationTitle = "⏰ SIA Reminder"
        var notificationContent = task.description

        when (task.actionType) {
            "REMINDER" -> {
                isSuccess = true
                resultMessage = "Reminder delivered: ${task.description}"
                notificationTitle = "🔔 SIA Reminder"
                notificationContent = task.description
            }
            "TOGGLE_TORCH" -> {
                val res = executor.executeTool("toggle_torch", mapOf("state" to "toggle"))
                isSuccess = res.status == ToolResultStatus.SUCCESS
                resultMessage = res.message
                notificationTitle = "🔦 SIA Action Executed"
                notificationContent = res.message
            }
            "WEB_SEARCH", "ACTION_TASK", "SUMMARY_TASK" -> {
                // Execute actual information gathering workflow
                try {
                    val query = task.actionPayload.ifEmpty { task.description }
                    val provider = ProviderFactory.createProvider(context)
                    val promptContext = PromptContext(
                        systemPrompt = "You are SIA by FAST. The user scheduled a task to gather information or summarize: '$query'. Provide a concise 2-3 sentence summary in Hindi/Hinglish or English.",
                        permanentMemory = "",
                        rollingSummary = "",
                        recentMessages = emptyList(),
                        currentUserMessage = "Scheduled task due: $query",
                        availableTools = ToolRegistry.getAllTools()
                    )
                    val result = provider.generateResponse(promptContext)
                    if (result.isSuccess) {
                        val reply = result.getOrThrow().textReply
                        isSuccess = true
                        resultMessage = reply
                        notificationTitle = "📰 SIA: ${task.description}"
                        notificationContent = reply

                        // Also record in conversation thread
                        db.siaDao().insertMessage(
                            ConversationEntity(
                                sender = "assistant",
                                text = "⏰ [Scheduled Task Complete] ${task.description}\n\n$reply",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } else {
                        // Fallback to search intent
                        val res = executor.executeTool("web_search", mapOf("query" to query))
                        isSuccess = res.status == ToolResultStatus.SUCCESS
                        resultMessage = res.message
                        notificationTitle = "🔍 SIA Web Task"
                        notificationContent = res.message
                    }
                } catch (e: Exception) {
                    isSuccess = false
                    resultMessage = e.message ?: "Task execution error"
                }
            }
            else -> {
                isSuccess = true
                resultMessage = task.description
                notificationTitle = "⏰ SIA Task"
                notificationContent = task.description
            }
        }

        if (isSuccess) {
            db.siaDao().updateScheduledTask(
                task.copy(
                    status = "COMPLETED",
                    attemptCount = currentAttempts,
                    lastAttemptTimeMs = System.currentTimeMillis(),
                    lastResult = resultMessage
                )
            )
            showNotification(context, notificationTitle, notificationContent)
        } else {
            // Bounded retry: Maximum 3 attempts
            if (currentAttempts < 3) {
                val retryTimeMs = System.currentTimeMillis() + (2 * 60 * 1000)
                db.siaDao().updateScheduledTask(
                    task.copy(
                        attemptCount = currentAttempts,
                        lastAttemptTimeMs = System.currentTimeMillis(),
                        lastResult = "Attempt $currentAttempts failed: $resultMessage. Retrying in 2 mins..."
                    )
                )

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val retryIntent = Intent(context, ScheduledTaskReceiver::class.java).apply {
                    action = "com.example.sia.EXECUTE_SCHEDULED_TASK"
                    putExtra("task_id", taskId)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    taskId.hashCode(),
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager?.let { am ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, retryTimeMs, pi)
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, retryTimeMs, pi)
                    }
                }
            } else {
                // Max attempts reached, stop and report failure honestly
                db.siaDao().updateScheduledTask(
                    task.copy(
                        status = "FAILED",
                        attemptCount = currentAttempts,
                        lastAttemptTimeMs = System.currentTimeMillis(),
                        lastResult = "Execution failed after 3 attempts: $resultMessage"
                    )
                )
                showNotification(context, "⚠️ SIA Task Notice", "Task '${task.description}' failed after 3 attempts: $resultMessage")
            }
        }
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "sia_tasks_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SIA Task Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled SIA tasks and reminders"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
