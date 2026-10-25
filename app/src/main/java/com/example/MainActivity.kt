package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.crypto.VaultLockManager
import com.example.isolation.ui.components.PasscodeScreen
import com.example.isolation.ui.screens.AppsScreen
import com.example.isolation.ui.screens.AuditLogsScreen
import com.example.isolation.ui.screens.SettingsScreen
import com.example.isolation.ui.screens.files.FilesScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.IsoVaultTheme
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

enum class NavDestination(val label: String) {
    APPS("Apps"),
    FILES("Files"),
    ACTIVITY("Activity"),
    SETTINGS("Settings")
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
        // Prevent screen capture / recents leaks of sensitive decrypted data
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            IsoVaultTheme(darkTheme = true) {
                MainAppContainer()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Auto-lock vault on background and wipe sensitive decrypted in-memory caches
        VaultLockManager.lockVault(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: IsoVaultViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(NavDestination.APPS) }
    val isVaultLocked by VaultLockManager.isLocked.collectAsState()
    var isPasscodeLocked by remember { mutableStateOf(isVaultLocked) }

    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isLsposedActive by viewModel.isLsposedActive.collectAsState()

    LaunchedEffect(isVaultLocked) {
        if (isVaultLocked) {
            isPasscodeLocked = true
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler(enabled = currentScreen != NavDestination.APPS || isPasscodeLocked) {
        if (!isPasscodeLocked) {
            currentScreen = NavDestination.APPS
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.background(DarkBackground)) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AccentSky.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = AccentSky,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hollow",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = JakartaFontFamily,
                                        color = TextWhite
                                    )
                                    Text(
                                        text = "Storage isolation & encrypted vault",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }
                        },
                        actions = {
                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isLsposedActive) AccentMint.copy(alpha = 0.15f) else AccentAmber.copy(alpha = 0.15f))
                                    .clickable { viewModel.toggleLsposedStatus() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isLsposedActive) AccentMint else AccentAmber)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isLsposedActive) "Protected" else "Standby",
                                        color = if (isLsposedActive) AccentMint else AccentAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JakartaFontFamily
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Passcode Lock button
                            IconButton(
                                onClick = {
                                    VaultLockManager.lockVault(context)
                                    isPasscodeLocked = true
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DarkSurfaceElevated)
                                    .testTag("lock_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock Vault",
                                    tint = AccentSky,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DarkBackground,
                            titleContentColor = TextWhite
                        )
                    )
                }
            },
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
                        // Apps Tab
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
                                    fontWeight = if (currentScreen == NavDestination.APPS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentSky,
                                selectedTextColor = AccentSky,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentSky.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_apps")
                        )

                        // Files Tab
                        NavigationBarItem(
                            selected = currentScreen == NavDestination.FILES,
                            onClick = { currentScreen = NavDestination.FILES },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == NavDestination.FILES) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = "Files"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.FILES.label,
                                    fontSize = 11.sp,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (currentScreen == NavDestination.FILES) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentSky,
                                selectedTextColor = AccentSky,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentSky.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_files")
                        )

                        // Activity Tab
                        NavigationBarItem(
                            selected = currentScreen == NavDestination.ACTIVITY,
                            onClick = { currentScreen = NavDestination.ACTIVITY },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == NavDestination.ACTIVITY) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = "Activity"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.ACTIVITY.label,
                                    fontSize = 11.sp,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (currentScreen == NavDestination.ACTIVITY) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentSky,
                                selectedTextColor = AccentSky,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentSky.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_activity")
                        )

                        // Settings Tab
                        NavigationBarItem(
                            selected = currentScreen == NavDestination.SETTINGS,
                            onClick = { currentScreen = NavDestination.SETTINGS },
                            icon = {
                                Icon(
                                    imageVector = if (currentScreen == NavDestination.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = {
                                Text(
                                    text = NavDestination.SETTINGS.label,
                                    fontSize = 11.sp,
                                    fontFamily = JakartaFontFamily,
                                    fontWeight = if (currentScreen == NavDestination.SETTINGS) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentSky,
                                selectedTextColor = AccentSky,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted,
                                indicatorColor = AccentSky.copy(alpha = 0.15f)
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
                    NavDestination.APPS -> AppsScreen(viewModel = viewModel)
                    NavDestination.FILES -> FilesScreen(viewModel = viewModel)
                    NavDestination.ACTIVITY -> AuditLogsScreen(viewModel = viewModel)
                    NavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }

        // Passcode Screen Overlay (Lock Screen)
        AnimatedVisibility(
            visible = isPasscodeLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PasscodeScreen(
                correctPin = "1234",
                onUnlocked = {
                    VaultLockManager.unlockVault("1234", context)
                    isPasscodeLocked = false
                }
            )
        }
    }
}
