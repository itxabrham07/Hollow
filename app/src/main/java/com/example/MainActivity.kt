package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.ui.components.PasscodeScreen
import com.example.isolation.ui.screens.AlbumsGalleryScreen
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.DashboardScreen
import com.example.isolation.ui.screens.MtFileExplorerScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

enum class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTagId: String
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_vault"),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps, "nav_apps"),
    GALLERY("Gallery", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, "nav_albums"),
    FILES("Files", Icons.Filled.Folder, Icons.Outlined.Folder, "nav_files"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
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
    var isPasscodeLocked by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val primaryPin by viewModel.primaryPin.collectAsState()
    val panicPin by viewModel.panicPin.collectAsState()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(enabled = currentScreen != NavDestination.DASHBOARD || isPasscodeLocked) {
        if (!isPasscodeLocked) {
            currentScreen = NavDestination.DASHBOARD
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    HorizontalDivider(color = DarkBorderSubtle, thickness = 1.dp)
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = TextWhite,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .height(68.dp)
                            .testTag("bottom_nav_bar")
                    ) {
                        NavDestination.entries.forEach { dest ->
                            val isSelected = currentScreen == dest
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentScreen = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        fontSize = 11.sp,
                                        fontFamily = JakartaFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentSky,
                                    selectedTextColor = AccentSky,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = AccentSky.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag(dest.testTagId)
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
                when (currentScreen) {
                    NavDestination.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToApps = { currentScreen = NavDestination.APPS },
                        onNavigateToGallery = { currentScreen = NavDestination.GALLERY },
                        onNavigateToExplorer = { currentScreen = NavDestination.FILES },
                        onNavigateToSettings = { currentScreen = NavDestination.SETTINGS },
                        onTriggerLock = { isPasscodeLocked = true }
                    )
                    NavDestination.APPS -> AppsScreen(
                        viewModel = viewModel,
                        onOpenExplorerForPackage = {
                            currentScreen = NavDestination.FILES
                        }
                    )
                    NavDestination.GALLERY -> AlbumsGalleryScreen(
                        viewModel = viewModel
                    )
                    NavDestination.FILES -> MtFileExplorerScreen(
                        viewModel = viewModel
                    )
                    NavDestination.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onTriggerLock = { isPasscodeLocked = true }
                    )
                }
            }
        }

        // Passcode Screen Overlay (Lock Screen with Panic PIN support)
        AnimatedVisibility(
            visible = isPasscodeLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PasscodeScreen(
                correctPin = primaryPin,
                panicPin = panicPin,
                onUnlocked = { isPasscodeLocked = false },
                onPanicUnlocked = {
                    isPasscodeLocked = false
                    viewModel.triggerPanicMode()
                    currentScreen = NavDestination.GALLERY
                },
                onUpdatePins = { p, pan -> viewModel.updatePins(p, pan) }
            )
        }
    }
}
