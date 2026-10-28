package com.example.isolation.core.module

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.core.security.HollowSecureClient
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File
import java.net.URLClassLoader
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class ModuleSecurityIncidentRecord(
    val moduleId: String,
    val triggerVector: String,
    val details: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class IncompatibleModuleVersionRecord(
    val moduleId: String,
    val requiredMinCoreVersion: Int,
    val currentCoreVersion: Int,
    val details: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Real Dynamic DEX Loader (`HollowDexModuleLoader`) with SHA-256 Checksum, Ed25519 Verification,
 * and `requiredMinCoreVersion` compatibility gate.
 *
 * Security & compatibility pipeline before any bytecode is mounted:
 * 1. Reads decrypted module bundle JSON from `/data/adb/.hollow_vault/modules/<moduleId>.dex.enc`.
 * 2. Extracts `dexPayloadBase64`, `sha256Checksum` (or `sha256`), `signatureB64`, `minCoreVersion`, and `entryClass`.
 * 3. Computes SHA-256 over the raw `.dex` bytes and compares against `sha256Checksum`.
 * 4. Verifies the Ed25519 `signatureB64` over `sha256Checksum` (UTF-8 bytes) using the
 *    hardcoded public key `BuildConfig.HOLLOW_MODULE_PUBLIC_KEY_B64`.
 * 5. On any cryptographic mismatch, refuses to load, deletes the tampered module from the Root Vault,
 *    and dispatches a `SecurityIncident` to the licensing server.
 * 6. Checks `minCoreVersion` (in manifest JSON) and `HollowModule.requiredMinCoreVersion` (after class
 *    instantiation) against `CORE_ENGINE_VERSION`. If the module requires a newer Core APK, skips
 *    mounting gracefully (`return null`) without crashing.
 * 7. Writes verified `.dex` bytes to a private, non-world-readable read-only file inside
 *    `context.codeCacheDir/hollow_dex/` and loads `entryClass` via `DexClassLoader` whose parent
 *    classloader is `:app`'s classloader (`HollowModule::class.java.classLoader`).
 */
object HollowDexModuleLoader {

    private const val TAG = "HollowDexModuleLoader"

    /** Current Core Engine contract version supported by this APK. */
    const val CORE_ENGINE_VERSION: Int = 1

    // Cache of verified & mounted HollowModule instances keyed by "<moduleId>:<sha256Checksum>"
    private val loadedModuleCache = ConcurrentHashMap<String, HollowModule>()

    // In-memory audit trail of security incidents (inspected by diagnostics & unit tests)
    private val _securityIncidents = CopyOnWriteArrayList<ModuleSecurityIncidentRecord>()
    val recordedSecurityIncidents: List<ModuleSecurityIncidentRecord>
        get() = _securityIncidents.toList()

    // In-memory record of modules skipped gracefully due to requiredMinCoreVersion > CORE_ENGINE_VERSION
    private val _incompatibleVersions = CopyOnWriteArrayList<IncompatibleModuleVersionRecord>()
    val recordedIncompatibleVersions: List<IncompatibleModuleVersionRecord>
        get() = _incompatibleVersions.toList()

    fun clearCacheAndIncidentsForTest() {
        loadedModuleCache.clear()
        _securityIncidents.clear()
        _incompatibleVersions.clear()
    }

    /**
     * Loads and verifies a downloaded module from the Root License Vault.
     * Returns `null` if missing, tampered, incompatible with `CORE_ENGINE_VERSION`, or failing SHA-256 / Ed25519 verification.
     */
    fun loadModuleFromVault(
        context: Context,
        moduleId: String,
        serverBaseUrl: String = ""
    ): HollowModule? {
        val rawBundleJson = RootLicenseVaultManager.readDownloadedModulePayload(context, moduleId)
        if (rawBundleJson.isNullOrBlank()) {
            return null
        }
        return verifyAndLoadFromBundleJson(
            context = context,
            moduleId = moduleId,
            rawBundleJson = rawBundleJson,
            serverBaseUrl = serverBaseUrl
        )
    }

    /**
     * Seeds debug-only compiled `.dex` bundles from `assets/debug_modules/<moduleId>.json`
     * when `BuildConfig.DEBUG && devFullBuild` is enabled for offline developer testing.
     * Never used in release builds (`!BuildConfig.DEBUG`).
     */
    fun loadDebugBundledModules(context: Context): List<HollowModule> {
        if (!BuildConfig.DEBUG) return emptyList()
        val results = mutableListOf<HollowModule>()
        val assetManager = context.assets
        val files = try {
            assetManager.list("debug_modules")?.sorted() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        for (fileName in files) {
            if (!fileName.endsWith(".json")) continue
            val moduleId = fileName.removeSuffix(".json")
            val rawJson = try {
                assetManager.open("debug_modules/$fileName").bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                null
            }
            if (!rawJson.isNullOrBlank()) {
                val mod = verifyAndLoadFromBundleJson(
                    context = context,
                    moduleId = moduleId,
                    rawBundleJson = rawJson,
                    serverBaseUrl = ""
                )
                if (mod != null) {
                    results.add(mod)
                }
            }
        }
        return results
    }

    fun readDebugModuleBundleJson(context: Context, moduleId: String): String? {
        if (!BuildConfig.DEBUG) return null
        return try {
            context.assets.open("debug_modules/$moduleId.json").bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            // Fallback for local JVM unit tests reading from repo root if assets aren't merged yet
            val repoDebugFile = File("app/src/debug/assets/debug_modules/$moduleId.json")
                .takeIf { it.exists() }
                ?: File("../app/src/debug/assets/debug_modules/$moduleId.json").takeIf { it.exists() }
            repoDebugFile?.readText()
        }
    }

    /**
     * Verifies `sha256Checksum`, Ed25519 `signatureB64`, and `minCoreVersion` on a module payload JSON object.
     */
    fun verifyModuleBundleIntegrity(
        bundleObj: JSONObject,
        currentCoreVersion: Int = CORE_ENGINE_VERSION
    ): VerificationOutcome {
        val moduleId = bundleObj.optString("moduleId", "unknown_module")
        val dexB64 = bundleObj.optString("dexPayloadBase64", "")
        if (dexB64.isBlank()) {
            return VerificationOutcome.Failure("MISSING_DEX_PAYLOAD", "Module $moduleId payload is missing dexPayloadBase64.")
        }

        val dexBytes = try {
            Base64.decode(dexB64, Base64.DEFAULT)
        } catch (t: Throwable) {
            return VerificationOutcome.Failure("CORRUPT_BASE64", "Module $moduleId has invalid Base64 DEX payload: ${t.message}")
        }

        if (dexBytes.size < 8 ||
            dexBytes[0] != 'd'.code.toByte() ||
            dexBytes[1] != 'e'.code.toByte() ||
            dexBytes[2] != 'x'.code.toByte() ||
            dexBytes[3] != '\n'.code.toByte()
        ) {
            return VerificationOutcome.Failure("INVALID_DEX_MAGIC", "Module $moduleId binary does not start with valid DEX magic header.")
        }

        val expectedSha256 = bundleObj.optString("sha256Checksum", bundleObj.optString("sha256", "")).lowercase().trim()
        if (expectedSha256.length != 64) {
            return VerificationOutcome.Failure("MISSING_SHA256", "Module $moduleId manifest is missing a valid 64-char sha256Checksum.")
        }

        val actualSha256 = sha256Hex(dexBytes)
        if (actualSha256 != expectedSha256) {
            return VerificationOutcome.Failure(
                "DEX_CHECKSUM_MISMATCH",
                "Module $moduleId SHA-256 mismatch: expected=$expectedSha256 actual=$actualSha256"
            )
        }

        val signatureB64 = bundleObj.optString("signatureB64", "").trim()
        if (signatureB64.isBlank()) {
            return VerificationOutcome.Failure("MISSING_ED25519_SIGNATURE", "Module $moduleId is missing Ed25519 signatureB64.")
        }

        val sigValid = verifyEd25519Signature(
            messageBytes = expectedSha256.toByteArray(Charsets.UTF_8),
            signatureB64 = signatureB64,
            publicKeySpkiB64 = BuildConfig.HOLLOW_MODULE_PUBLIC_KEY_B64
        )
        if (!sigValid) {
            return VerificationOutcome.Failure(
                "ED25519_SIGNATURE_INVALID",
                "Module $moduleId Ed25519 signature verification failed against core public key."
            )
        }

        // Gate 1: Manifest minCoreVersion check before classloading
        val manifestMinCore = bundleObj.optInt(
            "minCoreVersion",
            bundleObj.optInt("requiredMinCoreVersion", 1)
        )
        if (manifestMinCore > currentCoreVersion) {
            return VerificationOutcome.IncompatibleVersion(
                moduleId = moduleId,
                requiredMinCoreVersion = manifestMinCore,
                currentCoreVersion = currentCoreVersion,
                details = "Module $moduleId requires minCoreVersion=$manifestMinCore (current core is v$currentCoreVersion)."
            )
        }

        val entryClass = bundleObj.optString("entryClass", "").trim()
        if (entryClass.isBlank()) {
            return VerificationOutcome.Failure("MISSING_ENTRY_CLASS", "Module $moduleId manifest is missing entryClass.")
        }

        return VerificationOutcome.Success(
            dexBytes = dexBytes,
            sha256Checksum = actualSha256,
            entryClass = entryClass
        )
    }

    fun verifyAndLoadFromBundleJson(
        context: Context,
        moduleId: String,
        rawBundleJson: String,
        serverBaseUrl: String = "",
        currentCoreVersion: Int = CORE_ENGINE_VERSION
    ): HollowModule? {
        val bundleObj = try {
            JSONObject(rawBundleJson)
        } catch (t: Throwable) {
            handleVerificationFailure(
                context = context,
                moduleId = moduleId,
                serverBaseUrl = serverBaseUrl,
                triggerVector = "MALFORMED_MODULE_MANIFEST",
                details = "Failed to parse decrypted module JSON for $moduleId: ${t.message}"
            )
            return null
        }

        when (val verification = verifyModuleBundleIntegrity(bundleObj, currentCoreVersion)) {
            is VerificationOutcome.Failure -> {
                handleVerificationFailure(
                    context = context,
                    moduleId = moduleId,
                    serverBaseUrl = serverBaseUrl,
                    triggerVector = verification.reasonCode,
                    details = verification.details
                )
                return null
            }

            is VerificationOutcome.IncompatibleVersion -> {
                recordIncompatibleVersion(
                    moduleId = moduleId,
                    requiredMinCoreVersion = verification.requiredMinCoreVersion,
                    currentCoreVersion = verification.currentCoreVersion,
                    details = verification.details
                )
                return null
            }

            is VerificationOutcome.Success -> {
                val cacheKey = "$moduleId:${verification.sha256Checksum}:$currentCoreVersion"
                loadedModuleCache[cacheKey]?.let { return it }

                return try {
                    val loaded = loadVerifiedDexClass(
                        context = context,
                        moduleId = moduleId,
                        dexBytes = verification.dexBytes,
                        sha256Checksum = verification.sha256Checksum,
                        entryClass = verification.entryClass
                    )
                    if (loaded != null) {
                        // Gate 2: Post-instantiation HollowModule.requiredMinCoreVersion check
                        if (loaded.requiredMinCoreVersion > currentCoreVersion) {
                            recordIncompatibleVersion(
                                moduleId = moduleId,
                                requiredMinCoreVersion = loaded.requiredMinCoreVersion,
                                currentCoreVersion = currentCoreVersion,
                                details = "Instantiated HollowModule ${loaded.id} requires requiredMinCoreVersion=${loaded.requiredMinCoreVersion} (current core is v$currentCoreVersion)."
                            )
                            return null
                        }
                        loadedModuleCache[cacheKey] = loaded
                    }
                    loaded
                } catch (t: Throwable) {
                    handleVerificationFailure(
                        context = context,
                        moduleId = moduleId,
                        serverBaseUrl = serverBaseUrl,
                        triggerVector = "DEX_CLASSLOAD_FAILURE",
                        details = "Failed to instantiate ${verification.entryClass} from $moduleId: ${t.javaClass.simpleName}: ${t.message}"
                    )
                    null
                }
            }
        }
    }

    private fun recordIncompatibleVersion(
        moduleId: String,
        requiredMinCoreVersion: Int,
        currentCoreVersion: Int,
        details: String
    ) {
        Log.w(TAG, "SKIPPING INCOMPATIBLE MODULE '$moduleId': $details")
        _incompatibleVersions.add(
            IncompatibleModuleVersionRecord(
                moduleId = moduleId,
                requiredMinCoreVersion = requiredMinCoreVersion,
                currentCoreVersion = currentCoreVersion,
                details = details
            )
        )
    }

    private fun loadVerifiedDexClass(
        context: Context,
        moduleId: String,
        dexBytes: ByteArray,
        sha256Checksum: String,
        entryClass: String
    ): HollowModule? {
        // Write verified DEX to a private, non-world-readable directory
        val dexDir = File(context.codeCacheDir, "hollow_dex").apply {
            if (!exists()) mkdirs()
            setReadable(false, false)
            setReadable(true, true)
            setExecutable(false, false)
            setExecutable(true, true)
        }
        val optDir = File(context.codeCacheDir, "hollow_dex_opt").apply {
            if (!exists()) mkdirs()
        }

        val dexFile = File(dexDir, "${moduleId}_${sha256Checksum.take(12)}.dex")
        if (dexFile.exists()) {
            dexFile.setWritable(true, true)
            dexFile.delete()
        }
        dexFile.writeBytes(dexBytes)
        // Android 14+ (API 34+) requires dynamically loaded DEX files to be strictly read-only
        dexFile.setReadable(false, false)
        dexFile.setReadable(true, true)
        dexFile.setWritable(false, false)

        val parentClassLoader = HollowModule::class.java.classLoader ?: context.classLoader
        val clazz: Class<*> = try {
            val dexLoader = DexClassLoader(
                dexFile.absolutePath,
                optDir.absolutePath,
                null,
                parentClassLoader
            )
            dexLoader.loadClass(entryClass)
        } catch (cnfe: ClassNotFoundException) {
            // In local JVM unit tests (Robolectric on OpenJDK), Dalvik bytecode is not natively
            // interpreted by the host JVM ClassLoader. Load the companion compiled class jar built
            // from the exact same modules-src/<moduleId>/ sources ONLY when on non-Dalvik JVM.
            val vmName = System.getProperty("java.vm.name").orEmpty()
            if (!vmName.contains("Dalvik", ignoreCase = true)) {
                loadFromLocalJvmModuleJar(moduleId, entryClass, parentClassLoader) ?: throw cnfe
            } else {
                throw cnfe
            }
        }

        val instance: Any = try {
            // Support Kotlin `object` declarations (`INSTANCE` static field)
            val instanceField = clazz.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.get(null)
        } catch (_: NoSuchFieldException) {
            val ctor = clazz.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance()
        }

        return instance as? HollowModule
    }

    private fun loadFromLocalJvmModuleJar(
        moduleId: String,
        entryClass: String,
        parentClassLoader: ClassLoader
    ): Class<*>? {
        val candidates = listOf(
            File("modules-src/$moduleId/build/module-classes.jar"),
            File("../modules-src/$moduleId/build/module-classes.jar")
        )
        val jarFile = candidates.firstOrNull { it.exists() } ?: return null
        val urlLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), parentClassLoader)
        return urlLoader.loadClass(entryClass)
    }

    private fun handleVerificationFailure(
        context: Context,
        moduleId: String,
        serverBaseUrl: String,
        triggerVector: String,
        details: String
    ) {
        Log.e(TAG, "SECURITY INCIDENT [$triggerVector] on module '$moduleId': $details")
        // 1. Delete tampered/unverified module payload immediately from Root Vault
        RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, moduleId)

        // 2. Record incident in memory and report to licensing server
        val record = ModuleSecurityIncidentRecord(
            moduleId = moduleId,
            triggerVector = triggerVector,
            details = details
        )
        _securityIncidents.add(record)

        HollowSecureClient.reportSecurityIncidentAsync(
            context = context,
            serverBaseUrl = serverBaseUrl,
            moduleId = moduleId,
            triggerVector = triggerVector,
            details = details
        )
    }

    fun verifyEd25519Signature(
        messageBytes: ByteArray,
        signatureB64: String,
        publicKeySpkiB64: String
    ): Boolean {
        return try {
            val pubKeyBytes = Base64.decode(publicKeySpkiB64.trim(), Base64.DEFAULT)
            val sigBytes = Base64.decode(signatureB64.trim(), Base64.DEFAULT)
            if (sigBytes.size != 64) return false

            val keySpec = X509EncodedKeySpec(pubKeyBytes)
            val kf = KeyFactory.getInstance("Ed25519")
            val pubKey = kf.generatePublic(keySpec)
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(pubKey)
            verifier.update(messageBytes)
            verifier.verify(sigBytes)
        } catch (t: Throwable) {
            Log.e(TAG, "Ed25519 verification error: ${t.message}")
            false
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    sealed class VerificationOutcome {
        data class Success(
            val dexBytes: ByteArray,
            val sha256Checksum: String,
            val entryClass: String
        ) : VerificationOutcome()

        data class IncompatibleVersion(
            val moduleId: String,
            val requiredMinCoreVersion: Int,
            val currentCoreVersion: Int,
            val details: String
        ) : VerificationOutcome()

        data class Failure(
            val reasonCode: String,
            val details: String
        ) : VerificationOutcome()
    }
}
