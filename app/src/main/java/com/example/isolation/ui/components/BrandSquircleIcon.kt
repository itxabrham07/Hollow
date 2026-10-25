package com.example.isolation.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JakartaFontFamily

private fun drawableToImageBitmap(drawable: Drawable, targetPx: Int = 128): ImageBitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: targetPx
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: targetPx
        val bmp = Bitmap.createBitmap(
            width.coerceAtMost(256),
            height.coerceAtMost(256),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
fun BrandSquircleIcon(
    name: String,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
    packageName: String? = null,
    appDrawable: Drawable? = null
) {
    val context = LocalContext.current
    val cornerRadius = size * 0.28f
    val cleanName = name.lowercase().trim()

    val resolvedBitmap: ImageBitmap? = remember(packageName, appDrawable) {
        if (appDrawable != null) {
            drawableToImageBitmap(appDrawable)
        } else if (!packageName.isNullOrBlank() &&
            packageName != "shared.vault" &&
            !packageName.startsWith("volume.")
        ) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawableToImageBitmap(drawable)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    if (resolvedBitmap != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Image(
                bitmap = resolvedBitmap,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    when {
        packageName == "shared.vault" || cleanName.contains("shared space") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = Icons.Default.FolderShared,
                    contentDescription = name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.54f)
                )
            }
        }
        cleanName.contains("google") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.White)
            ) {
                Text(
                    text = "G",
                    fontSize = (size.value * 0.55f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = Color(0xFF4285F4)
                )
            }
        }
        cleanName.contains("snapchat") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFFFFFC00))
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = name,
                    tint = Color.Black,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }
        cleanName.contains("whatsapp") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF25D366))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }
        cleanName.contains("instagram") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }
        cleanName.contains("telegram") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF2AABEE))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.48f)
                )
            }
        }
        cleanName.contains("photo") || cleanName.contains("gallery") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF52B5F8))
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }
        else -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            ) {
                Text(
                    text = name.trim().take(1).uppercase().ifBlank { "V" },
                    fontSize = (size.value * 0.48f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
