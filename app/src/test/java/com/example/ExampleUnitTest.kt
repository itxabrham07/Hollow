package com.example

import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ExampleUnitTest {

    @Test
    fun streamingChunkedCipher_roundTripAndMultiChunkSeeking() {
        val masterKey = AesVaultCipher.generateVolumeKey()
        AesVaultCipher.setMasterKeyForTesting(masterKey)
        val volumeKey = AesVaultCipher.generateVolumeKey()

        // Create 15,000 bytes (spans 4 chunks of 4096 bytes each)
        val plaintext = ByteArray(15_000) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
        val tempFile = Files.createTempFile("isovault_test_v2_", ".isovault").toFile()

        try {
            tempFile.outputStream().use { fos ->
                AesVaultCipher.encryptStream(ByteArrayInputStream(plaintext), fos, volumeKey)
            }

            assertTrue(AesVaultCipher.isEncryptedIsoVaultFile(tempFile))
            assertEquals(2, AesVaultCipher.getContainerVersion(tempFile))

            // Full streaming decrypt check
            val decryptedOut = ByteArrayOutputStream()
            tempFile.inputStream().use { fis ->
                AesVaultCipher.decryptStream(fis, decryptedOut, volumeKey)
            }
            assertArrayEquals(plaintext, decryptedOut.toByteArray())

            // Random-access seeking across chunk boundaries (e.g. offset 3900, length 2500 spans chunk 0 and chunk 1)
            val sliceOffset = 3900L
            val sliceLen = 2500
            val soughtBytes = AesVaultCipher.readDecryptedRange(tempFile, sliceOffset, sliceLen, volumeKey)
            val expectedSlice = plaintext.copyOfRange(sliceOffset.toInt(), sliceOffset.toInt() + sliceLen)
            assertArrayEquals(expectedSlice, soughtBytes)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun legacyV1Format_migratesTransparentlyToV2() {
        val masterKey = AesVaultCipher.generateVolumeKey()
        AesVaultCipher.setMasterKeyForTesting(masterKey)

        val plain = "Legacy v1 secret payload that should upgrade to v2".toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val legacySpec = PBEKeySpec(
            "IsoVault#Android15#SecureIsolationKey".toCharArray(),
            salt,
            10_000,
            256
        )
        val legacyKey = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(legacySpec).encoded,
            "AES"
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)

        val v1Header = byteArrayOf(0x49, 0x53, 0x4F, 0x56, 0x01)
        val v1Container = ByteArrayOutputStream().apply {
            write(v1Header)
            write(salt)
            write(iv)
            write(ct)
        }.toByteArray()

        val tempFile = Files.createTempFile("isovault_legacy_v1_", ".isovault").toFile()
        try {
            tempFile.writeBytes(v1Container)
            assertEquals(1, AesVaultCipher.getContainerVersion(tempFile))

            val migrated = AesVaultCipher.migrateLegacyFileIfNeeded(tempFile, masterKey)
            assertTrue(migrated)
            assertEquals(2, AesVaultCipher.getContainerVersion(tempFile))

            val decrypted = AesVaultCipher.decryptToMemory(tempFile, masterKey)
            assertArrayEquals(plain, decrypted)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun perVolumeKeyWrapping_isolatesVolumesCryptographically() {
        val masterKey = AesVaultCipher.generateVolumeKey()
        val volKeyA = AesVaultCipher.generateVolumeKey()
        val volKeyB = AesVaultCipher.generateVolumeKey()

        val wrappedA = BufferWrapCheck(volKeyA, masterKey)
        val unwrappedA = AesVaultCipher.unwrapVolumeKey(wrappedA, masterKey)
        assertArrayEquals(volKeyA.encoded, unwrappedA.encoded)

        val payload = "Private data in Volume A".toByteArray()
        val encryptedInA = AesVaultCipher.encrypt(payload, volKeyA)

        // Volume B's key must fail to decrypt Volume A's ciphertext
        var failedWithWrongVolumeKey = false
        try {
            AesVaultCipher.decrypt(encryptedInA, volKeyB)
        } catch (_: Exception) {
            failedWithWrongVolumeKey = true
        }
        assertTrue(failedWithWrongVolumeKey)
    }

    private fun BufferWrapCheck(volKey: javax.crypto.SecretKey, masterKey: javax.crypto.SecretKey): String {
        return AesVaultCipher.wrapVolumeKey(volKey, masterKey)
    }

    @Test
    fun sharedSpaceAndPerAppRuleResolution_routesCorrectly() {
        val tempRoot = Files.createTempDirectory("isovault_rules_test").toFile()
        try {
            val appSandbox = File(tempRoot, "sandboxes/com.whatsapp").apply { mkdirs() }
            val sharedSpace = File(tempRoot, "sandboxes/shared.vault").apply { mkdirs() }

            // 1. When useSharedSpace = false, /storage/emulated/0/Shared/photo.jpg goes to the app's private volume
            IsolationRuleManager.updateRule(
                packageName = "com.whatsapp",
                mode = IsolationMode.ENCRYPTED_VAULT,
                blockRootPollution = true,
                whitelistedFolders = setOf("Download"),
                useSharedSpace = false
            )
            val privateResolved = IsolationRuleManager.resolveRedirectedFile(
                packageName = "com.whatsapp",
                originalPath = "/storage/emulated/0/Shared/photo.jpg",
                appSpecificSandboxDir = appSandbox,
                sharedVolumeDir = sharedSpace
            )
            assertNotNull(privateResolved)
            assertTrue(privateResolved!!.absolutePath.startsWith(appSandbox.absolutePath))
            assertFalse(privateResolved.absolutePath.startsWith(sharedSpace.absolutePath))

            // 2. When useSharedSpace = true, /storage/emulated/0/Shared/photo.jpg routes to the Shared Space volume
            IsolationRuleManager.updateRule(
                packageName = "com.whatsapp",
                mode = IsolationMode.ENCRYPTED_VAULT,
                blockRootPollution = true,
                whitelistedFolders = setOf("Download"),
                useSharedSpace = true
            )
            val sharedResolved = IsolationRuleManager.resolveRedirectedFile(
                packageName = "com.whatsapp",
                originalPath = "/storage/emulated/0/Shared/photo.jpg",
                appSpecificSandboxDir = appSandbox,
                sharedVolumeDir = sharedSpace
            )
            assertNotNull(sharedResolved)
            assertEquals(File(sharedSpace, "photo.jpg").absolutePath, sharedResolved!!.absolutePath)

            // 3. Whitelisted folder returns null (permitted passthrough)
            val whitelisted = IsolationRuleManager.resolveRedirectedFile(
                packageName = "com.whatsapp",
                originalPath = "/storage/emulated/0/Download/report.pdf",
                appSpecificSandboxDir = appSandbox,
                sharedVolumeDir = sharedSpace
            )
            assertNull(whitelisted)

            // 4. Fail-closed rule creation uses FAKE_EMPTY and disables shared space
            val failClosed = IsolationRuleManager.createFailClosedRule("com.untrusted.app")
            assertEquals(IsolationMode.FAKE_EMPTY, failClosed.mode)
            assertFalse(failClosed.useSharedSpace)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
