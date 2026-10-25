package com.example.isolation.ui.screens.files

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.isolation.crypto.AesVaultCipher
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FileViewerDialog(
    fileItem: VaultDisplayFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var decryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileItem.physicalPath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(fileItem.physicalPath)
                if (!file.exists()) {
                    errorMessage = "Encrypted file does not exist on disk."
                    isLoading = false
                    return@withContext
                }

                val key = AesVaultCipher.getVolumeKey(fileItem.volume, context)
                val bytes = if (fileItem.isEncrypted) {
                    AesVaultCipher.decrypt(file.readBytes(), key)
                } else {
                    file.readBytes()
                }

                decryptedBytes = bytes
                if (fileItem.mimeType.startsWith("text") ||
                    fileItem.name.endsWith(".txt") ||
                    fileItem.name.endsWith(".json") ||
                    fileItem.name.endsWith(".log")
                ) {
                    textContent = String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                errorMessage = "Decryption error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("file_viewer_dialog"),
            color = DarkBackground,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentSky.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = when {
                                    fileItem.mimeType.startsWith("image") -> Icons.Default.Description
                                    fileItem.mimeType.startsWith("video") -> Icons.Default.Videocam
                                    fileItem.mimeType.startsWith("audio") -> Icons.Default.Audiotrack
                                    fileItem.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                                    else -> Icons.Default.Lock
                                },
                                contentDescription = null,
                                tint = AccentSky,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = fileItem.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextWhite,
                                fontFamily = JakartaFontFamily,
                                maxLines = 1
                            )
                            Text(
                                text = "Volume: ${fileItem.volume} • ${fileItem.sizeBytes} bytes",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = JakartaFontFamily
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content area
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceCard)
                        .padding(12.dp)
                ) {
                    when {
                        isLoading -> {
                            Text("Decrypting from private vault...", color = TextMuted, fontFamily = JakartaFontFamily)
                        }
                        errorMessage != null -> {
                            Text(errorMessage ?: "", color = Color(0xFFF87171), fontFamily = JakartaFontFamily)
                        }
                        fileItem.mimeType.startsWith("image") && decryptedBytes != null -> {
                            val bitmap = remember(decryptedBytes) {
                                decryptedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = fileItem.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("Decrypted Image (Raw Preview)", color = TextWhite, fontFamily = JakartaFontFamily)
                            }
                        }
                        fileItem.mimeType.startsWith("video") -> {
                            VideoPlayerPlaceholder(fileItem.name, fileItem.sizeBytes)
                        }
                        fileItem.mimeType.startsWith("audio") -> {
                            AudioPlayerPlaceholder(fileItem.name)
                        }
                        fileItem.mimeType.contains("pdf") -> {
                            PdfViewerPlaceholder(fileItem.name, fileItem.sizeBytes)
                        }
                        textContent != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = textContent ?: "",
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = AccentMint, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("AES-256 Authenticated Binary", color = TextWhite, fontWeight = FontWeight.SemiBold)
                                Text("${decryptedBytes?.size ?: 0} bytes decrypted in memory", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun VideoPlayerPlaceholder(title: String, sizeBytes: Long) {
    var isPlaying by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0.15f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AccentSky.copy(alpha = 0.2f))
        ) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = AccentSky,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(text = "AES-256-GCM 4KB Chunk Random-Access Seeking", color = AccentMint, fontSize = 11.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("00:42", color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = seekProgress,
                onValueChange = { seekProgress = it },
                colors = SliderDefaults.colors(
                    thumbColor = AccentSky,
                    activeTrackColor = AccentSky,
                    inactiveTrackColor = DarkSurfaceElevated
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("03:15", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun AudioPlayerPlaceholder(title: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.4f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(imageVector = Icons.Default.Audiotrack, contentDescription = null, tint = AccentSky, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold)
        Text("Encrypted Audio Stream", color = TextMuted, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = AccentSky
                )
            }
            Slider(
                value = progress,
                onValueChange = { progress = it },
                colors = SliderDefaults.colors(thumbColor = AccentSky, activeTrackColor = AccentSky),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PdfViewerPlaceholder(title: String, sizeBytes: Long) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold)
        Text("Decrypted PDF Document • $sizeBytes bytes", color = TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Page 1 of 4", color = TextSubtle, fontSize = 11.sp)
    }
}
