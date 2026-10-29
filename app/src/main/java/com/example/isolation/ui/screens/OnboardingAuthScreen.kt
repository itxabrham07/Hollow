package com.example.isolation.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.ui.components.ContentCard
import com.example.isolation.ui.components.SectionHeader
import com.example.isolation.ui.components.StatusIndicator
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlinx.coroutines.delay

private val ButtonDarkOnyx = Color(0xFF111827)
private val ButtonLightGray = Color(0xFFF1F5F9)
private val TextDarkOnyx = Color(0xFF111827)
private val TextMutedSlate = Color(0xFF64748B)
private val CardBorderColor = Color(0xFFE2E8F0)
private val BackdropSoftBlue = Color(0xFFEFF6FF)

private enum class OnboardingStep {
    SPLASH,
    WELCOME,
    ROOT_REQUEST,
    REGISTER,
    LOGIN,
    APPROVAL_SUCCESS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAuthScreen(
    viewModel: IsoVaultViewModel,
    onContinueToDashboard: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val vaultRecord by viewModel.vaultLicenseRecord.collectAsStateWithLifecycle()
    val moduleStates by viewModel.moduleStates.collectAsStateWithLifecycle()
    val isRootGranted by viewModel.isRootAccessGranted.collectAsStateWithLifecycle()
    val isRequestingRoot by viewModel.isRequestingRoot.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val hw = viewModel.hardwareBinding

    var currentStep by remember { mutableStateOf(OnboardingStep.SPLASH) }
    var previousStatus by remember { mutableStateOf(vaultRecord.status) }

    // If device was pending approval and becomes approved, show Screen 06 (Approval Success)
    LaunchedEffect(vaultRecord.status) {
        if (previousStatus == LicenseAccountStatus.PENDING_APPROVAL &&
            vaultRecord.status == LicenseAccountStatus.APPROVED
        ) {
            currentStep = OnboardingStep.APPROVAL_SUCCESS
        }
        previousStatus = vaultRecord.status

        // Auto-poll status every 5 seconds while waiting for approval
        if (vaultRecord.status == LicenseAccountStatus.PENDING_APPROVAL) {
            while (true) {
                viewModel.syncLicenseAndModulesFromServer()
                delay(5_000L)
            }
        }
    }

    // Hardware back handler for multi-step onboarding
    BackHandler(enabled = currentStep != OnboardingStep.WELCOME && currentStep != OnboardingStep.SPLASH && vaultRecord.status == LicenseAccountStatus.UNREGISTERED) {
        currentStep = when (currentStep) {
            OnboardingStep.ROOT_REQUEST -> OnboardingStep.WELCOME
            OnboardingStep.REGISTER -> OnboardingStep.ROOT_REQUEST
            OnboardingStep.LOGIN -> OnboardingStep.WELCOME
            else -> OnboardingStep.WELCOME
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_auth_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                // Screen 05: Approval Pending
                vaultRecord.status == LicenseAccountStatus.PENDING_APPROVAL -> {
                    ApprovalPendingScreen(
                        username = vaultRecord.username,
                        email = vaultRecord.email,
                        hardwareId = hw.fingerprintHex,
                        deviceModel = hw.deviceModel,
                        statusMessage = statusMessage,
                        onCheckStatus = { viewModel.syncLicenseAndModulesFromServer() },
                        onCopyHardwareId = {
                            copyToClipboard(context, "Hollow Device ID", hw.fingerprintHex)
                            viewModel.postStatusMessage("Device ID copied to clipboard")
                        },
                        onSwitchAccount = { viewModel.simulateAdminResetToUnregistered() }
                    )
                }

                // Screen 06: Approval Success (or if user just approved)
                currentStep == OnboardingStep.APPROVAL_SUCCESS ||
                (vaultRecord.status == LicenseAccountStatus.APPROVED && onBack == null && currentStep == OnboardingStep.APPROVAL_SUCCESS) -> {
                    ApprovalSuccessScreen(
                        onOpenDashboard = {
                            viewModel.downloadAllAuthorizedModules()
                            onContinueToDashboard()
                        }
                    )
                }

                // If opened as Hub from top bar when already approved
                vaultRecord.status == LicenseAccountStatus.APPROVED -> {
                    ApprovedAccountHubScreen(
                        viewModel = viewModel,
                        username = vaultRecord.username,
                        email = vaultRecord.email,
                        hardwareId = hw.fingerprintHex,
                        allowedCount = vaultRecord.allowedModules.size,
                        downloadedCount = vaultRecord.downloadedModules.size,
                        moduleStates = moduleStates,
                        onContinueToDashboard = onContinueToDashboard,
                        onBack = onBack
                    )
                }

                // Banned device
                vaultRecord.status == LicenseAccountStatus.BANNED -> {
                    BannedDeviceScreen(
                        banReason = vaultRecord.banReason,
                        hardwareId = hw.fingerprintHex,
                        onReset = { viewModel.simulateAdminResetToUnregistered() }
                    )
                }

                // Multi-step Onboarding (Screens 02, 03, 04)
                else -> {
                    Crossfade(
                        targetState = currentStep,
                        animationSpec = tween(220),
                        label = "onboarding_step_crossfade"
                    ) { step ->
                        when (step) {
                            OnboardingStep.SPLASH -> {
                                SplashScreen(
                                    onContinue = { currentStep = OnboardingStep.WELCOME }
                                )
                            }

                            OnboardingStep.WELCOME -> {
                                WelcomeGetStartedScreen(
                                    onCreateAccount = {
                                        // If root not yet granted, guide through Screen 03 (Root Request)
                                        currentStep = if (!isRootGranted) OnboardingStep.ROOT_REQUEST else OnboardingStep.REGISTER
                                    },
                                    onLogin = { currentStep = OnboardingStep.LOGIN }
                                )
                            }

                            OnboardingStep.ROOT_REQUEST -> {
                                RootAccessRequestScreen(
                                    isRootGranted = isRootGranted,
                                    isRequestingRoot = isRequestingRoot,
                                    onBack = { currentStep = OnboardingStep.WELCOME },
                                    onGrantRoot = {
                                        viewModel.requestRootAccess { granted ->
                                            if (granted) {
                                                currentStep = OnboardingStep.REGISTER
                                            }
                                        }
                                    },
                                    onContinue = { currentStep = OnboardingStep.REGISTER }
                                )
                            }

                            OnboardingStep.REGISTER -> {
                                RegisterAccountScreen(
                                    isAuthLoading = isAuthLoading,
                                    statusMessage = statusMessage,
                                    defaultUsername = vaultRecord.username,
                                    defaultEmail = vaultRecord.email,
                                    onBack = {
                                        currentStep = if (!isRootGranted) OnboardingStep.ROOT_REQUEST else OnboardingStep.WELCOME
                                    },
                                    onSwitchToLogin = { currentStep = OnboardingStep.LOGIN },
                                    onSubmitRegister = { u, e, p ->
                                        viewModel.registerAccountWithCloud(u, e, p)
                                    }
                                )
                            }

                            OnboardingStep.LOGIN -> {
                                LoginAccountScreen(
                                    isAuthLoading = isAuthLoading,
                                    statusMessage = statusMessage,
                                    defaultIdentifier = vaultRecord.username.ifBlank { vaultRecord.email },
                                    onBack = { currentStep = OnboardingStep.WELCOME },
                                    onSwitchToRegister = { currentStep = OnboardingStep.REGISTER },
                                    onSubmitSignIn = { identifier, p ->
                                        viewModel.signInWithCloud(identifier, p)
                                    }
                                )
                            }

                            OnboardingStep.APPROVAL_SUCCESS -> {
                                ApprovalSuccessScreen(
                                    onOpenDashboard = {
                                        viewModel.downloadAllAuthorizedModules()
                                        onContinueToDashboard()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Screen 01: Splash Screen
// ============================================================================
@Composable
private fun SplashScreen(
    onContinue: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1400L)
        onContinue()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .clickable { onContinue() }
            .testTag("screen_splash"),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background subtle wavy contours
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val wave1 = Path().apply {
                moveTo(w * 0.65f, 0f)
                cubicTo(w * 0.95f, h * 0.25f, w * 0.45f, h * 0.65f, w, h * 0.8f)
                lineTo(w, 0f)
                close()
            }
            drawPath(wave1, color = Color(0xFFF1F5F9))

            val wave2 = Path().apply {
                moveTo(0f, h * 0.35f)
                cubicTo(w * 0.25f, h * 0.55f, w * 0.1f, h * 0.85f, w * 0.45f, h)
                lineTo(0f, h)
                close()
            }
            drawPath(wave2, color = Color(0xFFF1F5F9))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            HollowSplashEmblem(modifier = Modifier.size(100.dp))

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Hollow",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = TextDarkOnyx
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your Privacy. Your Rules.",
                style = MaterialTheme.typography.titleMedium,
                color = TextMutedSlate
            )
        }
    }
}

@Composable
fun HollowSplashEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Left pillar (faceted dark geometric shapes)
        val leftTop = Path().apply {
            moveTo(w * 0.08f, 0f)
            lineTo(w * 0.44f, h * 0.14f)
            lineTo(w * 0.44f, h * 0.58f)
            lineTo(w * 0.08f, h * 0.44f)
            close()
        }
        drawPath(leftTop, color = Color(0xFF1E293B))

        val leftBottom = Path().apply {
            moveTo(w * 0.08f, h * 0.44f)
            lineTo(w * 0.44f, h * 0.58f)
            lineTo(w * 0.44f, h)
            lineTo(w * 0.08f, h * 0.86f)
            close()
        }
        drawPath(leftBottom, color = Color(0xFF0F172A))

        // Right pillar (faceted dark geometric shapes)
        val rightTop = Path().apply {
            moveTo(w * 0.56f, h * 0.14f)
            lineTo(w * 0.92f, 0f)
            lineTo(w * 0.92f, h * 0.44f)
            lineTo(w * 0.56f, h * 0.58f)
            close()
        }
        drawPath(rightTop, color = Color(0xFF334155))

        val rightBottom = Path().apply {
            moveTo(w * 0.56f, h * 0.58f)
            lineTo(w * 0.92f, h * 0.44f)
            lineTo(w * 0.92f, h * 0.86f)
            lineTo(w * 0.56f, h)
            close()
        }
        drawPath(rightBottom, color = Color(0xFF1E293B))

        // Dynamic center folding crossbar
        val centerFold = Path().apply {
            moveTo(w * 0.44f, h * 0.32f)
            lineTo(w * 0.56f, h * 0.42f)
            lineTo(w * 0.56f, h * 0.68f)
            lineTo(w * 0.44f, h * 0.58f)
            close()
        }
        drawPath(centerFold, color = Color(0xFF475569))
    }
}

// ============================================================================
// Screen 02: Welcome / Get Started
// ============================================================================
@Composable
private fun WelcomeGetStartedScreen(
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Section: Pager Indicator & Hero Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.height(8.dp))
            // 3 Pager Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(ButtonDarkOnyx)
                )
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCBD5E1))
                )
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCBD5E1))
                )
            }

            Spacer(Modifier.height(36.dp))

            Text(
                text = "Take control\nof your digital life",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                ),
                color = TextDarkOnyx,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Modular. Secure. Always evolving.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMutedSlate,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Center Section: 3 Feature Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WelcomeFeatureRow(
                icon = Icons.Default.Lock,
                title = "Hide, isolate and protect apps"
            )
            WelcomeFeatureRow(
                icon = Icons.Default.Shield,
                title = "Advanced privacy modules"
            )
            WelcomeFeatureRow(
                icon = Icons.Default.Settings,
                title = "Install only what you need"
            )
        }

        // Bottom Section: Action Buttons & Privacy Tagline
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Solid Black Capsule Button: Create Account
            Button(
                onClick = onCreateAccount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkOnyx,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_get_started_create_account")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Light Gray Tonal Capsule Button: Login
            Button(
                onClick = onLogin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonLightGray,
                    contentColor = TextDarkOnyx
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_get_started_login")
            ) {
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Your data stays private. Always.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedSlate
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    title: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFF1F5F9),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextDarkOnyx,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = TextDarkOnyx
                )
            )
        }
    }
}

// ============================================================================
// Screen 03: Root Access Request
// ============================================================================
@Composable
private fun RootAccessRequestScreen(
    isRootGranted: Boolean,
    isRequestingRoot: Boolean,
    onBack: () -> Unit,
    onGrantRoot: () -> Unit,
    onContinue: () -> Unit
) {
    var showWhyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Back Button
        Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_root_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextDarkOnyx
                )
            }

            Spacer(Modifier.height(8.dp))

            // Phone with `#` Illustration
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PhoneRootGraphic(modifier = Modifier.size(130.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Root Access Required",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                ),
                color = TextDarkOnyx,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Hollow needs root access to install and\nrun privacy modules securely.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedSlate,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // 4 Checklist Items with BLACK TICKS (per user request)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RootChecklistItem(text = "Install approved modules")
                RootChecklistItem(text = "Enable system-level privacy")
                RootChecklistItem(text = "Hide and isolate apps")
                RootChecklistItem(text = "Manage device identifiers")
            }
        }

        // Bottom Section: Grant Button & "Why root access?" link
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (isRootGranted) onContinue() else onGrantRoot() },
                enabled = !isRequestingRoot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkOnyx,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_request_root")
            ) {
                if (isRequestingRoot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Approve in Magisk / KernelSU...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Text(
                        text = if (isRootGranted) "Root Granted · Continue" else "Grant Root Access",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            TextButton(onClick = { showWhyDialog = true }) {
                Text(
                    text = "Why root access?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2563EB),
                        textDecoration = TextDecoration.Underline
                    )
                )
            }
        }
    }

    if (showWhyDialog) {
        AlertDialog(
            onDismissRequest = { showWhyDialog = false },
            title = {
                Text(
                    text = "Why Does Hollow Need Root?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Hollow enforces hardware-level sandboxing, isolated mount namespaces, and per-app identity spoofing via superuser (su) commands. Root allows Hollow to protect your personal files and identity without relying on external cloud trackers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedSlate
                )
            },
            confirmButton = {
                TextButton(onClick = { showWhyDialog = false }) {
                    Text("Understood", fontWeight = FontWeight.Bold, color = ButtonDarkOnyx)
                }
            }
        )
    }
}

/**
 * Checklist item with a solid BLACK circle and WHITE tick (per user instruction:
 * "instead of blue ticks use black ticks like button color").
 */
@Composable
private fun RootChecklistItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        BlackTickCircle(size = 22.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                color = TextDarkOnyx
            )
        )
    }
}

// ============================================================================
// Screen 04: Register Account
// ============================================================================
@Composable
private fun RegisterAccountScreen(
    isAuthLoading: Boolean,
    statusMessage: String?,
    defaultUsername: String,
    defaultEmail: String,
    onBack: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onSubmitRegister: (String, String, String) -> Unit
) {
    var username by remember(defaultUsername) { mutableStateOf(defaultUsername) }
    var email by remember(defaultEmail) { mutableStateOf(defaultEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var agreeTerms by remember { mutableStateOf(true) }
    var agreePrivacy by remember { mutableStateOf(true) }
    var keepAnonymous by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_register_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextDarkOnyx
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Create Your Account",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                ),
                color = TextDarkOnyx
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Join Hollow to access approved modules.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedSlate
            )

            Spacer(Modifier.height(24.dp))

            // Form Fields
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                placeholder = { Text("shad0wuser") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextMutedSlate)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButtonDarkOnyx,
                    unfocusedBorderColor = CardBorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_register_username")
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { Text("shad0wuser@mail.com") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TextMutedSlate)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButtonDarkOnyx,
                    unfocusedBorderColor = CardBorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_register_email")
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("••••••••") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextMutedSlate)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password",
                            tint = TextMutedSlate
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButtonDarkOnyx,
                    unfocusedBorderColor = CardBorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_register_password")
            )

            Spacer(Modifier.height(20.dp))

            // Checkboxes with BLACK TICKS (per user request: "instead of blue ticks use black ticks like button color")
            BlackCheckboxRow(
                checked = agreeTerms,
                onCheckedChange = { agreeTerms = it },
                text = "I agree to the Terms of Service"
            )

            Spacer(Modifier.height(10.dp))

            BlackCheckboxRow(
                checked = agreePrivacy,
                onCheckedChange = { agreePrivacy = it },
                text = "I agree to the Privacy Policy"
            )

            Spacer(Modifier.height(10.dp))

            BlackCheckboxRow(
                checked = keepAnonymous,
                onCheckedChange = { keepAnonymous = it },
                text = "Keep me anonymous (recommended)"
            )

            if (!statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Bottom Action Button: Register & Switch link
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    onSubmitRegister(
                        username.ifBlank { "user_${System.currentTimeMillis() % 10000}" },
                        email.ifBlank { "user@hollow.app" },
                        password.ifBlank { "hollow_pass" }
                    )
                },
                enabled = !isAuthLoading && username.isNotBlank() && agreeTerms && agreePrivacy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkOnyx,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_submit_registration")
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Submitting registration...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Text(
                        text = "Register",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedSlate
                )
                Text(
                    text = "Login",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.clickable { onSwitchToLogin() }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================================
// Screen 04 (Login Mode): Sign In
// ============================================================================
@Composable
private fun LoginAccountScreen(
    isAuthLoading: Boolean,
    statusMessage: String?,
    defaultIdentifier: String,
    onBack: () -> Unit,
    onSwitchToRegister: () -> Unit,
    onSubmitSignIn: (String, String) -> Unit
) {
    var identifier by remember(defaultIdentifier) { mutableStateOf(defaultIdentifier) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_login_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextDarkOnyx
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                ),
                color = TextDarkOnyx
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Sign in to access your approved modules.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedSlate
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                label = { Text("Username or Email") },
                placeholder = { Text("shad0wuser") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextMutedSlate)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButtonDarkOnyx,
                    unfocusedBorderColor = CardBorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_login_identifier")
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("••••••••") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextMutedSlate)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password",
                            tint = TextMutedSlate
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ButtonDarkOnyx,
                    unfocusedBorderColor = CardBorderColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_login_password")
            )

            if (!statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onSubmitSignIn(identifier, password) },
                enabled = !isAuthLoading && identifier.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkOnyx,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_submit_login")
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Signing in...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Don't have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedSlate
                )
                Text(
                    text = "Register",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    ),
                    modifier = Modifier.clickable { onSwitchToRegister() }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================================
// Screen 05: Approval Pending ("Account Under Review")
// ============================================================================
@Composable
private fun ApprovalPendingScreen(
    username: String,
    email: String,
    hardwareId: String,
    deviceModel: String,
    statusMessage: String?,
    onCheckStatus: () -> Unit,
    onCopyHardwareId: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("card_waiting_approval"),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.height(16.dp))

            // Document with Clock Badge Graphic
            DocumentReviewGraphic(modifier = Modifier.size(130.dp))

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Account Under Review",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                ),
                color = TextDarkOnyx,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your account is being reviewed by\nour team. This helps us maintain\na safe and trusted community.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedSlate,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Stepper Timeline with Black Tick on Submitted
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Step 1: Submitted (with BLACK TICK like button color)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BlackTickCircle(size = 22.dp)
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(32.dp)
                                .background(ButtonDarkOnyx)
                        )
                    }
                    Column {
                        Text(
                            text = "Submitted",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextDarkOnyx
                        )
                        Text(
                            text = "Today · ${username.ifBlank { "Device Bound" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedSlate
                        )
                    }
                }

                // Step 2: Under Review (In-progress pulse)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = ButtonDarkOnyx,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(32.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                    }
                    Column {
                        Text(
                            text = "Under Review",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextDarkOnyx
                        )
                        Text(
                            text = "Usually takes a few minutes to a few hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedSlate
                        )
                    }
                }

                // Step 3: Approved (empty circle)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.size(22.dp)
                    ) {}
                    Column {
                        Text(
                            text = "Approved",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Bottom Info Callout & Actions
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Soft Blue Info Callout
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BackdropSoftBlue,
                border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "You'll get a notification when your account is approved.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E3A8A)
                        )
                    )
                }
            }

            // Check Status Button (Black Capsule)
            Button(
                onClick = onCheckStatus,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonDarkOnyx,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_poll_approval_status")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Check Approval Status",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Switch Account Text Button
            TextButton(
                onClick = onSwitchAccount,
                modifier = Modifier.testTag("btn_edit_registration")
            ) {
                Text(
                    text = "Switch Account",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMutedSlate)
                )
            }
        }
    }
}

// ============================================================================
// Screen 06: Approval Success ("Welcome to Hollow!")
// ============================================================================
@Composable
private fun ApprovalSuccessScreen(
    onOpenDashboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Center Content: Large Green Checkmark Circle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFDCFCE7),
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Welcome to Hollow!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily
                ),
                color = TextDarkOnyx,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Your account has been approved.\nYou can now access the dashboard\nand install modules.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedSlate,
                textAlign = TextAlign.Center
            )
        }

        // Bottom Action Button: Open Dashboard
        Button(
            onClick = onOpenDashboard,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonDarkOnyx,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_approval_success_open_dashboard")
        ) {
            Text(
                text = "Open Dashboard",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

// ============================================================================
// Hub Screen: When already approved and accessed via Top Bar Key icon
// ============================================================================
@Composable
private fun ApprovedAccountHubScreen(
    viewModel: IsoVaultViewModel,
    username: String,
    email: String,
    hardwareId: String,
    allowedCount: Int,
    downloadedCount: Int,
    moduleStates: List<com.example.isolation.core.module.DynamicModuleState>,
    onContinueToDashboard: () -> Unit,
    onBack: (() -> Unit)?
) {
    val activeCount = moduleStates.count { it.isReadyToUse }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Modules",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = com.example.ui.theme.JakartaFontFamily
                    ),
                    color = TextDarkOnyx
                )
                Text(
                    text = "$activeCount of $allowedCount active",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedSlate
                )
            }
        }

        items(moduleStates, key = { it.module.id }) { modState ->
            val mod = modState.module
            ContentCard(
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                borderColor = CardBorderColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("module_vault_row_${mod.id}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (modState.isReadyToUse) Color(0xFFF1F5F9) else Color(0xFFF8FAFC),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = mod.icon,
                                    contentDescription = mod.cardTitle,
                                    tint = if (modState.isDisabledByUser) TextMutedSlate else TextDarkOnyx,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mod.cardTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (modState.isDisabledByUser) TextMutedSlate else TextDarkOnyx,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (modState.isDisabledByUser) "Disabled • Hidden from Dashboard" else mod.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedSlate,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))

                    when {
                        modState.isReadyToUse -> {
                            androidx.compose.material3.Switch(
                                checked = true,
                                onCheckedChange = {
                                    viewModel.disableModuleFromVault(mod.id)
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ButtonDarkOnyx
                                ),
                                modifier = Modifier.testTag("toggle_module_${mod.id}")
                            )
                        }
                        modState.isDisabledByUser && (modState.isAuthorizedByAdmin || modState.isDownloadedInRootVault) -> {
                            androidx.compose.material3.Switch(
                                checked = false,
                                onCheckedChange = {
                                    viewModel.enableModuleInVault(mod.id)
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ButtonDarkOnyx
                                ),
                                modifier = Modifier.testTag("toggle_module_${mod.id}")
                            )
                        }
                        modState.isAuthorizedByAdmin -> {
                            Button(
                                onClick = { viewModel.downloadModuleIntoRootVault(mod.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ButtonDarkOnyx,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_download_${mod.id}")
                            ) {
                                Text("Install", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        else -> {
                            StatusIndicator(label = "Locked", color = TextMutedSlate, showDot = false)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Banned Device Screen
// ============================================================================
@Composable
private fun BannedDeviceScreen(
    banReason: String,
    hardwareId: String,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 480.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorderColor),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_device_banned")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Restricted",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Device Access Restricted",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = banReason.ifBlank { "This device has been restricted by the administrator." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedSlate
                )
                Button(
                    onClick = onReset,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonDarkOnyx,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_unban_reset")
                ) {
                    Text("Reset Registration")
                }
            }
        }
    }
}

// ============================================================================
// Reusable Custom Graphics & Black Ticks
// ============================================================================

/**
 * Solid BLACK circle with WHITE tick (as requested: "instead of blue ticks use black ticks like button color").
 */
@Composable
fun BlackTickCircle(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Surface(
        shape = CircleShape,
        color = ButtonDarkOnyx,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Checked",
                tint = Color.White,
                modifier = Modifier.size(size * 0.65f)
            )
        }
    }
}

/**
 * Checkbox row with solid BLACK box and WHITE tick when checked.
 */
@Composable
private fun BlackCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (checked) ButtonDarkOnyx else Color.Transparent,
            border = BorderStroke(1.5.dp, if (checked) ButtonDarkOnyx else Color(0xFFCBD5E1)),
            modifier = Modifier.size(20.dp)
        ) {
            if (checked) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextDarkOnyx,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

/**
 * Graphic for Screen 03: Smartphone silhouette with `#` root symbol and a dark gear badge.
 */
@Composable
fun PhoneRootGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Soft blue circular backdrop
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E7FF).copy(alpha = 0.7f))
        )

        // Phone outline
        Canvas(modifier = Modifier.size(width = 54.dp, height = 90.dp)) {
            val w = size.width
            val h = size.height
            val strokeW = 3f

            // Phone Body
            drawRoundRect(
                color = Color.White,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = ButtonDarkOnyx,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = strokeW)
            )

            // Speaker slit
            drawLine(
                color = ButtonDarkOnyx,
                start = Offset(w * 0.38f, 7f),
                end = Offset(w * 0.62f, 7f),
                strokeWidth = 2.5f
            )
        }

        // Center `#` text
        Text(
            text = "#",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            color = ButtonDarkOnyx,
            modifier = Modifier.align(Alignment.Center)
        )

        // Overlapping Gear Badge at bottom-right
        Surface(
            shape = CircleShape,
            color = ButtonDarkOnyx,
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-6).dp, y = (-6).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Graphic for Screen 05: Document with Clock badge illustration.
 */
@Composable
fun DocumentReviewGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Soft blue circular backdrop
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E7FF).copy(alpha = 0.7f))
        )

        // Document Canvas
        Canvas(modifier = Modifier.size(width = 58.dp, height = 76.dp)) {
            val w = size.width
            val h = size.height

            // Document body with folded corner
            val docPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(w - 14f, 0f)
                lineTo(w, 14f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(docPath, color = Color.White)
            drawPath(docPath, color = Color(0xFF93C5FD), style = Stroke(width = 2.5f))

            // Document fold triangle
            val foldPath = Path().apply {
                moveTo(w - 14f, 0f)
                lineTo(w - 14f, 14f)
                lineTo(w, 14f)
                close()
            }
            drawPath(foldPath, color = Color(0xFFDBEAFE))

            // 3 Horizontal text placeholder lines
            drawLine(Color(0xFF93C5FD), Offset(10f, 24f), Offset(w - 16f, 24f), strokeWidth = 3f)
            drawLine(Color(0xFF93C5FD), Offset(10f, 34f), Offset(w - 16f, 34f), strokeWidth = 3f)
            drawLine(Color(0xFF93C5FD), Offset(10f, 44f), Offset(w * 0.55f, 44f), strokeWidth = 3f)
        }

        // Overlapping Clock Badge at bottom-right
        Surface(
            shape = CircleShape,
            color = ButtonDarkOnyx,
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-4).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}
