package com.example.isolation.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.isolation.crypto.AesVaultCipher
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.JakartaFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

/**
 * Seekable MediaDataSource backed by AesVaultCipher's 4KB chunked streaming AEAD (`readDecryptedRange`).
 * Allows MediaPlayer to seek through large encrypted media streams in O(1) without loading the entire file into memory.
 */
class EncryptedChunkMediaDataSource(
    private val encryptedFile: File,
    private val volumeKey: SecretKey
) : MediaDataSource() {

    private val estimatedPlainLength: Long = run {
        val rawLen = encryptedFile.length()
        if (rawLen <= AesVaultCipher.V2_HEADER_TOTAL_BYTES) {
            0L
        } else {
            val cipherLen = rawLen - AesVaultCipher.V2_HEADER_TOTAL_BYTES
            val numChunks = (cipherLen + AesVaultCipher.ENCRYPTED_CHUNK_SIZE - 1) / AesVaultCipher.ENCRYPTED_CHUNK_SIZE
            (cipherLen - numChunks * AesVaultCipher.GCM_TAG_BYTES).coerceAtLeast(0L)
        }
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= estimatedPlainLength || size <= 0) return -1
        val toRead = minOf(size.toLong(), estimatedPlainLength - position).toInt()
        val decryptedSlice = AesVaultCipher.readDecryptedRange(
            file = encryptedFile,
            plaintextOffset = position,
            length = toRead,
            volumeKey = volumeKey
        )
        if (decryptedSlice.isEmpty()) return -1
        System.arraycopy(decryptedSlice, 0, buffer, offset, decryptedSlice.size)
        return decryptedSlice.size
    }

    override fun getSize(): Long = estimatedPlainLength

    override fun close() {}
}

/**
 * 1. Full-screen Photo Viewer with Swipe (HorizontalPager), Pinch-to-Zoom, and Favorite Toggle.
 *    Decrypts photos strictly in memory using the volume's 256-bit key.
 */
@Composable
fun VaultPhotoViewerDialog(
    photos: List<VaultEntry>,
    initialIndex: Int,
    resolveVolumeKey: (String) -> SecretKey = { AesVaultCipher.getMasterKey() },
    favoritePaths: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    onShowDetails: (VaultEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
        pageCount = { photos.size }
    )
    val currentEntry = photos.getOrNull(pagerState.currentPage) ?: return
    val isFav = favoritePaths.contains(currentEntry.path)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090C))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photoEntry = photos[page]
                var imageBitmap by remember(photoEntry.path) { mutableStateOf<ImageBitmap?>(null) }
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offset by remember(page) { mutableStateOf(Offset.Zero) }

                LaunchedEffect(photoEntry.path) {
                    imageBitmap = withContext(Dispatchers.IO) {
                        try {
                            val key = resolveVolumeKey(photoEntry.sourcePackage)
                            val bytes = AesVaultCipher.decryptToMemory(photoEntry.file, key)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
                                offset = if (newScale > 1f) {
                                    Offset(
                                        x = (offset.x + pan.x).coerceIn(-600f, 600f),
                                        y = (offset.y + pan.y).coerceIn(-600f, 600f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        }
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap!!,
                            contentDescription = photoEntry.cleanName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    } else {
                        Text(
                            text = "Decrypting photo in memory...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontFamily = JakartaFontFamily,
                            fontSize = 14.sp
                        )
                    }

                    if (scale > 1.05f) {
                        IconButton(
                            onClick = {
                                scale = 1f
                                offset = Offset.Zero
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Rounded.ZoomOutMap, contentDescription = "Reset Zoom", tint = Color.White)
                        }
                    }
                }
            }

            // Top Overlay Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentEntry.cleanName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} of ${photos.size} • ${currentEntry.sourceAppName}",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily
                    )
                }

                Row {
                    IconButton(onClick = { onToggleFavorite(currentEntry.path) }) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFFF43F5E) else Color.White
                        )
                    }
                    IconButton(onClick = { onShowDetails(currentEntry) }) {
                        Icon(Icons.Rounded.Info, contentDescription = "File Details", tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 2. Built-in Video Player with Seeking
 *    Uses 4KB-chunked streaming decryption into a short-lived private cache stream wiped on close.
 */
@Composable
fun VaultVideoPlayerDialog(
    entry: VaultEntry,
    volumeKey: SecretKey = AesVaultCipher.getMasterKey(),
    onShowDetails: (VaultEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val ephemeralFile = remember(entry.path) {
        try {
            AesVaultCipher.createEphemeralPlaybackFile(context, entry.file, volumeKey)
        } catch (e: Exception) {
            playbackError = e.message
            null
        }
    }

    DisposableEffect(entry.path) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
            AesVaultCipher.wipeEphemeralCache(context)
        }
    }

    LaunchedEffect(isPlaying, videoViewRef) {
        while (isPlaying && videoViewRef != null) {
            try {
                currentPositionMs = videoViewRef?.currentPosition ?: 0
                val dur = videoViewRef?.duration ?: 1
                if (dur > 0) durationMs = dur
            } catch (_: Exception) {}
            delay(250L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090C)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.cleanName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "AES256_GCM_HKDF_4KB streaming playback",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        fontFamily = JakartaFontFamily
                    )
                }
                IconButton(onClick = { onShowDetails(entry) }) {
                    Icon(Icons.Rounded.Info, contentDescription = "Details", tint = Color.White)
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (ephemeralFile != null && playbackError == null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                videoViewRef = this
                                setVideoPath(ephemeralFile.absolutePath)
                                setOnPreparedListener { mp ->
                                    durationMs = mp.duration.coerceAtLeast(1)
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    currentPositionMs = durationMs
                                }
                                setOnErrorListener { _, _, _ ->
                                    playbackError = "Unable to decode video stream"
                                    true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = playbackError ?: "Preparing encrypted video stream...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = JakartaFontFamily,
                        fontSize = 14.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF12161E))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Slider(
                    value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { newPos ->
                        currentPositionMs = newPos.toInt()
                        videoViewRef?.seekTo(currentPositionMs)
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMs(currentPositionMs),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = {
                            val vv = videoViewRef ?: return@IconButton
                            if (vv.isPlaying) {
                                vv.pause()
                                isPlaying = false
                            } else {
                                vv.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = formatDurationMs(durationMs),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * 3. Built-in Audio Player with Seeking (uses EncryptedChunkMediaDataSource / streaming playback)
 */
@Composable
fun VaultAudioPlayerDialog(
    entry: VaultEntry,
    volumeKey: SecretKey = AesVaultCipher.getMasterKey(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(1) }

    DisposableEffect(entry.path) {
        val mp = MediaPlayer()
        try {
            if (AesVaultCipher.getContainerVersion(entry.file) == 2) {
                mp.setDataSource(EncryptedChunkMediaDataSource(entry.file, volumeKey))
            } else {
                val tempFile = AesVaultCipher.createEphemeralPlaybackFile(context, entry.file, volumeKey)
                mp.setDataSource(tempFile.absolutePath)
            }
            mp.prepare()
            durationMs = mp.duration.coerceAtLeast(1)
            mp.start()
            isPlaying = true
            mediaPlayer = mp
        } catch (_: Exception) {}

        onDispose {
            try {
                mp.stop()
                mp.release()
            } catch (_: Exception) {}
            AesVaultCipher.wipeEphemeralCache(context)
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying && mediaPlayer != null) {
            try {
                currentPosMs = mediaPlayer?.currentPosition ?: 0
            } catch (_: Exception) {}
            delay(250L)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Encrypted Audio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FrauncesFontFamily,
                        color = colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AudioFile,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = entry.cleanName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = entry.sourceAppName,
                    fontSize = 13.sp,
                    fontFamily = JakartaFontFamily,
                    color = colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Slider(
                    value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { pos ->
                        currentPosMs = pos.toInt()
                        mediaPlayer?.seekTo(currentPosMs)
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(currentPosMs),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDurationMs(durationMs),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                IconButton(
                    onClick = {
                        val mp = mediaPlayer ?: return@IconButton
                        if (mp.isPlaying) {
                            mp.pause()
                            isPlaying = false
                        } else {
                            mp.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * 4. Built-in PDF Viewer using Android's PdfRenderer
 */
@Composable
fun VaultPdfViewerDialog(
    entry: VaultEntry,
    volumeKey: SecretKey = AesVaultCipher.getMasterKey(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var renderedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var descriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(entry.path) {
        val tempFile = try {
            AesVaultCipher.createEphemeralPlaybackFile(context, entry.file, volumeKey)
        } catch (_: Exception) {
            null
        }
        try {
            if (tempFile != null) {
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                descriptor = pfd
                pdfRenderer = renderer
                pageCount = renderer.pageCount.coerceAtLeast(1)
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                pdfRenderer?.close()
                descriptor?.close()
            } catch (_: Exception) {}
            AesVaultCipher.wipeEphemeralCache(context)
        }
    }

    LaunchedEffect(pdfRenderer, currentPageIndex) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        renderedBitmap = withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(currentPageIndex)
                val bmp = Bitmap.createBitmap(
                    (page.width * 2).coerceAtLeast(400),
                    (page.height * 2).coerceAtLeast(600),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.cleanName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of $pageCount",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(48.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    if (renderedBitmap != null) {
                        Image(
                            bitmap = renderedBitmap!!,
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text(
                            text = "Rendering encrypted PDF page...",
                            color = colorScheme.onSurfaceVariant,
                            fontFamily = JakartaFontFamily
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surface)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex -= 1 },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = "Previous Page")
                    }

                    Text(
                        text = "${currentPageIndex + 1} / $pageCount",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface
                    )

                    IconButton(
                        onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex += 1 },
                        enabled = currentPageIndex < pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = "Next Page")
                    }
                }
            }
        }
    }
}

/**
 * 5. Built-in Text / Document Viewer (decrypts strictly in memory)
 */
@Composable
fun VaultTextViewerDialog(
    entry: VaultEntry,
    volumeKey: SecretKey = AesVaultCipher.getMasterKey(),
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var textContent by remember(entry.path) { mutableStateOf("Decrypting in memory...") }

    LaunchedEffect(entry.path) {
        textContent = withContext(Dispatchers.IO) {
            try {
                val bytes = AesVaultCipher.decryptToMemory(entry.file, volumeKey)
                String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                "Could not decode text stream: ${e.message}"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = entry.cleanName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Decrypted in memory • AES256_GCM_HKDF_4KB",
                                fontSize = 11.sp,
                                fontFamily = JakartaFontFamily,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceVariant)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = textContent,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontFamily = JakartaFontFamily,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
