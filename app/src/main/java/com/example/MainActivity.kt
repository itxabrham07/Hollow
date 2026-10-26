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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.AuditLogsScreen
import com.example.isolation.ui.screens.DashboardScreen
import com.example.isolation.ui.screens.FilesScreen
import com.example.isolation.ui.screens.MountTerminalScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily

enum class NavDestination(val label: String) {
    DASHBOARD("Home"),
    VAULT("Files"),
    APPS("Apps"),
    AUDIT("Activity"),
    SETTINGS("Settings"),
    MOUNTS("System")
}

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        fun isLsposedModuleActiveReal(): Boolean {
            return false // Hooked by StorageIsolationHook on Companion and outer MainActivity
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: IsoVaultViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            IsoVaultTheme(
                darkTheme = settings.darkTheme,
                dynamicColor = settings.dynamicColor
            ) {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: IsoVaultViewModel = viewModel()) {
    val scheme = MaterialTheme.colorScheme
    var currentScreen by remember { mutableStateOf(NavDestination.DASHBOARD) }
    var filesInitialTab by remember { mutableIntStateOf(0) } // 0 = Browse (Google Files), 1 = Photos, 2 = Safe Folder
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(enabled = currentScreen != NavDestination.DASHBOARD) {
        currentScreen = NavDestination.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(scheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hollow",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = scheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setDarkTheme(!settings.darkTheme) }
                    ) {
                        Icon(
                            imageVector = if (settings.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Theme",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                    StatusBadge(
                        text = if (isLsposedActive) "Protected" else "Ready",
                        color = if (isLsposedActive) scheme.primary else scheme.secondary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onBackground
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = scheme.outlineVariant, thickness = 1.dp)
                NavigationBar(
                    containerColor = scheme.surface,
                    contentColor = scheme.onSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.onPrimaryContainer,
                        selectedTextColor = scheme.primary,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                        indicatorColor = scheme.primaryContainer
                    )

                    val isHomeSection = currentScreen == NavDestination.DASHBOARD ||
                            currentScreen == NavDestination.APPS ||
                            currentScreen == NavDestination.AUDIT

                    NavigationBarItem(
                        selected = isHomeSection,
                        onClick = { currentScreen = NavDestination.DASHBOARD },
                        icon = {
                            Icon(
                                imageVector = if (isHomeSection) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = {
                            Text(
                                text = NavDestination.DASHBOARD.label,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (isHomeSection) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_dashboard")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavDestination.VAULT,
                        onClick = {
                            filesInitialTab = 0
                            currentScreen = NavDestination.VAULT
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == NavDestination.VAULT) Icons.Filled.Folder else Icons.Outlined.Folder,
                                contentDescription = "Files"
                            )
                        },
                        label = {
                            Text(
                                text = NavDestination.VAULT.label,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (currentScreen == NavDestination.VAULT) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_vault")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavDestination.SETTINGS || currentScreen == NavDestination.MOUNTS,
                        onClick = { currentScreen = NavDestination.SETTINGS },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == NavDestination.SETTINGS || currentScreen == NavDestination.MOUNTS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = {
                            Text(
                                text = NavDestination.SETTINGS.label,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (currentScreen == NavDestination.SETTINGS) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = navItemColors,
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
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
                    onNavigateToVault = {
                        filesInitialTab = 2
                        currentScreen = NavDestination.VAULT
                    },
                    onNavigateToFileManager = {
                        filesInitialTab = 0
                        currentScreen = NavDestination.VAULT
                    },
                    onNavigateToGallery = {
                        filesInitialTab = 1
                        currentScreen = NavDestination.VAULT
                    },
                    onNavigateToTerminal = { currentScreen = NavDestination.MOUNTS },
                    onNavigateToAudit = { currentScreen = NavDestination.AUDIT }
                )
                NavDestination.APPS -> AppsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = NavDestination.DASHBOARD }
                )
                NavDestination.VAULT -> FilesScreen(
                    viewModel = viewModel,
                    initialTab = filesInitialTab,
                    onNavigateToApps = { currentScreen = NavDestination.APPS }
                )
                NavDestination.MOUNTS -> MountTerminalScreen(viewModel = viewModel)
                NavDestination.AUDIT -> AuditLogsScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = NavDestination.DASHBOARD }
                )
                NavDestination.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenDiagnostics = { currentScreen = NavDestination.MOUNTS }
                )
            }
        }
    }
}
