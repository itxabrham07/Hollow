package com.example.isolation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JakartaFontFamily

@Composable
fun BrandSquircleIcon(
    name: String,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val cornerRadius = size * 0.28f
    val cleanName = name.lowercase().trim()

    when {
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
        cleanName.contains("apple") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF000000))
            ) {
                Text(
                    text = "",
                    fontSize = (size.value * 0.52f).sp,
                    color = Color.White
                )
            }
        }
        cleanName.contains("amazon") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color.White)
            ) {
                Text(
                    text = "a",
                    fontSize = (size.value * 0.6f).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = JakartaFontFamily,
                    color = Color.Black
                )
            }
        }
        cleanName.contains("behance") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFF0057FF))
            ) {
                Text(
                    text = "Bē",
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = Color.White
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
                Text(
                    text = "👻",
                    fontSize = (size.value * 0.5f).sp
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
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
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
                    contentDescription = null,
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
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.48f)
                )
            }
        }
        cleanName.contains("binance") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFFF3BA2F))
            ) {
                Text(
                    text = "◈",
                    fontSize = (size.value * 0.55f).sp,
                    color = Color.Black
                )
            }
        }
        cleanName.contains("adobe") -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xFFED2224))
            ) {
                Text(
                    text = "A",
                    fontSize = (size.value * 0.55f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = Color.White
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
                    contentDescription = null,
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
                    .background(Color(0xFF282A34))
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    fontSize = (size.value * 0.5f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = Color.White
                )
            }
        }
    }
}
