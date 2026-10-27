package com.example.isolation.core.license

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class HardwareBindingInfo(
    val fingerprintHex: String,
    val socSerial: String,
    val androidId: String,
    val storageCid: String,
    val deviceModel: String
)

/**
 * Generates an immutable hardware-bound fingerprint and AES-256 key derived from:
 * 1. SoC / Bootloader Serial (ro.boot.serialno / Build.BOARD)
 * 2. Android Secure ID (Settings.Secure.ANDROID_ID)
 * 3. Flash Storage CID (/sys/block/mmcblk0/device/cid or /sys/block/sda/device/cid)
 */
object DeviceHardwareFingerprint {

    private const val PBKDF2_SALT = "HollowRootVaultSalt_v1_UID0"

    @Volatile
    private var cachedInfo: HardwareBindingInfo? = null

    @Volatile
    private var cachedAesKey: SecretKeySpec? = null

    fun collect(context: Context): HardwareBindingInfo {
        cachedInfo?.let { return it }

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() } ?: "9774d56d682e549c"
        } catch (_: Throwable) {
            "9774d56d682e549c"
        }

        val socSerial = readSystemProperty("ro.boot.serialno")
            .ifBlank { readSystemProperty("ro.serialno") }
            .ifBlank { "${Build.BOARD}_${Build.HARDWARE}_${Build.BOOTLOADER}" }

        val storageCid = readStorageCid().ifBlank {
            "cid_${Build.FINGERPRINT.hashCode().toUInt().toString(16)}"
        }

        val rawComposite = "SOC:$socSerial|AID:$androidId|CID:$storageCid|BRD:${Build.BOARD}"
        val digest = MessageDigest.getInstance("SHA-256").digest(rawComposite.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(32).uppercase()

        val info = HardwareBindingInfo(
            fingerprintHex = "HLW-${hex.substring(0, 8)}-${hex.substring(8, 16)}-${hex.substring(16, 24)}",
            socSerial = socSerial,
            androidId = androidId,
            storageCid = storageCid,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        cachedInfo = info
        return info
    }

    fun deriveHardwareAesKey(context: Context): SecretKeySpec {
        cachedAesKey?.let { return it }
        val info = collect(context)
        val spec = PBEKeySpec(
            "${info.fingerprintHex}:${info.socSerial}:${info.androidId}:${info.storageCid}".toCharArray(),
            PBKDF2_SALT.toByteArray(Charsets.UTF_8),
            12000,
            256
        )
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES").also { cachedAesKey = it }
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val completed = proc.waitFor(400, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!completed) {
                proc.destroyForcibly()
                return ""
            }
            BufferedReader(InputStreamReader(proc.inputStream)).use { it.readLine()?.trim().orEmpty() }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readStorageCid(): String {
        val paths = listOf(
            "/sys/block/mmcblk0/device/cid",
            "/sys/block/sda/device/cid",
            "/sys/class/block/mmcblk0/device/cid"
        )
        for (p in paths) {
            try {
                val file = java.io.File(p)
                if (file.exists() && file.canRead()) {
                    val text = file.readText().trim()
                    if (text.isNotEmpty()) return text
                }
            } catch (_: Throwable) {
            }
        }
        return ""
    }
}
