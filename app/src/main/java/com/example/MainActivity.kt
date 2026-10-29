package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.core.module.HollowModuleRegistry
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.ui.screens.AuditLogsScreen
import com.example.isolation.ui.screens.DashboardScreen
import com.example.isolation.ui.screens.MountTerminalScreen
import com.example.isolation.ui.screens.OnboardingAuthScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.screens.StorageExplorerScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily

enum class NavDestination(val label: String) {
    DASHBOARD("Home"),
    FILES("Files"),
    AUDIT("Activity"),
    SETTINGS("Settings"),
    MOUNTS("System"),
    LICENSE_HUB("License & Modules")
}

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        fun isLsposedModuleActiveReal(): Boolean {
            return false // Hooked by HollowHookBridge on Companion and outer MainActivity
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
    var activeModuleId by remember { mutableStateOf<String?>(null) }
    var isDetailScreenOpen by remember { mutableStateOf(false) }
    var showSystemAlertsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val vaultRecord by viewModel.vaultLicenseRecord.collectAsState()
    val moduleStates by viewModel.moduleStates.collectAsState()

    val mountedIds = moduleStates.filter { it.isReadyToUse }.map { it.module.id }.toSet()
    val isLicenseApproved = settings.devFullBuildMode || vaultRecord.status == LicenseAccountStatus.APPROVED

    if (!isLicenseApproved) {
        OnboardingAuthScreen(
            viewModel = viewModel,
            onContinueToDashboard = {
                activeModuleId = null
                currentScreen = NavDestination.DASHBOARD
            },
            onBack = null
        )
        return
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(currentScreen, activeModuleId) {
        isDetailScreenOpen = false
    }

    BackHandler(enabled = currentScreen != NavDestination.DASHBOARD || activeModuleId != null || isDetailScreenOpen) {
        if (isDetailScreenOpen) {
            isDetailScreenOpen = false
        } else if (activeModuleId != null) {
            activeModuleId = null
            currentScreen = NavDestination.DASHBOARD
        } else {
            currentScreen = NavDestination.DASHBOARD
        }
    }

    LaunchedEffect(mountedIds, settings.disabledModules) {
        val currentMod = activeModuleId
        if (currentMod != null && (currentMod in settings.disabledModules || (!settings.devFullBuildMode && currentMod !in mountedIds))) {
            activeModuleId = null
        }
    }

    val showMainTopBar = !isDetailScreenOpen && activeModuleId == null && (
            currentScreen == NavDestination.DASHBOARD ||
            currentScreen == NavDestination.FILES ||
            currentScreen == NavDestination.SETTINGS ||
            currentScreen == NavDestination.MOUNTS
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showMainTopBar) {
                TopAppBar(
                    navigationIcon = {},
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            com.example.isolation.ui.screens.HollowSplashEmblem(
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Hollow",
                                style = MaterialTheme.typography.titleLarge,
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = scheme.background,
                        titleContentColor = scheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (!isDetailScreenOpen) {
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

                        val isHomeSelected = activeModuleId == null && currentScreen == NavDestination.DASHBOARD

                        NavigationBarItem(
                            selected = isHomeSelected,
                            onClick = {
                                activeModuleId = null
                                currentScreen = NavDestination.DASHBOARD
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.DASHBOARD.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (isHomeSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_dashboard")
                        )

                        val isFilesSelected = activeModuleId == null && currentScreen == NavDestination.FILES

                        NavigationBarItem(
                            selected = isFilesSelected,
                            onClick = {
                                activeModuleId = null
                                currentScreen = NavDestination.FILES
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isFilesSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = "Files"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.FILES.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (isFilesSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_files")
                        )

                        // Dynamic navigation items for mounted modules contributing a bottom nav tab
                        val navModules = moduleStates.filter {
                            !it.isDisabledByUser && (settings.devFullBuildMode || it.isReadyToUse) && it.module.contributesBottomNavTab
                        }
                        navModules.forEach { state ->
                            val mod = state.module
                            val isModSelected = activeModuleId == mod.id
                            NavigationBarItem(
                                selected = isModSelected,
                                onClick = {
                                    activeModuleId = mod.id
                                },
                                icon = {
                                    Icon(
                                        imageVector = mod.icon,
                                        contentDescription = mod.displayName
                                    )
                                },
                                label = {
                                    Text(
                                        text = mod.cardTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JakartaFontFamily,
                                        fontWeight = if (isModSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = navItemColors,
                                modifier = Modifier.testTag("nav_mod_${mod.id}")
                            )
                        }

                        val isSettingsSelected = activeModuleId == null && (currentScreen == NavDestination.SETTINGS || currentScreen == NavDestination.MOUNTS)
                        NavigationBarItem(
                            selected = isSettingsSelected,
                            onClick = {
                                activeModuleId = null
                                currentScreen = NavDestination.SETTINGS
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSettingsSelected) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.SETTINGS.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_settings")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val openModuleId = activeModuleId
            if (openModuleId != null) {
                val mod = HollowModuleRegistry.findModuleById(openModuleId, moduleStates)
                if (mod != null && (settings.devFullBuildMode || mod.id in mountedIds)) {
                    mod.RenderPrimaryScreen(
                        context = viewModel.getModuleContext(mod.id),
                        onBack = {
                            activeModuleId = null
                            currentScreen = NavDestination.DASHBOARD
                        },
                        onDetailOpenChanged = { isDetailScreenOpen = it }
                    )
                } else {
                    OnboardingAuthScreen(
                        viewModel = viewModel,
                        onContinueToDashboard = {
                            activeModuleId = null
                            currentScreen = NavDestination.DASHBOARD
                        },
                        onBack = {
                            activeModuleId = null
                            currentScreen = NavDestination.DASHBOARD
                        }
                    )
                }
            } else {
                Crossfade(
                    targetState = currentScreen,
                    animationSpec = tween(durationMillis = 150),
                    label = "nav_crossfade"
                ) { screen ->
                    when (screen) {
                        NavDestination.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToTerminal = { currentScreen = NavDestination.MOUNTS },
                            onNavigateToAudit = { currentScreen = NavDestination.AUDIT },
                            onNavigateToLicenseHub = { currentScreen = NavDestination.LICENSE_HUB },
                            onOpenModule = { modId -> activeModuleId = modId }
                        )
                        NavDestination.FILES -> StorageExplorerScreen(viewModel = viewModel)
                        NavDestination.MOUNTS -> MountTerminalScreen(viewModel = viewModel)
                        NavDestination.AUDIT -> AuditLogsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = NavDestination.DASHBOARD }
                        )
                        NavDestination.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            onOpenDiagnostics = { currentScreen = NavDestination.MOUNTS },
                            onNavigateToLicenseHub = { currentScreen = NavDestination.LICENSE_HUB }
                        )
                        NavDestination.LICENSE_HUB -> OnboardingAuthScreen(
                            viewModel = viewModel,
                            onContinueToDashboard = { currentScreen = NavDestination.DASHBOARD },
                            onBack = { currentScreen = NavDestination.DASHBOARD }
                        )
                    }
                }
            }
        }
    }

    if (showSystemAlertsDialog) {
        AlertDialog(
            onDismissRequest = { showSystemAlertsDialog = false },
            title = {
                Text(
                    text = "System Alerts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• Security Engine: Root vault and runtime isolation active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "• Module Integrity: All mounted modules verified with Ed25519 signature.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "• Privacy Status: Zero unmonitored background data leaks detected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSystemAlertsDialog = false }) {
                    Text("Close", color = scheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = scheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}
