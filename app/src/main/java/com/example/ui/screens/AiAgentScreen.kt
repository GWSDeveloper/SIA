package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
        // Mode Switcher: Animation vs Chat
        TabRow(
            selectedTabIndex = if (agentMode == AgentMode.ANIMATION) 0 else 1,
            containerColor = SiaSurfaceDark,
            contentColor = SiaCyan,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[if (agentMode == AgentMode.ANIMATION) 0 else 1]),
                    color = SiaCyan,
                    height = 3.dp
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = agentMode == AgentMode.ANIMATION,
                onClick = { viewModel.setAgentMode(AgentMode.ANIMATION) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Animation",
                            fontWeight = if (agentMode == AgentMode.ANIMATION) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                },
                modifier = Modifier.testTag("tab_animation_mode")
            )
            Tab(
                selected = agentMode == AgentMode.CHAT,
                onClick = { viewModel.setAgentMode(AgentMode.CHAT) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chat (${messages.size})",
                            fontWeight = if (agentMode == AgentMode.CHAT) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                },
                modifier = Modifier.testTag("tab_chat_mode")
            )
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
                            AssistantVisualState.LISTENING, AssistantVisualState.PREPARING, AssistantVisualState.SPEAKING -> viewModel.stopVoiceInteraction()
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
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
                    size = 230.dp,
                    audioLevel = audioLevel,
                    onClick = onOrbClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = assistantName,
                    color = SiaTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "By FAST",
                    color = SiaCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
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
                        .padding(bottom = 12.dp)
                        .border(1.dp, SiaBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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

        // Quick action chips (Single & Multi-action examples)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            val chips = listOf(
                "📱 Install on Phone" to "install on phone",
                "YouTube + Torch" to "Open YouTube and turn on the torch",
                "Torch + Volume 50%" to "Torch on karo aur volume 50 kar do",
                "Flashlight On" to "torch on",
                "Open YouTube" to "open youtube",
                "Remind in 5m" to "remind me in 5 minutes to take a break"
            )
            items(chips) { (label, command) ->
                SuggestionChip(
                    onClick = { onQuickCommand(command) },
                    label = { Text(label, color = SiaTextPrimary, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SiaSurfaceVariantDark
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = SiaBorder
                    )
                )
            }
        }

        // Mic Action Button with distinct state colors
        val micBgColor = when (visualState) {
            AssistantVisualState.LISTENING -> SiaGreen
            AssistantVisualState.PREPARING -> SiaAmber
            else -> SiaCyan
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(micBgColor)
                .clickable { onOrbClick() }
                .testTag("orb_voice_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (visualState == AssistantVisualState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Voice Input",
                tint = SiaBgDark,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        val hintText = when (visualState) {
            AssistantVisualState.PREPARING -> "Preparing audio..."
            AssistantVisualState.LISTENING -> "Listening now! Speak your command..."
            AssistantVisualState.THINKING -> "Thinking..."
            AssistantVisualState.EXECUTING -> "Executing actions..."
            AssistantVisualState.SPEAKING -> "Speaking response..."
            else -> "Tap to speak in Hindi, Hinglish, or English"
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
                        text = "Continuous Conversation Thread",
                        color = SiaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Supports compound commands and Hindi / Hinglish voice.",
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
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(message = msg, onSpeak = { onSpeakMessage(msg.text) })
                }
            }
        }

        // Bottom input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SiaSurfaceDark)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val micColor = when (visualState) {
                AssistantVisualState.LISTENING -> SiaGreen
                AssistantVisualState.PREPARING -> SiaAmber
                else -> SiaSurfaceVariantDark
            }
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(micColor)
                    .testTag("chat_mic_button")
            ) {
                Icon(
                    imageVector = if (visualState == AssistantVisualState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (visualState == AssistantVisualState.LISTENING) SiaBgDark else SiaCyan
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                placeholder = { Text("Ask SIA or command multiple actions...", color = SiaTextMuted, fontSize = 14.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SiaCyan,
                    unfocusedBorderColor = SiaBorder,
                    focusedTextColor = SiaTextPrimary,
                    unfocusedTextColor = SiaTextPrimary,
                    focusedContainerColor = SiaCardBg,
                    unfocusedContainerColor = SiaCardBg
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSendMessage,
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) SiaCyan else SiaSurfaceVariantDark)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank()) SiaBgDark else SiaTextMuted
                )
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
                containerColor = if (isUser) SiaViolet.copy(alpha = 0.25f) else SiaCardBg
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
                    color = if (isUser) SiaViolet.copy(alpha = 0.5f) else SiaBorder,
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

                Text(
                    text = cleanText,
                    color = SiaTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

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
