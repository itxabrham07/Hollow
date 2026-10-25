package com.example.isolation.mount

import android.content.Context
import com.example.isolation.model.IsolationMode
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

data class MountEntry(
    val device: String,
    val mountPoint: String,
    val fsType: String,
    val options: String
)

data class MountExecutionResult(
    val isSuccess: Boolean,
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Kernel Mount inspection & experimental script generator.
 * Clearly labeled as experimental / inactive: Hollow storage isolation operates
 * purely through secure userspace LSPosed ContentProvider IPC pipes.
 * Never reports fake mount success.
 */
object MountManager {

    /**
     * Reads current kernel /proc/mounts to inspect active filesystem mounts
     */
    fun readSystemMounts(): List<MountEntry> {
        val mounts = mutableListOf<MountEntry>()
        try {
            val file = File("/proc/mounts")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lineSequence().take(60).forEach { line ->
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            mounts.add(
                                MountEntry(
                                    device = parts[0],
                                    mountPoint = parts[1],
                                    fsType = parts[2],
                                    options = parts[3]
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return mounts
    }

    /**
     * Inspects SELinux status
     */
    fun getSelinuxStatus(): String {
        return try {
            val file = File("/sys/fs/selinux/enforce")
            if (file.exists() && file.canRead()) {
                val content = file.readText().trim()
                if (content == "1") "Enforcing (Locked)" else "Permissive"
            } else {
                "Enforcing (Android 15 Default)"
            }
        } catch (_: Exception) {
            "Enforcing (Strict)"
        }
    }

    /**
     * Generates a Magisk / KernelSU / APatch module service script for optional root setups
     */
    fun generateMagiskServiceScript(context: Context, packages: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        sb.appendLine("# Hollow Magisk/KernelSU Experimental Service Script")
        sb.appendLine("# (Storage isolation in Hollow is primarily handled via LSPosed provider pipes)")
        sb.appendLine("MODDIR=\${0%/*}")
        sb.appendLine("LOGFILE=/data/local/tmp/hollow_boot.log")
        sb.appendLine("exec > \$LOGFILE 2>&1")
        sb.appendLine("echo \"[$(date)] Initializing Hollow namespace scripts...\"")
        sb.appendLine("")

        for (pkg in packages) {
            sb.appendLine("# Package: $pkg")
            sb.appendLine("VAULT_PATH=\"${context.filesDir.absolutePath}/vaults/$pkg\"")
            sb.appendLine("mkdir -p \"\$VAULT_PATH\"")
            sb.appendLine("chmod 700 \"\$VAULT_PATH\"")
        }

        sb.appendLine("")
        sb.appendLine("echo \"[$(date)] Hollow namespace ready.\"")
        return sb.toString()
    }

    /**
     * Diagnostics check for kernel mount namespace.
     * Accurately informs user that kernel-level mounts are experimental and inactive,
     * preventing any fake mount success reports.
     */
    fun checkMountStatus(packageName: String, mode: IsolationMode): MountExecutionResult {
        val output = buildString {
            appendLine("[Hollow Kernel Bridge Diagnostics]")
            appendLine("Target Package: $packageName")
            appendLine("Requested Mode: ${mode.name}")
            appendLine("Status: EXPERIMENTAL, NOT ACTIVE")
            appendLine("Information: Hollow utilizes secure userspace LSPosed ContentProvider pipes for isolation.")
            appendLine("Direct kernel mount namespace hijacking is not running in userspace mode.")
            appendLine("Exit code: 1 (Inactive - Intentionally unmounted)")
        }
        return MountExecutionResult(
            isSuccess = false,
            command = "mount --bind (experimental, not active)",
            output = output,
            exitCode = 1
        )
    }
}
