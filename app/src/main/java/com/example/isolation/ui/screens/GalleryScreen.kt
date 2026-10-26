package com.example.isolation.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.mount.FileKind
import com.example.isolation.mount.FileSource
import com.example.isolation.mount.RemoteEntry
import com.example.isolation.ui.viewmodel.GalleryViewModel
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import com.example.ui.theme.CoralError
import com.example.ui.theme.ForestPrimary
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.JakartaFontFamily
import com.example.ui.theme.SageSoft
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmSand
import com.example.ui.theme.WarmStone
import java.text.DateFormat
import java.util.Date

@Composable
fun GalleryScreen(
    viewModel: IsoVaultViewModel,
    gallery: GalleryViewModel = viewModel()
) {
    val configs by viewModel.configs.collectAsState()
    val state by gallery.state.collectAsState()
    val notice by gallery.notice.collectAsState()

    val isolatedApps = configs.filter { it.isIsolated }
    var selected by remember { mutableStateOf<RemoteEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<RemoteEntry?>(null) }
    var exportTarget by remember { mutableStateOf<RemoteEntry?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val entry = exportTarget
        if (uri != null && entry != null) gallery.export(entry, uri)
        exportTarget = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .testTag("gallery_screen")
    ) {
        Text(
            text = "Files inside your isolated apps. Reading them needs root.",
            fontSize = 13.sp,
            color = InkSecondary,
            fontFamily = JakartaFontFamily
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (isolatedApps.isEmpty()) {
            EmptyNote("No isolated apps yet", "Turn on isolation for an app in the Apps tab, then its files show up here.")
            return@Column
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(isolatedApps, key = { it.packageName }) { cfg ->
                val isSelected = state.packageName == cfg.packageName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) ForestPrimary else WarmSand)
                        .clickable { gallery.open(cfg.packageName, state.source) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cfg.appName,
                        color = if (isSelected) Color.White else InkPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JakartaFontFamily
                    )
                }
            }
        }

        val pkg = state.packageName
        if (pkg == null) {
            Spacer(modifier = Modifier.height(16.dp))
            EmptyNote("Pick an app", "Choose an app above to see what it has saved.")
            return@Column
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (source in FileSource.values()) {
                val on = state.source == source
                if (on) {
                    Button(
                        onClick = { gallery.open(pkg, source) },
                        colors = ButtonDefaults.buttonColors(containerColor = SageSoft, contentColor = ForestPrimary),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) { Text(source.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = JakartaFontFamily) }
                } else {
                    OutlinedButton(
                        onClick = { gallery.open(pkg, source) },
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f)
                    ) { Text(source.title, fontSize = 12.sp, color = InkSecondary, fontFamily = JakartaFontFamily) }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { gallery.up() }, enabled = state.path.isNotEmpty()) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
            }
            Text(
                text = "/" + state.path,
                fontSize = 13.sp,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = JakartaFontFamily,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { gallery.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        notice?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarmSand)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(msg, fontSize = 12.sp, color = InkPrimary, fontFamily = JakartaFontFamily, modifier = Modifier.weight(1f))
                TextButton(onClick = { gallery.clearNotice() }) {
                    Text("OK", fontSize = 12.sp, color = ForestPrimary, fontFamily = JakartaFontFamily)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> CircularProgressIndicator(
                    color = ForestPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )

                state.locked -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This volume is locked", fontWeight = FontWeight.SemiBold, fontFamily = FrauncesFontFamily, color = InkPrimary)
                    Text(
                        "Its files are encrypted. Unlock to browse them, and lock it again when you are done.",
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { gallery.unlock() },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(50)
                    ) { Text("Unlock", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold) }
                }

                state.entries.isEmpty() -> Text(
                    text = state.message ?: "Nothing here yet.",
                    fontSize = 13.sp,
                    color = InkSecondary,
                    fontFamily = JakartaFontFamily,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.entries, key = { it.relPath }) { entry ->
                        EntryTile(
                            entry = entry,
                            gallery = gallery,
                            onClick = { if (entry.isDir) gallery.openFolder(entry) else selected = entry }
                        )
                    }
                }
            }
        }

        // A volume that has been unlocked for browsing can be sealed again from here.
        if (state.source == FileSource.VOLUME && !state.locked && !state.loading) {
            OutlinedButton(
                onClick = { gallery.lock() },
                border = androidx.compose.foundation.BorderStroke(1.dp, WarmStone),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp), tint = InkPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lock volume (stops the app)", fontSize = 13.sp, color = InkPrimary, fontFamily = JakartaFontFamily)
            }
        }
    }

    selected?.let { entry ->
        EntryDialog(
            entry = entry,
            gallery = gallery,
            onClose = { selected = null },
            onExport = {
                exportTarget = entry
                exportLauncher.launch(entry.name)
                selected = null
            },
            onDelete = {
                pendingDelete = entry
                selected = null
            }
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = WarmIvory,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete ${entry.name}?", fontFamily = FrauncesFontFamily, color = InkPrimary) },
            text = {
                Text(
                    "This removes it from the app's isolated storage for good, and the app is stopped while it happens. It cannot be undone.",
                    fontFamily = JakartaFontFamily,
                    color = InkSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        gallery.delete(entry)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralError, contentColor = Color.White),
                    shape = RoundedCornerShape(50)
                ) { Text("Delete", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = InkSecondary, fontFamily = JakartaFontFamily)
                }
            }
        )
    }
}

@Composable
private fun EmptyNote(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFamily = FrauncesFontFamily, color = InkPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = InkSecondary, fontFamily = JakartaFontFamily)
    }
}

@Composable
private fun EntryTile(entry: RemoteEntry, gallery: GalleryViewModel, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(WarmIvory)
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(WarmSand)
        ) {
            val thumb by produceState<ImageBitmap?>(null, entry.relPath, entry.modifiedSec) {
                value = if (entry.kind == FileKind.IMAGE) gallery.thumbnail(entry)?.asImageBitmap() else null
            }
            val bitmap = thumb
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = iconFor(entry),
                    contentDescription = null,
                    tint = ForestPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = entry.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = InkPrimary,
            fontFamily = JakartaFontFamily
        )
    }
}

private fun iconFor(entry: RemoteEntry) = when {
    entry.isDir -> Icons.Default.Folder
    entry.kind == FileKind.VIDEO -> Icons.Default.Movie
    entry.kind == FileKind.AUDIO -> Icons.Default.MusicNote
    entry.kind == FileKind.TEXT -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
private fun EntryDialog(
    entry: RemoteEntry,
    gallery: GalleryViewModel,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val image by produceState<ImageBitmap?>(null, entry.relPath) {
        value = if (entry.kind == FileKind.IMAGE) gallery.fullImage(entry)?.asImageBitmap() else null
    }
    val text by produceState<String?>(null, entry.relPath) {
        value = if (entry.kind == FileKind.TEXT) gallery.textPreview(entry) else null
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = WarmIvory,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FrauncesFontFamily, color = InkPrimary)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val bitmap = image
                val body = text
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = entry.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )

                    body != null -> Text(body, fontSize = 12.sp, color = InkPrimary, fontFamily = JakartaFontFamily)

                    entry.kind == FileKind.IMAGE || entry.kind == FileKind.TEXT -> Text(
                        "Loading preview...",
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )

                    else -> Text(
                        "No preview for this type. Export it to open it in another app.",
                        fontSize = 12.sp,
                        color = InkSecondary,
                        fontFamily = JakartaFontFamily
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = formatBytes(entry.size) + "  -  " +
                        DateFormat.getDateTimeInstance().format(Date(entry.modifiedSec * 1000)),
                    fontSize = 12.sp,
                    color = InkSecondary,
                    fontFamily = JakartaFontFamily
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = ForestPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(50)
            ) { Text("Export", fontFamily = JakartaFontFamily, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = CoralError, fontFamily = JakartaFontFamily) }
                TextButton(onClick = onClose) { Text("Close", color = InkSecondary, fontFamily = JakartaFontFamily) }
            }
        }
    )
}
