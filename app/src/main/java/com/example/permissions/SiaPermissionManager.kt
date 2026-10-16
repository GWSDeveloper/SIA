package com.example.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatus(
    val name: String,
    val permissionId: String,
    val isGranted: Boolean,
    val description: String,
    val isSpecialAccess: Boolean = false
)

object SiaPermissionManager {

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun getAllPermissionsStatus(context: Context): List<PermissionStatus> {
        val list = mutableListOf<PermissionStatus>()

        list.add(
            PermissionStatus(
                name = "Microphone",
                permissionId = Manifest.permission.RECORD_AUDIO,
                isGranted = hasRecordAudioPermission(context),
                description = "Required for voice interactions and listening to commands."
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionStatus(
                    name = "Notifications",
                    permissionId = Manifest.permission.POST_NOTIFICATIONS,
                    isGranted = hasNotificationPermission(context),
                    description = "Required for persistent assistant bar and scheduled task alerts."
                )
            )
        }

        list.add(
            PermissionStatus(
                name = "Display Over Other Apps",
                permissionId = "android.settings.action.MANAGE_OVERLAY_PERMISSION",
                isGranted = hasOverlayPermission(context),
                description = "Required to show the floating assistant orb when triggered from notifications.",
                isSpecialAccess = true
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(
                PermissionStatus(
                    name = "Exact Alarms",
                    permissionId = "android.settings.REQUEST_SCHEDULE_EXACT_ALARM",
                    isGranted = hasExactAlarmPermission(context),
                    description = "Required to trigger scheduled reminders precisely on time.",
                    isSpecialAccess = true
                )
            )
        }

        return list
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
