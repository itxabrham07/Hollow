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
                        try {
                            val modContext = HollowModuleContextImpl(context, moduleId)
                            loaded.onMount(modContext)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Module $moduleId onMount failed safely: ${t.message}", t)
                            com.example.isolation.core.stability.ModuleQuarantineManager.recordModuleFailure(
                                context = context,
                                moduleId = moduleId,
                                throwable = t,
                                serverBaseUrl = serverBaseUrl
                            )
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

            // 1. In JVM environments (Robolectric / OpenJDK), find SunEC via ServiceLoader if not already in Security
            try {
                if (java.security.Security.getProvider("SunEC") == null) {
                    for (p in java.util.ServiceLoader.load(java.security.Provider::class.java)) {
                        if ("SunEC".equals(p.name, ignoreCase = true)) {
                            java.security.Security.addProvider(p)
                            break
                        }
                    }
                }
            } catch (_: Throwable) {}

            // 2. Try JCA providers (Conscrypt, SunEC, BC), explicitly skipping AndroidKeyStore to avoid KeyGenParameterSpec crashes
            val providers = java.security.Security.getProviders()
            for (provider in providers) {
                if (provider.name.contains("AndroidKeyStore", ignoreCase = true)) continue
                for (algo in listOf("Ed25519", "EdDSA")) {
                    try {
                        val kf = KeyFactory.getInstance(algo, provider)
                        val pubKey = kf.generatePublic(keySpec)
                        val verifier = Signature.getInstance(algo, provider)
                        verifier.initVerify(pubKey)
                        verifier.update(messageBytes)
                        if (verifier.verify(sigBytes)) return true
                    } catch (_: Throwable) {}
                }
            }

            // 3. Pure-Kotlin RFC 8032 Ed25519 fallback verifier
            val rawPubKey32 = if (pubKeyBytes.size >= 32) {
                pubKeyBytes.copyOfRange(pubKeyBytes.size - 32, pubKeyBytes.size)
            } else {
                pubKeyBytes
            }
            PureEd25519Verifier.verify(messageBytes, sigBytes, rawPubKey32)
        } catch (t: Throwable) {
            Log.e(TAG, "Ed25519 verification error: ${t.message}")
            false
        }
    }

    private object PureEd25519Verifier {
        private val P = java.math.BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
        private val L = java.math.BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed", 16)
        private val D = (java.math.BigInteger.valueOf(-121665).multiply(java.math.BigInteger.valueOf(121666).modInverse(P))).mod(P)
        private val I = java.math.BigInteger.valueOf(2).modPow(P.subtract(java.math.BigInteger.ONE).divide(java.math.BigInteger.valueOf(4)), P)
        private val B_Y = java.math.BigInteger.valueOf(4).multiply(java.math.BigInteger.valueOf(5).modInverse(P)).mod(P)
        private val B_X: java.math.BigInteger = recoverX(B_Y)
        private val BASE_POINT = Point(B_X, B_Y)

        data class Point(val x: java.math.BigInteger, val y: java.math.BigInteger)

        private fun recoverX(y: java.math.BigInteger): java.math.BigInteger {
            val y2 = y.multiply(y).mod(P)
            val u = y2.subtract(java.math.BigInteger.ONE).mod(P)
            val v = D.multiply(y2).add(java.math.BigInteger.ONE).mod(P)
            val x = u.multiply(v.modInverse(P)).mod(P)
            var root = x.modPow(P.add(java.math.BigInteger.valueOf(3)).divide(java.math.BigInteger.valueOf(8)), P)
            if (root.multiply(root).subtract(x).mod(P) != java.math.BigInteger.ZERO) {
                root = root.multiply(I).mod(P)
            }
            if (root.testBit(0)) {
                root = P.subtract(root).mod(P)
            }
            return root
        }

        private fun add(p1: Point, p2: Point): Point {
            val x1 = p1.x
            val y1 = p1.y
            val x2 = p2.x
            val y2 = p2.y
            val x1x2 = x1.multiply(x2).mod(P)
            val y1y2 = y1.multiply(y2).mod(P)
            val dx1x2y1y2 = D.multiply(x1x2).mod(P).multiply(y1y2).mod(P)

            val x3num = x1.multiply(y2).add(y1.multiply(x2)).mod(P)
            val x3den = java.math.BigInteger.ONE.add(dx1x2y1y2).mod(P)
            val x3 = x3num.multiply(x3den.modInverse(P)).mod(P)

            val y3num = y1y2.add(x1x2).mod(P)
            val y3den = java.math.BigInteger.ONE.subtract(dx1x2y1y2).mod(P)
            val y3 = y3num.multiply(y3den.modInverse(P)).mod(P)
            return Point(x3, y3)
        }

        private fun scalarMult(p: Point, s: java.math.BigInteger): Point {
            var result: Point? = null
            var current = p
            val bits = s.toString(2)
            for (i in bits.indices) {
                if (result != null) {
                    result = add(result, result)
                }
                if (bits[i] == '1') {
                    result = if (result == null) current else add(result, current)
                }
            }
            return result ?: Point(java.math.BigInteger.ZERO, java.math.BigInteger.ONE)
        }

        fun verify(message: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
            if (signature.size != 64 || publicKeyBytes.size != 32) return false
            return try {
                val rBytes = signature.copyOfRange(0, 32)
                val sBytes = signature.copyOfRange(32, 64)

                var s = java.math.BigInteger.ZERO
                for (i in 31 downTo 0) {
                    s = s.shiftLeft(8).add(java.math.BigInteger.valueOf((sBytes[i].toInt() and 0xFF).toLong()))
                }
                if (s >= L) return false

                val aPoint = decodePoint(publicKeyBytes) ?: return false
                val rPoint = decodePoint(rBytes) ?: return false

                val md = MessageDigest.getInstance("SHA-512")
                md.update(rBytes)
                md.update(publicKeyBytes)
                md.update(message)
                val hBytes = md.digest()
                var k = java.math.BigInteger.ZERO
                for (i in 63 downTo 0) {
                    k = k.shiftLeft(8).add(java.math.BigInteger.valueOf((hBytes[i].toInt() and 0xFF).toLong()))
                }
                k = k.mod(L)

                val sb = scalarMult(BASE_POINT, s)
                val ka = scalarMult(aPoint, k)
                val rPlusKa = add(rPoint, ka)

                sb.x == rPlusKa.x && sb.y == rPlusKa.y
            } catch (_: Throwable) {
                false
            }
        }

        private fun decodePoint(bytes: ByteArray): Point? {
            if (bytes.size != 32) return null
            val lastByte = bytes[31].toInt() and 0xFF
            val xBit = (lastByte and 0x80) != 0
            val yBytes = bytes.clone()
            yBytes[31] = (lastByte and 0x7F).toByte()

            var y = java.math.BigInteger.ZERO
            for (i in 31 downTo 0) {
                y = y.shiftLeft(8).add(java.math.BigInteger.valueOf((yBytes[i].toInt() and 0xFF).toLong()))
            }
            if (y >= P) return null

            val y2 = y.multiply(y).mod(P)
            val u = y2.subtract(java.math.BigInteger.ONE).mod(P)
            val v = D.multiply(y2).add(java.math.BigInteger.ONE).mod(P)
            val x = u.multiply(v.modInverse(P)).mod(P)

            var root = x.modPow(P.add(java.math.BigInteger.valueOf(3)).divide(java.math.BigInteger.valueOf(8)), P)
            if (root.multiply(root).subtract(x).mod(P) != java.math.BigInteger.ZERO) {
                root = root.multiply(I).mod(P)
            }
            if (root.multiply(root).subtract(x).mod(P) != java.math.BigInteger.ZERO) return null

            if (root.testBit(0) != xBit) {
                root = P.subtract(root).mod(P)
            }
            return Point(root, y)
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
