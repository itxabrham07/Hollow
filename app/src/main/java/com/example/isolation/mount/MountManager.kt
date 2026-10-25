package com.example.isolation.mount

import android.content.Context
import android.os.Build
import android.os.Environment
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
    val command: String,
    val output: String,
    val exitCode: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Root-backed per-app storage volumes.
 *
 * A volume is a directory owned by the manager that is bind-mounted over /storage/emulated/0
 * inside the mount namespace of ONE target app's processes, so only that app sees it and the
 * real shared storage is invisible to it. Nothing here pretends to succeed: every operation runs
 * real commands through `su` and reports the real exit code and output.
 *
 * Limits that cannot be fixed from userspace: a mount applied to a running process does not reach
 * processes started later (the boot service re-attaches them shortly after they start), and the
 * system MediaProvider still serves the real MediaStore, which is why the LSPosed MediaStore hooks
 * stay active alongside the mount.
 */
object MountManager {

    const val VOLUME_MARKER = ".isovault_volume"
    private const val EXTERNAL_ROOT = "/storage/emulated/0"
    private const val SU_TIMEOUT_SECONDS = 20L

    // ---- Inspection (no root needed) -------------------------------------------------------

    fun isStorageManagerGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /** Reads the kernel mount table visible to this app. Returns an empty list if unreadable. */
    fun readSystemMounts(): List<MountEntry> {
        val mounts = mutableListOf<MountEntry>()
        for (path in listOf("/proc/self/mounts", "/proc/mounts")) {
            val file = File(path)
            if (!file.canRead()) continue
            try {
                BufferedReader(FileReader(file)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        val parts = line.split(" ")
                        if (parts.size >= 4) {
                            mounts.add(MountEntry(parts[0], parts[1], parts[2], parts[3]))
                        }
                    }
                }
                if (mounts.isNotEmpty()) return mounts
            } catch (_: Exception) {
            }
        }
        return mounts
    }

    /** "Enforcing", "Permissive" or "Unknown" (apps are normally not allowed to read the flag). */
    fun getSelinuxStatus(): String {
        return try {
            val file = File("/sys/fs/selinux/enforce")
            if (!file.canRead()) return "Unknown"
            when (file.readText().trim()) {
                "1" -> "Enforcing"
                "0" -> "Permissive"
                else -> "Unknown"
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    // ---- Root execution --------------------------------------------------------------------

    private data class ShellResult(val exitCode: Int, val output: String, val timedOut: Boolean)

    private fun runAsRoot(script: String): ShellResult {
        val process = try {
            ProcessBuilder("su").redirectErrorStream(true).start()
        } catch (e: Exception) {
            return ShellResult(127, "su is not available: ${e.message}", false)
        }

        val output = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        try {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(script)
                writer.write("\nexit\n")
            }
        } catch (_: Exception) {
        }

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(SU_TIMEOUT_SECONDS)
        var exit: Int? = null
        while (exit == null && System.currentTimeMillis() < deadline) {
            exit = try {
                process.exitValue()
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
                null
            }
        }
        val timedOut = exit == null
        if (timedOut) process.destroy()
        reader.join(1000)
        return ShellResult(exit ?: 124, output.toString().trim(), timedOut)
    }

    fun isRootAvailable(): Boolean {
        val result = runAsRoot("id -u")
        return result.exitCode == 0 && result.output.lineSequence().any { it.trim() == "0" }
    }

    // ---- Volumes ---------------------------------------------------------------------------

    fun volumeDir(context: Context, packageName: String): File =
        File(context.filesDir, "vaults/$packageName/storage")

    private fun requirePackageName(packageName: String) {
        require(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(packageName)) {
            "Invalid package name"
        }
    }

    /**
     * Shell snippet that defines pids_of() and prepares the volume directory for [packageName]:
     * ownership and SELinux label are copied from the app's own data directory so the app can use
     * the mounted directory as ordinary storage.
     */
    private fun commonShell(packageName: String, volume: String): String = """
        PKG='$packageName'
        VOL='$volume'
        MARK="${'$'}VOL/$VOLUME_MARKER"
        pids_of() {
          for p in /proc/[0-9]*; do
            n=${'$'}(tr '\0' '\n' < "${'$'}p/cmdline" 2>/dev/null | head -n1)
            case "${'$'}n" in
              "${'$'}PKG"|"${'$'}PKG":*) echo "${'$'}{p#/proc/}" ;;
            esac
          done
        }
    """.trimIndent()

    private fun attachShell(packageName: String, volume: String): String = commonShell(packageName, volume) + """

        APP_DATA="/data/user/0/${'$'}PKG"
        [ -d "${'$'}APP_DATA" ] || APP_DATA="/data/data/${'$'}PKG"
        [ -d "${'$'}APP_DATA" ] || { echo "ERROR: ${'$'}PKG is not installed for user 0"; exit 2; }
        UID_APP=${'$'}(stat -c %u "${'$'}APP_DATA")
        CTX=${'$'}(ls -dZ "${'$'}APP_DATA" | awk '{print ${'$'}1}')
        mkdir -p "${'$'}VOL" "${'$'}VOL/Android/data/${'$'}PKG" "${'$'}VOL/Android/media/${'$'}PKG" || exit 3
        : > "${'$'}MARK"
        chown -R "${'$'}UID_APP:${'$'}UID_APP" "${'$'}VOL" || exit 4
        chmod 700 "${'$'}VOL"
        case "${'$'}CTX" in u:object_r:*) chcon -R "${'$'}CTX" "${'$'}VOL" || echo "WARN: chcon failed (SELinux label not copied)";; esac
        PIDS=${'$'}(pids_of)
        [ -n "${'$'}PIDS" ] || { echo "ERROR: ${'$'}PKG is not running. Open the app, then attach."; exit 5; }
        RC=0
        for pid in ${'$'}PIDS; do
          if nsenter -t "${'$'}pid" -m test -f "$EXTERNAL_ROOT/$VOLUME_MARKER" 2>/dev/null; then
            echo "pid ${'$'}pid: already attached"
            continue
          fi
          if nsenter -t "${'$'}pid" -m mount --bind "${'$'}VOL" "$EXTERNAL_ROOT"; then
            echo "pid ${'$'}pid: attached"
          else
            echo "pid ${'$'}pid: mount FAILED"
            RC=6
          fi
        done
        exit ${'$'}RC
    """.trimIndent()

    private fun detachShell(packageName: String, volume: String): String = commonShell(packageName, volume) + """

        PIDS=${'$'}(pids_of)
        [ -n "${'$'}PIDS" ] || { echo "${'$'}PKG is not running; nothing to detach"; exit 0; }
        RC=0
        for pid in ${'$'}PIDS; do
          # Only ever unmount a volume that carries our marker, never the real storage.
          if nsenter -t "${'$'}pid" -m test -f "$EXTERNAL_ROOT/$VOLUME_MARKER" 2>/dev/null; then
            if nsenter -t "${'$'}pid" -m umount -l "$EXTERNAL_ROOT"; then
              echo "pid ${'$'}pid: detached"
            else
              echo "pid ${'$'}pid: umount FAILED"
              RC=6
            fi
          else
            echo "pid ${'$'}pid: not attached"
          fi
        done
        exit ${'$'}RC
    """.trimIndent()

    private fun statusShell(packageName: String, volume: String): String = commonShell(packageName, volume) + """

        PIDS=${'$'}(pids_of)
        [ -n "${'$'}PIDS" ] || { echo "not running"; exit 0; }
        for pid in ${'$'}PIDS; do
          if nsenter -t "${'$'}pid" -m test -f "$EXTERNAL_ROOT/$VOLUME_MARKER" 2>/dev/null; then
            echo "pid ${'$'}pid: attached"
          else
            echo "pid ${'$'}pid: not attached"
          fi
        done
    """.trimIndent()

    fun attachVolume(context: Context, packageName: String): MountExecutionResult {
        requirePackageName(packageName)
        return execute("attach $packageName", attachShell(packageName, volumeDir(context, packageName).absolutePath))
    }

    fun detachVolume(context: Context, packageName: String): MountExecutionResult {
        requirePackageName(packageName)
        return execute("detach $packageName", detachShell(packageName, volumeDir(context, packageName).absolutePath))
    }

    fun volumeStatus(context: Context, packageName: String): MountExecutionResult {
        requirePackageName(packageName)
        return execute("status $packageName", statusShell(packageName, volumeDir(context, packageName).absolutePath))
    }

    /** Detaches the volume, then deletes it as root (the target app's UID owns its contents). */
    fun purgeVolume(context: Context, packageName: String): MountExecutionResult {
        requirePackageName(packageName)
        val base = File(context.filesDir, "vaults/$packageName").absolutePath
        val volume = volumeDir(context, packageName).absolutePath
        val script = detachShell(packageName, volume) + "\n" +
            "case '$base' in */vaults/$packageName) rm -rf '$base' && echo purged ;; *) echo refused; exit 7 ;; esac"
        return execute("purge $packageName", script)
    }

    private fun execute(label: String, script: String): MountExecutionResult {
        val result = runAsRoot(script)
        val output = when {
            result.timedOut -> "Timed out after ${SU_TIMEOUT_SECONDS}s\n${result.output}"
            else -> result.output
        }
        return MountExecutionResult(
            isSuccess = result.exitCode == 0,
            command = label,
            output = output.ifBlank { "(no output)" },
            exitCode = result.exitCode
        )
    }

    /**
     * Boot service for Magisk / KernelSU / APatch (service.d). It watches for the given packages
     * and attaches their volume to each new process shortly after it starts. Apps that touch
     * storage in their first moments of life can still see the real storage; the zygote-level
     * route that closes that window needs a native Zygisk module.
     */
    fun generateBootService(context: Context, packages: List<String>): String {
        val valid = packages.filter { Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$").matches(it) }
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        sb.appendLine("# IsoVault volume attach service (generated by IsoVault Manager)")
        sb.appendLine("# Attaches each isolated app's volume to its processes after they start.")
        sb.appendLine("until [ \"\$(getprop sys.boot_completed)\" = \"1\" ]; do sleep 5; done")
        sb.appendLine()
        sb.appendLine("attach_pid() {")
        sb.appendLine("  pkg=\"\$1\"; vol=\"\$2\"; pid=\"\$3\"")
        sb.appendLine("  nsenter -t \"\$pid\" -m test -f \"$EXTERNAL_ROOT/$VOLUME_MARKER\" 2>/dev/null && return 0")
        sb.appendLine("  nsenter -t \"\$pid\" -m mount --bind \"\$vol\" \"$EXTERNAL_ROOT\"")
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("prepare_volume() {")
        sb.appendLine("  pkg=\"\$1\"; vol=\"\$2\"")
        sb.appendLine("  app=\"/data/user/0/\$pkg\"")
        sb.appendLine("  [ -d \"\$app\" ] || return 1")
        sb.appendLine("  uid=\$(stat -c %u \"\$app\")")
        sb.appendLine("  ctx=\$(ls -dZ \"\$app\" | awk '{print \$1}')")
        sb.appendLine("  mkdir -p \"\$vol\" \"\$vol/Android/data/\$pkg\" \"\$vol/Android/media/\$pkg\"")
        sb.appendLine("  : > \"\$vol/$VOLUME_MARKER\"")
        sb.appendLine("  chown -R \"\$uid:\$uid\" \"\$vol\"; chmod 700 \"\$vol\"")
        sb.appendLine("  case \"\$ctx\" in u:object_r:*) chcon -R \"\$ctx\" \"\$vol\" ;; esac")
        sb.appendLine("}")
        sb.appendLine()
        sb.appendLine("while true; do")
        for (pkg in valid) {
            val vol = volumeDir(context, pkg).absolutePath
            sb.appendLine("  [ -f '$vol/$VOLUME_MARKER' ] || prepare_volume '$pkg' '$vol'")
            sb.appendLine("  for p in /proc/[0-9]*; do")
            sb.appendLine("    n=\$(tr '\\0' '\\n' < \"\$p/cmdline\" 2>/dev/null | head -n1)")
            sb.appendLine("    case \"\$n\" in '$pkg'|'$pkg':*) attach_pid '$pkg' '$vol' \"\${p#/proc/}\" ;; esac")
            sb.appendLine("  done")
        }
        sb.appendLine("  sleep 2")
        sb.appendLine("done")
        return sb.toString()
    }
}
