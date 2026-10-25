package com.example

import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.model.AuditAction
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testAesVaultCipherRoundtrip() {
        val sampleText = "IsoVault Confidential Storage Content for Android 15"
        val plainBytes = sampleText.toByteArray(Charsets.UTF_8)

        val encrypted = AesVaultCipher.encrypt(plainBytes)
        // Verify ciphertext is different and longer (due to magic header, salt, IV, tag)
        assertNotEquals(plainBytes.toList(), encrypted.toList())
        assertTrue(encrypted.size > plainBytes.size + 30)

        val decrypted = AesVaultCipher.decrypt(encrypted)
        val decryptedText = String(decrypted, Charsets.UTF_8)
        assertEquals(sampleText, decryptedText)
    }

    @Test
    fun testStorageIsolationPathResolution() {
        val testPkg = "com.test.isolated.app"
        IsolationRuleManager.updateRule(
            packageName = testPkg,
            mode = IsolationMode.ENCRYPTED_VAULT,
            blockRootPollution = true,
            whitelistedFolders = setOf("Download")
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
        assertTrue(normalAccess.resolvedPath.endsWith(".isovault"))

        // Test whitelisted folder
        val whitelisted = IsolationRuleManager.resolvePath(
            packageName = testPkg,
            requestedPath = "/storage/emulated/0/Download/Document.pdf"
        )
        assertEquals(AuditAction.PERMITTED, whitelisted.action)
    }

    @Test
    fun testFileManagerHelperZipAndHexDump() {
        val tempDir = java.nio.file.Files.createTempDirectory("isovault_test").toFile()
        try {
            val fileA = java.io.File(tempDir, "fileA.txt").apply { writeText("Hello MT Manager A") }
            val fileB = java.io.File(tempDir, "fileB.txt").apply { writeText("Hello MT Manager B") }

            val zipDest = java.io.File(tempDir, "archive.zip")
            val zipRes = com.example.isolation.storage.FileManagerHelper.compressToZip(listOf(fileA, fileB), zipDest)
            assertTrue(zipRes.isSuccess)
            assertTrue(zipDest.exists() && zipDest.length() > 0)

            val extractDir = java.io.File(tempDir, "extracted")
            val extractRes = com.example.isolation.storage.FileManagerHelper.extractZip(zipDest, extractDir)
            assertTrue(extractRes.isSuccess)
            assertEquals(2, extractRes.getOrNull())
            assertEquals("Hello MT Manager A", java.io.File(extractDir, "fileA.txt").readText())
            assertEquals("Hello MT Manager B", java.io.File(extractDir, "fileB.txt").readText())

            // Test Hex dump
            val hexDump = com.example.isolation.storage.FileManagerHelper.generateHexDump(fileA)
            assertTrue(hexDump.isNotEmpty())
            assertEquals("00000000", hexDump[0].offset)
            assertTrue(hexDump[0].asciiString.contains("Hello"))

            // Test unique name resolution
            val unique = com.example.isolation.storage.FileManagerHelper.resolveUniqueFile(tempDir, "fileA.txt")
            assertEquals("fileA (1).txt", unique.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
