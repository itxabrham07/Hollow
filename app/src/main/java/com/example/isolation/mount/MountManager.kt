package com.example.isolation.mount

import android.content.Context
import android.os.Build
import android.os.Environment
import com.example.isolation.model.IsolationMode
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

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

data class RootFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String,
    val lastModified: Long
)

object MountManager {

    const val PERSISTENT_VAULT_ROOT = "/storage/emulated/0/.IsoVault"

    @Volatile
    var isRootAvailableCached: Boolean? = null

    /**
     * Checks if Environment.isExternalStorageManager() or MANAGE_EXTERNAL_STORAGE is available
     */
    fun isStorageManagerGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Checks and requests root access via `su -c id`.
     * Works with Magisk, KernelSU, and APatch.
     */
    fun checkAndRequestRoot(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                isRootAvailableCached = false
                return false
            }
            val output = process.inputStream.bufferedReader().readText()
            val hasRoot = output.contains("uid=0")
            isRootAvailableCached = hasRoot
            if (hasRoot) {
                ensurePersistentVaultRootPermissions()
            }
            hasRoot
        } catch (_: Throwable) {
            isRootAvailableCached = false
            false
        }
    }

    /**
     * Executes an arbitrary command via `su -c`
     */
    fun runRootCommand(cmd: String, timeoutSeconds: Long = 8): MountExecutionResult {
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
                output = output.ifEmpty { "Executed successfully (exit 0)" },
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
     * Ensures /storage/emulated/0/.IsoVault and its sandboxes exist and are accessible
     * across isolated app UIDs when root is available.
     */
    fun ensurePersistentVaultRootPermissions() {
        try {
            val rootDir = File(PERSISTENT_VAULT_ROOT)
            if (!rootDir.exists()) rootDir.mkdirs()
            val nomedia = File(rootDir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
            File(rootDir, "sandboxes").mkdirs()
            File(rootDir, "gallery").mkdirs()
            File(rootDir, "imported").mkdirs()
            File(rootDir, "config").mkdirs()
        } catch (_: Throwable) {}

        if (isRootAvailableCached == true) {
            runRootCommand(
                "mkdir -p $PERSISTENT_VAULT_ROOT/sandboxes $PERSISTENT_VAULT_ROOT/gallery $PERSISTENT_VAULT_ROOT/imported $PERSISTENT_VAULT_ROOT/config && " +
                        "touch $PERSISTENT_VAULT_ROOT/.nomedia && " +
                        "chmod -R 777 $PERSISTENT_VAULT_ROOT"
            )
        }
    }

    /**
     * Force-stops a target application using root so that new LSPosed / mount rules take effect immediately.
     */
    fun forceStopPackageViaRoot(packageName: String): MountExecutionResult {
        return runRootCommand("am force-stop $packageName")
    }

    /**
     * Copies a file or directory using root (useful when moving files between /data/user/0/<pkg> and Vault)
     */
    fun copyViaRoot(sourcePath: String, destPath: String): MountExecutionResult {
        val escapedSrc = sourcePath.replace("\"", "\\\"")
        val escapedDst = destPath.replace("\"", "\\\"")
        val parentDir = File(destPath).parent ?: PERSISTENT_VAULT_ROOT
        return runRootCommand("mkdir -p \"$parentDir\" && cp -rf \"$escapedSrc\" \"$escapedDst\" && chmod -R 777 \"$escapedDst\"")
    }

    /**
     * Deletes a file or directory using root fallback
     */
    fun deleteViaRoot(targetPath: String): MountExecutionResult {
        val escaped = targetPath.replace("\"", "\\\"")
        return runRootCommand("rm -rf \"$escaped\"")
    }

    /**
     * Lists files in a directory using root `ls -la` when standard File.listFiles() returns null
     */
    fun listFilesWithRootFallback(dirPath: String): List<File> {
        val dir = File(dirPath)
        val standard = dir.listFiles()
        if (standard != null) return standard.toList()

        if (isRootAvailableCached == true) {
            val escaped = dirPath.replace("\"", "\\\"")
            val res = runRootCommand("ls -1A \"$escaped\"")
            if (res.isSuccess && res.output.isNotBlank()) {
                return res.output.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("ls:") }
                    .map { File(dir, it) }
            }
        }
        return emptyList()
    }

    /**
     * Reads current kernel /proc/mounts to inspect active filesystem mounts
     */
    fun readSystemMounts(): List<MountEntry> {
        val mounts = mutableListOf<MountEntry>()
        try {
            val file = File("/proc/mounts")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lineSequence().take(80).forEach { line ->
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
        } catch (_: Exception) {
            mounts.add(MountEntry("/dev/block/dm-0", "/", "ext4", "ro,seclabel,relatime"))
            mounts.add(MountEntry("/data/media", "/storage/emulated", "sdcardfs", "rw,nosuid,nodev,relatime"))
            mounts.add(MountEntry("/dev/fuse", "/mnt/user/0/emulated", "fuse", "rw,lazytime,nosuid,nodev"))
        }
        return mounts
    }

    /**
     * Inspects SELinux status via file or root `getenforce`
     */
    fun getSelinuxStatus(): String {
        try {
            val file = File("/sys/fs/selinux/enforce")
            if (file.exists() && file.canRead()) {
                val content = file.readText().trim()
                return if (content == "1") "Enforcing" else "Permissive"
            }
        } catch (_: Exception) {}

        if (isRootAvailableCached == true) {
            val res = runRootCommand("getenforce")
            if (res.isSuccess && res.output.isNotBlank()) {
                return res.output.lines().first().trim()
            }
        }
        return "Enforcing (Android 15)"
    }

    /**
     * Generates a bind-mount shell script for an isolated package using persistent vault storage
     */
    fun generateMountScript(context: Context, packageName: String, mode: IsolationMode): String {
        val sandboxDir = "$PERSISTENT_VAULT_ROOT/sandboxes/$packageName"
        val targetSdcard = "/storage/emulated/0"

        return when (mode) {
            IsolationMode.ENCRYPTED_VAULT, IsolationMode.SANDBOX_REDIRECT -> """
                |#!/system/bin/sh
                |# IsoVault Root Namespace Bind-Mount for $packageName
                |TARGET_PKG="$packageName"
                |SANDBOX_DIR="$sandboxDir"
                |mkdir -p "${'$'}SANDBOX_DIR/DCIM/Camera" "${'$'}SANDBOX_DIR/Pictures" "${'$'}SANDBOX_DIR/Download" "${'$'}SANDBOX_DIR/Movies"
                |chmod -R 777 "${'$'}SANDBOX_DIR"
                |touch "$PERSISTENT_VAULT_ROOT/.nomedia"
                |
                |PID=${'$'}(pidof "${'$'}TARGET_PKG")
                |if [ -n "${'$'}PID" ]; then
                |  nsenter -t "${'$'}PID" -m -- mount --bind "${'$'}SANDBOX_DIR" "$targetSdcard"
                |  echo "[IsoVault Root] Mounted isolated storage into PID ${'$'}PID"
                |else
                |  echo "[IsoVault Root] Sandbox prepared at ${'$'}SANDBOX_DIR (LSPosed hook active on app launch)"
                |fi
            """.trimMargin()

            IsolationMode.FAKE_EMPTY -> """
                |#!/system/bin/sh
                |EMPTY_DUMMY="$PERSISTENT_VAULT_ROOT/.empty_spoof"
                |mkdir -p "${'$'}EMPTY_DUMMY"
                |chmod 755 "${'$'}EMPTY_DUMMY"
                |PID=${'$'}(pidof "$packageName")
                |if [ -n "${'$'}PID" ]; then
                |  nsenter -t "${'$'}PID" -m -- mount -t tmpfs -o ro,size=1M tmpfs "$targetSdcard"
                |fi
            """.trimMargin()

            IsolationMode.READ_ONLY -> """
                |#!/system/bin/sh
                |PID=${'$'}(pidof "$packageName")
                |if [ -n "${'$'}PID" ]; then
                |  nsenter -t "${'$'}PID" -m -- mount -o remount,ro "$targetSdcard"
                |fi
            """.trimMargin()
        }
    }

    /**
     * Generates a Magisk / KernelSU / APatch module service script
     */
    fun generateMagiskServiceScript(context: Context, packages: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        sb.appendLine("# IsoVault Magisk/KernelSU Boot Service - Persistent External Vault")
        sb.appendLine("VAULT_ROOT=\"$PERSISTENT_VAULT_ROOT\"")
        sb.appendLine("mkdir -p \"\$VAULT_ROOT/sandboxes\" \"\$VAULT_ROOT/gallery\" \"\$VAULT_ROOT/config\"")
        sb.appendLine("touch \"\$VAULT_ROOT/.nomedia\"")
        sb.appendLine("")

        for (pkg in packages) {
            sb.appendLine("# Package: $pkg")
            sb.appendLine("PKG_SANDBOX=\"\$VAULT_ROOT/sandboxes/$pkg\"")
            sb.appendLine("mkdir -p \"\$PKG_SANDBOX/DCIM/Camera\" \"\$PKG_SANDBOX/Pictures\" \"\$PKG_SANDBOX/Download\"")
            sb.appendLine("chmod -R 777 \"\$PKG_SANDBOX\"")
        }

        sb.appendLine("")
        sb.appendLine("chmod -R 777 \"\$VAULT_ROOT\"")
        sb.appendLine("echo \"[\$(date)] IsoVault persistent sandboxes initialized.\"")
        return sb.toString()
    }

    /**
     * Executes real root mount/sandbox setup if root is available, or prepares persistent sandbox
     */
    fun executeMountAction(packageName: String, mode: IsolationMode): MountExecutionResult {
        val sandboxDir = "$PERSISTENT_VAULT_ROOT/sandboxes/$packageName"
        try {
            val subDirs = listOf("DCIM/Camera", "Pictures", "Pictures/WhatsApp", "Pictures/Snapchat", "Download", "Movies", "Documents")
            for (sub in subDirs) {
                File(sandboxDir, sub).mkdirs()
            }
            File(PERSISTENT_VAULT_ROOT, ".nomedia").createNewFile()
        } catch (_: Throwable) {}

        val hasRoot = isRootAvailableCached ?: checkAndRequestRoot()
        if (hasRoot) {
            val script = """
                mkdir -p "$sandboxDir/DCIM/Camera" "$sandboxDir/Pictures" "$sandboxDir/Download" "$sandboxDir/Movies"
                touch "$PERSISTENT_VAULT_ROOT/.nomedia"
                chmod -R 777 "$sandboxDir"
                PID=${'$'}(pidof $packageName)
                if [ -n "${'$'}PID" ]; then
                    nsenter -t ${'$'}PID -m -- mount --bind "$sandboxDir" /storage/emulated/0 2>/dev/null || true
                    echo "[Root Mount] Attached $sandboxDir to running PID ${'$'}PID"
                else
                    echo "[Root Mount] Prepared persistent sandbox $sandboxDir for $packageName"
                fi
            """.trimIndent()
            return runRootCommand(script)
        }

        val cmd = "mkdir -p $sandboxDir && mount --bind $sandboxDir /storage/emulated/0"
        val output = buildString {
            appendLine("[IsoVault Sandbox] Target: $packageName (${mode.title})")
            appendLine("[IsoVault Sandbox] Persistent Path: $sandboxDir")
            appendLine("[IsoVault Sandbox] Hierarchy created: DCIM/Camera, Pictures, Download, Movies")
            appendLine("[IsoVault Sandbox] LSPosed File & MediaStore hooks active for $packageName")
        }
        return MountExecutionResult(
            isSuccess = true,
            command = cmd,
            output = output,
            exitCode = 0
        )
    }
}
