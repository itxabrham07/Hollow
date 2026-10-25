package com.example.isolation.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.isolation.crypto.AesVaultCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

enum class VaultFileCategory(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    OTHER("Other")
}

enum class VaultSortOption(val label: String) {
    DATE("Date"),
    NAME("Name"),
    SIZE("Size")
}

enum class VaultBrowserMode(val label: String) {
    FILES("Files"),
    GALLERY("Gallery")
}

enum class GallerySubMode(val label: String) {
    ALBUMS("Albums"),
    TIMELINE("Timeline"),
    FAVORITES("Favorites")
}

data class VaultAppBucket(
    val packageName: String,
    val appName: String,
    val rootDir: File,
    val fileCount: Int,
    val totalBytes: Long,
    val isHookActive: Boolean,
    val isSharedVolume: Boolean = false,
    val useSharedSpace: Boolean = false,
    val quotaMb: Int = 0
)

data class VaultEntry(
    val file: File,
    val rawName: String,
    val cleanName: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isEncrypted: Boolean,
    val category: VaultFileCategory,
    val sourceAppName: String,
    val sourcePackage: String,
    val isFolderPinLocked: Boolean = false,
    val isFavorite: Boolean = false
)

data class GalleryAlbumItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverEntry: VaultEntry,
    val entries: List<VaultEntry>,
    val isSharedAlbum: Boolean
)

data class RecycleBinItem(
    val file: File,
    val originalName: String,
    val sourceAppName: String,
    val sourcePackage: String,
    val deletedAtMillis: Long,
    val daysRemaining: Int,
    val sizeBytes: Long
)

object VaultFileScanner {

    private val PHOTO_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "webm", "3gp", "avi")
    private val AUDIO_EXTS = setOf("mp3", "wav", "m4a", "ogg", "flac", "aac")
    private val DOC_EXTS = setOf("pdf", "txt", "md", "json", "xml", "csv", "doc", "docx", "log")

    fun categorizeFileName(cleanName: String): VaultFileCategory {
        val ext = cleanName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in PHOTO_EXTS -> VaultFileCategory.PHOTOS
            in VIDEO_EXTS -> VaultFileCategory.VIDEOS
            in AUDIO_EXTS -> VaultFileCategory.AUDIO
            in DOC_EXTS -> VaultFileCategory.DOCUMENTS
            else -> VaultFileCategory.OTHER
        }
    }

    fun isPdfFile(cleanName: String): Boolean {
        return cleanName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)
    }

    fun isTextFile(cleanName: String): Boolean {
        val ext = cleanName.substringAfterLast('.', "").lowercase()
        return ext in setOf("txt", "md", "json", "xml", "csv", "log", "ini", "cfg")
    }

    fun listEntriesInDirectory(
        directory: File,
        sourceAppName: String,
        sourcePackage: String,
        folderPins: Map<String, String>,
        favorites: Set<String> = emptySet()
    ): List<VaultEntry> {
        if (!directory.exists()) directory.mkdirs()
        val children = directory.listFiles() ?: return emptyList()
        val result = mutableListOf<VaultEntry>()

        for (child in children) {
            if (child.name == ".nomedia" || child.name.startsWith(".seeded")) continue
            val clean = child.name.removeSuffix(".isovault")
            val isDir = child.isDirectory
            val size = if (isDir) {
                child.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.sumOf { it.length() }
            } else {
                child.length()
            }
            val category = if (isDir) VaultFileCategory.OTHER else categorizeFileName(clean)
            val isEncrypted = !isDir && (child.name.endsWith(".isovault", ignoreCase = true) || AesVaultCipher.isEncryptedIsoVaultFile(child))

            result.add(
                VaultEntry(
                    file = child,
                    rawName = child.name,
                    cleanName = clean,
                    path = child.absolutePath,
                    isDirectory = isDir,
                    sizeBytes = size,
                    lastModified = child.lastModified(),
                    isEncrypted = isEncrypted,
                    category = category,
                    sourceAppName = sourceAppName,
                    sourcePackage = sourcePackage,
                    isFolderPinLocked = isDir && folderPins.containsKey(child.absolutePath),
                    isFavorite = favorites.contains(child.absolutePath)
                )
            )
        }
        return result
    }

    /**
     * Collects all files across a list of volume buckets for the combined "All" / "Shared" / per-app view
     * and Gallery view (Albums, Timeline, Favorites).
     */
    fun listAllFilesAcrossBuckets(
        buckets: List<VaultAppBucket>,
        favorites: Set<String>
    ): List<VaultEntry> {
        val all = mutableListOf<VaultEntry>()
        for (bucket in buckets) {
            if (!bucket.rootDir.exists()) continue
            bucket.rootDir.walkTopDown().forEach { f ->
                if (f.isFile && f.name != ".nomedia" && !f.name.startsWith(".seeded")) {
                    val clean = f.name.removeSuffix(".isovault")
                    val cat = categorizeFileName(clean)
                    all.add(
                        VaultEntry(
                            file = f,
                            rawName = f.name,
                            cleanName = clean,
                            path = f.absolutePath,
                            isDirectory = false,
                            sizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            isEncrypted = f.name.endsWith(".isovault", ignoreCase = true) || AesVaultCipher.isEncryptedIsoVaultFile(f),
                            category = cat,
                            sourceAppName = bucket.appName,
                            sourcePackage = bucket.packageName,
                            isFolderPinLocked = false,
                            isFavorite = favorites.contains(f.absolutePath)
                        )
                    )
                }
            }
        }
        return all.sortedByDescending { it.lastModified }
    }

    /**
     * Moves or copies a file/folder between volumes (e.g., between a per-app volume and Shared Space),
     * streaming-re-encrypting files with the destination volume's 256-bit DEK when volumes differ.
     */
    fun transferEntryBetweenVolumes(
        entry: VaultEntry,
        destinationFolder: File,
        sourceKey: SecretKey,
        destKey: SecretKey,
        isMove: Boolean
    ): Boolean {
        return try {
            destinationFolder.mkdirs()
            val target = File(destinationFolder, entry.rawName)
            if (entry.file.canonicalPath == target.canonicalPath) return false
            val sameKey = sourceKey.encoded.contentEquals(destKey.encoded)

            fun transferFile(srcFile: File, dstFile: File) {
                dstFile.parentFile?.mkdirs()
                val isEnc = srcFile.name.endsWith(".isovault", ignoreCase = true) ||
                    AesVaultCipher.isEncryptedIsoVaultFile(srcFile)
                if (isEnc && !sameKey) {
                    AesVaultCipher.newDecryptingInputStream(srcFile, sourceKey).use { plainIn ->
                        dstFile.outputStream().use { cipherOut ->
                            AesVaultCipher.encryptStream(plainIn, cipherOut, destKey)
                        }
                    }
                } else {
                    srcFile.copyTo(dstFile, overwrite = true)
                }
            }

            if (entry.isDirectory) {
                target.mkdirs()
                entry.file.walkTopDown().forEach { child ->
                    val relPath = child.relativeTo(entry.file).path
                    if (relPath.isNotEmpty()) {
                        val dstChild = File(target, relPath)
                        if (child.isDirectory) {
                            dstChild.mkdirs()
                        } else if (child.name != ".nomedia" && !child.name.startsWith(".seeded")) {
                            transferFile(child, dstChild)
                        }
                    }
                }
                if (isMove) entry.file.deleteRecursively()
            } else {
                transferFile(entry.file, target)
                if (isMove) entry.file.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Moves a file or folder into the 30-day Recycle Bin with metadata encoded in filename:
     * `<timestamp>__<package>__<originalFileName>`
     */
    fun moveToRecycleBin(entry: VaultEntry, recycleBinDir: File): Boolean {
        return try {
            if (!recycleBinDir.exists()) recycleBinDir.mkdirs()
            val safePkg = entry.sourcePackage.replace("_", "-")
            val targetName = "${System.currentTimeMillis()}__${safePkg}__${entry.rawName}"
            val dest = File(recycleBinDir, targetName)
            if (entry.file.isDirectory) {
                entry.file.copyRecursively(dest, overwrite = true)
                entry.file.deleteRecursively()
            } else {
                entry.file.copyTo(dest, overwrite = true)
                entry.file.delete()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun listRecycleBinItems(recycleBinDir: File, appNameLookup: Map<String, String>): List<RecycleBinItem> {
        if (!recycleBinDir.exists()) return emptyList()
        val now = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L

        return (recycleBinDir.listFiles() ?: emptyArray()).mapNotNull { file ->
            val parts = file.name.split("__", limit = 3)
            val deletedAt = parts.getOrNull(0)?.toLongOrNull() ?: file.lastModified()
            val pkg = parts.getOrNull(1)?.replace("-", "_") ?: "shared.vault"
            val origName = (parts.getOrNull(2) ?: file.name).removeSuffix(".isovault")
            val elapsed = (now - deletedAt).coerceAtLeast(0L)
            val remainingDays = ((thirtyDaysMs - elapsed) / (24L * 60L * 60L * 1000L)).toInt().coerceIn(0, 30)

            RecycleBinItem(
                file = file,
                originalName = origName,
                sourceAppName = appNameLookup[pkg] ?: "Shared Space",
                sourcePackage = pkg,
                deletedAtMillis = deletedAt,
                daysRemaining = remainingDays,
                sizeBytes = if (file.isDirectory) {
                    file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else {
                    file.length()
                }
            )
        }.sortedByDescending { it.deletedAtMillis }
    }
}

/**
 * Loads and decrypts an image thumbnail strictly in memory using the volume's 256-bit DEK
 * with two-pass bounds calculation so high-resolution photos never spike heap memory.
 */
@Composable
fun rememberDecryptedThumbnail(
    entry: VaultEntry,
    volumeKey: SecretKey = AesVaultCipher.getMasterKey()
): ImageBitmap? {
    var bitmap by remember(entry.path, entry.lastModified) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(entry.path, entry.lastModified) {
        if (entry.category == VaultFileCategory.PHOTOS && !entry.isDirectory) {
            val decoded = withContext(Dispatchers.IO) {
                try {
                    val bytes = AesVaultCipher.decryptToMemory(entry.file, volumeKey)
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                    var sampleSize = 1
                    val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
                    while (maxDim / sampleSize > 320) {
                        sampleSize *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
            bitmap = decoded
        }
    }
    return bitmap
}
