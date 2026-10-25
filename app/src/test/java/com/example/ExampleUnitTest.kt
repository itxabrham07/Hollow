package com.example

import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.crypto.MasterKeyProvider
import com.example.isolation.model.IsolationRuleManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The Android Keystore is not available on the JVM, so tests wrap keys with an in-memory key. */
private class SoftwareKeyProvider : MasterKeyProvider {
    private val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

    override fun wrap(fileKey: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(fileKey)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
        return cipher.doFinal(wrapped, 12, wrapped.size - 12)
    }
}

class ExampleUnitTest {

    @Before
    fun useSoftwareKeys() {
        AesVaultCipher.setKeyProvider(SoftwareKeyProvider())
    }

    private fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    @Test
    fun cipherRoundTripsText() {
        val plain = "IsoVault confidential storage content".toByteArray(Charsets.UTF_8)
        val encrypted = AesVaultCipher.encrypt(plain)

        assertNotEquals(plain.toList(), encrypted.toList())
        assertTrue(encrypted.size > plain.size + 16)
        assertArrayEquals(plain, AesVaultCipher.decrypt(encrypted))
    }

    @Test
    fun cipherRoundTripsEmptyAndMultiChunkInputs() {
        for (size in listOf(0, 1, 65535, 65536, 65537, 131072, 300_000)) {
            val plain = randomBytes(size)
            assertArrayEquals("size $size", plain, AesVaultCipher.decrypt(AesVaultCipher.encrypt(plain)))
        }
    }

    @Test
    fun encryptingTwiceGivesDifferentCiphertext() {
        val plain = randomBytes(1000)
        assertFalse(AesVaultCipher.encrypt(plain).contentEquals(AesVaultCipher.encrypt(plain)))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val encrypted = AesVaultCipher.encrypt(randomBytes(200_000))
        encrypted[encrypted.size / 2] = (encrypted[encrypted.size / 2].toInt() xor 0x01).toByte()
        assertDecryptFails(encrypted)
    }

    @Test
    fun truncatedCiphertextIsRejected() {
        val encrypted = AesVaultCipher.encrypt(randomBytes(200_000))
        // Cut exactly on a chunk boundary: only the final-chunk marker can catch this.
        val header = encrypted.size - (200_000 + 4 * 16)
        assertDecryptFails(encrypted.copyOf(header + 2 * (64 * 1024 + 16)))
        assertDecryptFails(encrypted.copyOf(encrypted.size - 5))
    }

    @Test
    fun foreignDataIsRejected() {
        assertDecryptFails("definitely not an isovault file".toByteArray())
    }

    @Test
    fun pathsCannotEscapeTheSandbox() {
        assertEquals("DCIM/a.jpg", IsolationRuleManager.extractRelative("/storage/emulated/0/DCIM/a.jpg"))
        assertEquals("Download/b.pdf", IsolationRuleManager.extractRelative("/sdcard/Download/b.pdf"))
        assertEquals("", IsolationRuleManager.extractRelative("/sdcard"))
        assertEquals("data/data/victim", IsolationRuleManager.extractRelative("/sdcard/../../data/data/victim"))
        assertEquals("a/c", IsolationRuleManager.sanitizeRelative("a/./b/../c"))
        assertEquals("", IsolationRuleManager.sanitizeRelative("../../.."))
    }

    @Test
    fun rootPollutionFoldersAreRecognised() {
        assertTrue(IsolationRuleManager.isRootPollution("/sdcard/.tencent/ad_tracker"))
        assertFalse(IsolationRuleManager.isRootPollution("/sdcard/DCIM/Camera/photo.jpg"))
    }

    private fun assertDecryptFails(data: ByteArray) {
        try {
            AesVaultCipher.decrypt(data)
            fail("decrypt should have failed")
        } catch (_: IOException) {
            // expected
        }
    }
}
