package com.example.isolation.model

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object IsolationRuleManager {

    const val PREFS_NAME = "isovault_rules_prefs"
    const val SHARED_VOLUME_ID = "shared.vault"

    val DEFAULT_VOLUME_FOLDERS = listOf(
        "DCIM",
        "DCIM/Camera",
        "Pictures",
        "Download",
        "Movies",
        "Music",
        "Documents"
    )

    data class Rule(
        val packageName: String,
        val mode: IsolationMode,
        val blockRootPollution: Boolean,
        val whitelistedFolders: Set<String>,
        val useSharedSpace: Boolean = false,
        val volumeQuotaMb: Int = 0
    )

    private val activeRules = ConcurrentHashMap<String, Rule>()

    // Callback to record real-time interceptions in the UI & Room database
    var onAccessIntercepted: ((pkg: String, path: String, action: AuditAction, details: String) -> Unit)? = null

    private val STANDARD_ANDROID_DIRS = setOf(
        "Android", "DCIM", "Pictures", "Download", "Documents",
        "Movies", "Music", "Podcasts", "Ringtones", "Alarms",
        "Notifications", "Audiobooks", "Recordings", "Shared"
    )

    @Volatile
    var isSimulatedHookActive: Boolean = false

    fun updateRule(
        packageName: String,
        mode: IsolationMode,
        blockRootPollution: Boolean,
        whitelistedFolders: Set<String>,
        useSharedSpace: Boolean = false,
        volumeQuotaMb: Int = 0
    ) {
        activeRules[packageName] = Rule(
            packageName = packageName,
            mode = mode,
            blockRootPollution = blockRootPollution,
            whitelistedFolders = whitelistedFolders,
            useSharedSpace = useSharedSpace,
            volumeQuotaMb = volumeQuotaMb
        )
        syncPrefsIfPossible()
    }

    fun removeRule(packageName: String) {
        activeRules.remove(packageName)
        syncPrefsIfPossible()
    }

    fun getRule(packageName: String): Rule? = activeRules[packageName]

    fun getAllActiveRules(): Map<String, Rule> = activeRules.toMap()

    /**
     * Stores rules strictly in Context.MODE_PRIVATE (never MODE_WORLD_READABLE).
     * Hooked apps retrieve their rule via the UID-verified IsoVaultConfigProvider and cache it in memory.
     */
    private fun syncPrefsIfPossible() {
        try {
            val app = com.example.IsoVaultApp.instanceOrNull() ?: return
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit().clear()
            for ((pkg, rule) in activeRules) {
                val whitelistStr = rule.whitelistedFolders.joinToString(",")
                val serialized = "${rule.mode.name}|${rule.blockRootPollution}|$whitelistStr|${rule.useSharedSpace}|${rule.volumeQuotaMb}"
                editor.putString("rule_$pkg", serialized)
            }
            editor.putLong("last_updated", System.currentTimeMillis())
            editor.apply()
        } catch (_: Throwable) {}
    }

    /**
     * Parses a serialized rule string.
     */
    fun parseRuleString(packageName: String, serialized: String): Rule? {
        if (serialized.isBlank()) return null
        val parts = serialized.split("|")
        if (parts.size < 2) return null
        val mode = IsolationMode.entries.find { it.name == parts[0] } ?: IsolationMode.ENCRYPTED_VAULT
        val blockRoot = parts[1].toBooleanStrictOrNull() ?: true
        val whitelist = parts.getOrNull(2)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()
        val useShared = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
        val quotaMb = parts.getOrNull(4)?.toIntOrNull() ?: 0
        return Rule(
            packageName = packageName,
            mode = mode,
            blockRootPollution = blockRoot,
            whitelistedFolders = whitelist,
            useSharedSpace = useShared,
            volumeQuotaMb = quotaMb
        )
    }

    /**
     * Initializes the standard Android directory structure inside a volume directory.
     */
    fun ensureStandardVolumeFolders(volumeRoot: File) {
        try {
            if (!volumeRoot.exists()) volumeRoot.mkdirs()
            DEFAULT_VOLUME_FOLDERS.forEach { folder ->
                File(volumeRoot, folder).mkdirs()
            }
            // Also keep friendly aliases used by the Vault File Manager
            listOf("Photos", "Videos", "Audio", "Documents").forEach { folder ->
                File(volumeRoot, folder).mkdirs()
            }
            val nomedia = File(volumeRoot, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        } catch (_: Throwable) {}
    }

    /**
     * Fail-closed fallback rule when the Vault provider cannot be reached by a hooked process:
     * restricts the app to an isolated empty/private directory so real storage is never exposed.
     */
    fun createFailClosedRule(packageName: String): Rule {
        return Rule(
            packageName = packageName,
            mode = IsolationMode.FAKE_EMPTY,
            blockRootPollution = true,
            whitelistedFolders = emptySet(),
            useSharedSpace = false,
            volumeQuotaMb = 0
        )
    }

    /**
     * Evaluates a file path request from a hooked package and returns the redirected File
     * (or null if no redirection should happen).
     * Supports both Per-App Private Volume and Shared Space Volume when `rule.useSharedSpace == true`.
     */
    fun resolveRedirectedFile(
        packageName: String,
        originalPath: String,
        appSpecificSandboxDir: File,
        sharedVolumeDir: File? = null
    ): File? {
        val rule = activeRules[packageName] ?: return null

        val normalized = originalPath.trim()
        val sdcardPrefixes = listOf(
            "/storage/emulated/0",
            "/sdcard",
            "/mnt/sdcard"
        )

        val matchedPrefix = sdcardPrefixes.firstOrNull { normalized.startsWith(it) } ?: return null
        val relativePath = normalized.removePrefix(matchedPrefix).trimStart('/')

        // Ignore app's own official Android/data or Android/media directory
        if (relativePath.startsWith("Android/data/$packageName") ||
            relativePath.startsWith("Android/media/$packageName") ||
            relativePath.startsWith("Android/obb/$packageName")
        ) {
            return null
        }

        // Prevent infinite recursion if path is already inside the sandbox or shared volume
        if (normalized.startsWith(appSpecificSandboxDir.absolutePath)) {
            return null
        }
        if (sharedVolumeDir != null && normalized.startsWith(sharedVolumeDir.absolutePath)) {
            return null
        }
        if (normalized.startsWith("/storage/emulated/0/.IsoVault")) {
            return null
        }

        val topLevelFolder = relativePath.substringBefore('/').ifEmpty { "" }

        // Shared Space routing: if the app opted into Shared Space and accesses /storage/emulated/0/Shared/...
        if (rule.useSharedSpace && sharedVolumeDir != null &&
            (topLevelFolder.equals("Shared", ignoreCase = true) || relativePath.startsWith("Shared/"))
        ) {
            val sharedSubPath = relativePath.removePrefix("Shared").trimStart('/')
            val redirectedShared = if (sharedSubPath.isEmpty()) {
                sharedVolumeDir
            } else {
                File(sharedVolumeDir, sharedSubPath)
            }
            redirectedShared.parentFile?.mkdirs()
            onAccessIntercepted?.invoke(
                packageName,
                originalPath,
                AuditAction.REDIRECTED,
                "Routed to Shared Volume -> ${redirectedShared.absolutePath}"
            )
            return redirectedShared
        }

        // Check if folder is whitelisted for real shared storage access
        if (topLevelFolder.isNotEmpty() && rule.whitelistedFolders.any { it.equals(topLevelFolder, ignoreCase = true) }) {
            onAccessIntercepted?.invoke(
                packageName,
                originalPath,
                AuditAction.PERMITTED,
                "Whitelisted folder '$topLevelFolder' permitted"
            )
            return null
        }

        // Check root folder pollution blocking (e.g., /sdcard/.TempData, /sdcard/AppCustomFolder)
        if (rule.blockRootPollution && topLevelFolder.isNotEmpty() && topLevelFolder !in STANDARD_ANDROID_DIRS) {
            val redirectedRootFolder = File(appSpecificSandboxDir, "root_isolated/$relativePath")
            redirectedRootFolder.parentFile?.mkdirs()
            onAccessIntercepted?.invoke(
                packageName,
                originalPath,
                AuditAction.BLOCKED,
                "Blocked non-standard root folder '$topLevelFolder' -> Redirected to private volume"
            )
            return redirectedRootFolder
        }

        return when (rule.mode) {
            IsolationMode.ENCRYPTED_VAULT -> {
                val target = if (relativePath.isEmpty()) {
                    File(appSpecificSandboxDir, "vault_storage")
                } else {
                    File(appSpecificSandboxDir, "vault_storage/$relativePath")
                }
                target.parentFile?.mkdirs()
                onAccessIntercepted?.invoke(
                    packageName,
                    originalPath,
                    AuditAction.ENCRYPTED,
                    "AES-256-GCM Volume Stream -> ${target.absolutePath}"
                )
                target
            }

            IsolationMode.SANDBOX_REDIRECT -> {
                val target = if (relativePath.isEmpty()) {
                    File(appSpecificSandboxDir, "sandbox_storage")
                } else {
                    File(appSpecificSandboxDir, "sandbox_storage/$relativePath")
                }
                target.parentFile?.mkdirs()
                onAccessIntercepted?.invoke(
                    packageName,
                    originalPath,
                    AuditAction.REDIRECTED,
                    "Isolated Volume -> ${target.absolutePath}"
                )
                target
            }

            IsolationMode.FAKE_EMPTY -> {
                val emptyDir = File(appSpecificSandboxDir, "empty_spoof")
                if (!emptyDir.exists()) emptyDir.mkdirs()
                val target = if (relativePath.isEmpty()) emptyDir else File(emptyDir, relativePath)
                onAccessIntercepted?.invoke(
                    packageName,
                    originalPath,
                    AuditAction.SPOOFED_EMPTY,
                    "Returned spoofed empty directory"
                )
                target
            }

            IsolationMode.READ_ONLY -> {
                onAccessIntercepted?.invoke(
                    packageName,
                    originalPath,
                    AuditAction.PERMITTED,
                    "Read-Only mode inspected path"
                )
                null
            }
        }
    }
}
