package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SiaDatabase
import com.example.permissions.SiaPermissionManager
import com.example.preferences.LlmProviderType
import com.example.preferences.VoiceGender
import com.example.service.SiaFloatingService
import com.example.ui.SiaViewModel
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SiaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = viewModel.preferences
    val coroutineScope = rememberCoroutineScope()

    var userName by remember { mutableStateOf(prefs.getUserName()) }
    var assistantName by remember { mutableStateOf(prefs.getAssistantName()) }
    var selectedProvider by remember { mutableStateOf(prefs.getSelectedProvider()) }
    var modelName by remember { mutableStateOf(prefs.getModelName(selectedProvider)) }
    var apiKeyText by remember { mutableStateOf(prefs.getApiKey(selectedProvider)) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var autoSpeak by remember { mutableStateOf(prefs.isAutoSpeakEnabled()) }
    var languagePref by remember { mutableStateOf(prefs.getLanguagePreference()) }
    var persistentNotif by remember { mutableStateOf(prefs.isPersistentNotificationEnabled()) }

    var selectedVoiceName by remember { mutableStateOf(prefs.getSelectedVoiceName()) }
    var speechPitch by remember { mutableFloatStateOf(prefs.getSpeechPitch()) }
    var speechRate by remember { mutableFloatStateOf(prefs.getSpeechRate()) }

    var isSarvamEnabled by remember { mutableStateOf(prefs.isSarvamTtsEnabled()) }
    var gnaniApiKeyInput by remember { mutableStateOf("") }
    var hasGnaniKeyConfigured by remember { mutableStateOf(prefs.getGnaniApiKey().isNotBlank()) }
    var sarvamApiKeyInput by remember { mutableStateOf("") }
    var hasSarvamKeyConfigured by remember { mutableStateOf(prefs.getSarvamApiKey().isNotBlank()) }
    var tavilyApiKeyInput by remember { mutableStateOf("") }
    var hasTavilyKeyConfigured by remember { mutableStateOf(prefs.getTavilyApiKey().isNotBlank()) }
    var isTavilyKeyVisible by remember { mutableStateOf(false) }
    val isUsingSarvam by viewModel.speechManager.isUsingSarvam.collectAsState()
    val sarvamStatus by viewModel.speechManager.sarvamStatusMessage.collectAsState()

    var wakeWordEnabled by remember { mutableStateOf(prefs.isWakeWordEnabled()) }
    var wakeWordPhrase by remember { mutableStateOf(prefs.getWakeWordPhrase()) }
    var stopPhrase by remember { mutableStateOf(prefs.getStopPhrase()) }

    var isProviderDropdownExpanded by remember { mutableStateOf(false) }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }
    var isVoiceDropdownExpanded by remember { mutableStateOf(false) }

    val availableVoices by viewModel.speechManager.availableVoices.collectAsState()
    val hasHindiVoice by viewModel.speechManager.hasHindiVoice.collectAsState()
    val ttsDiagnostic by viewModel.speechManager.ttsDiagnosticMessage.collectAsState()

    // Memory management state
    val permanentMemories by viewModel.memoryManager.permanentMemoriesFlow.collectAsState(initial = emptyList())
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<com.example.data.MemoryEntity?>(null) }
    var editedMemoryContent by remember { mutableStateOf("") }
    var showMemoryEditConfirmation by remember { mutableStateOf(false) }

    // Scheduled Tasks state
    val db = remember { SiaDatabase.getDatabase(context) }
    val scheduledTasks by db.siaDao().getAllScheduledTasksFlow().collectAsState(initial = emptyList())

    // Permissions list
    val permissionsList = remember { SiaPermissionManager.getAllPermissionsStatus(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SiaBgDark)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header
        item {
            Column {
                Text(
                    text = "Settings & Preferences",
                    color = SiaTextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Configure high-quality Hindi/Hinglish voice, wake word, providers, and actions.",
                    color = SiaTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        item {
            SettingsCard(title = "Your Name", icon = Icons.Default.Psychology, accentColor = SiaPink) {
                OutlinedTextField(value = userName, onValueChange = { userName = it; prefs.setUserName(it) }, label = { Text("What should SIA call you?", color = SiaTextSecondary) }, modifier = Modifier.fillMaxWidth().testTag("settings_user_name_input"), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SiaCyan, unfocusedBorderColor = SiaBorder, focusedTextColor = SiaTextPrimary, unfocusedTextColor = SiaTextPrimary))
                Text("SIA will use your name naturally in conversations.", color = SiaTextMuted, fontSize = 11.sp)
            }
        }

        // Section: Assistant Identity
        item {
            SettingsCard(title = "Assistant Identity", icon = Icons.Default.Psychology, accentColor = SiaCyan) {
                OutlinedTextField(
                    value = assistantName,
                    onValueChange = {
                        assistantName = it
                        prefs.setAssistantName(it)
                    },
                    label = { Text("Assistant Display Name", color = SiaTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiaCyan,
                        unfocusedBorderColor = SiaBorder,
                        focusedTextColor = SiaTextPrimary,
                        unfocusedTextColor = SiaTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_assistant_name_input")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Default is SIA. Changing name personalizes voice interactions.",
                    color = SiaTextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Section 1: HIGH-QUALITY HINDI & HINGLISH VOICE
        item {
            SettingsCard(title = "Voice & Speech Engine", icon = Icons.Default.RecordVoiceOver, accentColor = SiaGreen) {

                Text("Gnani.ai Speech Recognition", color = SiaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Use Gnani Vachana STT for Hindi/Hinglish commands. When configured, SIA uses this instead of Android speech recognition.",
                    color = SiaTextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = gnaniApiKeyInput,
                    onValueChange = {
                        gnaniApiKeyInput = it
                        if (it.isNotBlank()) {
                            prefs.setGnaniApiKey(it)
                            hasGnaniKeyConfigured = true
                        }
                    },
                    label = { Text("Gnani API Key ID", color = SiaTextSecondary) },
                    placeholder = { Text(if (hasGnaniKeyConfigured) "Key configured · enter to replace" else "Paste your Gnani API key ID", color = SiaTextMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "Gnani API key",
                            tint = if (hasGnaniKeyConfigured) SiaGreen else SiaTextSecondary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiaCyan, unfocusedBorderColor = SiaBorder,
                        focusedTextColor = SiaTextPrimary, unfocusedTextColor = SiaTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_gnani_stt_api_key")
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasGnaniKeyConfigured) "Gnani STT configured" else "Not configured · Android STT remains active",
                        color = if (hasGnaniKeyConfigured) SiaGreen else SiaTextMuted, fontSize = 11.sp)
                    if (hasGnaniKeyConfigured) {
                        TextButton(onClick = {
                            prefs.setGnaniApiKey("")
                            gnaniApiKeyInput = ""
                            hasGnaniKeyConfigured = false
                        }) { Text("Clear", color = SiaRed, fontSize = 11.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                // Sarvam AI Neural Streaming Voice Header & Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sarvam AI Streaming Voice", color = SiaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SiaCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Bulbul v4-Flash", color = SiaCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("Ultra-expressive streaming Hindi & Hinglish companion voice.", color = SiaTextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isSarvamEnabled,
                        onCheckedChange = {
                            isSarvamEnabled = it
                            prefs.setSarvamTtsEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SiaBgDark, checkedTrackColor = SiaCyan),
                        modifier = Modifier.testTag("switch_sarvam_tts")
                    )
                }

                if (isSarvamEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sarvamApiKeyInput,
                        onValueChange = {
                            sarvamApiKeyInput = it
                            if (it.isNotBlank()) {
                                prefs.setSarvamApiKey(it)
                                hasSarvamKeyConfigured = true
                            }
                        },
                        label = { Text("Sarvam API Subscription Key", color = SiaTextSecondary) },
                        placeholder = {
                            Text(
                                if (hasSarvamKeyConfigured) "●●●●●●●● Key configured (enter to replace)" else "Paste your Sarvam subscription key",
                                color = SiaTextMuted
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Protected subscription key",
                                tint = if (hasSarvamKeyConfigured) SiaGreen else SiaTextSecondary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaCyan,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sarvam_api_key")
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (hasSarvamKeyConfigured) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SiaGreen.copy(alpha = 0.12f))
                                .border(1.dp, SiaGreen.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SiaGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sarvam AI Active (speaker: suman_hi_companion • 24kHz stream)",
                                    color = SiaGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(
                                onClick = {
                                    prefs.setSarvamApiKey("")
                                    sarvamApiKeyInput = ""
                                    hasSarvamKeyConfigured = prefs.getSarvamApiKey().isNotBlank()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", color = SiaRed, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SiaAmber.copy(alpha = 0.12f))
                                .border(1.dp, SiaAmber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = SiaAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Key not set. Using native Android TTS as fallback.",
                                color = SiaAmber,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Fallback Engine: Android Native TTS", color = SiaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                // Diagnostic status badge / card
                if (hasHindiVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SiaGreen.copy(alpha = 0.12f))
                            .border(1.dp, SiaGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SiaGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Genuine Hindi TTS Voice Active (100% Free on-device synthesis)",
                            color = SiaGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SiaAmber.copy(alpha = 0.12f))
                            .border(1.dp, SiaAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = SiaAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hindi Voice Pack Not Installed",
                                color = SiaAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android Text-to-Speech requires the free Hindi voice data pack for authentic Hindi & Hinglish pronunciation. Fallback Indian English is active.",
                            color = SiaTextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.speechManager.openTtsSystemSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SiaAmber, contentColor = SiaBgDark),
                            modifier = Modifier.fillMaxWidth().testTag("btn_install_tts_voice")
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Android TTS Settings to Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real available voices dropdown
                Text("Installed TTS Voices (${availableVoices.size} available)", color = SiaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                if (availableVoices.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = isVoiceDropdownExpanded,
                        onExpandedChange = { isVoiceDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val currentVoiceName = availableVoices.firstOrNull { it.name == selectedVoiceName }?.displayName
                            ?: availableVoices.firstOrNull()?.displayName
                            ?: "Default Voice"

                        OutlinedTextField(
                            value = currentVoiceName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Selected Device Voice", color = SiaTextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isVoiceDropdownExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SiaGreen,
                                unfocusedBorderColor = SiaBorder,
                                focusedTextColor = SiaTextPrimary,
                                unfocusedTextColor = SiaTextPrimary
                            ),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isVoiceDropdownExpanded,
                            onDismissRequest = { isVoiceDropdownExpanded = false }
                        ) {
                            availableVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.displayName, color = SiaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            Text("Identifier: ${voice.name}", color = SiaTextMuted, fontSize = 10.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedVoiceName = voice.name
                                        prefs.setSelectedVoiceName(voice.name)
                                        viewModel.speechManager.applyVoiceSettings()
                                        isVoiceDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Using system default Text-to-Speech voice.",
                        color = SiaTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test Voice Preview Button
                Button(
                    onClick = {
                        val testPhrase = "नमस्ते! मैं सिया हूँ, आपकी पर्सनल एआई असिस्टेंट। मैं आपकी क्या मदद कर सकती हूँ?"
                        viewModel.speechManager.testVoice(testPhrase)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaGreen, contentColor = SiaBgDark),
                    modifier = Modifier.fillMaxWidth().testTag("btn_test_voice")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Selected Voice", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auto speak toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Speak Voice Confirmations", color = SiaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Speaks concise Hindi/Hinglish action confirmations.", color = SiaTextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoSpeak,
                        onCheckedChange = {
                            autoSpeak = it
                            prefs.setAutoSpeakEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SiaBgDark, checkedTrackColor = SiaGreen),
                        modifier = Modifier.testTag("switch_auto_speak")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Language selection
                ExposedDropdownMenuBox(
                    expanded = isLanguageDropdownExpanded,
                    onExpandedChange = { isLanguageDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = languagePref,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language Preference", color = SiaTextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLanguageDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaGreen,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isLanguageDropdownExpanded,
                        onDismissRequest = { isLanguageDropdownExpanded = false }
                    ) {
                        listOf(
                            "Hinglish (Hindi + English)",
                            "Auto (Hindi / Hinglish / English)",
                            "Hindi (शुद्ध हिंदी)",
                            "English"
                        ).forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang, color = SiaTextPrimary) },
                                onClick = {
                                    languagePref = lang
                                    prefs.setLanguagePreference(lang)
                                    isLanguageDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pitch slider
                Text(
                    text = "Speech Pitch: ${String.format(Locale.ROOT, "%.2f", speechPitch)}",
                    color = SiaTextPrimary,
                    fontSize = 13.sp
                )
                Slider(
                    value = speechPitch,
                    onValueChange = {
                        speechPitch = it
                        prefs.setSpeechPitch(it)
                        viewModel.speechManager.applyVoiceSettings()
                    },
                    valueRange = 0.75f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = SiaGreen, activeTrackColor = SiaGreen)
                )

                // Rate slider
                Text(
                    text = "Speech Rate: ${String.format(Locale.ROOT, "%.2f", speechRate)}x",
                    color = SiaTextPrimary,
                    fontSize = 13.sp
                )
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                        prefs.setSpeechRate(it)
                        viewModel.speechManager.applyVoiceSettings()
                    },
                    valueRange = 0.7f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = SiaGreen, activeTrackColor = SiaGreen)
                )
            }
        }

        // Section 8: HANDS-FREE WAKE WORD ("Hey SIA")
        item {
            SettingsCard(title = "Hands-Free Wake Word", icon = Icons.Default.Hearing, accentColor = SiaAmber) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable 'Hey SIA' Wake Word", color = SiaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Allows continuous voice interaction during active sessions.", color = SiaTextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = wakeWordEnabled,
                        onCheckedChange = {
                            wakeWordEnabled = it
                            prefs.setWakeWordEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SiaBgDark, checkedTrackColor = SiaAmber),
                        modifier = Modifier.testTag("switch_wake_word")
                    )
                }

                if (wakeWordEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = wakeWordPhrase,
                        onValueChange = {
                            wakeWordPhrase = it
                            prefs.setWakeWordPhrase(it)
                        },
                        label = { Text("Wake Phrase", color = SiaTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaAmber,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = stopPhrase,
                        onValueChange = {
                            stopPhrase = it
                            prefs.setStopPhrase(it)
                        },
                        label = { Text("Stop Phrase", color = SiaTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaAmber,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🔒 Privacy & Battery: Wake word is session-based and active only while SIA's assistant screen or floating orb is visible. No audio is recorded in background when session is closed.",
                        color = SiaTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Section: AI Provider & Model Configuration
        item {
            SettingsCard(title = "LLM Provider & Credentials", icon = Icons.Default.Key, accentColor = SiaViolet) {
                ExposedDropdownMenuBox(
                    expanded = isProviderDropdownExpanded,
                    onExpandedChange = { isProviderDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Active LLM Provider", color = SiaTextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProviderDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaViolet,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("settings_provider_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = isProviderDropdownExpanded,
                        onDismissRequest = { isProviderDropdownExpanded = false }
                    ) {
                        LlmProviderType.values().forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName, color = SiaTextPrimary) },
                                onClick = {
                                    selectedProvider = provider
                                    prefs.setSelectedProvider(provider)
                                    modelName = prefs.getModelName(provider)
                                    apiKeyText = prefs.getApiKey(provider)
                                    isProviderDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = {
                        modelName = it
                        prefs.setModelName(it, selectedProvider)
                    },
                    label = { Text("Model Identifier", color = SiaTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiaViolet,
                        unfocusedBorderColor = SiaBorder,
                        focusedTextColor = SiaTextPrimary,
                        unfocusedTextColor = SiaTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedProvider != LlmProviderType.LOCAL_OFFLINE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            prefs.setApiKey(selectedProvider, it)
                        },
                        label = { Text("${selectedProvider.displayName} API Key", color = SiaTextSecondary) },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle visibility",
                                    tint = SiaTextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaViolet,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_api_key_input")
                    )
                }
            }
        }

        // Web search provider configuration
        item {
            SettingsCard(title = "Web Search · Tavily", icon = Icons.Default.Key, accentColor = SiaCyan) {
                Text(
                    "SIA uses Tavily to search the web and retrieve answer context. Get a free key at app.tavily.com.",
                    color = SiaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tavilyApiKeyInput,
                    onValueChange = {
                        tavilyApiKeyInput = it
                        prefs.setTavilyApiKey(it)
                        hasTavilyKeyConfigured = it.isNotBlank()
                    },
                    label = { Text("Tavily API Key", color = SiaTextSecondary) },
                    placeholder = {
                        Text(
                            if (hasTavilyKeyConfigured) "Key configured (paste to replace)" else "Paste your Tavily API key",
                            color = SiaTextMuted
                        )
                    },
                    visualTransformation = if (isTavilyKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isTavilyKeyVisible = !isTavilyKeyVisible }) {
                            Icon(
                                imageVector = if (isTavilyKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Tavily key visibility",
                                tint = if (hasTavilyKeyConfigured) SiaGreen else SiaTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SiaCyan,
                        unfocusedBorderColor = SiaBorder,
                        focusedTextColor = SiaTextPrimary,
                        unfocusedTextColor = SiaTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("settings_tavily_api_key_input")
                )
            }
        }

        // Section 7: Notification & Floating Assistant Overlay
        item {
            SettingsCard(title = "Notification & Floating Orb", icon = Icons.Default.Notifications, accentColor = SiaPink) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Persistent 'Start SIA' Notification", color = SiaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Displays 'Start SIA' button in notification bar to launch the floating assistant overlay.", color = SiaTextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = persistentNotif,
                        onCheckedChange = {
                            persistentNotif = it
                            prefs.setPersistentNotificationEnabled(it)
                            if (it) {
                                SiaFloatingService.startService(context, showOverlay = false)
                            } else {
                                SiaFloatingService.stopService(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SiaBgDark, checkedTrackColor = SiaPink),
                        modifier = Modifier.testTag("switch_persistent_notification")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { SiaPermissionManager.openOverlaySettings(context) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating Overlay Permission", color = SiaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Required for the full-screen dimmed floating orb over any other app.", color = SiaTextSecondary, fontSize = 12.sp)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Settings", tint = SiaPink)
                }
            }
        }

        // Section: Permanent Memory
        item {
            SettingsCard(title = "Permanent Memory (${permanentMemories.size})", icon = Icons.Default.Memory, accentColor = SiaCyan) {
                Text(
                    text = "Permanent knowledge about your habits and preferences retained across sessions.",
                    color = SiaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                permanentMemories.forEach { mem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mem.key, color = SiaCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(mem.content, color = SiaTextPrimary, fontSize = 12.sp)
                        }
                        Row {
                            IconButton(onClick = {
                                editingMemory = mem
                                editedMemoryContent = mem.content
                            }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Memory", tint = SiaCyan, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                coroutineScope.launch { viewModel.memoryManager.deletePermanentMemory(mem) }
                            }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Memory", tint = SiaTextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showAddMemoryDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SiaCyan, contentColor = SiaBgDark),
                        modifier = Modifier.testTag("btn_add_memory")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Memory")
                    }

                    if (permanentMemories.isNotEmpty()) {
                        TextButton(onClick = { coroutineScope.launch { viewModel.memoryManager.clearPermanentMemory() } }) {
                            Text("Clear All", color = SiaRed)
                        }
                    }
                }
            }
        }

        // Section: Scheduled Tasks & Reminders
        item {
            SettingsCard(title = "Scheduled Tasks & Reminders (${scheduledTasks.size})", icon = Icons.Default.Schedule, accentColor = SiaViolet) {
                if (scheduledTasks.isEmpty()) {
                    Text("No scheduled tasks found.", color = SiaTextSecondary, fontSize = 13.sp)
                } else {
                    scheduledTasks.forEach { task ->
                        val dateFormatted = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(task.scheduledTimeMs))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.description, color = SiaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("$dateFormatted • Status: ${task.status} (Attempts: ${task.attemptCount}/3)", color = SiaTextMuted, fontSize = 11.sp)
                                if (!task.lastResult.isNullOrBlank()) {
                                    Text("Outcome: ${task.lastResult}", color = SiaTextSecondary, fontSize = 11.sp)
                                }
                            }
                            if (task.status == "PENDING") {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            db.siaDao().updateTaskStatus(task.id, "CANCELLED")
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Cancel", tint = SiaRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Android Permissions Status Dashboard
        item {
            SettingsCard(title = "Android Permissions Status", icon = Icons.Default.Security, accentColor = SiaGreen) {
                permissionsList.forEach { perm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (perm.isSpecialAccess) {
                                    if (perm.name.contains("Overlay")) {
                                        SiaPermissionManager.openOverlaySettings(context)
                                    } else {
                                        SiaPermissionManager.openExactAlarmSettings(context)
                                    }
                                } else {
                                    SiaPermissionManager.openAppSettings(context)
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (perm.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (perm.isGranted) SiaGreen else SiaRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(perm.name, color = SiaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(perm.description, color = SiaTextMuted, fontSize = 11.sp)
                            }
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SiaTextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Section: Install on Phone / Export APK
        item {
            SettingsCard(
                title = "Install on Phone / Export APK",
                icon = Icons.Default.PhoneAndroid,
                accentColor = SiaCyan
            ) {
                Column {
                    Text(
                        text = "To run SIA v2 directly on your personal Android phone:",
                        color = SiaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Tap 'Direct Install' to invoke Android Package Installer directly on this device.\n• Tap 'Share / Save APK' to send the .apk file via WhatsApp, Google Drive, Gmail, or save to Downloads.\n• If Android shows 'Blocked by Play Protect' or 'Install unknown apps', tap 'More details' -> 'Install anyway' or allow from Settings.",
                        color = SiaTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.directInstallApk() },
                            colors = ButtonDefaults.buttonColors(containerColor = SiaCyan, contentColor = SiaBgDark),
                            modifier = Modifier.weight(1f).testTag("btn_direct_install_apk")
                        ) {
                            Text("Direct Install", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { viewModel.exportApk() },
                            colors = ButtonDefaults.buttonColors(containerColor = SiaSurfaceVariantDark, contentColor = SiaCyan),
                            modifier = Modifier.weight(1f).testTag("btn_export_share_apk")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share APK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Clear Conversation Button
        item {
            Button(
                onClick = { viewModel.clearConversation() },
                colors = ButtonDefaults.buttonColors(containerColor = SiaSurfaceVariantDark, contentColor = SiaRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_clear_conversation")
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear Conversation History")
            }
        }

        // Section: About SIA by FAST
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SiaSurfaceDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SiaBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SIA v2",
                        color = SiaCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Personal Android AI Assistant • By FAST",
                        color = SiaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "High-quality Hindi/Hinglish voice, fast speech recognition, multi-action sequential execution, floating assistant overlay, and scheduled action workflows.",
                        color = SiaTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    // Editing permanent memory requires an explicit review/confirmation step.
    editingMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { editingMemory = null; showMemoryEditConfirmation = false },
            title = { Text("Edit Permanent Memory", color = SiaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(memory.key, color = SiaCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedMemoryContent,
                        onValueChange = { editedMemoryContent = it },
                        label = { Text("Memory profile text", color = SiaTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaCyan,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        )
                    )
                    if (showMemoryEditConfirmation) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Save these changes to SIA's permanent memory?", color = SiaTextPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                if (!showMemoryEditConfirmation) {
                    Button(onClick = { showMemoryEditConfirmation = true }) { Text("Review changes") }
                } else {
                    Button(onClick = {
                        coroutineScope.launch {
                            viewModel.memoryManager.updatePermanentMemory(memory.id, memory.key, editedMemoryContent)
                        }
                        editingMemory = null
                        showMemoryEditConfirmation = false
                    }) { Text("Confirm & Save") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (showMemoryEditConfirmation) showMemoryEditConfirmation = false else editingMemory = null
                }) { Text(if (showMemoryEditConfirmation) "Back" else "Cancel", color = SiaTextSecondary) }
            },
            containerColor = SiaCardBg
        )
    }

    // Add Memory Dialog
    if (showAddMemoryDialog) {
        var memKey by remember { mutableStateOf("") }
        var memContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Add Permanent Memory", color = SiaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = memKey,
                        onValueChange = { memKey = it },
                        label = { Text("Topic / Key (e.g. Favorite Genre)", color = SiaTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaCyan,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memContent,
                        onValueChange = { memContent = it },
                        label = { Text("Statement", color = SiaTextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SiaCyan,
                            unfocusedBorderColor = SiaBorder,
                            focusedTextColor = SiaTextPrimary,
                            unfocusedTextColor = SiaTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (memKey.isNotBlank() && memContent.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.memoryManager.addPermanentMemory(memKey, memContent)
                            }
                        }
                        showAddMemoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SiaCyan, contentColor = SiaBgDark)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) {
                    Text("Cancel", color = SiaTextSecondary)
                }
            },
            containerColor = SiaCardBg
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SiaCardBg),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SiaBorder.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = title, color = SiaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(9.dp))
            content()
        }
    }
}
