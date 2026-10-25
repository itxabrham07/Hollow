package com.example

import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class ExampleUnitTest {

    @Test
    fun testPlaintextDoesNotAppearOnDisk() {
        val sensitivePlaintext = "CONFIDENTIAL_HOLLOW_SECRET_DATA_NO_LEAKS_ALLOWED_12345"
        val plainBytes = sensitivePlaintext.toByteArray(Charsets.UTF_8)
        val key = AesVaultCipher.getVolumeKey("com.test.volume")

        val tempPlainFile = File.createTempFile("hollow_plain", ".tmp")
        tempPlainFile.writeBytes(plainBytes)
        val tempEncFile = File.createTempFile("hollow_enc", ".hllw")

        try {
            AesVaultCipher.encryptFile(tempPlainFile, tempEncFile, key)
            val rawBytesOnDisk = tempEncFile.readBytes()

            // Verify header has Hollow magic
            assertTrue(rawBytesOnDisk.size > AesVaultCipher.HEADER_SIZE_BYTES)
            for (i in AesVaultCipher.MAGIC_HEADER_V2.indices) {
                assertEquals(AesVaultCipher.MAGIC_HEADER_V2[i], rawBytesOnDisk[i])
            }

            // Assert plaintext string or byte sequence does NOT appear in raw bytes on disk
            val rawDiskString = String(rawBytesOnDisk, Charsets.ISO_8859_1)
            assertFalse(
                "Plaintext must not appear in raw ciphertext on disk",
                rawDiskString.contains(sensitivePlaintext)
            )

            // Verify clean decryption
            val tempDecryptedFile = File.createTempFile("hollow_dec", ".tmp")
            AesVaultCipher.decryptFile(tempEncFile, tempDecryptedFile, key)
            assertEquals(sensitivePlaintext, tempDecryptedFile.readText(Charsets.UTF_8))
            tempDecryptedFile.delete()
        } finally {
            tempPlainFile.delete()
            tempEncFile.delete()
        }
    }

    @Test
    fun testTamperedCiphertextIsRejected() {
        val plainBytes = "Strict AEAD Integrity Authentication Test".toByteArray(Charsets.UTF_8)
        val key = AesVaultCipher.getVolumeKey("com.test.integrity")

        val ciphertext = AesVaultCipher.encrypt(plainBytes, key)
        assertTrue(ciphertext.size > AesVaultCipher.HEADER_SIZE_BYTES + 16)

        // Tamper with a single byte in ciphertext
        val tampered = ciphertext.clone()
        val tamperIndex = AesVaultCipher.HEADER_SIZE_BYTES + 2
        tampered[tamperIndex] = (tampered[tamperIndex].toInt() xor 0xFF).toByte()

        try {
            AesVaultCipher.decrypt(tampered, key)
            fail("Decryption of tampered ciphertext must throw an AEAD integrity exception!")
        } catch (_: AEADBadTagException) {
            // Expected
        } catch (_: Exception) {
            // Expected (e.g. wrapped AEAD exception)
        }
    }

    @Test
    fun testRandomAccessReadForVideoSeeking() {
        // Create 10,000 bytes of sequential data spanning multiple 4KB chunks
        val largeData = ByteArray(10_000)
        for (i in largeData.indices) {
            largeData[i] = (i % 256).toByte()
        }

        val key = AesVaultCipher.getVolumeKey("com.test.seek")
        val encFile = File.createTempFile("hollow_seek", ".hllw")

        try {
            val encBytes = AesVaultCipher.encrypt(largeData, key)
            encFile.writeBytes(encBytes)

            // Seek and read 100 bytes across chunk boundary (offset 4050 to 4150)
            val seekOffset = 4050L
            val readLength = 100
            val buffer = ByteArray(readLength)

            val bytesRead = AesVaultCipher.readAt(encFile, seekOffset, buffer, 0, readLength, key)
            assertEquals(readLength, bytesRead)

            val expectedSlice = ByteArray(readLength)
            System.arraycopy(largeData, seekOffset.toInt(), expectedSlice, 0, readLength)
            assertArrayEquals(expectedSlice, buffer)
        } finally {
            encFile.delete()
        }
    }

    @Test
    fun testPbkdf2PassphraseDerivation() {
        val passphrase = "MasterBackupPassphrase2026!".toCharArray()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val key1 = AesVaultCipher.deriveKeyFromPassphrase(passphrase, salt)
        val key2 = AesVaultCipher.deriveKeyFromPassphrase(passphrase, salt)

        assertEquals("AES", key1.algorithm)
        assertEquals(32, key1.encoded.size) // 256 bits
        assertArrayEquals(key1.encoded, key2.encoded)
    }

    @Test
    fun testLegacyV1Migration() {
        val originalText = "Legacy IsoVault v1 Data for Migration Test"
        val key = AesVaultCipher.getVolumeKey("com.test.migration")

        // Construct legacy ISOV v1 container: [MAGIC(5)][SALT(16)][IV(12)][CIPHERTEXT]
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherBytes = cipher.doFinal(originalText.toByteArray(Charsets.UTF_8))

        val legacyBytes = ByteArrayOutputStream().apply {
            write(AesVaultCipher.MAGIC_HEADER_LEGACY_V1)
            write(salt)
            write(iv)
            write(cipherBytes)
        }.toByteArray()

        val tempV1File = File.createTempFile("isov_v1", ".isovault")
        try {
            tempV1File.writeBytes(legacyBytes)
            assertTrue(AesVaultCipher.isLegacyIsoVaultV1(tempV1File))

            val migrated = AesVaultCipher.migrateV1FileIfNeeded(tempV1File, key)
            assertTrue("Migration must succeed", migrated)
            assertTrue("Must now be Hollow v2 format", AesVaultCipher.isHollowV2File(tempV1File))

            val decrypted = AesVaultCipher.decrypt(tempV1File.readBytes(), key)
            assertEquals(originalText, String(decrypted, Charsets.UTF_8))
        } finally {
            tempV1File.delete()
        }
    }

    @Test
    fun testStorageIsolationPathResolution() {
        val testPkg = "com.test.isolated.app"
        IsolationRuleManager.updateRule(
            packageName = testPkg,
            mode = IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = true,
            whitelistedFolders = setOf("Download"),
            useSharedSpace = false
        )

        // Test root pollution block
        val rootPollution = IsolationRuleManager.resolvePath(
            packageName = testPkg,
            requestedPath = "/sdcard/.tencent/ad_tracker"
        )
        assertEquals(AuditAction.BLOCKED, rootPollution.action)

        // Test normal storage file redirection to encrypted vault
        val normalAccess = IsolationRuleManager.resolvePath(
            packageName = testPkg,
            requestedPath = "/storage/emulated/0/DCIM/Photo.jpg"
        )
        assertEquals(AuditAction.ENCRYPTED, normalAccess.action)
        assertTrue(normalAccess.resolvedPath.endsWith(".hllw"))

        // Test whitelisted folder
        val whitelisted = IsolationRuleManager.resolvePath(
            packageName = testPkg,
            requestedPath = "/storage/emulated/0/Download/Document.pdf"
        )
        assertEquals(AuditAction.PERMITTED, whitelisted.action)
    }
}
