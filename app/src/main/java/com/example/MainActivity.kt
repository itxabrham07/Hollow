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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.ui.components.HollowDynamicEmblem
import com.example.isolation.ui.components.StatusBadge
import com.example.isolation.ui.screens.AppHiderScreen
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.AuditLogsScreen
import com.example.isolation.ui.screens.DashboardScreen
import com.example.isolation.ui.screens.DeviceSpooferScreen
import com.example.isolation.ui.screens.FilesScreen
import com.example.isolation.ui.screens.MountTerminalScreen
import com.example.isolation.ui.screens.OnboardingAuthScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.screens.VpnProxyScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily

enum class NavDestination(val label: String) {
    DASHBOARD("Home"),
    VAULT("Files"),
    APPS("Storage Isolate"),
    APP_HIDER("Hide App"),
    DEVICE_SPOOFER("Privacy"),
    AUDIT("Activity"),
    SETTINGS("Settings"),
    MOUNTS("System"),
    VPN_PROXY("V2Ray & Proxy"),
    LICENSE_HUB("License & Modules")
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
    var activeModuleId by remember { mutableStateOf<String?>(null) }
    var filesInitialTab by remember { mutableIntStateOf(0) }
    var isDetailScreenOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val vaultRecord by viewModel.vaultLicenseRecord.collectAsState()
    val moduleStates by viewModel.moduleStates.collectAsState()

    val mountedIds = moduleStates.filter { it.isReadyToUse }.map { it.module.id }.toSet()
    val isPrivacyMounted = "module_privacy" in mountedIds
    val isIsolationMounted = "module_isolation" in mountedIds

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

    val showMainTopBar = !isDetailScreenOpen && activeModuleId == null && (
            currentScreen == NavDestination.DASHBOARD ||
            currentScreen == NavDestination.VAULT ||
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
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HollowDynamicEmblem(
                                iconTheme = settings.hollowIconTheme,
                                size = 34.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Hollow",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = JakartaFontFamily,
                                color = scheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                activeModuleId = null
                                currentScreen = NavDestination.LICENSE_HUB
                            },
                            modifier = Modifier.testTag("top_bar_license_hub")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Root Vault & Module Hub",
                                tint = scheme.primary
                            )
                        }
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

                        val isPrivacyActive = (activeModuleId == "module_privacy") ||
                                (activeModuleId == null && currentScreen == NavDestination.DEVICE_SPOOFER)

                        val isHomeSection = !isPrivacyActive && (
                                activeModuleId != null ||
                                currentScreen == NavDestination.DASHBOARD ||
                                currentScreen == NavDestination.APPS ||
                                currentScreen == NavDestination.APP_HIDER ||
                                currentScreen == NavDestination.AUDIT ||
                                currentScreen == NavDestination.VPN_PROXY ||
                                currentScreen == NavDestination.LICENSE_HUB
                        )

                        NavigationBarItem(
                            selected = isHomeSection,
                            onClick = {
                                activeModuleId = null
                                currentScreen = NavDestination.DASHBOARD
                            },
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

                        if (isPrivacyMounted) {
                            NavigationBarItem(
                                selected = isPrivacyActive,
                                onClick = {
                                    activeModuleId = "module_privacy"
                                    currentScreen = NavDestination.DEVICE_SPOOFER
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isPrivacyActive) Icons.Filled.Shield else Icons.Outlined.Shield,
                                        contentDescription = "Privacy"
                                    )
                                },
                                label = {
                                    Text(
                                        text = NavDestination.DEVICE_SPOOFER.label,
                                        fontSize = 11.sp,
                                        fontFamily = JakartaFontFamily,
                                        fontWeight = if (isPrivacyActive) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = navItemColors,
                                modifier = Modifier.testTag("nav_privacy")
                            )
                        }

                        if (isIsolationMounted) {
                            NavigationBarItem(
                                selected = activeModuleId == null && currentScreen == NavDestination.VAULT,
                                onClick = {
                                    activeModuleId = null
                                    filesInitialTab = 0
                                    currentScreen = NavDestination.VAULT
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (activeModuleId == null && currentScreen == NavDestination.VAULT) Icons.Filled.Folder else Icons.Outlined.Folder,
                                        contentDescription = "Files"
                                    )
                                },
                                label = {
                                    Text(
                                        text = NavDestination.VAULT.label,
                                        fontSize = 11.sp,
                                        fontFamily = JakartaFontFamily,
                                        fontWeight = if (activeModuleId == null && currentScreen == NavDestination.VAULT) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = navItemColors,
                                modifier = Modifier.testTag("nav_vault")
                            )
                        }

                        NavigationBarItem(
                            selected = activeModuleId == null && (currentScreen == NavDestination.SETTINGS || currentScreen == NavDestination.MOUNTS),
                            onClick = {
                                activeModuleId = null
                                currentScreen = NavDestination.SETTINGS
                            },
                            icon = {
                                Icon(
                                    imageVector = if (activeModuleId == null && (currentScreen == NavDestination.SETTINGS || currentScreen == NavDestination.MOUNTS)) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.SETTINGS.label,
                                    fontSize = 11.sp,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (activeModuleId == null && currentScreen == NavDestination.SETTINGS) FontWeight.Bold else FontWeight.Medium
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
                val mod = com.example.isolation.core.module.HollowModuleRegistry.findModuleById(openModuleId, moduleStates)
                if (mod != null && (settings.devFullBuildMode || mod.id in mountedIds)) {
                    mod.RenderPrimaryScreen(
                        viewModel = viewModel,
                        onBack = {
                            activeModuleId = null
                            currentScreen = NavDestination.DASHBOARD
                        },
                        onDetailOpenChanged = { isDetailScreenOpen = it }
                    )
                } else {
                    OnboardingAuthScreen(
                        viewModel = viewModel,
                        isStandaloneGate = false,
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
                            onNavigateToApps = { activeModuleId = "module_isolation" },
                            onNavigateToAppHider = { activeModuleId = "module_hider" },
                            onNavigateToDeviceSpoofer = { activeModuleId = "module_privacy" },
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
                            onNavigateToAudit = { currentScreen = NavDestination.AUDIT },
                            onNavigateToVpnProxy = { activeModuleId = "module_network" },
                            onNavigateToLicenseHub = { currentScreen = NavDestination.LICENSE_HUB },
                            onOpenModule = { modId -> activeModuleId = modId }
                        )
                        NavDestination.APPS,
                        NavDestination.APP_HIDER,
                        NavDestination.DEVICE_SPOOFER,
                        NavDestination.VPN_PROXY -> {
                            val mod = com.example.isolation.core.module.HollowModuleRegistry.findModuleByRoute(screen.name, moduleStates)
                            if (mod != null && (settings.devFullBuildMode || mod.id in mountedIds)) {
                                mod.RenderPrimaryScreen(
                                    viewModel = viewModel,
                                    onBack = { currentScreen = NavDestination.DASHBOARD },
                                    onDetailOpenChanged = { isDetailScreenOpen = it }
                                )
                            } else {
                                OnboardingAuthScreen(
                                    viewModel = viewModel,
                                    isStandaloneGate = false,
                                    onBack = { currentScreen = NavDestination.DASHBOARD }
                                )
                            }
                        }
                        NavDestination.VAULT -> FilesScreen(
                            viewModel = viewModel,
                            initialTab = filesInitialTab,
                            onNavigateToApps = { activeModuleId = "module_isolation" }
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
                        NavDestination.LICENSE_HUB -> OnboardingAuthScreen(
                            viewModel = viewModel,
                            isStandaloneGate = false,
                            onBack = { currentScreen = NavDestination.DASHBOARD }
                        )
                    }
                }
            }
        }
    }
}
