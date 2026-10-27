package com.example.isolation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.isolation.model.AuditAction
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.IsolationMode
import com.example.ui.theme.ClaySecondary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.LavenderAccent

private data class EmblemPalette(
    val bgColors: List<Color>,
    val rimColors: List<Color>,
    val ringColors: List<Color>,
    val coreColor: Color
)

/**
 * Renders the upgraded Hollow Eclipse & Hexagonal Vault emblem dynamically.
 * Adapts to Material You dynamic theme or the selected HollowIconTheme preset.
 */
@Composable
fun HollowDynamicEmblem(
    iconTheme: HollowIconTheme = HollowIconTheme.DYNAMIC_MATERIAL_YOU,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val palette = when (iconTheme) {
        HollowIconTheme.DYNAMIC_MATERIAL_YOU -> EmblemPalette(
            bgColors = listOf(scheme.primaryContainer, scheme.secondaryContainer),
            rimColors = listOf(scheme.primary, scheme.tertiary),
            ringColors = listOf(scheme.primary, scheme.tertiary),
            coreColor = scheme.onPrimaryContainer
        )
        HollowIconTheme.CYBER_ECLIPSE -> EmblemPalette(
            bgColors = listOf(Color(0xFF060B18), Color(0xFF111C38)),
            rimColors = listOf(Color(0xFF00F0FF), Color(0xFF6366F1)),
            ringColors = listOf(Color(0xFF00F5D4), Color(0xFF00BBF9)),
            coreColor = Color(0xFF00F5D4)
        )
        HollowIconTheme.CRIMSON_VOID -> EmblemPalette(
            bgColors = listOf(Color(0xFF0B0508), Color(0xFF2A0D18)),
            rimColors = listOf(Color(0xFFFF2E63), Color(0xFFFB923C)),
            ringColors = listOf(Color(0xFFFF2E63), Color(0xFFE11D48)),
            coreColor = Color(0xFFFFE4E6)
        )
        HollowIconTheme.OBSIDIAN_MINIMAL -> EmblemPalette(
            bgColors = listOf(Color(0xFF0A0A0C), Color(0xFF18181C)),
            rimColors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8)),
            ringColors = listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1)),
            coreColor = Color(0xFF38BDF8)
        )
    }
    val (bgColors, rimColors, ringColors, coreColor) = palette

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(Brush.linearGradient(bgColors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Hexagonal Vault Shield
            val hexRadius = w * 0.38f
            val hexPath = Path().apply {
                moveTo(cx, cy - hexRadius)
                lineTo(cx + hexRadius * 0.866f, cy - hexRadius * 0.5f)
                lineTo(cx + hexRadius * 0.866f, cy + hexRadius * 0.5f)
                lineTo(cx, cy + hexRadius)
                lineTo(cx - hexRadius * 0.866f, cy + hexRadius * 0.5f)
                lineTo(cx - hexRadius * 0.866f, cy - hexRadius * 0.5f)
                close()
            }

            drawPath(
                path = hexPath,
                color = bgColors.first().copy(alpha = 0.75f)
            )
            drawPath(
                path = hexPath,
                brush = Brush.linearGradient(rimColors),
                style = Stroke(
                    width = w * 0.055f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Hollow Eclipse Crescent Portal Ring
            drawCircle(
                brush = Brush.linearGradient(ringColors),
                radius = w * 0.24f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = bgColors.first(),
                radius = w * 0.17f,
                center = Offset(cx + w * 0.038f, cy - h * 0.01f)
            )

            // Singularity Core Node
            drawCircle(
                color = coreColor,
                radius = w * 0.055f,
                center = Offset(cx - w * 0.06f, cy - h * 0.015f)
            )
        }
    }
}

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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = JakartaFontFamily,
                softWrap = false,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ModeBadge(mode: IsolationMode) {
    val scheme = MaterialTheme.colorScheme
    val (color, label) = when (mode) {
        IsolationMode.ENCRYPTED_VAULT -> Pair(scheme.primary, "Per-App Locker")
        IsolationMode.SANDBOX_REDIRECT -> Pair(scheme.secondary, "Per-App Sandbox")
        IsolationMode.SHARED_WORKSPACE -> Pair(scheme.primary, "Hollow Workspace")
        IsolationMode.FAKE_EMPTY -> Pair(scheme.tertiary, "Hide Photos")
        IsolationMode.READ_ONLY -> Pair(LavenderAccent, "Read-Only")
    }
    StatusBadge(text = label, color = color)
}

@Composable
fun AuditActionBadge(action: AuditAction) {
    val scheme = MaterialTheme.colorScheme
    val (color, text) = when (action) {
        AuditAction.ENCRYPTED -> Pair(scheme.primary, "Saved Privately")
        AuditAction.REDIRECTED -> Pair(scheme.secondary, "Kept Private")
        AuditAction.BLOCKED -> Pair(ClaySecondary, "Blocked")
        AuditAction.SPOOFED_EMPTY -> Pair(scheme.tertiary, "Hidden")
        AuditAction.PERMITTED -> Pair(scheme.onSurfaceVariant, "Allowed")
        AuditAction.IDENTITY_SPOOFED -> Pair(scheme.tertiary, "Identity Spoofed")
    }
    StatusBadge(text = text, color = color)
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
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    fontFamily = JakartaFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (subtitle.isNotBlank()) "$value • $subtitle" else value,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = JakartaFontFamily
                )
            }
        }
    }
}

@Composable
fun LsposedStatusBanner(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (isActive) scheme.primaryContainer else scheme.surfaceContainerLow
    val contentColor = if (isActive) scheme.onPrimaryContainer else scheme.onSurface

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = if (isActive) null else BorderStroke(1.dp, scheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("lsposed_status_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isActive) scheme.primary else scheme.primaryContainer)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isActive) scheme.onPrimary else scheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "App Protection is On" else "Hollow Files & Safe Folder",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isActive) {
                        "Protected apps only see their own private folder, keeping your personal photos hidden."
                    } else {
                        "Manage files, browse photos, or lock private items in your Safe Folder."
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = contentColor.copy(alpha = 0.8f),
                    fontFamily = JakartaFontFamily
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh status",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ProSegmentedTabRow(
    options: List<Pair<String, ImageVector?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, (label, icon) ->
                val selected = selectedIndex == index
                val bgColor = if (selected) scheme.primaryContainer else Color.Transparent
                val contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(bgColor)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = label,
                            color = contentColor,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }
            }
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
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = scheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title.substringAfterLast('/'),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Safe Folder note",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
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
                        .background(scheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(
                        text = content,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = scheme.onSurface,
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
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
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
                Text("Close", color = scheme.onSurfaceVariant, fontFamily = JakartaFontFamily)
            }
        }
    )
}
