package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val scheduledTimeMs: Long,
    val timeZone: String = "UTC",
    val recurrence: String = "ONE_TIME", // "ONE_TIME", "DAILY"
    val actionType: String, // "REMINDER", "WEB_SEARCH", "TOGGLE_TORCH", "CUSTOM"
    val actionPayload: String, // json or parameter text
    val requiredPermissions: String = "",
    val status: String = "PENDING", // "PENDING", "COMPLETED", "FAILED", "CANCELLED"
    val attemptCount: Int = 0,
    val lastAttemptTimeMs: Long = 0L,
    val lastResult: String? = null,
    val notificationMessage: String? = null
)
