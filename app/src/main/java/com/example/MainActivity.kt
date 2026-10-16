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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
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
            NavigationBar(
                containerColor = SiaSurfaceDark,
                contentColor = SiaTextPrimary,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_bottom_nav_bar")
            ) {
                // Workspace
                NavigationBarItem(
                    selected = currentTab == MainScreenTab.WORKSPACE,
                    onClick = { viewModel.setTab(MainScreenTab.WORKSPACE) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Workspace",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Workspace",
                            fontSize = 12.sp,
                            fontWeight = if (currentTab == MainScreenTab.WORKSPACE) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SiaCyan,
                        selectedTextColor = SiaCyan,
                        unselectedIconColor = SiaTextMuted,
                        unselectedTextColor = SiaTextMuted,
                        indicatorColor = SiaCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_workspace")
                )

                // AI Agent (Default Center Destination)
                NavigationBarItem(
                    selected = currentTab == MainScreenTab.AI_AGENT,
                    onClick = { viewModel.setTab(MainScreenTab.AI_AGENT) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Agent",
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "AI Agent",
                            fontSize = 12.sp,
                            fontWeight = if (currentTab == MainScreenTab.AI_AGENT) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SiaCyan,
                        selectedTextColor = SiaCyan,
                        unselectedIconColor = SiaTextMuted,
                        unselectedTextColor = SiaTextMuted,
                        indicatorColor = SiaCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_ai_agent")
                )

                // Settings
                NavigationBarItem(
                    selected = currentTab == MainScreenTab.SETTINGS,
                    onClick = { viewModel.setTab(MainScreenTab.SETTINGS) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Settings",
                            fontSize = 12.sp,
                            fontWeight = if (currentTab == MainScreenTab.SETTINGS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SiaCyan,
                        selectedTextColor = SiaCyan,
                        unselectedIconColor = SiaTextMuted,
                        unselectedTextColor = SiaTextMuted,
                        indicatorColor = SiaCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
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
