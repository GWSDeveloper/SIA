package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.permissions.SiaPermissionManager
import com.example.service.SiaFloatingService
import com.example.ui.MainScreenTab
import com.example.ui.SiaViewModel
import com.example.ui.screens.AiAgentScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SiaBgDark
import com.example.ui.theme.SiaBorder
import com.example.ui.theme.SiaCyan
import com.example.ui.theme.SiaSurfaceDark
import com.example.ui.theme.SiaTextMuted
import com.example.ui.theme.SiaTextPrimary

class MainActivity : ComponentActivity() {

    private val viewModel: SiaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleLaunchIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppScaffold(
                    viewModel = viewModel,
                    onStartVoiceRequest = {
                        viewModel.startVoiceInteraction()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action == "com.example.ACTION_START_SIA_VOICE") {
            viewModel.setTab(MainScreenTab.AI_AGENT)
            if (SiaPermissionManager.hasRecordAudioPermission(this)) {
                viewModel.startVoiceInteraction()
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    viewModel: SiaViewModel,
    onStartVoiceRequest: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsState()
    var nameDraft by remember { mutableStateOf(viewModel.preferences.getUserName()) }
    var showNameSetup by remember { mutableStateOf(nameDraft.isBlank()) }
    if (showNameSetup) {
        AlertDialog(onDismissRequest = {}, title = { Text("Hey! Main SIA hoon 💗") }, text = { OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it }, label = { Text("Tumhara naam kya hai?") }, singleLine = true) }, confirmButton = { Button(onClick = { if (nameDraft.isNotBlank()) { viewModel.preferences.setUserName(nameDraft); showNameSetup = false } }, enabled = nameDraft.isNotBlank()) { Text("Chalo, start karein") } })
    }

    // Request runtime permissions on launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (micGranted && notifGranted) {
            // All good
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // BackHandler: Return to AI_AGENT from sub-screens
    BackHandler(enabled = currentTab != MainScreenTab.AI_AGENT) {
        viewModel.setTab(MainScreenTab.AI_AGENT)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color.Transparent).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(SiaSurfaceDark.copy(alpha = 0.98f))
                        .border(1.dp, SiaBorder, RoundedCornerShape(30.dp))
                        .padding(7.dp)
                        .testTag("main_bottom_nav_bar"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingNavItem("Workspace", { Icon(Icons.Default.Folder, contentDescription = "Workspace", modifier = Modifier.size(21.dp)) }, currentTab == MainScreenTab.WORKSPACE, { viewModel.setTab(MainScreenTab.WORKSPACE) }, Modifier.weight(1f).testTag("nav_tab_workspace"))
                    FloatingNavItem("SIA", { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Agent", modifier = Modifier.size(22.dp)) }, currentTab == MainScreenTab.AI_AGENT, { viewModel.setTab(MainScreenTab.AI_AGENT) }, Modifier.weight(1f).testTag("nav_tab_ai_agent"))
                    FloatingNavItem("Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(21.dp)) }, currentTab == MainScreenTab.SETTINGS, { viewModel.setTab(MainScreenTab.SETTINGS) }, Modifier.weight(1f).testTag("nav_tab_settings"))
                }
            }
        },

        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SiaBgDark)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainScreenTab.WORKSPACE -> WorkspaceScreen()
                MainScreenTab.AI_AGENT -> AiAgentScreen(viewModel = viewModel)
                MainScreenTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}


@Composable
private fun FloatingNavItem(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = if (selected) SiaTextPrimary else SiaTextMuted
    Column(
        modifier = modifier.clip(RoundedCornerShape(24.dp))
            .background(if (selected) SiaCyan.copy(alpha = 0.17f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground) { icon() }
        Text(label, color = if (selected) SiaCyan else SiaTextMuted, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}
