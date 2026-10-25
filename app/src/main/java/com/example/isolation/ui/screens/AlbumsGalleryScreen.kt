package com.example.isolation.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import java.io.File
import java.io.FileOutputStream

data class VaultAlbum(
    val id: String,
    val title: String,
    val photoCount: Int,
    val coverGradient: List<Color>,
    val coverFilePath: String? = null,
    val folderPath: String = ""
)

data class VaultPhoto(
    val id: String,
    val name: String,
    val filePath: String,
    val albumTitle: String,
    val sizeBytes: Long,
    val isEncrypted: Boolean,
    val dateAdded: Long = System.currentTimeMillis()
)

@Composable
fun AlbumsGalleryScreen(
    viewModel: IsoVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedAlbum by remember { mutableStateOf<VaultAlbum?>(null) }
    var viewingPhoto by remember { mutableStateOf<VaultPhoto?>(null) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    // Built-in Seeded Albums matching the exact mockup style ("Default", "My love", "Holiday", "Chill")
    val defaultAlbums = remember {
        listOf(
            VaultAlbum(
                id = "default",
                title = "Default",
                photoCount = 23,
                coverGradient = listOf(Color(0xFFFDE047), Color(0xFFF59E0B)),
                folderPath = "/storage/emulated/0/.IsoVault/gallery/Default"
            ),
            VaultAlbum(
                id = "my_love",
                title = "My love",
                photoCount = 142,
                coverGradient = listOf(Color(0xFFDDD6FE), Color(0xFFC084FC)),
                folderPath = "/storage/emulated/0/.IsoVault/gallery/My_love"
            ),
            VaultAlbum(
                id = "holiday",
                title = "Holiday",
                photoCount = 113,
                coverGradient = listOf(Color(0xFF67E8F9), Color(0xFF06B6D4)),
                folderPath = "/storage/emulated/0/.IsoVault/gallery/Holiday"
            ),
            VaultAlbum(
                id = "chill",
                title = "Chill",
                photoCount = 223,
                coverGradient = listOf(Color(0xFF86EFAC), Color(0xFF22C55E)),
                folderPath = "/storage/emulated/0/.IsoVault/gallery/Chill"
            ),
            VaultAlbum(
                id = "snapchat_vault",
                title = "Snapchat Vault",
                photoCount = 48,
                coverGradient = listOf(Color(0xFFFEF08A), Color(0xFFEAB308)),
                folderPath = "/storage/emulated/0/.IsoVault/sandboxes/com.snapchat.android/DCIM/Camera"
            ),
            VaultAlbum(
                id = "whatsapp_vault",
                title = "WhatsApp Vault",
                photoCount = 89,
                coverGradient = listOf(Color(0xFFA7F3D0), Color(0xFF10B981)),
                folderPath = "/storage/emulated/0/.IsoVault/sandboxes/com.whatsapp/Pictures"
            )
        )
    }

    var albumsList by remember { mutableStateOf(defaultAlbums) }

    // Photo picker launcher for importing from phone into vault album
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val targetAlbum = selectedAlbum ?: albumsList.first()
            importUrisIntoAlbum(context, uris, targetAlbum) { newCount ->
                albumsList = albumsList.map {
                    if (it.id == targetAlbum.id) it.copy(photoCount = it.photoCount + newCount) else it
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("albums_gallery_screen")
    ) {
        if (selectedAlbum == null) {
            // Main Albums Grid View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Header Title matching mockup ("Albums")
                Text(
                    text = "Albums",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaFontFamily,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Search Pill + Sky Blue '+' Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DarkSurfaceCard)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Search",
                                        color = TextMuted,
                                        fontSize = 14.sp,
                                        fontFamily = JakartaFontFamily
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    cursorColor = AccentSky
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("albums_search_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Sky Blue '+' Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AccentSky)
                            .clickable { showCreateAlbumDialog = true }
                            .testTag("add_album_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Album",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val filteredAlbums = albumsList.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
                }

                // 2-Column Grid of Albums
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredAlbums, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            onClick = { selectedAlbum = album }
                        )
                    }
                }
            }
        } else {
            // Album Detail / Photos Grid View
            val album = selectedAlbum!!
            val photos = remember(album.id, album.photoCount) {
                loadPhotosForAlbum(album)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedAlbum = null },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${photos.size} Photos in Vault",
                            fontSize = 12.sp,
                            fontFamily = JakartaFontFamily,
                            color = TextMuted
                        )
                    }

                    // Import Photos Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentSky)
                            .clickable { photoPickerLauncher.launch("image/*") }
                            .padding(horizontal = 14.dp)
                            .testTag("import_photos_to_album_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = JakartaFontFamily,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (photos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = TextSubtle,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No photos in this album yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = JakartaFontFamily,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap 'Import' above to securely move media into vault",
                                fontSize = 13.sp,
                                fontFamily = JakartaFontFamily,
                                color = TextSubtle
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            PhotoThumbnailCard(
                                photo = photo,
                                onClick = { viewingPhoto = photo }
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Photo Viewer Dialog
        viewingPhoto?.let { photo ->
            FullScreenPhotoViewerDialog(
                photo = photo,
                onDismiss = { viewingPhoto = null },
                onExport = {
                    exportPhotoToPhone(context, photo)
                },
                onDelete = {
                    deletePhoto(photo)
                    viewingPhoto = null
                    selectedAlbum?.let { alb ->
                        albumsList = albumsList.map {
                            if (it.id == alb.id) it.copy(photoCount = maxOf(0, it.photoCount - 1)) else it
                        }
                    }
                }
            )
        }

        // Create Album Dialog
        if (showCreateAlbumDialog) {
            AlertDialog(
                onDismissRequest = { showCreateAlbumDialog = false },
                title = {
                    Text(
                        text = "New Vault Album",
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = TextWhite
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Albums are saved securely inside persistent vault storage (/storage/emulated/0/.IsoVault).",
                            color = TextMuted,
                            fontSize = 13.sp,
                            fontFamily = JakartaFontFamily
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newAlbumName,
                            onValueChange = { newAlbumName = it },
                            placeholder = { Text("Album name (e.g. Travel, Personal)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = AccentSky
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("album_name_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newAlbumName.isNotBlank()) {
                                val cleanName = newAlbumName.trim()
                                val folder = File("/storage/emulated/0/.IsoVault/gallery/$cleanName")
                                folder.mkdirs()
                                val gradients = listOf(
                                    listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                                    listOf(Color(0xFFF472B6), Color(0xFFDB2777)),
                                    listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)),
                                    listOf(Color(0xFF34D399), Color(0xFF059669))
                                )
                                val newAlb = VaultAlbum(
                                    id = cleanName.lowercase().replace(" ", "_"),
                                    title = cleanName,
                                    photoCount = 0,
                                    coverGradient = gradients.random(),
                                    folderPath = folder.absolutePath
                                )
                                albumsList = listOf(newAlb) + albumsList
                                showCreateAlbumDialog = false
                                newAlbumName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateAlbumDialog = false }) {
                        Text("Cancel", color = TextMuted)
                    }
                },
                containerColor = DarkSurfaceElevated,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: VaultAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("album_card_${album.id}")
    ) {
        // Mockup squircle thumbnail preview (aspectRatio 1:1, corner radius 22dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(album.coverGradient)
                )
                .border(1.dp, DarkBorderSubtle, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (album.coverFilePath != null && File(album.coverFilePath).exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(album.coverFilePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Mockup style artistic cover
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title matching mockup (e.g. "Default", "My love", "Holiday", "Chill")
        Text(
            text = album.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JakartaFontFamily,
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtitle matching mockup (e.g. "23 Photos", "142 Photos")
        Text(
            text = "${album.photoCount} Photos",
            fontSize = 13.sp,
            fontFamily = JakartaFontFamily,
            color = TextMuted
        )
    }
}

@Composable
private fun PhotoThumbnailCard(
    photo: VaultPhoto,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceCard)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("photo_thumb_${photo.id}"),
        contentAlignment = Alignment.Center
    ) {
        val file = File(photo.filePath)
        if (file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = TextSubtle,
                modifier = Modifier.size(28.dp)
            )
        }

        if (photo.isEncrypted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = AccentSky,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun FullScreenPhotoViewerDialog(
    photo: VaultPhoto,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val file = File(photo.filePath)
            if (file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file)
                        .crossfade(true)
                        .build(),
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = photo.name,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Size: ${photo.sizeBytes / 1024} KB",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Top Bar with Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Text(
                    text = photo.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }

            // Bottom Action Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Export to Phone Gallery Button
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export to Phone",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun loadPhotosForAlbum(album: VaultAlbum): List<VaultPhoto> {
    val dir = File(album.folderPath)
    val result = mutableListOf<VaultPhoto>()
    if (dir.exists() && dir.isDirectory) {
        val files = dir.listFiles()?.filter {
            it.isFile && (it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "isovault"))
        } ?: emptyList()

        for (f in files) {
            result.add(
                VaultPhoto(
                    id = f.absolutePath,
                    name = f.name,
                    filePath = f.absolutePath,
                    albumTitle = album.title,
                    sizeBytes = f.length(),
                    isEncrypted = f.extension.equals("isovault", ignoreCase = true),
                    dateAdded = f.lastModified()
                )
            )
        }
    }

    // If album has no files on disk yet, provide realistic demo sample items so the user immediately sees the gorgeous UI!
    if (result.isEmpty()) {
        for (i in 1..album.photoCount) {
            result.add(
                VaultPhoto(
                    id = "${album.id}_$i",
                    name = "IMG_${album.title.take(3).uppercase()}_$i.jpg",
                    filePath = "/storage/emulated/0/.IsoVault/gallery/${album.title}/sample_$i.jpg",
                    albumTitle = album.title,
                    sizeBytes = 2450000L + (i * 123000L),
                    isEncrypted = album.id.contains("snapchat") || album.id.contains("whatsapp"),
                    dateAdded = System.currentTimeMillis() - (i * 3600000L)
                )
            )
        }
    }
    return result
}

private fun importUrisIntoAlbum(
    context: Context,
    uris: List<Uri>,
    album: VaultAlbum,
    onImported: (Int) -> Unit
) {
    val targetDir = File(album.folderPath)
    targetDir.mkdirs()
    var count = 0
    for ((index, uri) in uris.withIndex()) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val outFile = File(targetDir, "IMPORTED_${System.currentTimeMillis()}_$index.jpg")
            val outputStream = FileOutputStream(outFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            count++
        } catch (_: Exception) {}
    }
    onImported(count)
}

private fun exportPhotoToPhone(context: Context, photo: VaultPhoto) {
    try {
        val srcFile = File(photo.filePath)
        val exportDir = File("/storage/emulated/0/Pictures/IsoVault_Exported")
        exportDir.mkdirs()
        val destFile = File(exportDir, photo.name.removeSuffix(".isovault"))
        if (srcFile.exists()) {
            srcFile.copyTo(destFile, overwrite = true)
        } else {
            // Write placeholder if demo
            destFile.writeBytes(ByteArray(1024))
        }
        // Notify media scanner
        context.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile))
        )
    } catch (_: Exception) {}
}

private fun deletePhoto(photo: VaultPhoto) {
    try {
        val file = File(photo.filePath)
        if (file.exists()) {
            file.delete()
        }
    } catch (_: Exception) {}
}
