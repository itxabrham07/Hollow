package com.example.isolation.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.isolation.mount.FileKind
import com.example.isolation.mount.FileSource
import com.example.isolation.mount.MountManager
import com.example.isolation.mount.RemoteEntry
import com.example.isolation.mount.RootFileBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

data class BrowserState(
    val packageName: String? = null,
    val source: FileSource = FileSource.VOLUME,
    val path: String = "",
    val entries: List<RemoteEntry> = emptyList(),
    val loading: Boolean = false,
    /** Set when the volume is encrypted and has to be unlocked before it can be browsed. */
    val locked: Boolean = false,
    /** Why the listing is empty or failed, if there is a reason worth showing. */
    val message: String? = null
)

/**
 * Browses the isolated files of one app at a time (its root volume or its hook sandbox).
 * Plain copies made for previews live in the manager's cache only while they are being decoded.
 */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MAX_THUMB_BYTES = 30L * 1024 * 1024
        private const val MAX_FULL_IMAGE_BYTES = 80L * 1024 * 1024
        private const val MAX_TEXT_BYTES = 1L * 1024 * 1024
        private const val THUMB_SIDE = 256
        private const val FULL_SIDE = 2048
    }

    private val ctx = application
    private val cacheDir = File(application.cacheDir, "gallery")

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val thumbs = LruCache<String, Bitmap>(96)
    private val decodeGate = Semaphore(2)
    private var listJob: Job? = null
    private val generation = AtomicLong(0)

    init {
        // Anything left from a previous run (a crash mid-preview) is plaintext: remove it.
        wipeCache()
    }

    override fun onCleared() {
        wipeCache()
        thumbs.evictAll()
    }

    private fun wipeCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun clearNotice() {
        _notice.value = null
    }

    // ---- Navigation ------------------------------------------------------------------------

    fun open(packageName: String, source: FileSource) {
        thumbs.evictAll()
        _state.value = BrowserState(packageName = packageName, source = source)
        refresh()
    }

    fun openFolder(entry: RemoteEntry) {
        if (!entry.isDir) return
        _state.update { it.copy(path = entry.relPath) }
        refresh()
    }

    fun up() {
        val current = _state.value.path
        if (current.isEmpty()) return
        _state.update { it.copy(path = current.substringBeforeLast('/', "")) }
        refresh()
    }

    fun refresh() {
        val s = _state.value
        val pkg = s.packageName ?: return
        listJob?.cancel()
        val gen = generation.incrementAndGet()

        if (s.source == FileSource.VOLUME && MountManager.isSealed(ctx, pkg)) {
            _state.update {
                it.copy(entries = emptyList(), loading = false, locked = true, message = "This volume is locked (encrypted).")
            }
            return
        }

        _state.update { it.copy(loading = true, locked = false, message = null) }
        listJob = viewModelScope.launch(Dispatchers.IO) {
            val result = RootFileBrowser.list(ctx, pkg, s.source, s.path)
            _state.update {
                // Ignore a result that arrived after a newer refresh or after the user moved on.
                if (generation.get() != gen) it
                else it.copy(
                    entries = result.entries,
                    loading = false,
                    message = result.error ?: if (result.entries.isEmpty()) "Nothing here yet." else null
                )
            }
        }
    }

    // ---- Lock / unlock ---------------------------------------------------------------------

    /** Decrypts the app's sealed volume so it can be browsed. */
    fun unlock() {
        val pkg = _state.value.packageName ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true) }
            val result = MountManager.unsealVolume(ctx, pkg)
            _notice.value = if (result.isSuccess) "Volume unlocked. Lock it again when you are done." else result.output
            refresh()
        }
    }

    /** Stops the app, then encrypts the volume again. */
    fun lock() {
        val pkg = _state.value.packageName ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loading = true) }
            MountManager.detachVolume(ctx, pkg)
            val result = MountManager.sealVolume(ctx, pkg)
            thumbs.evictAll()
            _notice.value = if (result.isSuccess) "Volume locked. The app was stopped." else result.output
            refresh()
        }
    }

    // ---- Previews --------------------------------------------------------------------------

    private fun key(e: RemoteEntry) = "${e.packageName}|${e.source.name}|${e.relPath}|${e.modifiedSec}|${e.size}"

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** A small preview for the grid, or null for anything that is not a reasonably sized image. */
    suspend fun thumbnail(entry: RemoteEntry): Bitmap? = withContext(Dispatchers.IO) {
        if (entry.packageName.isEmpty() || entry.kind != FileKind.IMAGE || entry.size > MAX_THUMB_BYTES) return@withContext null
        val k = key(entry)
        thumbs.get(k)?.let { return@withContext it }

        decodeGate.withPermit {
            thumbs.get(k)?.let { return@withPermit it }
            val (file, _) = RootFileBrowser.copyToCache(
                ctx, entry.packageName, entry.source, entry.relPath, cacheDir, entry.size + 4096
            )
            if (file == null) return@withPermit null
            try {
                decodeSampled(file, THUMB_SIDE)?.also { thumbs.put(k, it) }
            } finally {
                file.delete()
            }
        }
    }

    /** A large version of an image for the viewer. */
    suspend fun fullImage(entry: RemoteEntry): Bitmap? = withContext(Dispatchers.IO) {
        if (entry.packageName.isEmpty() || entry.kind != FileKind.IMAGE || entry.size > MAX_FULL_IMAGE_BYTES) return@withContext null
        decodeGate.withPermit {
            val (file, _) = RootFileBrowser.copyToCache(
                ctx, entry.packageName, entry.source, entry.relPath, cacheDir, entry.size + 4096
            )
            if (file == null) return@withPermit null
            try {
                decodeSampled(file, FULL_SIDE)
            } finally {
                file.delete()
            }
        }
    }

    suspend fun textPreview(entry: RemoteEntry): String? = withContext(Dispatchers.IO) {
        if (entry.packageName.isEmpty() || entry.kind != FileKind.TEXT || entry.size > MAX_TEXT_BYTES) return@withContext null
        val (file, _) = RootFileBrowser.copyToCache(
            ctx, entry.packageName, entry.source, entry.relPath, cacheDir, MAX_TEXT_BYTES + 1
        )
        if (file == null) return@withContext null
        try {
            file.readText(Charsets.UTF_8) // already capped at MAX_TEXT_BYTES + 1 by the copy
        } finally {
            file.delete()
        }
    }

    // ---- Export / delete -------------------------------------------------------------------

    /** Copies the file to a place the user picked. The original stays where it is. */
    fun export(entry: RemoteEntry, destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val error: String? = try {
                val out = ctx.contentResolver.openOutputStream(destination, "w")
                if (out == null) "Could not open the destination." else out.use {
                    RootFileBrowser.copyOut(ctx, entry.packageName, entry.source, entry.relPath, it)
                }
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            if (error != null) {
                // Do not leave a half-written plaintext copy behind.
                try { DocumentsContract.deleteDocument(ctx.contentResolver, destination) } catch (_: Exception) {}
            }
            _notice.value = error ?: "Exported ${entry.name}."
        }
    }

    /** Deletes one file or folder. The owning app is stopped first so it cannot race the delete. */
    fun delete(entry: RemoteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val error = RootFileBrowser.delete(ctx, entry.packageName, entry.source, entry.relPath)
            thumbs.remove(key(entry))
            _notice.value = error ?: "Deleted ${entry.name}."
            refresh()
        }
    }
}
