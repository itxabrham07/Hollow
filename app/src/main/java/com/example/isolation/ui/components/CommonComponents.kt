package com.example.isolation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.ui.theme.ClaySecondary
import com.example.ui.theme.CoralError
import com.example.ui.theme.ForestDark
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.HoneySoft
import com.example.ui.theme.HoneyTertiary
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.LavenderAccent
import com.example.ui.theme.SageSoft
import com.example.ui.theme.SageTint
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmSand
import com.example.ui.theme.WarmStone

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JakartaFontFamily
            )
        }
    }
}

@Composable
fun ModeBadge(mode: IsolationMode) {
    val (color, label) = when (mode) {
        IsolationMode.ENCRYPTED_VAULT -> Pair(ForestPrimary, "Private Locker")
        IsolationMode.SANDBOX_REDIRECT -> Pair(Color(0xFF2B6E52), "Separate Folder")
        IsolationMode.FAKE_EMPTY -> Pair(HoneyTertiary, "Blank Gallery")
        IsolationMode.READ_ONLY -> Pair(LavenderAccent, "View Only")
    }
    StatusBadge(text = label, color = color)
}

@Composable
fun AuditActionBadge(action: AuditAction) {
    val color = when (action) {
        AuditAction.ENCRYPTED -> ForestPrimary
        AuditAction.REDIRECTED -> Color(0xFF2B6E52)
        AuditAction.BLOCKED -> ClaySecondary
        AuditAction.SPOOFED_EMPTY -> HoneyTertiary
        AuditAction.PERMITTED -> InkSecondary
    }
    StatusBadge(text = action.label, color = color)
}

@Composable
fun CyberStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarmIvory),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = value,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                fontFamily = FrauncesFontFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                fontFamily = JakartaFontFamily
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily
            )
        }
    }
}

@Composable
fun LsposedStatusBanner(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgBrush = if (isActive) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1F4E3D), Color(0xFF2B6852))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFFF6EFE4), Color(0xFFEDE3D2))
        )
    }
    val contentColor = if (isActive) Color.White else InkPrimary
    val secondaryColor = if (isActive) Color.White.copy(alpha = 0.82f) else InkSecondary

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(26.dp),
        border = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(bgBrush)
            .clickable { onToggle() }
            .testTag("lsposed_status_banner")
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = if (isActive) Color.White.copy(alpha = 0.18f) else HoneyTertiary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isActive) Color(0xFFA7F3D0) else HoneyTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActive) "Privacy Shield Active" else "Setup Needed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) Color.White else HoneyTertiary,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isActive) {
                    "Your photos & personal folders are tucked away safely."
                } else {
                    "Turn on IsoVault in Vector or LSPosed to hide your gallery."
                },
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FrauncesFontFamily,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isActive) {
                    "Selected apps only see their own private folder instead of your personal camera roll."
                } else {
                    "Once enabled in your module manager, force-stop Snapchat or WhatsApp to apply."
                },
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = secondaryColor,
                fontFamily = JakartaFontFamily
            )
        }
    }
}

@Composable
fun DecryptedPreviewDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = WarmIvory,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SageSoft)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ForestPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Private File Preview",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = InkPrimary
                    )
                    Text(
                        text = title.substringAfterLast('/'),
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(WarmSand)
                        .padding(16.dp)
                ) {
                    Text(
                        text = content,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = InkPrimary,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(content))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(50)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = InkSecondary, fontFamily = JakartaFontFamily)
            }
        }
    )
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}

fun friendlyPathSummary(path: String): String {
    return when {
        path.contains("/DCIM/Camera", ignoreCase = true) -> "Camera Photos & Videos (DCIM)"
        path.contains("/Pictures", ignoreCase = true) -> "Pictures & Saved Photos"
        path.contains("/Download", ignoreCase = true) -> "Downloaded Files & Media"
        path.contains("/Documents", ignoreCase = true) -> "Personal Documents"
        path.contains("/Android/media", ignoreCase = true) -> "App Media & Chats"
        else -> path.substringAfterLast('/').ifBlank { path }
    }
}
