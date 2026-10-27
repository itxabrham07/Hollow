package com.example.isolation.core.root

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.example.isolation.core.license.DeviceHardwareFingerprint
import com.example.isolation.core.license.MonotonicClockGuard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

enum class LicenseAccountStatus {
    UNREGISTERED,
    PENDING_APPROVAL,
    APPROVED,
    REVOKED,
    BANNED
}

data class RootVaultLicenseRecord(
    val status: LicenseAccountStatus = LicenseAccountStatus.UNREGISTERED,
    val username: String = "",
    val email: String = "",
    val hardwareFingerprint: String = "",
    val licenseToken: String = "",
    val signature: String = "",
    val allowedModules: Set<String> = emptySet(),
    val downloadedModules: Set<String> = emptySet(),
    val issuedAtMs: Long = 0L,
    val validUntilMs: Long = 0L,
    val highWaterWallClockMs: Long = 0L,
    val lastElapsedRealtimeMs: Long = 0L,
    val banReason: String = "",
    val rootVaultMountedAt: String = "/data/adb/.hollow_vault"
)

/**
 * Manages the Root-Level Encrypted Secret Volume at `/data/adb/.hollow_vault` (chmod 700, chown 0:0).
 *
 * Why this is immune to "Clear App Data" and backup/restore attacks:
 * 1. Stored in `/data/adb/.hollow_vault` outside `/data/data/com.example/`.
 * 2. When a user clears app storage or reinstalls the APK, `/data/adb/.hollow_vault` remains intact.
 * 3. Encrypted with AES-256-GCM using a key derived from the physical hardware fingerprint (SoC + AID + CID).
 * 4. Stores both `license.enc` (token + monotonic clock) and `modules/<moduleId>.dex.enc` (downloaded modules).
 */
object RootLicenseVaultManager {

    const val ROOT_VAULT_DIR = "/data/adb/.hollow_vault"
    private const val ROOT_MODULES_DIR = "$ROOT_VAULT_DIR/modules"
    private const val ROOT_LICENSE_FILE = "$ROOT_VAULT_DIR/license.enc"

    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /**
     * Initializes `/data/adb/.hollow_vault` via `su` when root is available,
     * and maintains a hardware-encrypted fallback mirror for non-root / JVM test environments.
     */
    fun ensureSecretVolumeProvisioned(context: Context): Boolean {
        val rootCreated = execRootCommand(
            "mkdir -p $ROOT_MODULES_DIR && chmod 700 $ROOT_VAULT_DIR && chmod 700 $ROOT_MODULES_DIR && chown -R 0:0 $ROOT_VAULT_DIR"
        )
        val fallbackDir = getFallbackVaultDir(context)
        if (!fallbackDir.exists()) fallbackDir.mkdirs()
        val fallbackMods = File(fallbackDir, "modules")
        if (!fallbackMods.exists()) fallbackMods.mkdirs()
        return rootCreated
    }

    fun readVaultRecord(context: Context): RootVaultLicenseRecord {
        val hw = DeviceHardwareFingerprint.collect(context)
        val rawBase64 = readRawFileFromRootOrFallback(context, ROOT_LICENSE_FILE, "license.enc")
        if (rawBase64.isBlank()) {
            return RootVaultLicenseRecord(
                status = LicenseAccountStatus.UNREGISTERED,
                hardwareFingerprint = hw.fingerprintHex
            )
        }

        val decryptedJson = decryptWithHardwareKey(context, rawBase64) ?: return RootVaultLicenseRecord(
            status = LicenseAccountStatus.REVOKED,
            hardwareFingerprint = hw.fingerprintHex,
            banReason = "Hardware signature mismatch or tampered root vault."
        )

        return try {
            val obj = JSONObject(decryptedJson)
            val savedHw = obj.optString("hardwareFingerprint", "")
            if (savedHw.isNotBlank() && savedHw != hw.fingerprintHex) {
                return RootVaultLicenseRecord(
                    status = LicenseAccountStatus.REVOKED,
                    hardwareFingerprint = hw.fingerprintHex,
                    banReason = "License vault belongs to a different hardware fingerprint."
                )
            }

            val status = try {
                LicenseAccountStatus.valueOf(obj.optString("status", LicenseAccountStatus.UNREGISTERED.name))
            } catch (_: Throwable) {
                LicenseAccountStatus.UNREGISTERED
            }

            val allowedArr = obj.optJSONArray("allowedModules") ?: JSONArray()
            val allowedSet = mutableSetOf<String>()
            for (i in 0 until allowedArr.length()) {
                allowedSet.add(allowedArr.getString(i))
            }

            val downloadedSet = listDownloadedModulesInVault(context)

            RootVaultLicenseRecord(
                status = status,
                username = obj.optString("username", ""),
                email = obj.optString("email", ""),
                hardwareFingerprint = hw.fingerprintHex,
                licenseToken = obj.optString("licenseToken", ""),
                signature = obj.optString("signature", ""),
                allowedModules = allowedSet,
                downloadedModules = downloadedSet,
                issuedAtMs = obj.optLong("issuedAtMs", 0L),
                validUntilMs = obj.optLong("validUntilMs", 0L),
                highWaterWallClockMs = obj.optLong("highWaterWallClockMs", 0L),
                lastElapsedRealtimeMs = obj.optLong("lastElapsedRealtimeMs", 0L),
                banReason = obj.optString("banReason", "")
            )
        } catch (_: Throwable) {
            RootVaultLicenseRecord(
                status = LicenseAccountStatus.UNREGISTERED,
                hardwareFingerprint = hw.fingerprintHex
            )
        }
    }

    fun writeVaultRecord(context: Context, record: RootVaultLicenseRecord) {
        ensureSecretVolumeProvisioned(context)
        val nowWall = maxOf(System.currentTimeMillis(), record.highWaterWallClockMs)
        val nowElapsed = SystemClock.elapsedRealtime()

        val allowedArr = JSONArray()
        record.allowedModules.forEach { allowedArr.put(it) }

        val obj = JSONObject()
            .put("status", record.status.name)
            .put("username", record.username)
            .put("email", record.email)
            .put("hardwareFingerprint", record.hardwareFingerprint)
            .put("licenseToken", record.licenseToken)
            .put("signature", record.signature)
            .put("allowedModules", allowedArr)
            .put("issuedAtMs", record.issuedAtMs)
            .put("validUntilMs", record.validUntilMs)
            .put("highWaterWallClockMs", nowWall)
            .put("lastElapsedRealtimeMs", nowElapsed)
            .put("banReason", record.banReason)

        val encryptedB64 = encryptWithHardwareKey(context, obj.toString())
        writeRawFileToRootAndFallback(context, ROOT_LICENSE_FILE, "license.enc", encryptedB64)
    }

    /**
     * Saves a downloaded module's encrypted binary into `/data/adb/.hollow_vault/modules/<moduleId>.dex.enc`.
     * Because it lives in `/data/adb/.hollow_vault`, clearing app data does NOT delete downloaded modules!
     */
    fun saveDownloadedModuleToVault(context: Context, moduleId: String, modulePayloadJson: String): Boolean {
        ensureSecretVolumeProvisioned(context)
        val encrypted = encryptWithHardwareKey(context, modulePayloadJson)
        val rootPath = "$ROOT_MODULES_DIR/$moduleId.dex.enc"
        val relativeName = "modules/$moduleId.dex.enc"
        writeRawFileToRootAndFallback(context, rootPath, relativeName, encrypted)
        return true
    }

    fun deleteDownloadedModuleFromVault(context: Context, moduleId: String) {
        val rootPath = "$ROOT_MODULES_DIR/$moduleId.dex.enc"
        val stateRootPath = "$ROOT_MODULES_DIR/${moduleId}_state.enc"
        execRootCommand("rm -f $rootPath $stateRootPath")
        try {
            File(getFallbackVaultDir(context), "modules/$moduleId.dex.enc").delete()
            File(getFallbackVaultDir(context), "modules/${moduleId}_state.enc").delete()
        } catch (_: Throwable) {
        }
    }

    /**
     * Reads and decrypts the JSON manifest/payload of a downloaded module from
     * `/data/adb/.hollow_vault/modules/<moduleId>.dex.enc`.
     */
    fun readDownloadedModulePayload(context: Context, moduleId: String): String? {
        val rootPath = "$ROOT_MODULES_DIR/$moduleId.dex.enc"
        val relativeName = "modules/$moduleId.dex.enc"
        val raw = readRawFileFromRootOrFallback(context, rootPath, relativeName)
        if (raw.isBlank()) return null
        return decryptWithHardwareKey(context, raw)
    }

    fun readModuleToggleState(
        context: Context,
        moduleId: String,
        featureKey: String,
        defaultValue: Boolean = true
    ): Boolean {
        val rootPath = "$ROOT_MODULES_DIR/${moduleId}_state.enc"
        val relativeName = "modules/${moduleId}_state.enc"
        val raw = readRawFileFromRootOrFallback(context, rootPath, relativeName)
        if (raw.isBlank()) return defaultValue
        val decrypted = decryptWithHardwareKey(context, raw) ?: return defaultValue
        return try {
            JSONObject(decrypted).optBoolean(featureKey, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }
    }

    fun saveModuleToggleState(
        context: Context,
        moduleId: String,
        featureKey: String,
        enabled: Boolean
    ) {
        ensureSecretVolumeProvisioned(context)
        val rootPath = "$ROOT_MODULES_DIR/${moduleId}_state.enc"
        val relativeName = "modules/${moduleId}_state.enc"
        val raw = readRawFileFromRootOrFallback(context, rootPath, relativeName)
        val currentJson = try {
            val dec = if (raw.isNotBlank()) decryptWithHardwareKey(context, raw) else null
            if (!dec.isNullOrBlank()) JSONObject(dec) else JSONObject()
        } catch (_: Throwable) {
            JSONObject()
        }
        currentJson.put(featureKey, enabled)
        val encrypted = encryptWithHardwareKey(context, currentJson.toString())
        writeRawFileToRootAndFallback(context, rootPath, relativeName, encrypted)
    }

    fun listDownloadedModulesInVault(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        // Check root directory first
        val rootList = execRootCommandWithOutput("ls $ROOT_MODULES_DIR 2>/dev/null")
        rootList.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.endsWith(".dex.enc")) {
                result.add(trimmed.removeSuffix(".dex.enc"))
            }
        }
        // Check fallback mirror
        val fallbackMods = File(getFallbackVaultDir(context), "modules")
        fallbackMods.listFiles()?.forEach { file ->
            if (file.name.endsWith(".dex.enc")) {
                val modId = file.name.removeSuffix(".dex.enc")
                // Verify it decrypts with this device's hardware key
                val raw = try { file.readText().trim() } catch (_: Throwable) { "" }
                if (raw.isNotEmpty() && decryptWithHardwareKey(context, raw) != null) {
                    result.add(modId)
                }
            }
        }
        return result
    }

    /**
     * Triggered when the Admin bans the device: wipes `/data/adb/.hollow_vault/modules`
     * and records the permanent BAN state in the root vault.
     */
    fun wipeModulesAndLockOnBan(context: Context, banReason: String) {
        val hw = DeviceHardwareFingerprint.collect(context)
        execRootCommand("rm -rf $ROOT_MODULES_DIR/*")
        try {
            File(getFallbackVaultDir(context), "modules").listFiles()?.forEach { it.delete() }
        } catch (_: Throwable) {
        }
        val bannedRecord = RootVaultLicenseRecord(
            status = LicenseAccountStatus.BANNED,
            hardwareFingerprint = hw.fingerprintHex,
            allowedModules = emptySet(),
            downloadedModules = emptySet(),
            banReason = banReason.ifBlank { "Device permanently banned by Administrator." },
            highWaterWallClockMs = System.currentTimeMillis(),
            lastElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
        writeVaultRecord(context, bannedRecord)
    }

    fun verifyVaultClockIntegrity(context: Context): MonotonicClockGuard.ClockVerificationResult {
        val rec = readVaultRecord(context)
        val check = MonotonicClockGuard.verifyLicenseWindow(
            issuedServerTimeMs = rec.issuedAtMs,
            expiresServerTimeMs = rec.validUntilMs,
            lastRecordedWallClockMs = rec.highWaterWallClockMs,
            lastRecordedElapsedRealtimeMs = rec.lastElapsedRealtimeMs
        )
        if (check.isValid && rec.status == LicenseAccountStatus.APPROVED) {
            // Advance monotonic watermark
            writeVaultRecord(context, rec.copy(highWaterWallClockMs = check.effectiveCurrentTimeMs))
        }
        return check
    }

    private fun encryptWithHardwareKey(context: Context, plainText: String): String {
        val key = DeviceHardwareFingerprint.deriveHardwareAesKey(context)
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptWithHardwareKey(context: Context, encryptedBase64: String): String? {
        return try {
            val combined = Base64.decode(encryptedBase64.trim(), Base64.NO_WRAP)
            if (combined.size <= GCM_IV_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val key = DeviceHardwareFingerprint.deriveHardwareAesKey(context)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getFallbackVaultDir(context: Context): File {
        // Placed alongside app root dir to simulate external secret volume in non-root emulator
        val parent = context.filesDir.parentFile ?: context.filesDir
        return File(parent, ".hollow_secret_vault")
    }

    private fun writeRawFileToRootAndFallback(
        context: Context,
        rootPath: String,
        relativeName: String,
        content: String
    ) {
        try {
            val f = File(getFallbackVaultDir(context), relativeName)
            f.parentFile?.mkdirs()
            f.writeText(content)
        } catch (_: Throwable) {
        }
        execRootCommand("printf '%s' '$content' > $rootPath && chmod 600 $rootPath")
    }

    private fun readRawFileFromRootOrFallback(
        context: Context,
        rootPath: String,
        relativeName: String
    ): String {
        val rootContent = execRootCommandWithOutput("cat $rootPath 2>/dev/null").trim()
        if (rootContent.isNotEmpty()) return rootContent
        return try {
            val f = File(getFallbackVaultDir(context), relativeName)
            if (f.exists()) f.readText().trim() else ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun execRootCommand(cmd: String): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            proc.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun execRootCommandWithOutput(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            out
        } catch (_: Throwable) {
            ""
        }
    }
}
