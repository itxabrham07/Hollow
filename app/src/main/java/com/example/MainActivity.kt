package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.AuditLogsScreen
import com.example.isolation.ui.screens.DashboardScreen
import com.example.isolation.ui.screens.MountTerminalScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.screens.VaultScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class NavDestination(val label: String) {
    DASHBOARD("Overview"),
    APPS("Apps"),
    VAULT("Vault"),
    MOUNTS("Mounts"),
    AUDIT("Audit"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        fun isLsposedModuleActiveReal(): Boolean {
            return false // Hooked by StorageIsolationHook when LSPosed is active
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IsoVaultTheme(darkTheme = true) {
                MainAppContainer()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: IsoVaultViewModel = viewModel()) {
    var currentScreen by remember { mutableStateOf(NavDestination.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    // Hardware back button navigation
    BackHandler(enabled = currentScreen != NavDestination.DASHBOARD) {
        currentScreen = NavDestination.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepObsidian,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "IsoVault",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusBadge(
                                    text = "v2.5",
                                    color = CyberCyan
                                )
                            }
                            Text(
                                text = "LSPosed Storage Isolation • Android 15",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                actions = {
                    StatusBadge(
                        text = if (isLsposedActive) "ACTIVE" else "STANDBY",
                        color = if (isLsposedActive) CyberEmerald else Color(0xFFFFB300),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepObsidian,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceElevated,
                contentColor = TextPrimary,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentScreen == NavDestination.DASHBOARD,
                    onClick = { currentScreen = NavDestination.DASHBOARD },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text(NavDestination.DASHBOARD.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_dashboard")
                )
                NavigationBarItem(
                    selected = currentScreen == NavDestination.APPS,
                    onClick = { currentScreen = NavDestination.APPS },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Apps") },
                    label = { Text(NavDestination.APPS.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_apps")
                )
                NavigationBarItem(
                    selected = currentScreen == NavDestination.VAULT,
                    onClick = { currentScreen = NavDestination.VAULT },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                    label = { Text(NavDestination.VAULT.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_vault")
                )
                NavigationBarItem(
                    selected = currentScreen == NavDestination.MOUNTS,
                    onClick = { currentScreen = NavDestination.MOUNTS },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "Mounts") },
                    label = { Text(NavDestination.MOUNTS.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_mounts")
                )
                NavigationBarItem(
                    selected = currentScreen == NavDestination.AUDIT,
                    onClick = { currentScreen = NavDestination.AUDIT },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Audit") },
                    label = { Text(NavDestination.AUDIT.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_audit")
                )
                NavigationBarItem(
                    selected = currentScreen == NavDestination.SETTINGS,
                    onClick = { currentScreen = NavDestination.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(NavDestination.SETTINGS.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        indicatorColor = CyberCyan.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                NavDestination.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToApps = { currentScreen = NavDestination.APPS },
                    onNavigateToVault = { currentScreen = NavDestination.VAULT },
                    onNavigateToTerminal = { currentScreen = NavDestination.MOUNTS },
                    onNavigateToAudit = { currentScreen = NavDestination.AUDIT }
                )
                NavDestination.APPS -> AppsScreen(viewModel = viewModel)
                NavDestination.VAULT -> VaultScreen(viewModel = viewModel)
                NavDestination.MOUNTS -> MountTerminalScreen(viewModel = viewModel)
                NavDestination.AUDIT -> AuditLogsScreen(viewModel = viewModel)
                NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
