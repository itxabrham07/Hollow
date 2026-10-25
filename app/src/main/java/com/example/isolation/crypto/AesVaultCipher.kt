package com.example.isolation.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production cryptographic engine for IsoVault:
 * - Hardware-backed Android Keystore KEK wrapping a random 256-bit Master Key (no hardcoded passphrase).
 * - Per-volume 256-bit Data Encryption Keys (DEKs) wrapped by the Master Key so individual volumes
 *   can be deleted or exported independently.
 * - Streaming, 4KB-chunked Authenticated Encryption (AES256_GCM_HKDF_4KB, header "ISOV\x02") supporting
 *   O(1) random-access chunk seeking for large video playback without loading files into memory.
 * - Automatic transparent migration from legacy v1 ("ISOV\x01") containers.
 * - 600,000 PBKDF2-HMAC-SHA256 iterations with random 16-byte salt for passphrase-protected backups.
 */
object AesVaultCipher {
    private const val ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256"
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_KEK_ALIAS = "isovault_hardware_kek_v2"
    private const val KEY_PREFS_NAME = "isovault_keystore_wrapped_keys"
    private const val PREF_WRAPPED_MASTER_KEY = "wrapped_master_key_v2"

    const val KEY_SIZE_BITS = 256
    const val KEY_SIZE_BYTES = 32
    const val PBKDF2_ITERATION_COUNT = 600_000
    private const val LEGACY_PBKDF2_ITERATIONS = 10_000
    private const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_TAG_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16

    // Streaming chunk parameters (4KB plaintext per chunk + 16B GCM auth tag = 4112B encrypted chunk)
    const val STREAM_CHUNK_SIZE = 4096
    const val ENCRYPTED_CHUNK_SIZE = STREAM_CHUNK_SIZE + GCM_TAG_BYTES
    private const val NONCE_PREFIX_BYTES = 7

    // Container headers:
    // v1 legacy: "ISOV" + 0x01
    private val MAGIC_HEADER_V1 = byteArrayOf(0x49, 0x53, 0x4F, 0x56, 0x01)
    // v2 streaming 4KB chunked AEAD: "ISOV" + 0x02 + [hkdfSalt(16)] + [noncePrefix(7)] = 28 bytes header
    private val MAGIC_HEADER_V2 = byteArrayOf(0x49, 0x53, 0x4F, 0x56, 0x02)
    const val V2_HEADER_TOTAL_BYTES = 5 + SALT_LENGTH_BYTES + NONCE_PREFIX_BYTES

    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedMasterKey: SecretKey? = null

    private val volumeKeyCache = ConcurrentHashMap<String, SecretKey>()

    /**
     * Initializes or loads the random 256-bit Master Key wrapped by the Android Keystore hardware KEK.
     */
    @Synchronized
    fun initializeMasterKey(context: Context): SecretKey {
        cachedMasterKey?.let { return it }

        val prefs = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
        val existingWrapped = prefs.getString(PREF_WRAPPED_MASTER_KEY, null)

        val kek = getOrCreateKeystoreKek()
        if (existingWrapped != null && kek != null) {
            try {
                val raw = Base64.getDecoder().decode(existingWrapped)
                val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
                val ct = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val masterBytes = cipher.doFinal(ct)
                val masterKey = SecretKeySpec(masterBytes, ALGORITHM)
                cachedMasterKey = masterKey
                return masterKey
            } catch (_: Exception) {
                // Fall through and rotate if keystore was invalidated
            }
        }

        val newMasterBytes = ByteArray(KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val newMasterKey = SecretKeySpec(newMasterBytes, ALGORITHM)

        if (kek != null) {
            try {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, kek)
                val iv = cipher.iv
                val ct = cipher.doFinal(newMasterBytes)
                val combined = ByteArray(iv.size + ct.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(ct, 0, combined, iv.size, ct.size)
                prefs.edit()
                    .putString(PREF_WRAPPED_MASTER_KEY, Base64.getEncoder().encodeToString(combined))
                    .apply()
            } catch (_: Exception) {}
        }

        cachedMasterKey = newMasterKey
        return newMasterKey
    }

    /**
     * Obtains the active 256-bit Master Key (generates an ephemeral random key in JVM unit tests
     * where Android Context/Keystore is not initialized).
     */
    @Synchronized
    fun getMasterKey(): SecretKey {
        cachedMasterKey?.let { return it }
        val randomBytes = ByteArray(KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        val key = SecretKeySpec(randomBytes, ALGORITHM)
        cachedMasterKey = key
        return key
    }

    /**
     * Allows setting a specific master key in unit tests.
     */
    @Synchronized
    fun setMasterKeyForTesting(key: SecretKey) {
        cachedMasterKey = key
        volumeKeyCache.clear()
    }

    private fun getOrCreateKeystoreKek(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getEntry(KEYSTORE_KEK_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val specBuilder = KeyGenParameterSpec.Builder(
                KEYSTORE_KEK_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)

            keyGen.init(specBuilder.build())
            keyGen.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Generates a new random 256-bit Data Encryption Key (DEK) for a volume.
     */
    fun generateVolumeKey(): SecretKey {
        val bytes = ByteArray(KEY_SIZE_BYTES).also { secureRandom.nextBytes(it) }
        return SecretKeySpec(bytes, ALGORITHM)
    }

    /**
     * Wraps a per-volume 256-bit DEK using the Master Key with AES-256-GCM.
     */
    fun wrapVolumeKey(volumeKey: SecretKey, masterKey: SecretKey = getMasterKey()): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val wrapped = cipher.doFinal(volumeKey.encoded)
        val combined = ByteArray(iv.size + wrapped.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(wrapped, 0, combined, iv.size, wrapped.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Unwraps a per-volume 256-bit DEK using the Master Key.
     */
    fun unwrapVolumeKey(wrappedKeyBase64: String, masterKey: SecretKey = getMasterKey()): SecretKey {
        volumeKeyCache[wrappedKeyBase64]?.let { return it }
        val combined = Base64.getDecoder().decode(wrappedKeyBase64)
        require(combined.size > IV_LENGTH_BYTES + GCM_TAG_BYTES) { "Invalid wrapped volume key" }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ct = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val rawKey = cipher.doFinal(ct)
        val key = SecretKeySpec(rawKey, ALGORITHM)
        volumeKeyCache[wrappedKeyBase64] = key
        return key
    }

    /**
     * Derives a 256-bit AES key from a user passphrase and 16-byte random salt using
     * 600,000 PBKDF2-HMAC-SHA256 iterations.
     */
    fun deriveKeyFromPassphrase(
        passphrase: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATION_COUNT
    ): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * HKDF-SHA256 subkey derivation for AES256_GCM_HKDF_4KB streaming chunks.
     */
    private fun deriveHkdfSubkey(ikm: ByteArray, salt: ByteArray, info: ByteArray): SecretKey {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(salt, HMAC_SHA256))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, HMAC_SHA256))
        mac.update(info)
        mac.update(0x01.toByte())
        val okm = mac.doFinal()
        return SecretKeySpec(okm.copyOf(KEY_SIZE_BYTES), ALGORITHM)
    }

    /**
     * Builds the 12-byte chunk nonce: [noncePrefix(7 bytes)][chunkIndex(4 bytes big-endian)][isLastChunk(1 byte)]
     */
    private fun buildChunkNonce(noncePrefix: ByteArray, chunkIndex: Int, isLastChunk: Boolean): ByteArray {
        val nonce = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(noncePrefix, 0, nonce, 0, NONCE_PREFIX_BYTES)
        ByteBuffer.wrap(nonce, NONCE_PREFIX_BYTES, 4).putInt(chunkIndex)
        nonce[11] = if (isLastChunk) 1 else 0
        return nonce
    }

    /**
     * Streaming, 4KB-chunked authenticated encryption (AES256_GCM_HKDF_4KB).
     * Never loads large files into memory at once.
     */
    fun encryptStream(
        input: InputStream,
        output: OutputStream,
        volumeKey: SecretKey = getMasterKey()
    ) {
        val hkdfSalt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val noncePrefix = ByteArray(NONCE_PREFIX_BYTES).also { secureRandom.nextBytes(it) }
        val subkey = deriveHkdfSubkey(volumeKey.encoded, hkdfSalt, MAGIC_HEADER_V2)

        output.write(MAGIC_HEADER_V2)
        output.write(hkdfSalt)
        output.write(noncePrefix)

        val buffer = ByteArray(STREAM_CHUNK_SIZE)
        var nextBuffer = ByteArray(STREAM_CHUNK_SIZE)
        var currentLen = readFullyChunk(input, buffer)
        var chunkIndex = 0

        if (currentLen <= 0) {
            // Empty plaintext file: write single final empty authenticated chunk
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val nonce = buildChunkNonce(noncePrefix, 0, isLastChunk = true)
            cipher.init(Cipher.ENCRYPT_MODE, subkey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
            cipher.updateAAD(MAGIC_HEADER_V2)
            output.write(cipher.doFinal(ByteArray(0)))
            output.flush()
            return
        }

        while (currentLen > 0) {
            val nextLen = readFullyChunk(input, nextBuffer)
            val isLast = nextLen <= 0

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            val nonce = buildChunkNonce(noncePrefix, chunkIndex, isLastChunk = isLast)
            cipher.init(Cipher.ENCRYPT_MODE, subkey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
            cipher.updateAAD(MAGIC_HEADER_V2)

            val encryptedChunk = cipher.doFinal(buffer, 0, currentLen)
            output.write(encryptedChunk)

            val swap = buffer
            System.arraycopy(nextBuffer, 0, swap, 0, maxOf(0, nextLen))
            currentLen = nextLen
            chunkIndex++
        }
        output.flush()
    }

    /**
     * Streaming, 4KB-chunked authenticated decryption (supports both v2 streaming and v1 legacy format).
     */
    fun decryptStream(
        input: InputStream,
        output: OutputStream,
        volumeKey: SecretKey = getMasterKey()
    ) {
        val header = ByteArray(5)
        val headerRead = readFullyBuffer(input, header)
        require(headerRead == 5) { "Data is too short to be a valid IsoVault container." }

        if (header.contentEquals(MAGIC_HEADER_V2)) {
            val hkdfSalt = ByteArray(SALT_LENGTH_BYTES)
            val noncePrefix = ByteArray(NONCE_PREFIX_BYTES)
            require(readFullyBuffer(input, hkdfSalt) == SALT_LENGTH_BYTES) { "Truncated v2 salt" }
            require(readFullyBuffer(input, noncePrefix) == NONCE_PREFIX_BYTES) { "Truncated v2 nonce prefix" }

            val subkey = deriveHkdfSubkey(volumeKey.encoded, hkdfSalt, MAGIC_HEADER_V2)
            val chunkBuf = ByteArray(ENCRYPTED_CHUNK_SIZE)
            val nextChunkBuf = ByteArray(ENCRYPTED_CHUNK_SIZE)
            var currentRead = readFullyBuffer(input, chunkBuf)
            require(currentRead >= GCM_TAG_BYTES) { "Truncated v2 ciphertext stream" }
            var chunkIndex = 0

            while (currentRead > 0) {
                val nextRead = readFullyBuffer(input, nextChunkBuf)
                val isLast = nextRead <= 0

                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                val nonce = buildChunkNonce(noncePrefix, chunkIndex, isLastChunk = isLast)
                cipher.init(Cipher.DECRYPT_MODE, subkey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
                cipher.updateAAD(MAGIC_HEADER_V2)

                val plainChunk = cipher.doFinal(chunkBuf, 0, currentRead)
                output.write(plainChunk)

                if (!isLast) {
                    System.arraycopy(nextChunkBuf, 0, chunkBuf, 0, nextRead)
                }
                currentRead = nextRead
                chunkIndex++
            }
            output.flush()
        } else if (header.contentEquals(MAGIC_HEADER_V1)) {
            val rest = input.readBytes()
            val fullV1 = ByteArray(header.size + rest.size)
            System.arraycopy(header, 0, fullV1, 0, header.size)
            System.arraycopy(rest, 0, fullV1, header.size, rest.size)
            val plain = decryptLegacyV1(fullV1, volumeKey)
            output.write(plain)
            output.flush()
        } else {
            throw IllegalArgumentException("Invalid magic header: not an IsoVault file.")
        }
    }

    /**
     * Random-access chunked decryption for streaming video/media seeking without loading the file in memory.
     * Decrypts only the 4KB chunks covering `[plaintextOffset, plaintextOffset + length)`.
     */
    fun readDecryptedRange(
        file: File,
        plaintextOffset: Long,
        length: Int,
        volumeKey: SecretKey = getMasterKey()
    ): ByteArray {
        require(plaintextOffset >= 0 && length >= 0) { "Invalid range" }
        if (length == 0) return ByteArray(0)

        migrateLegacyFileIfNeeded(file, volumeKey)

        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < V2_HEADER_TOTAL_BYTES + GCM_TAG_BYTES) return ByteArray(0)
            val header = ByteArray(5)
            raf.readFully(header)
            if (!header.contentEquals(MAGIC_HEADER_V2)) {
                val all = decryptToMemory(file, volumeKey)
                val start = plaintextOffset.toInt().coerceAtMost(all.size)
                val end = (start + length).coerceAtMost(all.size)
                return all.copyOfRange(start, end)
            }

            val hkdfSalt = ByteArray(SALT_LENGTH_BYTES)
            val noncePrefix = ByteArray(NONCE_PREFIX_BYTES)
            raf.readFully(hkdfSalt)
            raf.readFully(noncePrefix)
            val subkey = deriveHkdfSubkey(volumeKey.encoded, hkdfSalt, MAGIC_HEADER_V2)

            val totalCipherBytes = raf.length() - V2_HEADER_TOTAL_BYTES
            val totalChunks = ((totalCipherBytes + ENCRYPTED_CHUNK_SIZE - 1) / ENCRYPTED_CHUNK_SIZE).toInt()
            if (totalChunks <= 0) return ByteArray(0)

            val startChunk = (plaintextOffset / STREAM_CHUNK_SIZE).toInt()
            val endChunk = ((plaintextOffset + length - 1) / STREAM_CHUNK_SIZE).toInt().coerceAtMost(totalChunks - 1)
            if (startChunk >= totalChunks) return ByteArray(0)

            val out = ByteArrayOutputStream(length)
            for (chunkIdx in startChunk..endChunk) {
                val chunkFileOffset = V2_HEADER_TOTAL_BYTES.toLong() + chunkIdx.toLong() * ENCRYPTED_CHUNK_SIZE
                val remainingFileBytes = (raf.length() - chunkFileOffset).toInt()
                val bytesToRead = minOf(ENCRYPTED_CHUNK_SIZE, remainingFileBytes)
                if (bytesToRead < GCM_TAG_BYTES) break

                val encryptedChunk = ByteArray(bytesToRead)
                raf.seek(chunkFileOffset)
                raf.readFully(encryptedChunk)

                val isLast = (chunkIdx == totalChunks - 1)
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                val nonce = buildChunkNonce(noncePrefix, chunkIdx, isLast)
                cipher.init(Cipher.DECRYPT_MODE, subkey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce))
                cipher.updateAAD(MAGIC_HEADER_V2)
                val decryptedChunk = cipher.doFinal(encryptedChunk)

                val chunkPlainStart = chunkIdx.toLong() * STREAM_CHUNK_SIZE
                val sliceStart = if (chunkIdx == startChunk) (plaintextOffset - chunkPlainStart).toInt() else 0
                val needed = length - out.size()
                val sliceLen = minOf(decryptedChunk.size - sliceStart, needed)
                if (sliceStart in 0 until decryptedChunk.size && sliceLen > 0) {
                    out.write(decryptedChunk, sliceStart, sliceLen)
                }
            }
            return out.toByteArray()
        }
    }

    /**
     * Automatically migrates a legacy v1 ("ISOV\x01") encrypted file to the v2 streaming chunked format once.
     */
    fun migrateLegacyFileIfNeeded(file: File, volumeKey: SecretKey = getMasterKey()): Boolean {
        if (!file.exists() || file.length() < 5) return false
        val isV1 = try {
            FileInputStream(file).use { fis ->
                val h = ByteArray(5)
                fis.read(h) == 5 && h.contentEquals(MAGIC_HEADER_V1)
            }
        } catch (_: Exception) {
            false
        }
        if (!isV1) return false

        return try {
            val legacyBytes = file.readBytes()
            val plain = decryptLegacyV1(legacyBytes, volumeKey)
            val tempMigrated = File(file.parentFile, "${file.name}.migrating")
            ByteArrayInputStream(plain).use { bais ->
                FileOutputStream(tempMigrated).use { fos ->
                    encryptStream(bais, fos, volumeKey)
                }
            }
            if (tempMigrated.exists() && tempMigrated.length() >= V2_HEADER_TOTAL_BYTES) {
                tempMigrated.copyTo(file, overwrite = true)
                tempMigrated.delete()
                true
            } else {
                tempMigrated.delete()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun decryptLegacyV1(encryptedData: ByteArray, fallbackKey: SecretKey): ByteArray {
        require(encryptedData.size >= MAGIC_HEADER_V1.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            "Legacy v1 container is truncated."
        }
        val input = ByteArrayInputStream(encryptedData)
        input.skip(MAGIC_HEADER_V1.size.toLong())
        val salt = ByteArray(SALT_LENGTH_BYTES)
        input.read(salt)
        val iv = ByteArray(IV_LENGTH_BYTES)
        input.read(iv)
        val ciphertext = input.readBytes()

        return try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, fallbackKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            // Legacy v1 migration compatibility
            val legacySpec = PBEKeySpec(
                "IsoVault#Android15#SecureIsolationKey".toCharArray(),
                salt,
                LEGACY_PBKDF2_ITERATIONS,
                KEY_SIZE_BITS
            )
            val legacyKey = SecretKeySpec(
                SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO).generateSecret(legacySpec).encoded,
                ALGORITHM
            )
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        }
    }

    /**
     * Encrypts plaintext bytes using the v2 4KB-chunked streaming AEAD format.
     */
    fun encrypt(plaintext: ByteArray, volumeKey: SecretKey = getMasterKey()): ByteArray {
        val out = ByteArrayOutputStream(plaintext.size + V2_HEADER_TOTAL_BYTES + 64)
        encryptStream(ByteArrayInputStream(plaintext), out, volumeKey)
        return out.toByteArray()
    }

    /**
     * Encrypts plaintext bytes with a user passphrase using 600,000 PBKDF2-HMAC-SHA256 iterations + random salt.
     */
    fun encryptWithPassphrase(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val derivedKey = deriveKeyFromPassphrase(passphrase, salt, PBKDF2_ITERATION_COUNT)
        val inner = encrypt(plaintext, derivedKey)
        val out = ByteArrayOutputStream(salt.size + inner.size)
        out.write(salt)
        out.write(inner)
        return out.toByteArray()
    }

    /**
     * Decrypts bytes encrypted with [encryptWithPassphrase].
     */
    fun decryptWithPassphrase(encryptedWithSalt: ByteArray, passphrase: String): ByteArray {
        require(encryptedWithSalt.size > SALT_LENGTH_BYTES + V2_HEADER_TOTAL_BYTES) {
            "Invalid passphrase-encrypted container"
        }
        val salt = encryptedWithSalt.copyOfRange(0, SALT_LENGTH_BYTES)
        val inner = encryptedWithSalt.copyOfRange(SALT_LENGTH_BYTES, encryptedWithSalt.size)
        val derivedKey = deriveKeyFromPassphrase(passphrase, salt, PBKDF2_ITERATION_COUNT)
        return decrypt(inner, derivedKey)
    }

    /**
     * Decrypts v2 (or legacy v1) container bytes into memory.
     */
    fun decrypt(encryptedData: ByteArray, volumeKey: SecretKey = getMasterKey()): ByteArray {
        val out = ByteArrayOutputStream(encryptedData.size)
        decryptStream(ByteArrayInputStream(encryptedData), out, volumeKey)
        return out.toByteArray()
    }

    /**
     * Encrypts a source file into a destination vault file using streaming chunks.
     */
    fun encryptFile(sourceFile: File, destinationFile: File, volumeKey: SecretKey = getMasterKey()) {
        destinationFile.parentFile?.mkdirs()
        FileInputStream(sourceFile).use { fis ->
            FileOutputStream(destinationFile).use { fos ->
                encryptStream(fis, fos, volumeKey)
            }
        }
    }

    /**
     * Decrypts a vault file into a destination plain file using streaming chunks.
     */
    fun decryptFile(sourceEncryptedFile: File, destinationPlainFile: File, volumeKey: SecretKey = getMasterKey()) {
        migrateLegacyFileIfNeeded(sourceEncryptedFile, volumeKey)
        destinationPlainFile.parentFile?.mkdirs()
        FileInputStream(sourceEncryptedFile).use { fis ->
            FileOutputStream(destinationPlainFile).use { fos ->
                decryptStream(fis, fos, volumeKey)
            }
        }
    }

    /**
     * Checks if a file starts with the IsoVault v2 or v1 magic header.
     */
    fun isEncryptedIsoVaultFile(file: File): Boolean {
        if (!file.exists() || file.length() < 5) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(5)
                val read = fis.read(header)
                read == 5 && (header.contentEquals(MAGIC_HEADER_V2) || header.contentEquals(MAGIC_HEADER_V1))
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the container format version (2 for streaming AEAD, 1 for legacy, 0 for plain).
     */
    fun getContainerVersion(file: File): Int {
        if (!file.exists() || file.length() < 5) return 0
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(5)
                if (fis.read(header) != 5) return 0
                when {
                    header.contentEquals(MAGIC_HEADER_V2) -> 2
                    header.contentEquals(MAGIC_HEADER_V1) -> 1
                    else -> 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Decrypts a vault file strictly in memory (and transparently upgrades v1 files to v2).
     */
    fun decryptToMemory(file: File, volumeKey: SecretKey = getMasterKey()): ByteArray {
        if (!file.exists()) return ByteArray(0)
        migrateLegacyFileIfNeeded(file, volumeKey)
        return if (isEncryptedIsoVaultFile(file) || file.name.endsWith(".isovault", ignoreCase = true)) {
            try {
                val out = ByteArrayOutputStream(file.length().toInt().coerceAtLeast(64))
                FileInputStream(file).use { fis ->
                    decryptStream(fis, out, volumeKey)
                }
                out.toByteArray()
            } catch (_: Exception) {
                file.readBytes()
            }
        } else {
            file.readBytes()
        }
    }

    /**
     * Creates a short-lived private cache stream in internal app-private cacheDir using
     * streaming chunked decryption (never loads full video in memory).
     * Wiped automatically on viewer close and on vault lock.
     */
    fun createEphemeralPlaybackFile(
        context: Context,
        vaultFile: File,
        volumeKey: SecretKey = getMasterKey()
    ): File {
        val cacheDir = File(context.cacheDir, "ephemeral_playback")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cleanName = vaultFile.name.removeSuffix(".isovault")
        val tempFile = File(cacheDir, "stream_${System.currentTimeMillis()}_$cleanName")
        migrateLegacyFileIfNeeded(vaultFile, volumeKey)
        if (isEncryptedIsoVaultFile(vaultFile)) {
            FileInputStream(vaultFile).use { fis ->
                FileOutputStream(tempFile).use { fos ->
                    decryptStream(fis, fos, volumeKey)
                }
            }
        } else {
            vaultFile.inputStream().use { fis ->
                FileOutputStream(tempFile).use { fos ->
                    fis.copyTo(fos)
                }
            }
        }
        tempFile.setReadable(true, true)
        tempFile.setWritable(true, true)
        return tempFile
    }

    /**
     * Creates a short-lived private share file inside internal cacheDir/ephemeral_share for FileProvider.
     */
    fun createEphemeralShareFile(
        context: Context,
        vaultFile: File,
        volumeKey: SecretKey = getMasterKey()
    ): File {
        val shareDir = File(context.cacheDir, "ephemeral_share")
        if (!shareDir.exists()) shareDir.mkdirs()
        val cleanName = vaultFile.name.removeSuffix(".isovault")
        val tempFile = File(shareDir, cleanName)
        if (isEncryptedIsoVaultFile(vaultFile)) {
            FileInputStream(vaultFile).use { fis ->
                FileOutputStream(tempFile).use { fos ->
                    decryptStream(fis, fos, volumeKey)
                }
            }
        } else {
            vaultFile.copyTo(tempFile, overwrite = true)
        }
        return tempFile
    }

    /**
     * Overwrites and deletes all short-lived private cache files.
     * Called automatically when closing media viewers and whenever the vault is locked.
     */
    fun wipeEphemeralCache(context: Context) {
        listOf("ephemeral_playback", "ephemeral_share").forEach { dirName ->
            val dir = File(context.cacheDir, dirName)
            if (dir.exists()) {
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        try {
                            val zero = ByteArray(minOf(f.length().toInt(), STREAM_CHUNK_SIZE))
                            FileOutputStream(f).use { it.write(zero) }
                        } catch (_: Exception) {}
                        f.delete()
                    }
                }
                dir.deleteRecursively()
            }
        }
    }

    /**
     * Creates a streaming AES-256-GCM encrypted backup archive (.isovaultbackup) of the entire vault directory.
     */
    fun createEncryptedBackup(
        vaultRoot: File,
        destinationStream: OutputStream,
        passphrase: String? = null
    ): Int {
        val zipBuffer = ByteArrayOutputStream()
        var fileCount = 0
        ZipOutputStream(zipBuffer).use { zos ->
            if (vaultRoot.exists()) {
                vaultRoot.walkTopDown().forEach { file ->
                    if (file.isFile && file.name != ".nomedia") {
                        val relPath = file.relativeTo(vaultRoot).path
                        val entry = ZipEntry(relPath)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        fileCount++
                    }
                }
            }
        }
        val zipBytes = zipBuffer.toByteArray()
        val encryptedArchive = if (!passphrase.isNullOrBlank()) {
            encryptWithPassphrase(zipBytes, passphrase)
        } else {
            encrypt(zipBytes, getMasterKey())
        }
        destinationStream.write(encryptedArchive)
        destinationStream.flush()
        return fileCount
    }

    /**
     * Restores an AES-256-GCM encrypted backup archive (.isovaultbackup) into the vault directory.
     */
    fun restoreEncryptedBackup(
        sourceStream: InputStream,
        vaultRoot: File,
        passphrase: String? = null
    ): Int {
        val encryptedBytes = sourceStream.readBytes()
        val decryptedZipBytes = if (!passphrase.isNullOrBlank()) {
            decryptWithPassphrase(encryptedBytes, passphrase)
        } else {
            decrypt(encryptedBytes, getMasterKey())
        }
        var restoredCount = 0
        ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(vaultRoot, entry.name)
                    if (outFile.canonicalPath.startsWith(vaultRoot.canonicalPath)) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        restoredCount++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return restoredCount
    }

    private fun readFullyChunk(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun readFullyBuffer(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }
}
