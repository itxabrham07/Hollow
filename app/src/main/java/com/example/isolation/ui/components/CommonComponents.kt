package com.example.isolation.ui.components

import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.isolation.model.AuditAction
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.IsolationMode
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.M3AmberAccent

/**
 * Global in-memory LRU cache for decoded app launcher icons so LazyColumn scrolling stays 120fps.
 */
object AppIconBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(180) {}

    fun getOrDecode(packageName: String, drawable: Drawable?, sizePx: Int = 96): ImageBitmap? {
        if (drawable == null) return null
        cache.get(packageName)?.let { return it }
        return try {
            val bmp = drawable.toBitmap(sizePx, sizePx).asImageBitmap()
            cache.put(packageName, bmp)
            bmp
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun rememberAppIconBitmap(packageName: String, drawable: Drawable?, sizePx: Int = 96): ImageBitmap? {
    return remember(packageName) {
        AppIconBitmapCache.getOrDecode(packageName, drawable, sizePx)
    }
}

/**
 * ContentCard — the single card treatment across the app:
 * medium shape (or custom shape token), flat 0.dp elevation, 1.dp hairline border, neutral surface,
 * and standard 16.dp content padding.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = cardModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * StatusIndicator — a small colored dot + short label text in semantic color,
 * replacing decorative badge/pill/chevron chrome.
 */
@Composable
fun StatusIndicator(
    label: String,
    color: Color = MaterialTheme.colorScheme.primary,
    showDot: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * SectionHeader — one consistent section title treatment (Jakarta, titleMedium, no all-caps, no eyebrow line).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * ListRow — one unified row treatment for settings, app lists, and file lists.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    monoSubtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingBitmap: ImageBitmap? = null,
    tintLeadingIcon: Boolean = false,
    statusLabel: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.secondary,
    showNavigationChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingBitmap != null) {
            Image(
                bitmap = leadingBitmap,
                contentDescription = title,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(12.dp))
        } else if (leadingIcon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (tintLeadingIcon) scheme.primaryContainer
                        else scheme.surfaceVariant
                    )
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (tintLeadingIcon) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!statusLabel.isNullOrBlank()) {
                    StatusIndicator(label = statusLabel, color = statusColor)
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!monoSubtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                MonoDataText(
                    text = monoSubtitle,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        } else if (showNavigationChevron && onClick != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * PrimaryButton — the single filled button style app-wide.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (isDestructive) scheme.error else scheme.primary
    val contentColor = if (isDestructive) scheme.onError else scheme.onPrimary

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * SecondaryButton — the single outlined button style app-wide.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    isDestructive: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = if (isDestructive) scheme.error else scheme.onSurface
    val borderColor = if (isDestructive) scheme.error else scheme.outline

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * EmptyState — one consistent empty/zero-data treatment across all screens.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    ContentCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SecondaryButton(
                    text = actionLabel,
                    onClick = onAction
                )
            }
        }
    }
}

/**
 * MonoDataText — the only approved way to render technical data:
 * device IDs, hashes, fingerprints, IPs, file paths, logs, timestamps.
 */
@Composable
fun MonoDataText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxLines: Int = Int.MAX_VALUE
) {
    Text(
        text = text,
        style = style.copy(fontFamily = JetBrainsMonoFontFamily),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Renders the Hollow Eclipse & Hexagonal Vault emblem using theme tokens.
 */
@Composable
fun HollowDynamicEmblem(
    iconTheme: HollowIconTheme = HollowIconTheme.DYNAMIC_MATERIAL_YOU,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val bgColor = when (iconTheme) {
        HollowIconTheme.DYNAMIC_MATERIAL_YOU -> scheme.primaryContainer
        HollowIconTheme.CYBER_ECLIPSE -> scheme.surfaceContainerHigh
        HollowIconTheme.CRIMSON_VOID -> scheme.errorContainer
        HollowIconTheme.OBSIDIAN_MINIMAL -> scheme.surfaceVariant
    }
    val accentColor = when (iconTheme) {
        HollowIconTheme.DYNAMIC_MATERIAL_YOU -> scheme.primary
        HollowIconTheme.CYBER_ECLIPSE -> scheme.primary
        HollowIconTheme.CRIMSON_VOID -> scheme.error
        HollowIconTheme.OBSIDIAN_MINIMAL -> scheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

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
                color = accentColor,
                style = Stroke(
                    width = w * 0.06f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawCircle(
                color = accentColor,
                radius = w * 0.22f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = bgColor,
                radius = w * 0.15f,
                center = Offset(cx + w * 0.03f, cy - h * 0.01f)
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
    StatusIndicator(
        label = text,
        color = color,
        modifier = modifier
    )
}

@Composable
fun ModeBadge(mode: IsolationMode) {
    val scheme = MaterialTheme.colorScheme
    val (color, label) = when (mode) {
        IsolationMode.ENCRYPTED_VAULT -> Pair(scheme.secondary, "Per-App Locker")
        IsolationMode.SANDBOX_REDIRECT -> Pair(scheme.secondary, "Per-App Sandbox")
        IsolationMode.SHARED_WORKSPACE -> Pair(scheme.primary, "Hollow Workspace")
        IsolationMode.FAKE_EMPTY -> Pair(M3AmberAccent, "Hide Photos")
        IsolationMode.READ_ONLY -> Pair(scheme.onSurfaceVariant, "Read-Only")
    }
    StatusIndicator(label = label, color = color)
}

@Composable
fun AuditActionBadge(action: AuditAction) {
    val scheme = MaterialTheme.colorScheme
    val (color, text) = when (action) {
        AuditAction.ENCRYPTED -> Pair(scheme.secondary, "Saved Privately")
        AuditAction.REDIRECTED -> Pair(scheme.secondary, "Kept Private")
        AuditAction.BLOCKED -> Pair(scheme.error, "Blocked")
        AuditAction.SPOOFED_EMPTY -> Pair(M3AmberAccent, "Hidden")
        AuditAction.PERMITTED -> Pair(scheme.onSurfaceVariant, "Allowed")
        AuditAction.IDENTITY_SPOOFED -> Pair(scheme.secondary, "Identity Spoofed")
    }
    StatusIndicator(label = text, color = color)
}

@Composable
fun CyberStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val scheme = MaterialTheme.colorScheme
    ContentCard(
        modifier = modifier.testTag(testTag),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (subtitle.isNotBlank()) "$value • $subtitle" else value,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    val statusColor = if (isActive) scheme.secondary else M3AmberAccent

    ContentCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("lsposed_status_banner"),
        onClick = onToggle,
        borderColor = if (isActive) scheme.secondary else scheme.outlineVariant
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Shield,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isActive) "LSPosed & Root Core Active" else "Hollow Sandbox Core Ready",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    StatusIndicator(
                        label = if (isActive) "Active" else "Standby",
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isActive) {
                        "Kernel mounts, runtime hooks & identity shields are enforcing rules."
                    } else {
                        "Standalone mode active • Enable module in LSPosed for system-wide hooks."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh status",
                    tint = scheme.onSurfaceVariant,
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
        color = scheme.surface,
        shape = MaterialTheme.shapes.small,
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
                val bgColor = if (selected) scheme.primary else Color.Transparent
                val contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(bgColor)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp, horizontal = 8.dp)
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
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Safe Folder note",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
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
                        .clip(MaterialTheme.shapes.small)
                        .background(scheme.surfaceContainerLow)
                        .padding(16.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Copy",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    clipboard.setText(AnnotatedString(content))
                    onDismiss()
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    fontFamily = JakartaFontFamily
                )
            }
        }
    )
}

/**
 * Banner shown on Home Dashboard when new modules are ready to download (consolidated from HollowUiKit).
 */
@Composable
fun ModuleDownloadNotificationBanner(
    pendingDownloadCount: Int,
    pendingModuleNames: String,
    onDownloadAllClick: () -> Unit,
    onOpenModuleHubClick: () -> Unit
) {
    if (pendingDownloadCount <= 0) return
    val scheme = MaterialTheme.colorScheme

    ContentCard(
        onClick = onOpenModuleHubClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("banner_module_download_notification")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(scheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$pendingDownloadCount New Module${if (pendingDownloadCount == 1) "" else "s"} Available",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pendingModuleNames,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            PrimaryButton(
                text = "Install All",
                onClick = onDownloadAllClick,
                modifier = Modifier.testTag("btn_banner_download_modules")
            )
        }
    }
}

/**
 * Account & Modules Card on the Home Dashboard (consolidated from HollowUiKit).
 */
@Composable
fun RootVaultStatusCard(
    devFullBuild: Boolean,
    hardwareFingerprint: String,
    activeModulesCount: Int,
    totalModulesCount: Int,
    remainingDays: Int,
    onOpenLicenseHub: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    ContentCard(
        onClick = onOpenLicenseHub,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_root_vault_status")
    ) {
        ListRow(
            title = if (devFullBuild) "All Feature Modules Active" else "Cloud Account & Modules",
            subtitle = if (devFullBuild) {
                "Tap to manage cloud modules, account sync, or switch build mode"
            } else if (activeModulesCount > 0) {
                "$activeModulesCount modules installed • Tap to check for updates"
            } else {
                "Tap to sign in, sync your license, or download feature modules"
            },
            monoSubtitle = hardwareFingerprint,
            leadingIcon = if (devFullBuild) Icons.Default.Widgets else Icons.Default.VerifiedUser,
            statusLabel = "$activeModulesCount/$totalModulesCount Ready",
            statusColor = if (activeModulesCount > 0) scheme.secondary else M3AmberAccent,
            showNavigationChevron = true
        )
    }
}
