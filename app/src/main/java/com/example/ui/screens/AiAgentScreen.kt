package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConversationEntity
import com.example.speech.AssistantVisualState
import com.example.ui.AgentMode
import com.example.ui.SiaViewModel
import com.example.ui.components.AnimatedOrb
import com.example.ui.theme.SiaAmber
import com.example.ui.theme.SiaBgDark
import com.example.ui.theme.SiaBorder
import com.example.ui.theme.SiaCardBg
import com.example.ui.theme.SiaCyan
import com.example.ui.theme.SiaGreen
import com.example.ui.theme.SiaPink
import com.example.ui.theme.SiaRed
import com.example.ui.theme.SiaSurfaceDark
import com.example.ui.theme.SiaSurfaceVariantDark
import com.example.ui.theme.SiaTextMuted
import com.example.ui.theme.SiaTextPrimary
import com.example.ui.theme.SiaTextSecondary
import com.example.ui.theme.SiaViolet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(
    viewModel: SiaViewModel,
    modifier: Modifier = Modifier
) {
    val agentMode by viewModel.agentMode.collectAsState()
    val visualState by viewModel.visualState.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val lastReply by viewModel.lastAssistantReply.collectAsState()
    val messages by viewModel.conversationMessages.collectAsState()
    val inputText by viewModel.currentInputText.collectAsState()
    val audioLevel by viewModel.speechManager.rmsAudioLevel.collectAsState()
    val assistantName = viewModel.preferences.getAssistantName()
    var showInstallDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SiaBgDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(22.dp)).background(SiaSurfaceDark)
                .border(1.dp, SiaBorder.copy(alpha = 0.85f), RoundedCornerShape(22.dp)).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeSegment("Assistant", { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }, agentMode == AgentMode.ANIMATION, { viewModel.setAgentMode(AgentMode.ANIMATION) }, Modifier.weight(1f).testTag("tab_animation_mode"))
            ModeSegment("Chat", { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp)) }, agentMode == AgentMode.CHAT, { viewModel.setAgentMode(AgentMode.CHAT) }, Modifier.weight(1f).testTag("tab_chat_mode"))
        }

        // Mode Content
        when (agentMode) {
            AgentMode.ANIMATION -> {
                AnimationModeContent(
                    assistantName = assistantName,
                    visualState = visualState,
                    statusText = statusText,
                    lastReply = lastReply,
                    audioLevel = audioLevel,
                    onOrbClick = {
                        when (visualState) {
                            AssistantVisualState.PAUSED -> viewModel.resumeVoiceInteraction()
                            AssistantVisualState.LISTENING, AssistantVisualState.PREPARING -> viewModel.stopVoiceInteraction()
                            else -> viewModel.startVoiceInteraction()
                        }
                    },
                    onQuickCommand = { cmd ->
                        viewModel.processUserMessage(cmd, isFromVoice = false)
                    },
                    onShowInstallDialog = { showInstallDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }
            AgentMode.CHAT -> {
                ChatModeContent(
                    messages = messages,
                    inputText = inputText,
                    onInputTextChange = viewModel::setInputText,
                    onSendMessage = viewModel::submitTextMessage,
                    onMicClick = {
                        when (visualState) {
                            AssistantVisualState.PAUSED -> viewModel.resumeVoiceInteraction()
                            AssistantVisualState.LISTENING, AssistantVisualState.PREPARING, AssistantVisualState.SPEAKING -> viewModel.stopVoiceInteraction()
                            else -> viewModel.startVoiceInteraction()
                        }
                    },
                    visualState = visualState,
                    onSpeakMessage = viewModel::speakMessage,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = SiaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Install SIA v2 on Phone",
                        color = SiaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "SIA v2 is compiled and ready for Android installation!",
                        color = SiaCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Direct Install: Launches Android's Package Installer directly on this device.\n\n2. Share / Save APK: Sends the complete SIA APK via WhatsApp, Google Drive, Gmail, or your Files app so you can open it on your physical phone.\n\n3. Unknown Apps: If Android prompts you, tap 'Settings' -> toggle 'Allow from this source', then tap Install.",
                        color = SiaTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstallDialog = false
                        viewModel.directInstallApk()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaCyan, contentColor = SiaBgDark)
                ) {
                    Text("Direct Install", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    Button(
                        onClick = {
                            showInstallDialog = false
                            viewModel.exportApk()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SiaSurfaceVariantDark, contentColor = SiaCyan)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share APK")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showInstallDialog = false }) {
                        Text("Close", color = SiaTextMuted)
                    }
                }
            },
            containerColor = SiaCardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun AnimationModeContent(
    assistantName: String,
    visualState: AssistantVisualState,
    statusText: String,
    lastReply: String,
    audioLevel: Float,
    onOrbClick: () -> Unit,
    onQuickCommand: (String) -> Unit,
    onShowInstallDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Row: State badge + Install APK button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StateBadge(state = visualState)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SiaCyan.copy(alpha = 0.15f))
                    .border(1.dp, SiaCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .clickable { onShowInstallDialog() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_top_install_apk")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Install APK",
                        tint = SiaCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Install APK",
                        color = SiaCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Center Animated Orb with live audio reactivity
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedOrb(
                    state = visualState,
                    size = 224.dp,
                    audioLevel = audioLevel,
                    onClick = onOrbClick
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = assistantName,
                    color = SiaTextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusText,
                    color = SiaTextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // Clean last spoken reply card
        AnimatedVisibility(
            visible = lastReply.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val cleanReply = cleanUserFacingText(lastReply)
            if (cleanReply.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SiaCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .border(1.dp, SiaBorder.copy(alpha = 0.52f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(13.dp)) {
                        Text(
                            text = "$assistantName:",
                            color = SiaCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cleanReply,
                            color = SiaTextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        Text("TRY A QUICK ACTION", color = SiaTextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            val actions = listOf(
                Triple("Install app", "install on phone", Icons.Default.PhoneAndroid),
                Triple("Open YouTube", "open youtube", Icons.Default.PlayArrow),
                Triple("Flashlight", "torch on", Icons.Default.FlashOn),
                Triple("Take a break", "remind me in 5 minutes to take a break", Icons.Default.NotificationsActive)
            )
            items(actions) { (label, command, actionIcon) ->
                Card(onClick = { onQuickCommand(command) }, colors = CardDefaults.cardColors(containerColor = SiaSurfaceDark), shape = RoundedCornerShape(18.dp), modifier = Modifier.width(142.dp).border(1.dp, SiaBorder.copy(alpha = 0.9f), RoundedCornerShape(18.dp))) {
                    Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(SiaCyan.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(actionIcon, contentDescription = null, tint = SiaCyan, modifier = Modifier.size(18.dp))
                        }
                        Text(label, color = SiaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }

        // The orb is the sole voice-entry control; keep the bottom area uncluttered.
        Spacer(modifier = Modifier.height(8.dp))
        val hintText = when (visualState) {
            AssistantVisualState.PREPARING -> "Preparing audio..."
            AssistantVisualState.LISTENING -> "Listening now! Speak your command..."
            AssistantVisualState.THINKING -> "Thinking..."
            AssistantVisualState.EXECUTING -> "Executing actions..."
            AssistantVisualState.SPEAKING -> "Speaking response..."
            else -> "Tap the orb to speak"
        }
        Text(
            text = hintText,
            color = SiaTextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
fun StateBadge(state: AssistantVisualState) {
    val (label, color) = when (state) {
        AssistantVisualState.IDLE -> "READY" to SiaCyan
        AssistantVisualState.PREPARING -> "PREPARING" to SiaAmber
        AssistantVisualState.LISTENING -> "LISTENING" to SiaGreen
        AssistantVisualState.THINKING -> "THINKING" to SiaViolet
        AssistantVisualState.EXECUTING -> "EXECUTING ACTIONS" to SiaPink
        AssistantVisualState.SPEAKING -> "SPEAKING" to SiaCyan
        AssistantVisualState.PAUSED -> "PAUSED" to SiaAmber
        AssistantVisualState.ERROR -> "NOTICE" to SiaRed
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ChatModeContent(
    messages: List<ConversationEntity>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onMicClick: () -> Unit,
    visualState: AssistantVisualState,
    onSpeakMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = SiaTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "A little space for big ideas",
                        color = SiaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Ask anything, or tell SIA what you need.",
                        color = SiaTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(message = msg, onSpeak = { onSpeakMessage(msg.text) })
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(SiaBgDark).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val micColor = when (visualState) {
                AssistantVisualState.LISTENING -> SiaGreen
                AssistantVisualState.PREPARING -> SiaAmber
                else -> SiaSurfaceVariantDark
            }
            IconButton(onClick = onMicClick, modifier = Modifier.padding(bottom = 3.dp).size(44.dp).clip(CircleShape).background(micColor.copy(alpha = 0.92f)).testTag("chat_mic_button")) {
                Icon(imageVector = if (visualState == AssistantVisualState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Voice input", tint = if (visualState == AssistantVisualState.LISTENING) SiaBgDark else SiaCyan)
            }
            Spacer(modifier = Modifier.width(9.dp))
            OutlinedTextField(
                value = inputText, onValueChange = onInputTextChange,
                placeholder = { Text("Message SIA…", color = SiaTextMuted, fontSize = 15.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SiaCyan.copy(alpha = 0.72f), unfocusedBorderColor = SiaBorder,
                    focusedTextColor = SiaTextPrimary, unfocusedTextColor = SiaTextPrimary,
                    focusedContainerColor = SiaSurfaceDark, unfocusedContainerColor = SiaSurfaceDark, cursorColor = SiaCyan
                ),
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("chat_input_field"),
                maxLines = 5
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSendMessage, enabled = inputText.isNotBlank(), modifier = Modifier.padding(bottom = 3.dp).size(48.dp).clip(CircleShape).background(if (inputText.isNotBlank()) SiaCyan else SiaSurfaceVariantDark).testTag("chat_send_button")) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = if (inputText.isNotBlank()) SiaBgDark else SiaTextMuted)
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ConversationEntity,
    onSpeak: () -> Unit
) {
    val isUser = message.sender == "user"
    val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    val cleanText = cleanUserFacingText(message.text)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF35465D) else SiaSurfaceDark
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .border(
                    width = 1.dp,
                    color = if (isUser) SiaViolet.copy(alpha = 0.45f) else SiaBorder.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "SIA",
                        color = if (isUser) SiaViolet else SiaCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFormatted,
                            color = SiaTextMuted,
                            fontSize = 11.sp
                        )
                        if (!isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Play voice",
                                tint = SiaCyan,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onSpeak() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                MarkdownMessage(text = cleanText)

                // Clean human-readable action badge without ANY raw JSON
                if (!message.toolName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SiaSurfaceVariantDark)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when (message.toolStatus) {
                                "SUCCESS" -> SiaGreen
                                "PARTIAL_SUCCESS" -> SiaAmber
                                "PERMISSION_REQUIRED" -> SiaPink
                                "UNSUPPORTED" -> SiaRed
                                else -> SiaCyan
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val displayBadge = message.toolResult?.takeIf { it.isNotBlank() && !it.contains("{") }
                                ?: message.toolName.replace("_", " ").replaceFirstChar { it.uppercase() }
                            Text(
                                text = "Action: $displayBadge",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Strips any leftover raw tool_call markdown blocks or JSON from user-facing text.
 */
fun cleanUserFacingText(raw: String): String {
    return raw
        .replace(Regex("""```(?:tool_call|tool_calls|json)?\s*\{.*?\}\s*```""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""```(?:tool_calls|tools|json)?\s*\[.*?\]\s*```""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""\{\s*"tool"\s*:\s*.*\}""", RegexOption.DOT_MATCHES_ALL), "")
        .trim()
}


@Composable
private fun MarkdownMessage(text: String) {
    val lines = text.lines()
    var inCode = false
    val codeLines = mutableListOf<String>()
    val fence = 96.toChar().toString().repeat(3)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { raw ->
            val line = raw.trimEnd()
            if (line.trimStart().startsWith(fence)) {
                if (inCode) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111D2B)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(codeLines.joinToString("\n"), color = Color(0xFFD7E8E4), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(12.dp))
                    }
                    codeLines.clear()
                    inCode = false
                } else inCode = true
            } else if (inCode) {
                codeLines += line
            } else if (line.isBlank()) {
                Spacer(Modifier.height(2.dp))
            } else {
                val heading = Regex("""^#{1,3}\s+(.+)$""").find(line)
                val bullet = Regex("""^\s*[-*+]\s+(.+)$""").find(line)
                val numbered = Regex("""^\s*\d+[.)]\s+(.+)$""").find(line)
                when {
                    heading != null -> Text(heading.groupValues[1], color = SiaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
                    bullet != null -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = SiaCyan, fontSize = 16.sp)
                        InlineMarkdownText(bullet.groupValues[1])
                    }
                    numbered != null -> Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(numbered.groupValues[0].takeWhile { it.isDigit() || it == '.' || it == ')' }, color = SiaCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        InlineMarkdownText(numbered.groupValues[1])
                    }
                    line.trim().startsWith("|") && line.trim().endsWith("|") -> {
                        val row = line.trim().trim('|').split('|').joinToString("   ·   ") { it.trim() }
                        if (row.replace(":", "").replace("-", "").replace(" ", "").isNotEmpty()) Text(row, color = SiaTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    else -> InlineMarkdownText(line)
                }
            }
        }
        if (inCode && codeLines.isNotEmpty()) Text(codeLines.joinToString("\\n"), color = SiaTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun InlineMarkdownText(text: String) {
    val tick = 96.toChar()
    val pattern = Regex("""(\*\*.+?\*\*|${tick}[^${tick}]+${tick}|\*[^*]+\*|~~.+?~~)""")
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        pattern.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") -> androidx.compose.ui.text.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SiaTextPrimary)) { append(token.removeSurrounding("**")) }
                token.firstOrNull() == tick -> androidx.compose.ui.text.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = SiaSurfaceVariantDark, color = SiaAmber)) { append(token.drop(1).dropLast(1)) }
                token.startsWith("~~") -> androidx.compose.ui.text.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = SiaTextSecondary)) { append(token.removeSurrounding("~~")) }
                token.startsWith("*") -> androidx.compose.ui.text.withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(token.removeSurrounding("*")) }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    Text(annotated, color = SiaTextPrimary, fontSize = 14.sp, lineHeight = 21.sp)
}


@Composable
private fun ModeSegment(title: String, icon: @Composable () -> Unit, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(17.dp)).background(if (selected) SiaCyan.copy(alpha = 0.16f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides if (selected) SiaCyan else SiaTextMuted) { icon() }
        Spacer(Modifier.width(7.dp))
        Text(title, color = if (selected) SiaCyan else SiaTextSecondary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}
