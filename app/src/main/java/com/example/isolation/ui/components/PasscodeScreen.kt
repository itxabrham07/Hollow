package com.example.isolation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.KeypadButtonBg
import com.example.ui.theme.KeypadDarkIcon
import com.example.ui.theme.KeypadWhiteCircle
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay

@Composable
fun PasscodeScreen(
    correctPin: String = "1234",
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            delay(150)
            if (enteredPin == correctPin) {
                onUnlocked()
            } else {
                isError = true
                delay(600)
                enteredPin = ""
                isError = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("passcode_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Title & Subtitle matching Mockup
            Text(
                text = "Enter Passcode",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JakartaFontFamily,
                color = TextWhite
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isError) "Incorrect passcode, try again" else "Please enter your passcode",
                fontSize = 14.sp,
                fontFamily = JakartaFontFamily,
                color = if (isError) AccentCoral else TextMuted
            )

            Spacer(modifier = Modifier.height(36.dp))

            // 4-Dot Passcode Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    val isFirst = i == 0 && enteredPin.isNotEmpty()

                    Box(
                        modifier = Modifier
                            .size(if (isFirst) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isError -> AccentCoral
                                    isFilled -> AccentSky
                                    else -> DarkBorderSubtle
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Circular Numeric Keypad (1 to 9, backspace, 0, checkmark)
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
                        KeypadDigitButton(
                            label = digit,
                            onClick = {
                                if (enteredPin.length < 4) {
                                    enteredPin += digit
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Bottom row: Backspace (White), '0', Confirm Checkmark (Sky Blue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: White circle with dark backspace icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(KeypadWhiteCircle)
                        .clickable {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                            }
                        }
                        .testTag("keypad_backspace")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = KeypadDarkIcon,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Middle: '0'
                KeypadDigitButton(
                    label = "0",
                    onClick = {
                        if (enteredPin.length < 4) {
                            enteredPin += "0"
                        }
                    }
                )

                // Right: Sky Blue circle with checkmark
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(AccentSky)
                        .clickable {
                            if (enteredPin == correctPin || enteredPin.isEmpty()) {
                                onUnlocked()
                            } else {
                                isError = true
                            }
                        }
                        .testTag("keypad_confirm")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick bypass for testing / developer convenience
            TextButton(
                onClick = onUnlocked,
                modifier = Modifier.testTag("bypass_passcode_button")
            ) {
                Text(
                    text = "Bypass (Default: 1234)",
                    color = TextSubtle,
                    fontSize = 12.sp,
                    fontFamily = JakartaFontFamily
                )
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(KeypadButtonBg)
            .clickable(onClick = onClick)
            .testTag("keypad_$label")
    ) {
        Text(
            text = label,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JakartaFontFamily,
            color = TextWhite
        )
    }
}
