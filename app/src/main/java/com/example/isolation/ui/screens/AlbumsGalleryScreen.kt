package com.example.isolation.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.mount.MountManager
import com.example.isolation.ui.components.BrandSquircleIcon
import com.example.isolation.ui.components.formatBytes
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentLavender
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSubtle
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RealVaultFolder(
    val id: String,
    val title: String,
    val subtitle: String,
    val folderPath: String,
    val mediaFiles: List<RealVaultMediaFile>,
    val packageName: String? = null
)

data class RealVaultMediaFile(
    val file: File,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isEncrypted: Boolean,
    val isVideo: Boolean
)

private val MEDIA_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic",
    "mp4", "mkv", "mov", "webm", "3gp", "isovault"
)

@Composable
fun AlbumsGalleryScreen(
    viewModel: IsoVaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configs by viewModel.configs.collectAsState()
    val isPanicModeActive by viewModel.isPanicModeActive.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var viewingMedia by remember { mutableStateOf<RealVaultMediaFile?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var encryptOnImport by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var vaultFolders by remember { mutableStateOf<List<RealVaultFolder>>(emptyList()) }

    // Scan real filesystem directories on launch & refresh
    LaunchedEffect(configs, isPanicModeActive, refreshTrigger) {
        if (isPanicModeActive) {
            vaultFolders = emptyList()
        } else {
            val scanned = withContext(Dispatchers.IO) {
                scanActualVaultMediaFolders(context, configs.map { it.packageName to it.appName })
            }
            vaultFolders = scanned
        }
    }

    val selectedFolder = vaultFolders.find { it.id == selectedFolderId }

    // Real Android Photo/Video picker to import media into the currently selected vault folder (or main Vault Gallery)
    val mediaImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val targetDirPath = selectedFolder?.folderPath
                ?: "${MountManager.PERSISTENT_VAULT_ROOT}/gallery/Imported"
            coroutineScope.launch(Dispatchers.IO) {
                val count = importRealMediaUrisToVault(
                    context = context,
                    uris = uris,
                    targetDirPath = targetDirPath,
                    encryptWithAes = encryptOnImport
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Imported $count media file(s) into $targetDirPath",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshTrigger++
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
        if (selectedFolder == null) {
            // Main Vault Gallery View (Real Sandbox & Vault Folders)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isPanicModeActive) "Vault Gallery (Cloaked)" else "Vault Gallery",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite
                        )
                        Text(
                            text = "Real media inside ${MountManager.PERSISTENT_VAULT_ROOT}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPanicModeActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AccentCoral.copy(alpha = 0.2f))
                                    .clickable { viewModel.exitPanicMode() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Exit Panic Mode",
                                    color = AccentCoral,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { refreshTrigger++ },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Disk",
                                tint = AccentSky,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Pill + Import Media + Create Folder
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
                                        text = "Search vault folders...",
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

                    Spacer(modifier = Modifier.width(10.dp))

                    // Import Media from Phone
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AccentSky)
                            .clickable { mediaImportLauncher.launch("*/*") }
                            .testTag("import_media_main_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Import Media",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // New Vault Folder
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceCard)
                            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(16.dp))
                            .clickable { showCreateFolderDialog = true }
                            .testTag("add_album_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = AccentMint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val filteredFolders = vaultFolders.filter {
                    searchQuery.isBlank() ||
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.folderPath.contains(searchQuery, ignoreCase = true)
                }

                if (filteredFolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isPanicModeActive) Icons.Default.VisibilityOff else Icons.Default.Image,
                                contentDescription = null,
                                tint = TextSubtle,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isPanicModeActive) "Vault Cloaked (Panic Mode)" else "No Vault Media Found Yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isPanicModeActive) "Real sandbox media is hidden." else "Tap the blue import button to add photos/videos, or save media inside an isolated app.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFolders, key = { it.id }) { folder ->
                            RealVaultFolderCard(
                                folder = folder,
                                onClick = { selectedFolderId = folder.id }
                            )
                        }
                    }
                }
            }
        } else {
            // Inside a Real Vault Folder -> Show Actual Media Files on Disk
            val mediaFiles = selectedFolder.mediaFiles

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
                        onClick = { selectedFolderId = null },
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
                            text = selectedFolder.title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JakartaFontFamily,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedFolder.folderPath.removePrefix("/storage/emulated/0"),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Import Media Into This Specific Sandbox/Vault Folder
                    Button(
                        onClick = { mediaImportLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("import_photos_to_album_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import Here",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Toggle whether imported files should be encrypted with AES-256 (.isovault) or plain sandbox media
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceCard)
                        .clickable { encryptOnImport = !encryptOnImport }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = encryptOnImport,
                        onCheckedChange = { encryptOnImport = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentSky)
                    )
                    Column {
                        Text(
                            text = "Encrypt imported files with AES-256-GCM (.isovault)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                        Text(
                            text = if (encryptOnImport) "Files will be encrypted on disk" else "Saved as standard media so isolated app pickers can read them directly",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (mediaFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = TextSubtle,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "0 Media Files on Disk",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Path: ${selectedFolder.folderPath}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSubtle
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { mediaImportLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Media from Phone", color = Color.White, fontWeight = FontWeight.Bold)
                            }
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
                        items(mediaFiles, key = { it.path }) { media ->
                            RealMediaThumbnailTile(
                                media = media,
                                onClick = { viewingMedia = media }
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Media Viewer Dialog (Supports both standard & AES-256 encrypted .isovault images!)
        viewingMedia?.let { media ->
            RealMediaViewerDialog(
                media = media,
                onDismiss = { viewingMedia = null },
                onExportToPhone = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val exportedPath = exportRealMediaToPublicGallery(context, media)
                        withContext(Dispatchers.Main) {
                            if (exportedPath != null) {
                                Toast.makeText(context, "Exported to $exportedPath", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onToggleEncryption = {
                    coroutineScope.launch(Dispatchers.IO) {
                        toggleFileEncryptionOnDisk(media)
                        withContext(Dispatchers.Main) {
                            viewingMedia = null
                            refreshTrigger++
                        }
                    }
                },
                onDelete = {
                    coroutineScope.launch(Dispatchers.IO) {
                        if (!media.file.delete() && MountManager.isRootAvailableCached == true) {
                            MountManager.deleteViaRoot(media.path)
                        }
                        withContext(Dispatchers.Main) {
                            viewingMedia = null
                            refreshTrigger++
                            Toast.makeText(context, "Deleted ${media.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Create New Real Folder Dialog
        if (showCreateFolderDialog) {
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = {
                    Text(
                        text = "Create Vault Folder",
                        fontWeight = FontWeight.Bold,
                        fontFamily = JakartaFontFamily,
                        color = TextWhite
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Creates a real directory inside ${MountManager.PERSISTENT_VAULT_ROOT}/gallery/.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            placeholder = { Text("Folder name (e.g. Private_Camera)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                val clean = newFolderName.trim()
                                val dir = File("${MountManager.PERSISTENT_VAULT_ROOT}/gallery/$clean")
                                if (!dir.mkdirs() && MountManager.isRootAvailableCached == true) {
                                    MountManager.runRootCommand("mkdir -p \"${dir.absolutePath}\" && chmod 777 \"${dir.absolutePath}\"")
                                }
                                showCreateFolderDialog = false
                                newFolderName = ""
                                refreshTrigger++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
                    ) {
                        Text("Create Folder", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFolderDialog = false }) {
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
private fun RealVaultFolderCard(
    folder: RealVaultFolder,
    onClick: () -> Unit
) {
    val firstMedia = folder.mediaFiles.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("album_card_${folder.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkSurfaceCard)
                .border(1.dp, DarkBorderSubtle, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (firstMedia != null && !firstMedia.isEncrypted) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(firstMedia.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = folder.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    BrandSquircleIcon(
                        name = folder.title,
                        size = 52.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (folder.mediaFiles.isEmpty()) "Empty Sandbox" else "${folder.mediaFiles.size} Files",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (folder.mediaFiles.isEmpty()) TextSubtle else AccentMint
                    )
                }
            }

            // Count badge at top right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${folder.mediaFiles.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = folder.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JakartaFontFamily,
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = folder.subtitle,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RealMediaThumbnailTile(
    media: RealVaultMediaFile,
    onClick: () -> Unit
) {
    // If file is .isovault encrypted image, decrypt thumbnail in background
    val decryptedBitmap by produceState<ImageBitmap?>(initialValue = null, media.path) {
        if (media.isEncrypted && media.file.exists()) {
            value = withContext(Dispatchers.IO) {
                try {
                    val plain = AesVaultCipher.decrypt(media.file.readBytes())
                    BitmapFactory.decodeByteArray(plain, 0, plain.size)?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceCard)
            .border(1.dp, DarkBorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            decryptedBitmap != null -> {
                Image(
                    bitmap = decryptedBitmap!!,
                    contentDescription = media.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !media.isEncrypted && media.file.exists() -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.file)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            media.isEncrypted -> Icons.Default.Lock
                            media.isVideo -> Icons.Default.VideoFile
                            else -> Icons.Default.Image
                        },
                        contentDescription = null,
                        tint = if (media.isEncrypted) AccentLavender else AccentSky,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = media.name,
                        fontSize = 9.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Size & Encryption overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBytes(media.sizeBytes),
                fontSize = 9.sp,
                color = Color.White
            )
            if (media.isEncrypted) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "AES-256",
                    tint = AccentLavender,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun RealMediaViewerDialog(
    media: RealVaultMediaFile,
    onDismiss: () -> Unit,
    onExportToPhone: () -> Unit,
    onToggleEncryption: () -> Unit,
    onDelete: () -> Unit
) {
    val decryptedBitmap by produceState<ImageBitmap?>(initialValue = null, media.path) {
        if (media.isEncrypted && media.file.exists()) {
            value = withContext(Dispatchers.IO) {
                try {
                    val plain = AesVaultCipher.decrypt(media.file.readBytes())
                    BitmapFactory.decodeByteArray(plain, 0, plain.size)?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when {
                decryptedBitmap != null -> {
                    Image(
                        bitmap = decryptedBitmap!!,
                        contentDescription = media.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !media.isEncrypted && media.file.exists() -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(media.file)
                            .crossfade(true)
                            .build(),
                        contentDescription = media.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (media.isEncrypted) Icons.Default.Lock else Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = AccentSky,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = media.name,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = media.path,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(top = 40.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = media.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val dateStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(media.lastModified))
                    Text(
                        text = "${formatBytes(media.sizeBytes)} • $dateStr",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentCoral)
                }
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onToggleEncryption,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (media.isEncrypted) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        tint = AccentLavender,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (media.isEncrypted) "Decrypt in Vault" else "Encrypt (AES)",
                        fontSize = 12.sp,
                        color = TextWhite
                    )
                }

                Button(
                    onClick = onExportToPhone,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export to Phone",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Scans the real device storage for all Vault & Sandbox directories and collects real media files.
 */
private fun scanActualVaultMediaFolders(
    context: Context,
    configuredApps: List<Pair<String, String>>
): List<RealVaultFolder> {
    MountManager.ensurePersistentVaultRootPermissions()
    val folders = mutableListOf<RealVaultFolder>()

    // 1. All Vault Media combined folder
    val allRoot = File(MountManager.PERSISTENT_VAULT_ROOT)
    val allFiles = collectMediaFilesRecursively(allRoot)
    folders.add(
        RealVaultFolder(
            id = "all_vault_media",
            title = "All Vault Media",
            subtitle = "/.IsoVault (${allFiles.size} files)",
            folderPath = "${MountManager.PERSISTENT_VAULT_ROOT}/gallery/Imported",
            mediaFiles = allFiles
        )
    )

    // 2. Imported / Custom Gallery subdirectories inside /storage/emulated/0/.IsoVault/gallery/
    val galleryRoot = File("${MountManager.PERSISTENT_VAULT_ROOT}/gallery")
    val importedDir = File(galleryRoot, "Imported")
    if (!importedDir.exists()) importedDir.mkdirs()

    val subGalleryDirs = MountManager.listFilesWithRootFallback(galleryRoot.absolutePath)
        .filter { it.isDirectory }
    for (sub in subGalleryDirs) {
        val files = collectMediaFilesRecursively(sub)
        folders.add(
            RealVaultFolder(
                id = "gallery_${sub.name}",
                title = sub.name,
                subtitle = "/.IsoVault/gallery/${sub.name}",
                folderPath = sub.absolutePath,
                mediaFiles = files
            )
        )
    }

    // 3. Each Configured App's Persistent Sandbox (/storage/emulated/0/.IsoVault/sandboxes/<pkg>)
    for ((pkg, appName) in configuredApps) {
        val sandboxDir = File("${MountManager.PERSISTENT_VAULT_ROOT}/sandboxes/$pkg")
        val cameraDir = File(sandboxDir, "DCIM/Camera")
        if (!cameraDir.exists()) cameraDir.mkdirs()
        val files = collectMediaFilesRecursively(sandboxDir)
        folders.add(
            RealVaultFolder(
                id = "sandbox_$pkg",
                title = "$appName Sandbox",
                subtitle = "/sandboxes/$pkg/DCIM",
                folderPath = cameraDir.absolutePath,
                mediaFiles = files,
                packageName = pkg
            )
        )
    }

    // 4. Also scan any other sandbox folders discovered in /storage/emulated/0/.IsoVault/sandboxes/
    val configuredPkgSet = configuredApps.map { it.first }.toSet()
    val sandboxesRoot = File("${MountManager.PERSISTENT_VAULT_ROOT}/sandboxes")
    val extraSandboxes = MountManager.listFilesWithRootFallback(sandboxesRoot.absolutePath)
        .filter { it.isDirectory && it.name !in configuredPkgSet }
    for (extra in extraSandboxes) {
        val files = collectMediaFilesRecursively(extra)
        folders.add(
            RealVaultFolder(
                id = "sandbox_${extra.name}",
                title = extra.name.substringAfterLast('.').replaceFirstChar { it.uppercase() },
                subtitle = "/sandboxes/${extra.name}",
                folderPath = File(extra, "DCIM/Camera").apply { mkdirs() }.absolutePath,
                mediaFiles = files,
                packageName = extra.name
            )
        )
    }

    return folders
}

private fun collectMediaFilesRecursively(root: File, maxDepth: Int = 5): List<RealVaultMediaFile> {
    val results = mutableListOf<RealVaultMediaFile>()
    fun walk(dir: File, depth: Int) {
        if (depth > maxDepth || !dir.exists()) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name == ".nomedia") continue
            if (child.isDirectory) {
                walk(child, depth + 1)
            } else if (child.isFile) {
                val ext = child.extension.lowercase()
                if (ext in MEDIA_EXTENSIONS && child.length() > 0L) {
                    val isEnc = ext == "isovault"
                    val isVid = ext in setOf("mp4", "mkv", "mov", "webm", "3gp")
                    results.add(
                        RealVaultMediaFile(
                            file = child,
                            name = child.name,
                            path = child.absolutePath,
                            sizeBytes = child.length(),
                            lastModified = child.lastModified(),
                            isEncrypted = isEnc,
                            isVideo = isVid
                        )
                    )
                }
            }
        }
    }
    walk(root, 0)
    return results.sortedByDescending { it.lastModified }
}

private fun importRealMediaUrisToVault(
    context: Context,
    uris: List<Uri>,
    targetDirPath: String,
    encryptWithAes: Boolean
): Int {
    val targetDir = File(targetDirPath).apply { mkdirs() }
    var importedCount = 0
    for (uri in uris) {
        val res = com.example.isolation.storage.FileManagerHelper.importFromUri(context, uri, targetDir, encryptWithAes)
        if (res.isSuccess) {
            importedCount++
        }
    }
    return importedCount
}

private fun exportRealMediaToPublicGallery(
    context: Context,
    media: RealVaultMediaFile
): String? {
    val res = com.example.isolation.storage.FileManagerHelper.exportToPublicDownload(
        context = context,
        sourceFile = media.file,
        decryptIfEncrypted = true
    )
    return res.getOrNull()
}

private fun toggleFileEncryptionOnDisk(media: RealVaultMediaFile) {
    try {
        if (media.isEncrypted) {
            val decrypted = AesVaultCipher.decrypt(media.file.readBytes())
            val plainName = media.name.removeSuffix(".isovault")
            val outFile = File(media.file.parentFile, plainName)
            outFile.writeBytes(decrypted)
            media.file.delete()
        } else {
            val encrypted = AesVaultCipher.encrypt(media.file.readBytes())
            val encFile = File(media.file.parentFile, "${media.name}.isovault")
            encFile.writeBytes(encrypted)
            media.file.delete()
        }
    } catch (_: Exception) {}
}
