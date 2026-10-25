package com.example.isolation.crypto

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.SecureRandom

/**
 * Manages vault lock state, auto-lock on background, in-memory cache wiping,
 * and secure credential verification.
 */
object VaultLockManager {
    private const val PREFS_LOCK = "hollow_security_prefs"
    private const val KEY_PIN_HASH = "vault_pin_hash"
    private const val KEY_SALT = "vault_pin_salt"
    private const val KEY_RECOVERY = "vault_recovery_key"

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Temporary memory cache registry to wipe on lock
    private val memoryCaches = mutableListOf<() -> Unit>()

    fun registerCacheWiper(wiper: () -> Unit) {
        synchronized(memoryCaches) {
            memoryCaches.add(wiper)
        }
    }

    fun lockVault(context: Context? = null) {
        _isLocked.value = true
        // Wipe all registered in-memory caches
        synchronized(memoryCaches) {
            for (wiper in memoryCaches) {
                try { wiper() } catch (_: Throwable) {}
            }
        }
        // Wipe temporary decrypted files in cacheDir
        context?.let { ctx ->
            try {
                val tempDir = File(ctx.cacheDir, "decrypted_temp")
                if (tempDir.exists()) {
                    tempDir.listFiles()?.forEach { it.delete() }
                }
            } catch (_: Throwable) {}
        }
    }

    fun unlockVault(enteredPin: String, context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE)
        val storedHash = prefs.getString(KEY_PIN_HASH, null)
        val storedSalt = prefs.getString(KEY_SALT, null)

        val isValid = if (storedHash == null || storedSalt == null) {
            // Default initial PIN: 1234
            enteredPin == "1234"
        } else {
            val salt = hexToBytes(storedSalt)
            val derived = AesVaultCipher.deriveKeyFromPassphrase(enteredPin.toCharArray(), salt)
            bytesToHex(derived.encoded) == storedHash
        }

        if (isValid) {
            _isLocked.value = false
        }
        return isValid
    }

    fun unlockWithBiometric() {
        _isLocked.value = false
    }

    fun setPasscode(newPin: String, context: Context) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = AesVaultCipher.deriveKeyFromPassphrase(newPin.toCharArray(), salt)
        val hash = bytesToHex(derived.encoded)
        val saltHex = bytesToHex(salt)

        context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_SALT, saltHex)
            .apply()
    }

    fun getOrGenerateRecoveryKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_LOCK, Context.MODE_PRIVATE)
        var recovery = prefs.getString(KEY_RECOVERY, null)
        if (recovery == null) {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            recovery = bytesToHex(bytes).chunked(4).joinToString("-").uppercase()
            prefs.edit().putString(KEY_RECOVERY, recovery).apply()
        }
        return recovery
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
