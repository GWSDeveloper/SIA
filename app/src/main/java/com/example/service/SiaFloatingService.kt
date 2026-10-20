package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.permissions.SiaPermissionManager
import com.example.preferences.SiaPreferences
import com.example.provider.PromptContext
import com.example.provider.PromptMessage
import com.example.data.SiaDatabase
import com.example.data.ConversationEntity
import com.example.memory.MemoryManager
import com.example.provider.ProviderFactory
import com.example.speech.AssistantVisualState
import com.example.speech.SiaSpeechManager
import com.example.tools.AndroidActionExecutor
import com.example.tools.ToolRegistry
import com.example.tools.ToolResultStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SiaFloatingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var isOverlayAttached = false

    private var speechManager: SiaSpeechManager? = null
    private var statusTextView: TextView? = null
    private var orbImageView: FloatingEnergyOrbView? = null
    private var micButton: FrameLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        speechManager = SiaSpeechManager(
            context = this,
            onSpeechRecognized = { command ->
                handleFloatingCommand(command)
            },
            onRecognitionError = { err ->
                statusTextView?.text = err
                updateOrbVisualState(AssistantVisualState.IDLE)
            }
        )

        serviceScope.launch {
            speechManager?.visualState?.collect { state ->
                updateOrbVisualState(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            removeFloatingOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        if (action == ACTION_START_SIA_OVERLAY) {
            showFloatingAssistantOverlay()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SIA Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for instant SIA floating orb and voice actions"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val prefs = SiaPreferences.getInstance(this)
        val assistantName = prefs.getAssistantName()

        // Tap notification body -> Open full app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action button "Start SIA" -> Triggers floating overlay via Service PendingIntent!
        val startOverlayIntent = Intent(this, SiaFloatingService::class.java).apply {
            action = ACTION_START_SIA_OVERLAY
        }
        val startOverlayPendingIntent = PendingIntent.getService(
            this,
            102,
            startOverlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$assistantName is Ready")
            .setContentText("Tap 'Start $assistantName' to open floating assistant")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Start $assistantName",
                startOverlayPendingIntent
            )
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingAssistantOverlay() {
        if (!SiaPermissionManager.hasOverlayPermission(this)) {
            // If overlay permission not granted, launch MainActivity with voice request
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                this.action = "com.example.ACTION_START_SIA_VOICE"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(launchIntent)
            return
        }

        if (isOverlayAttached) {
            // Already showing, trigger voice listening
            speechManager?.startListening()
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // Compact floating glass dock; it never dims or blocks the whole screen.
            val density = resources.displayMetrics.density
            val orbSize = (58 * density).toInt()
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 42 * density
                    setColor(Color.parseColor("#F0182935"))
                    setStroke((1 * density).toInt(), Color.parseColor("#6677DCCB"))
                }
                elevation = 12 * density
            }

            val orb = FloatingEnergyOrbView(this).apply {
                layoutParams = LinearLayout.LayoutParams(orbSize, orbSize)
                contentDescription = "SIA floating assistant. Tap to start or pause voice."
                setOnClickListener {
                    when (speechManager?.visualState?.value) {
                        AssistantVisualState.PAUSED -> speechManager?.resumeSession()
                        AssistantVisualState.LISTENING,
                        AssistantVisualState.PREPARING,
                        AssistantVisualState.SPEAKING -> speechManager?.pauseSession(notifyUser = true)
                        else -> speechManager?.startSession()
                    }
                }
            }
            orbImageView = orb
            root.addView(orb)

            val textColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * density).toInt()
                    marginEnd = (6 * density).toInt()
                }
            }
            val titleText = TextView(this).apply {
                text = "SIA"
                setTextColor(Color.parseColor("#DDFBF2"))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            textColumn.addView(titleText)

            val status = TextView(this).apply {
                text = "Tap orb to speak"
                setTextColor(Color.parseColor("#A9C5CB"))
                textSize = 11f
                maxLines = 2
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            statusTextView = status
            textColumn.addView(status)
            root.addView(textColumn)

            val closeButton = TextView(this).apply {
                text = "×"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#C8E1E4"))
                contentDescription = "Close floating assistant"
                layoutParams = LinearLayout.LayoutParams((34 * density).toInt(), (42 * density).toInt())
                setOnClickListener { removeFloatingOverlay() }
            }
            root.addView(closeButton)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (12 * density).toInt()
                y = (180 * density).toInt()
            }

            // Drag from the status/title area to reposition; tapping the orb still works.
            var downRawX = 0f
            var downRawY = 0f
            var startX = 0
            var startY = 0
            var dragged = false
            textColumn.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragged = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRawX).toInt()
                        val dy = (event.rawY - downRawY).toInt()
                        if (kotlin.math.abs(dx) > (5 * density).toInt() || kotlin.math.abs(dy) > (5 * density).toInt()) dragged = true
                        if (dragged) {
                            params.x = (startX - dx).coerceAtLeast(0)
                            params.y = (startY + dy).coerceAtLeast(0)
                            try { windowManager?.updateViewLayout(root, params) } catch (_: Exception) {}
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }

            windowManager?.addView(root, params)
            overlayRootView = root
            isOverlayAttached = true

            // Automatically start voice session when overlay opens
            Handler(Looper.getMainLooper()).postDelayed({
                speechManager?.startSession()
            }, 300)

        } catch (_: Exception) {}
    }

    private fun handleFloatingCommand(command: String) {
        statusTextView?.text = "Command: $command"
        updateOrbVisualState(AssistantVisualState.THINKING)

        serviceScope.launch(Dispatchers.IO) {
            val executor = AndroidActionExecutor(this@SiaFloatingService)
            val preferences = SiaPreferences.getInstance(this@SiaFloatingService)
            val memory = MemoryManager(this@SiaFloatingService)
            val dao = SiaDatabase.getDatabase(this@SiaFloatingService).siaDao()
            dao.insertMessage(
                ConversationEntity(sender = "user", text = command, timestamp = System.currentTimeMillis())
            )
            val recentHistory = dao.getRecentMessages(11).reversed()
                .dropLastWhile { it.sender == "user" && it.text == command }
                .takeLast(10)
                .map {
                    PromptMessage(
                        role = if (it.sender == "user") "user" else "assistant",
                        content = it.text
                    )
                }
            val systemPrompt = com.example.provider.SiaSystemPrompt.build(preferences.getAssistantName())

            val initialContext = PromptContext(
                systemPrompt = systemPrompt,
                permanentMemory = memory.getPermanentMemoryText(),
                rollingSummary = memory.getRollingSummaryText(),
                recentMessages = recentHistory,
                currentUserMessage = command,
                availableTools = ToolRegistry.getAgentTools()
            )

            val first = ProviderFactory.createProvider(this@SiaFloatingService).generateResponse(initialContext)
            if (first.isFailure) {
                val userMessage = "AI provider request failed. Check the selected provider and API key in Settings, then try again."
                launch(Dispatchers.Main) {
                    statusTextView?.text = userMessage
                    speechManager?.speak(userMessage)
                    updateOrbVisualState(AssistantVisualState.ERROR)
                }
                return@launch
            }

            val outcome = com.example.tools.ToolExecutionCoordinator(
                this@SiaFloatingService,
                executor
            ).execute(
                initialOutput = first.getOrThrow(),
                planningContext = initialContext,
                originalUserText = command
            )
            val executionResults = outcome.executionResults
            var finalText = outcome.finalOutput.textReply
            if (outcome.roundLimitReached) {
                finalText = if (command.any { it.code > 127 }) {
                    "Kaafi steps execute ho gaye, lekin poora task verify nahi ho paaya."
                } else {
                    "I reached the safe action limit before I could verify the entire request."
                }
            }

            val spoken = finalText.trim().takeIf { it.isNotBlank() && !it.contains("tool_calls") }
                ?: when {
                    executionResults.isEmpty() -> "I couldn't get a usable response. Please try again."
                    executionResults.all { it.second.status == ToolResultStatus.SUCCESS } ->
                        "The actions ran, but I couldn't get a final confirmation. Please check the phone."
                    executionResults.any { it.second.status == ToolResultStatus.SUCCESS } ->
                        "Some requested actions completed, but others could not be confirmed. Please check the phone and required permissions."
                    else -> "I couldn't complete that action. Please check the required permission or setting."
                }

            dao.insertMessage(
                ConversationEntity(
                    sender = "assistant",
                    text = spoken,
                    timestamp = System.currentTimeMillis(),
                    toolName = executionResults.joinToString(", ") { it.first.toolName }.ifBlank { null },
                    toolStatus = if (executionResults.isEmpty()) null else executionResults.joinToString(", ") { it.second.status.name },
                    toolResult = executionResults.joinToString("; ") { "${it.first.toolName}: ${it.second.message}" }.ifBlank { null },
                    isSpoken = true
                )
            )
            memory.checkAndSummarizeIfNeeded()

            launch(Dispatchers.Main) {
                statusTextView?.text = spoken
                speechManager?.speak(spoken)
                updateOrbVisualState(AssistantVisualState.SPEAKING)
            }
        }
    }

    private fun updateOrbVisualState(state: AssistantVisualState) {
        statusTextView?.text = when (state) {
            AssistantVisualState.PREPARING -> "Preparing voice…"
            AssistantVisualState.LISTENING -> "Listening · speak now"
            AssistantVisualState.THINKING -> "Thinking…"
            AssistantVisualState.EXECUTING -> "Working on it…"
            AssistantVisualState.SPEAKING -> "SIA is speaking"
            AssistantVisualState.PAUSED -> "Paused · tap orb to resume"
            AssistantVisualState.ERROR -> "Voice error · tap to retry"
            AssistantVisualState.IDLE -> "Tap orb to speak"
        }
        orbImageView?.setVisualState(state)
    }

    private fun removeFloatingOverlay() {
        speechManager?.stopSession()

        if (isOverlayAttached && overlayRootView != null) {
            try {
                windowManager?.removeView(overlayRootView)
            } catch (_: Exception) {}
            isOverlayAttached = false
            overlayRootView = null
        }
    }

    override fun onDestroy() {
        removeFloatingOverlay()
        speechManager?.destroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "sia_floating_service_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_SERVICE = "com.example.sia.START_SERVICE"
        const val ACTION_START_SIA_OVERLAY = "com.example.sia.START_OVERLAY"
        const val ACTION_STOP_SERVICE = "com.example.sia.STOP_SERVICE"

        fun startService(context: Context, showOverlay: Boolean = false) {
            val intent = Intent(context, SiaFloatingService::class.java).apply {
                action = if (showOverlay) ACTION_START_SIA_OVERLAY else ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SiaFloatingService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
