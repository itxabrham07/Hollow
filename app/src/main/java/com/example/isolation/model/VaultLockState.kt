package com.example.isolation.model

import android.content.Context

/**
 * Global Vault Lock State for fail-closed security.
 * When locked, all cross-process openFile() requests from hooked apps are rejected.
 */
object VaultLockState {
    @Volatile
    private var isRuntimeUnlocked: Boolean = false

    fun isLocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences("isovault_security_prefs", Context.MODE_PRIVATE)
        val pinHash = prefs.getString("master_pin_hash", "") ?: ""
        // If master PIN is not configured, the vault is unlocked by default
        if (pinHash.isBlank()) {
            return false
        }
        return !isRuntimeUnlocked
    }

    fun setUnlocked(unlocked: Boolean) {
        isRuntimeUnlocked = unlocked
    }

    fun lockNow() {
        isRuntimeUnlocked = false
    }
}
