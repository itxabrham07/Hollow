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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
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
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.HoneyTertiary
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.SageSoft
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmLinen
import com.example.ui.theme.WarmStone

enum class NavDestination(val label: String) {
    DASHBOARD("Home"),
    APPS("Apps"),
    VAULT("Files"),
    AUDIT("Activity"),
    SETTINGS("Settings"),
    MOUNTS("System")
}

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        fun isLsposedModuleActiveReal(): Boolean {
            return false // Hooked by StorageIsolationHook when Vector / LSPosed is active
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IsoVaultTheme(darkTheme = false) {
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

    BackHandler(enabled = currentScreen != NavDestination.DASHBOARD) {
        currentScreen = NavDestination.DASHBOARD
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WarmLinen,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(WarmLinen)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SageSoft)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = ForestPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "IsoVault",
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FrauncesFontFamily,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "Personal photo & folder privacy",
                                    fontSize = 12.sp,
                                    color = InkSecondary,
                                    fontFamily = JakartaFontFamily
                                )
                            }
                        }
                    },
                    actions = {
                        StatusBadge(
                            text = if (isLsposedActive) "Protected" else "Standby",
                            color = if (isLsposedActive) ForestPrimary else HoneyTertiary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = WarmLinen,
                        titleContentColor = InkPrimary
                    )
                )
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = WarmStone, thickness = 1.dp)
                NavigationBar(
                    containerColor = WarmIvory,
                    contentColor = InkPrimary,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == NavDestination.DASHBOARD,
                        onClick = { currentScreen = NavDestination.DASHBOARD },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == NavDestination.DASHBOARD) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = {
                            Text(
                                text = NavDestination.DASHBOARD.label,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (currentScreen == NavDestination.DASHBOARD) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForestPrimary,
                            selectedTextColor = ForestPrimary,
                            unselectedIconColor = InkSecondary,
                            unselectedTextColor = InkSecondary,
                            indicatorColor = SageSoft
                        ),
                        modifier = Modifier.testTag("nav_dashboard")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavDestination.APPS,
                        onClick = { currentScreen = NavDestination.APPS },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == NavDestination.APPS) Icons.Filled.Apps else Icons.Outlined.Apps,
                                contentDescription = "Apps"
                            )
                        },
                        label = {
                            Text(
                                text = NavDestination.APPS.label,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (currentScreen == NavDestination.APPS) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForestPrimary,
                            selectedTextColor = ForestPrimary,
                            unselectedIconColor = InkSecondary,
                            unselectedTextColor = InkSecondary,
                            indicatorColor = SageSoft
                        ),
                        modifier = Modifier.testTag("nav_apps")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavDestination.VAULT,
                        onClick = { currentScreen = NavDestination.VAULT },
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
                                fontWeight = if (currentScreen == NavDestination.VAULT) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForestPrimary,
                            selectedTextColor = ForestPrimary,
                            unselectedIconColor = InkSecondary,
                            unselectedTextColor = InkSecondary,
                            indicatorColor = SageSoft
                        ),
                        modifier = Modifier.testTag("nav_vault")
                    )

                    NavigationBarItem(
                        selected = currentScreen == NavDestination.AUDIT,
                        onClick = { currentScreen = NavDestination.AUDIT },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == NavDestination.AUDIT) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "Activity"
                            )
                        },
                        label = {
                            Text(
                                text = NavDestination.AUDIT.label,
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                fontWeight = if (currentScreen == NavDestination.AUDIT) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForestPrimary,
                            selectedTextColor = ForestPrimary,
                            unselectedIconColor = InkSecondary,
                            unselectedTextColor = InkSecondary,
                            indicatorColor = SageSoft
                        ),
                        modifier = Modifier.testTag("nav_audit")
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
                                fontWeight = if (currentScreen == NavDestination.SETTINGS) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForestPrimary,
                            selectedTextColor = ForestPrimary,
                            unselectedIconColor = InkSecondary,
                            unselectedTextColor = InkSecondary,
                            indicatorColor = SageSoft
                        ),
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
                    onNavigateToVault = { currentScreen = NavDestination.VAULT },
                    onNavigateToTerminal = { currentScreen = NavDestination.MOUNTS },
                    onNavigateToAudit = { currentScreen = NavDestination.AUDIT }
                )
                NavDestination.APPS -> AppsScreen(viewModel = viewModel)
                NavDestination.VAULT -> FilesScreen(viewModel = viewModel)
                NavDestination.MOUNTS -> MountTerminalScreen(viewModel = viewModel)
                NavDestination.AUDIT -> AuditLogsScreen(viewModel = viewModel)
                NavDestination.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenDiagnostics = { currentScreen = NavDestination.MOUNTS }
                )
            }
        }
    }
}
