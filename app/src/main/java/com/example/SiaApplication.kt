package com.example

import android.app.Application
import com.example.memory.MemoryManager
import com.example.permissions.SiaPermissionManager
import com.example.preferences.SiaPreferences
import com.example.service.SiaFloatingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize default permanent memories
        CoroutineScope(Dispatchers.IO).launch {
            val memoryManager = MemoryManager(this@SiaApplication)
            memoryManager.initializeDefaultMemoriesIfEmpty()
        }

        // Start persistent assistant notification if enabled and notification permission available
        val prefs = SiaPreferences.getInstance(this)
        if (prefs.isPersistentNotificationEnabled() && SiaPermissionManager.hasNotificationPermission(this)) {
            try {
                SiaFloatingService.startService(this, showOverlay = false)
            } catch (_: Exception) {}
        }
    }
}
