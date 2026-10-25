package com.example.isolation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.delay

@Composable
fun PasscodeScreen(
    correctPin: String = "1234",
    panicPin: String = "9999",
    decoyPinEnabled: Boolean = true,
    fingerprintEnabled: Boolean = true,
    onUnlocked: () -> Unit,
    onPanicUnlocked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var wrongAttempts by remember { mutableIntStateOf(0) }
    var cooldownSeconds by remember { mutableIntStateOf(0) }
    var statusHint by remember { mutableStateOf("Enter your 4-digit PIN to unlock") }

    // Cooldown countdown timer after wrong PIN attempts
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds -= 1
            if (cooldownSeconds == 0) {
                isError = false
                statusHint = "Enter your 4-digit PIN to unlock"
            } else {
                statusHint = "Wrong PIN. Wait ${cooldownSeconds}s to try again"
            }
        }
    }

    // Evaluate PIN once 4 digits are entered
    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4 && cooldownSeconds == 0) {
            delay(120L)
            when {
                enteredPin == correctPin -> {
                    wrongAttempts = 0
                    enteredPin = ""
                    onUnlocked()
                }
                decoyPinEnabled && enteredPin == panicPin -> {
                    wrongAttempts = 0
                    enteredPin = ""
                    onPanicUnlocked?.invoke() ?: onUnlocked()
                }
                else -> {
                    wrongAttempts += 1
                    isError = true
                    val delaySecs = if (wrongAttempts >= 3) 8 else 3
                    cooldownSeconds = delaySecs
                    statusHint = "Wrong PIN. Wait ${delaySecs}s to try again"
                    delay(350L)
                    enteredPin = ""
                }
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Surface(
        color = colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("passcode_screen")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 32.dp, vertical = 28.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Top Brand Logo + Title + Dots
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colorScheme.primary.copy(alpha = 0.14f))
                        .border(1.dp, colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Vault Logo",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Vault Locked",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FrauncesFontFamily,
                    color = colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = statusHint,
                    fontSize = 14.sp,
                    fontFamily = JakartaFontFamily,
                    color = if (isError || cooldownSeconds > 0) colorScheme.error else colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 4-Dot PIN Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (index in 0 until 4) {
                        val isFilled = index < enteredPin.length
                        val dotSize by animateDpAsState(
                            targetValue = if (isFilled) 14.dp else 12.dp,
                            animationSpec = spring(),
                            label = "dot_size"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                isError || cooldownSeconds > 0 -> colorScheme.error
                                isFilled -> colorScheme.primary
                                else -> colorScheme.outline
                            },
                            label = "dot_color"
                        )

                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }

            // Minimal Numeric Keypad with Fingerprint & Backspace
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (digit in row) {
                            PinDigitButton(
                                label = digit,
                                enabled = cooldownSeconds == 0,
                                onClick = {
                                    if (enteredPin.length < 4 && cooldownSeconds == 0) {
                                        enteredPin += digit
                                    }
                                }
                            )
                        }
                    }
                }

                // Bottom Row: Fingerprint | 0 | Backspace
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fingerprintEnabled) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(74.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .background(colorScheme.primary.copy(alpha = 0.12f))
                                .clickable(enabled = cooldownSeconds == 0) {
                                    onUnlocked()
                                }
                                .testTag("keypad_fingerprint")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = "Unlock with Fingerprint",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(74.dp))
                    }

                    PinDigitButton(
                        label = "0",
                        enabled = cooldownSeconds == 0,
                        onClick = {
                            if (enteredPin.length < 4 && cooldownSeconds == 0) {
                                enteredPin += "0"
                            }
                        }
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(74.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(colorScheme.surfaceVariant)
                            .clickable(enabled = cooldownSeconds == 0 && enteredPin.isNotEmpty()) {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                            }
                            .testTag("keypad_backspace")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = "Delete Digit",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PinDigitButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(74.dp)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(
                if (enabled) colorScheme.surfaceVariant else colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
            .border(1.dp, colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("keypad_$label")
    ) {
        Text(
            text = label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JakartaFontFamily,
            color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
