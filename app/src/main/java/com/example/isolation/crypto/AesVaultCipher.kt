package com.example.isolation.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AesVaultCipher {
    private const val ALGORITHM = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256"
    private const val KEY_SIZE_BITS = 256
    private const val ITERATION_COUNT = 10_000
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16

    // Magic container header: "ISOV" + version 1
    private val MAGIC_HEADER = byteArrayOf(0x49, 0x53, 0x4F, 0x56, 0x01)

    private val secureRandom = SecureRandom()

    // Default system master passkey (can be overridden by user in settings)
    var activeMasterPassphrase: String = "IsoVault#Android15#SecureIsolationKey"

    /**
     * Derives a 256-bit AES key from a passphrase and salt using PBKDF2
     */
    fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM.
     * Output format: [MAGIC_HEADER(5)] [SALT(16)] [IV(12)] [CIPHERTEXT + TAG]
     */
    fun encrypt(plaintext: ByteArray, passphrase: String = activeMasterPassphrase): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val ciphertext = cipher.doFinal(plaintext)

        val output = ByteArrayOutputStream(MAGIC_HEADER.size + salt.size + iv.size + ciphertext.size)
        output.write(MAGIC_HEADER)
        output.write(salt)
        output.write(iv)
        output.write(ciphertext)
        return output.toByteArray()
    }

    /**
     * Decrypts encrypted container bytes using AES-256-GCM.
     */
    fun decrypt(encryptedData: ByteArray, passphrase: String = activeMasterPassphrase): ByteArray {
        if (encryptedData.size < MAGIC_HEADER.size + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Data is too short to be a valid IsoVault encrypted container.")
        }

        // Verify magic header
        for (i in MAGIC_HEADER.indices) {
            if (encryptedData[i] != MAGIC_HEADER[i]) {
                throw IllegalArgumentException("Invalid magic header: not an IsoVault file.")
            }
        }

        val input = ByteArrayInputStream(encryptedData)
        input.skip(MAGIC_HEADER.size.toLong())

        val salt = ByteArray(SALT_LENGTH_BYTES)
        input.read(salt)

        val iv = ByteArray(IV_LENGTH_BYTES)
        input.read(iv)

        val ciphertext = input.readBytes()

        val secretKey = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts a source file into a destination vault file.
     */
    fun encryptFile(sourceFile: File, destinationFile: File, passphrase: String = activeMasterPassphrase) {
        val plainBytes = sourceFile.readBytes()
        val encryptedBytes = encrypt(plainBytes, passphrase)
        destinationFile.parentFile?.mkdirs()
        FileOutputStream(destinationFile).use { fos ->
            fos.write(encryptedBytes)
            fos.flush()
        }
    }

    /**
     * Decrypts a vault file into a destination plain file.
     */
    fun decryptFile(sourceEncryptedFile: File, destinationPlainFile: File, passphrase: String = activeMasterPassphrase) {
        val encryptedBytes = sourceEncryptedFile.readBytes()
        val decryptedBytes = decrypt(encryptedBytes, passphrase)
        destinationPlainFile.parentFile?.mkdirs()
        FileOutputStream(destinationPlainFile).use { fos ->
            fos.write(decryptedBytes)
            fos.flush()
        }
    }

    /**
     * Checks if a byte array or file starts with the IsoVault magic header.
     */
    fun isEncryptedIsoVaultFile(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_HEADER.size) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(MAGIC_HEADER.size)
                val read = fis.read(header)
                read == MAGIC_HEADER.size && header.contentEquals(MAGIC_HEADER)
            }
        } catch (_: Exception) {
            false
        }
    }
}
