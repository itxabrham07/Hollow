package com.example.isolation.hook

import android.os.Build
import com.example.BuildConfig
import com.example.isolation.sdk.hook.HollowHookEntry
import dalvik.system.DexClassLoader
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Zero-Code Xposed Hook Bridge for Hollow.
 *
 * Core APK contains ONLY this bridge stub. Zero feature-hook code is compiled into Core classes.dex.
 * When target processes or Zygote launch, HookBridge dynamically discovers verified module DEX files
 * from the secure staged directory, verifies their SHA-256 + Ed25519 signatures, and delegates
 * package load events to modules implementing [HollowHookEntry].
 *
 * Stability & Security Guardrails:
 * 1. Kill-Switch: If `/data/adb/.hollow_vault/DISABLE_HOOKS` exists, zero hooks are loaded.
 * 2. Fail-Closed: Corrupted, untrusted, or unsigned modules are rejected immediately.
 * 3. Crash Isolation: Every hook invocation is wrapped in try/catch to never crash system_server
 *    or target apps.
 */
class HollowHookBridge : IXposedHookZygoteInit, IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HollowHookBridge"
        private const val KILL_SWITCH_PATH = "/data/adb/.hollow_vault/DISABLE_HOOKS"
        private const val STAGED_HOOKS_DIR = "/data/local/tmp/.hollow_hooks"
        private const val FALLBACK_HOOKS_DIR = "/data/adb/.hollow_vault/staged_hooks"

        private val activeHookEntries = ConcurrentHashMap<String, HollowHookEntry>()
        private var isZygoteInitialized = false
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        if (isKillSwitchActive()) {
            XposedBridge.log("[$TAG] Kill-switch active ($KILL_SWITCH_PATH). Zero hooks initialized.")
            return
        }

        try {
            isZygoteInitialized = true
            val hookModules = loadAllVerifiedHookModules()
            for ((modId, entry) in hookModules) {
                try {
                    entry.initZygote(startupParam)
                } catch (t: Throwable) {
                    XposedBridge.log("[$TAG] Module $modId initZygote failed safely: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] initZygote safe catch: ${t.message}")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (isKillSwitchActive()) return

        val pkgName = lpparam.packageName ?: return

        try {
            val hookModules = if (activeHookEntries.isNotEmpty()) {
                activeHookEntries
            } else {
                loadAllVerifiedHookModules()
            }

            for ((modId, entry) in hookModules) {
                val targets = entry.hookTargets
                val matches = targets.isEmpty() ||
                        "*" in targets ||
                        pkgName in targets ||
                        (pkgName == "android" && "system_server" in targets)

                if (matches) {
                    try {
                        entry.handleLoadPackage(lpparam)
                    } catch (t: Throwable) {
                        XposedBridge.log("[$TAG] Suppressed hook exception in $modId for $pkgName: ${t.message}")
                    }
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] handleLoadPackage safe catch for $pkgName: ${t.message}")
        }
    }

    private fun isKillSwitchActive(): Boolean {
        return try {
            File(KILL_SWITCH_PATH).exists()
        } catch (_: Throwable) {
            false
        }
    }

    private fun loadAllVerifiedHookModules(): Map<String, HollowHookEntry> {
        val stagedDir = File(STAGED_HOOKS_DIR).takeIf { it.exists() && it.isDirectory }
            ?: File(FALLBACK_HOOKS_DIR).takeIf { it.exists() && it.isDirectory }
            ?: return emptyMap()

        val files = stagedDir.listFiles() ?: return emptyMap()
        val results = mutableMapOf<String, HollowHookEntry>()

        for (metaFile in files) {
            if (!metaFile.name.endsWith(".meta")) continue
            val moduleId = metaFile.name.removeSuffix(".meta")
            val dexFile = File(stagedDir, "$moduleId.dex")
            if (!dexFile.exists() || dexFile.length() < 8) continue

            try {
                val metaJson = JSONObject(metaFile.readText())
                val expectedSha256 = metaJson.optString("sha256", "").lowercase()
                val signatureB64 = metaJson.optString("signatureB64", "")
                val entryClass = metaJson.optString("entryClass", "")

                if (expectedSha256.length != 64 || signatureB64.isBlank() || entryClass.isBlank()) {
                    continue
                }

                val dexBytes = dexFile.readBytes()
                val actualSha256 = sha256Hex(dexBytes)
                if (actualSha256 != expectedSha256) {
                    XposedBridge.log("[$TAG] Checksum mismatch for hook module $moduleId. Rejecting.")
                    continue
                }

                val sigValid = verifyEd25519(
                    message = actualSha256.toByteArray(Charsets.UTF_8),
                    signatureB64 = signatureB64,
                    publicKeySpkiB64 = BuildConfig.HOLLOW_MODULE_PUBLIC_KEY_B64
                )
                if (!sigValid) {
                    XposedBridge.log("[$TAG] Ed25519 signature invalid for hook module $moduleId. Rejecting.")
                    continue
                }

                // Staged DEX verified: instantiate HollowHookEntry
                val optDir = File(stagedDir, "opt").apply { if (!exists()) mkdirs() }
                val cl = DexClassLoader(
                    dexFile.absolutePath,
                    optDir.absolutePath,
                    null,
                    HollowHookBridge::class.java.classLoader
                )
                val clazz = cl.loadClass(entryClass)
                val instance: Any = try {
                    val field = clazz.getDeclaredField("INSTANCE")
                    field.isAccessible = true
                    field.get(null)
                } catch (_: NoSuchFieldException) {
                    val ctor = clazz.getDeclaredConstructor()
                    ctor.isAccessible = true
                    ctor.newInstance()
                }

                if (instance is HollowHookEntry) {
                    results[moduleId] = instance
                    activeHookEntries[moduleId] = instance
                    XposedBridge.log("[$TAG] Successfully verified & loaded hook module: $moduleId")
                }
            } catch (t: Throwable) {
                XposedBridge.log("[$TAG] Failed to load hook module $moduleId: ${t.message}")
            }
        }

        return results
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun verifyEd25519(
        message: ByteArray,
        signatureB64: String,
        publicKeySpkiB64: String
    ): Boolean {
        return try {
            val pubBytes = android.util.Base64.decode(publicKeySpkiB64, android.util.Base64.DEFAULT)
            val sigBytes = android.util.Base64.decode(signatureB64, android.util.Base64.DEFAULT)
            val kf = KeyFactory.getInstance("Ed25519")
            val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(pubKey)
            sig.update(message)
            sig.verify(sigBytes)
        } catch (_: Throwable) {
            false
        }
    }
}
