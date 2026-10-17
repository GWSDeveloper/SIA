package com.example.tools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.provider.Settings

/**
 * Controls active media sessions when Notification Access is enabled.
 * Returns null only when access is granted but no active controllable session exists.
 */
class MediaSessionAgent(private val context: Context) {
    fun control(action: String): ToolExecutionResult? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
        val controllers = try {
            manager.getActiveSessions(ComponentName(context, SiaMediaNotificationListener::class.java))
        } catch (_: SecurityException) {
            return ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "MEDIA_ACCESS_REQUIRED: Enable SIA under Notification Access to control existing media apps.")
        } catch (_: Exception) {
            return null
        }
        val controller = controllers.firstOrNull() ?: return null
        return try {
            when (action.lowercase()) {
                "play", "resume" -> controller.transportControls.play()
                "pause" -> controller.transportControls.pause()
                "next" -> controller.transportControls.skipToNext()
                "previous", "prev" -> controller.transportControls.skipToPrevious()
                "stop" -> controller.transportControls.stop()
                "play_pause" -> {
                    val state = controller.playbackState?.state
                    if (state == android.media.session.PlaybackState.STATE_PLAYING) controller.transportControls.pause()
                    else controller.transportControls.play()
                }
                else -> return ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Unsupported media session action.")
            }
            ToolExecutionResult(ToolResultStatus.SUCCESS, "Media command sent to " + controller.packageName + ".")
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.RETRYABLE_ERROR, "The active media app did not accept the command.")
        }
    }
}

/** Required component identity for MediaSessionManager active-session access. */
class SiaMediaNotificationListener : android.service.notification.NotificationListenerService()
