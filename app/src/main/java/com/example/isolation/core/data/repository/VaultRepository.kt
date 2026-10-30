package com.example.isolation.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.VaultFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

interface VaultRepository {
    val lockerDir: File
    fun getAllVaultFiles(): Flow<List<VaultFileItem>>
    suspend fun getAllVaultFilesSync(): List<VaultFileItem>
    fun getVaultFilesForPackage(pkg: String): Flow<List<VaultFileItem>>
    fun getTotalEncryptedBytes(): Flow<Long>

    suspend fun addNote(name: String, content: String): HollowResult<VaultFileItem>
    suspend fun importFile(uri: Uri): HollowResult<VaultFileItem>
    suspend fun encryptLocalFile(sourceFile: File, deleteOriginal: Boolean = false): HollowResult<VaultFileItem>
    suspend fun exportFile(item: VaultFileItem, destination: Uri): HollowResult<Unit>
    suspend fun decryptVaultFileToWorkspace(item: VaultFileItem): HollowResult<File>
    suspend fun deleteVaultFile(item: VaultFileItem): HollowResult<Unit>
    suspend fun decryptPreviewText(item: VaultFileItem): HollowResult<Pair<String, String>>
    suspend fun decryptPreviewBitmap(item: VaultFileItem): HollowResult<Pair<String, Bitmap>>
    suspend fun purgeVaultForPackage(packageName: String): HollowResult<Unit>
}

class DefaultVaultRepository(
    private val context: Context,
    private val dao: IsolationDao
) : VaultRepository {

    companion object {
        const val MAX_PREVIEW_BYTES = 2 * 1024 * 1024
        const val MAX_IMAGE_PREVIEW_BYTES = 25 * 1024 * 1024
    }

    private val lockerPackage = context.packageName
    override val lockerDir: File get() = File(context.filesDir, "locker").apply { mkdirs() }

    override fun getAllVaultFiles(): Flow<List<VaultFileItem>> = dao.getAllVaultFiles()

    override suspend fun getAllVaultFilesSync(): List<VaultFileItem> = withContext(Dispatchers.IO) {
        dao.getAllVaultFilesSync()
    }

    override fun getVaultFilesForPackage(pkg: String): Flow<List<VaultFileItem>> =
        dao.getVaultFilesForPackage(pkg)

    override fun getTotalEncryptedBytes(): Flow<Long> = dao.getTotalEncryptedBytes()

    private suspend fun storeInLocker(
        displayName: String,
        mimeType: String,
        write: (java.io.OutputStream) -> Long
    ): VaultFileItem = withContext(Dispatchers.IO) {
        val target = File(lockerDir, "${UUID.randomUUID()}.isovault")
        var plainSize = 0L
        try {
            FileOutputStream(target).use { out -> plainSize = write(out) }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        val item = VaultFileItem(
            packageName = lockerPackage,
            relativePath = displayName,
            physicalPath = target.absolutePath,
            isEncrypted = true,
            originalSizeBytes = plainSize,
            encryptedSizeBytes = target.length(),
            mimeType = mimeType,
            lastModified = System.currentTimeMillis()
        )
        val insertedId = dao.insertVaultFile(item)
        item.copy(id = insertedId)
    }

    override suspend fun addNote(name: String, content: String): HollowResult<VaultFileItem> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to save encrypted note") {
            withContext(Dispatchers.IO) {
                val fileName = name.trim().substringAfterLast('/').ifBlank { "note" }
                    .let { if (it.contains('.')) it else "$it.txt" }
                storeInLocker(fileName, "text/plain") { out ->
                    AesVaultCipher.encryptStream(content.toByteArray(Charsets.UTF_8).inputStream(), out)
                }
            }
        }

    override suspend fun importFile(uri: Uri): HollowResult<VaultFileItem> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to import and encrypt file") {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val queriedName: String? = resolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                val name = queriedName?.takeIf { it.isNotBlank() } ?: "file_${System.currentTimeMillis()}"
                val mime = resolver.getType(uri) ?: inferMimeType(name)
                val input = resolver.openInputStream(uri) ?: throw IllegalStateException("File could not be opened")
                input.use { src ->
                    storeInLocker(name, mime) { out -> AesVaultCipher.encryptStream(src, out) }
                }
            }
        }

    override suspend fun encryptLocalFile(sourceFile: File, deleteOriginal: Boolean): HollowResult<VaultFileItem> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to encrypt local file: ${sourceFile.name}") {
            withContext(Dispatchers.IO) {
                if (!sourceFile.exists() || !sourceFile.isFile) {
                    throw IllegalArgumentException("File not found: ${sourceFile.absolutePath}")
                }
                val name = sourceFile.name
                val mime = inferMimeType(name)
                val result = FileInputStream(sourceFile).use { src ->
                    storeInLocker(name, mime) { out -> AesVaultCipher.encryptStream(src, out) }
                }
                if (deleteOriginal) {
                    sourceFile.delete()
                }
                result
            }
        }

    override suspend fun exportFile(item: VaultFileItem, destination: Uri): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to export ${item.relativePath}") {
            withContext(Dispatchers.IO) {
                val out = context.contentResolver.openOutputStream(destination, "w")
                    ?: throw IllegalStateException("Destination could not be opened")
                out.use { dst ->
                    FileInputStream(File(item.physicalPath)).use { src -> AesVaultCipher.decryptStream(src, dst) }
                }
            }
        }

    override suspend fun decryptVaultFileToWorkspace(item: VaultFileItem): HollowResult<File> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to decrypt to workspace") {
            withContext(Dispatchers.IO) {
                val workspaceExports = File(context.filesDir, "workspace/Decrypted").apply { mkdirs() }
                val cleanName = item.relativePath.substringAfterLast('/').ifBlank { "decrypted_${item.id}" }
                val dest = File(workspaceExports, cleanName)
                FileOutputStream(dest).use { dst ->
                    FileInputStream(File(item.physicalPath)).use { src -> AesVaultCipher.decryptStream(src, dst) }
                }
                dest
            }
        }

    override suspend fun deleteVaultFile(item: VaultFileItem): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to delete vault file ${item.relativePath}") {
            withContext(Dispatchers.IO) {
                File(item.physicalPath).delete()
                dao.deleteVaultFile(item.id)
            }
        }

    override suspend fun decryptPreviewText(item: VaultFileItem): HollowResult<Pair<String, String>> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to decrypt text preview") {
            withContext(Dispatchers.IO) {
                val file = File(item.physicalPath)
                if (!file.exists()) throw IllegalStateException("Locker file does not exist on disk.")
                if (item.originalSizeBytes > MAX_PREVIEW_BYTES) throw IllegalStateException("File too large to preview.")

                val buffer = ByteArrayOutputStream()
                if (item.isEncrypted) {
                    FileInputStream(file).use { AesVaultCipher.decryptStream(it, buffer) }
                } else {
                    file.inputStream().use { it.copyTo(buffer) }
                }
                val raw = buffer.toByteArray()
                val text = if (raw.any { it == 0.toByte() }) {
                    "Binary file (${raw.size} bytes). Cannot preview as text."
                } else {
                    String(raw, Charsets.UTF_8)
                }
                Pair(item.relativePath, text)
            }
        }

    override suspend fun decryptPreviewBitmap(item: VaultFileItem): HollowResult<Pair<String, Bitmap>> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to decrypt image preview") {
            withContext(Dispatchers.IO) {
                val file = File(item.physicalPath)
                if (!file.exists()) throw IllegalStateException("Locker file does not exist on disk.")
                if (item.originalSizeBytes > MAX_IMAGE_PREVIEW_BYTES) throw IllegalStateException("Image too large to preview.")

                val bytes = if (item.isEncrypted) {
                    val buffer = ByteArrayOutputStream()
                    FileInputStream(file).use { AesVaultCipher.decryptStream(it, buffer) }
                    buffer.toByteArray()
                } else {
                    file.readBytes()
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IllegalStateException("Could not decode image.")
                Pair(item.relativePath, bmp)
            }
        }

    override suspend fun purgeVaultForPackage(packageName: String): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "VaultRepo", errorMessage = "Failed to purge vault for $packageName") {
            withContext(Dispatchers.IO) {
                dao.deleteVaultFilesForPackage(packageName)
            }
        }

    private fun inferMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "mkv", "mov", "webm" -> "video/mp4"
            "mp3", "m4a", "wav", "ogg", "flac" -> "audio/mpeg"
            "txt", "md", "log", "json", "xml", "csv", "sh", "ini", "conf", "properties" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
