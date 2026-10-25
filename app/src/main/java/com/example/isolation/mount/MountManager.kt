package com.example.isolation.mount

import android.content.Context
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.IsolationRuleManager
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.TimeUnit

data class MountEntry(
    val device: String,
    val mountPoint: String,
    val fsType: String,
    val options: String
)

data class MountExecutionResult(
    val isSuccess: Boolean,
    val isExperimentalInactive: Boolean = false,
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

object MountManager {

    @Volatile
    var isRootAvailableCached: Boolean? = null

    /**
     * Checks and requests root access via `su -c id` ONLY when explicitly triggered by the user.
     * Never called automatically on startup.
     */
    fun checkAndRequestRoot(vaultRootDir: File? = null): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(3, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                isRootAvailableCached = false
                return false
            }
            val output = process.inputStream.bufferedReader().readText()
            val hasRoot = output.contains("uid=0")
            isRootAvailableCached = hasRoot
            if (hasRoot && vaultRootDir != null) {
                ensureVaultDirectories(vaultRootDir)
            }
            hasRoot
        } catch (_: Throwable) {
            isRootAvailableCached = false
            false
        }
    }

    /**
     * Executes a command via `su -c` when root is explicitly enabled.
     */
    fun runRootCommand(cmd: String, timeoutSeconds: Long = 5): MountExecutionResult {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return MountExecutionResult(
                    isSuccess = false,
                    command = cmd,
                    output = "Command timed out after ${timeoutSeconds}s",
                    exitCode = -1
                )
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val code = process.exitValue()
            MountExecutionResult(
                isSuccess = code == 0,
                command = cmd,
                output = output.ifEmpty { "Executed (exit 0)" },
                exitCode = code
            )
        } catch (e: Throwable) {
            MountExecutionResult(
                isSuccess = false,
                command = cmd,
                output = "Root execution unavailable: ${e.message}",
                exitCode = -1
            )
        }
    }

    /**
     * Initializes standard volume directories inside the app-private / external vault directory
     * without requiring root or broad storage permissions.
     */
    fun ensureVaultDirectories(vaultRoot: File) {
        try {
            if (!vaultRoot.exists()) vaultRoot.mkdirs()
            val nomedia = File(vaultRoot, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
            File(vaultRoot, "sandboxes").mkdirs()
            val sharedDir = File(vaultRoot, "sandboxes/${IsolationRuleManager.SHARED_VOLUME_ID}")
            IsolationRuleManager.ensureStandardVolumeFolders(sharedDir)
        } catch (_: Throwable) {}
    }

    /**
     * Force-stops a target application using root so that new LSPosed rules take effect immediately.
     */
    fun forceStopPackageViaRoot(packageName: String): MountExecutionResult {
        if (isRootAvailableCached != true) {
            return MountExecutionResult(
                isSuccess = false,
                isExperimentalInactive = true,
                command = "am force-stop $packageName",
                output = "Root namespace helper is inactive. Force-stop skipped; LSPosed hooks apply on next app launch.",
                exitCode = -1
            )
        }
        return runRootCommand("am force-stop $packageName")
    }

    /**
     * Reads current kernel /proc/mounts to inspect filesystem mounts (read-only diagnostic).
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
     * Inspects SELinux status without blocking on root.
     */
    fun getSelinuxStatus(): String {
        try {
            val file = File("/sys/fs/selinux/enforce")
            if (file.exists() && file.canRead()) {
                val content = file.readText().trim()
                return if (content == "1") "Enforcing" else "Permissive"
            }
        } catch (_: Exception) {}
        return "Enforcing"
    }

    /**
     * Prepares the volume directory hierarchy for an isolated package.
     * Never reports fake mount success: if root bind-mount is not active, clearly marks kernel mount
     * as experimental/inactive while user-space LSPosed hooks handle isolation.
     */
    fun prepareVolumeAndOptionalRootMount(
        vaultRoot: File,
        packageName: String,
        mode: IsolationMode
    ): MountExecutionResult {
        val sandboxDir = File(vaultRoot, "sandboxes/$packageName")
        IsolationRuleManager.ensureStandardVolumeFolders(sandboxDir)

        if (isRootAvailableCached == true) {
            val path = sandboxDir.absolutePath
            val script = """
                PID=${'$'}(pidof $packageName)
                if [ -n "${'$'}PID" ]; then
                    nsenter -t ${'$'}PID -m -- mount --bind "$path" /storage/emulated/0
                else
                    exit 2
                fi
            """.trimIndent()
            val res = runRootCommand(script)
            if (res.isSuccess) {
                return res
            }
        }

        // Honest status: kernel mount did NOT happen; user-space LSPosed hooks provide isolation
        return MountExecutionResult(
            isSuccess = false,
            isExperimentalInactive = true,
            command = "nsenter -m -- mount --bind (inactive)",
            output = "Kernel mount inactive (experimental). Volume folders prepared at ${sandboxDir.name}; isolation is enforced via user-space LSPosed hooks.",
            exitCode = -1
        )
    }
}
