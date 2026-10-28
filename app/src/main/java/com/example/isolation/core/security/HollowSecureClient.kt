package com.example.isolation.core.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.example.BuildConfig
import com.example.isolation.core.license.DeviceHardwareFingerprint
import com.example.isolation.core.module.HollowDexModuleLoader
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Anti-MITM Encrypted Network Client (`HollowSecureClient`).
 *
 * Defends against HTTP Toolkit, Charles Proxy, Burp Suite, and mitmproxy:
 * 1. Application-layer AES-256-GCM encryption on every request/response body (`payload_enc`).
 * 2. Hardware-tethered HMAC-SHA256 request signing (`X-Hollow-Signature` & `X-Hollow-HW`).
 * 3. Rejects proxy environment variables (`Proxy.NO_PROXY`) so userland proxy tools cannot intercept traffic.
 * 4. Verifies SHA-256 checksum and Ed25519 cryptographic signature on every downloaded `.dex` module.
 */
object HollowSecureClient {

    private const val DEFAULT_PAYLOAD_HEX_KEY =
        "4f8b9a2c1d7e6f5031425364758697a8b9c0d1e2f30415263748596a7b8c9d0e"
    private const val HMAC_SHARED_SECRET = "hollow_ultra_secret_jwt_signing_key_2026"

    fun encryptPayloadEnvelope(plainJson: JSONObject): JSONObject {
        val keyBytes = hexToBytes(DEFAULT_PAYLOAD_HEX_KEY)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainJson.toString().toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        val payloadB64 = Base64.encodeToString(combined, Base64.NO_WRAP)
        val signatureB64 = computeHmacSha256(payloadB64)
        return JSONObject()
            .put("payload_enc", payloadB64)
            .put("signature_b64", signatureB64)
            .put("ts", System.currentTimeMillis())
    }

    fun decryptPayloadEnvelope(envelopeJson: JSONObject): JSONObject? {
        if (!envelopeJson.has("payload_enc")) return envelopeJson
        return try {
            val payloadB64 = envelopeJson.getString("payload_enc")
            val raw = Base64.decode(payloadB64, Base64.NO_WRAP)
            if (raw.size <= 12) return null
            val iv = raw.copyOfRange(0, 12)
            val cipherBytes = raw.copyOfRange(12, raw.size)
            val keyBytes = hexToBytes(DEFAULT_PAYLOAD_HEX_KEY)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            val plainStr = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            JSONObject(plainStr)
        } catch (_: Throwable) {
            null
        }
    }

    fun postEncrypted(
        context: Context,
        serverBaseUrl: String,
        endpointPath: String,
        requestJson: JSONObject
    ): JSONObject? {
        if (serverBaseUrl.isBlank()) return null
        val hw = DeviceHardwareFingerprint.collect(context)
        val envelope = encryptPayloadEnvelope(requestJson)
        return try {
            val cleanBase = serverBaseUrl.trimEnd('/')
            val url = URL("$cleanBase$endpointPath")
            // Explicitly use Proxy.NO_PROXY to bypass HTTP Toolkit / Charles / Burp system proxies
            val conn = (url.openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4500
                readTimeout = 4500
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Hollow-HW", hw.fingerprintHex)
                setRequestProperty("X-Hollow-Signature", envelope.optString("signature_b64"))
                setRequestProperty("X-Hollow-Uptime", SystemClock.elapsedRealtime().toString())
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(envelope.toString())
                writer.flush()
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val bodyStr = stream?.bufferedReader()?.readText().orEmpty()
            if (bodyStr.isBlank()) return null
            decryptPayloadEnvelope(JSONObject(bodyStr))
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Downloads an authorized `.dex` module from `/api/v1/modules/download` (or `/api/v1/modules/download/<moduleId>`)
     * and verifies its SHA-256 checksum and Ed25519 signature before accepting the payload.
     */
    fun downloadEncryptedModule(
        context: Context,
        serverBaseUrl: String,
        moduleId: String
    ): JSONObject? {
        val hw = DeviceHardwareFingerprint.collect(context)
        val postReq = JSONObject()
            .put("hardwareFingerprint", hw.fingerprintHex)
            .put("moduleId", moduleId)

        val postResp = postEncrypted(
            context = context,
            serverBaseUrl = serverBaseUrl,
            endpointPath = "/api/v1/modules/download",
            requestJson = postReq
        )

        val rawResponse: JSONObject? = if (postResp != null && postResp.has("dexPayloadBase64")) {
            postResp
        } else {
            try {
                val cleanBase = serverBaseUrl.trimEnd('/')
                val url = URL("$cleanBase/api/v1/modules/download/$moduleId")
                val conn = (url.openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4500
                    readTimeout = 4500
                    setRequestProperty("X-Hollow-HW", hw.fingerprintHex)
                }
                if (conn.responseCode !in 200..299) {
                    null
                } else {
                    val bodyStr = conn.inputStream.bufferedReader().readText()
                    decryptPayloadEnvelope(JSONObject(bodyStr))
                }
            } catch (_: Throwable) {
                null
            }
        }

        if (rawResponse == null) return null

        // Verify SHA-256 checksum, Ed25519 signature & minCoreVersion immediately upon download
        return when (val check = HollowDexModuleLoader.verifyModuleBundleIntegrity(rawResponse)) {
            is HollowDexModuleLoader.VerificationOutcome.Success -> rawResponse
            is HollowDexModuleLoader.VerificationOutcome.IncompatibleVersion -> null
            is HollowDexModuleLoader.VerificationOutcome.Failure -> {
                reportSecurityIncidentAsync(
                    context = context,
                    serverBaseUrl = serverBaseUrl,
                    moduleId = moduleId,
                    triggerVector = check.reasonCode,
                    details = check.details
                )
                null
            }
        }
    }

    fun reportSecurityIncident(
        context: Context,
        serverBaseUrl: String,
        moduleId: String,
        triggerVector: String,
        details: String
    ): JSONObject? {
        if (serverBaseUrl.isBlank()) return null
        val hw = DeviceHardwareFingerprint.collect(context)
        val payload = JSONObject()
            .put("hardwareFingerprint", hw.fingerprintHex)
            .put("moduleId", moduleId)
            .put("triggerVector", triggerVector)
            .put("details", details)
            .put("timestampMs", System.currentTimeMillis())
        return postEncrypted(
            context = context,
            serverBaseUrl = serverBaseUrl,
            endpointPath = "/api/v1/security/incident",
            requestJson = payload
        )
    }

    fun reportSecurityIncidentAsync(
        context: Context,
        serverBaseUrl: String,
        moduleId: String,
        triggerVector: String,
        details: String
    ) {
        if (serverBaseUrl.isBlank()) return
        Thread {
            try {
                reportSecurityIncident(
                    context = context,
                    serverBaseUrl = serverBaseUrl,
                    moduleId = moduleId,
                    triggerVector = triggerVector,
                    details = details
                )
            } catch (_: Throwable) {
            }
        }.start()
    }

    /**
     * In DEBUG builds only (`BuildConfig.DEBUG`), returns the real compiled & Ed25519-signed
     * DEX module bundle from `debug_modules/<moduleId>.json` for offline emulator / Robolectric testing.
     * In RELEASE builds, returns an empty string (no local fallback bundles in release).
     */
    fun buildSignedSimulatedModuleBundle(context: Context, moduleId: String): String {
        if (!BuildConfig.DEBUG) return ""
        return HollowDexModuleLoader.readDebugModuleBundleJson(context, moduleId).orEmpty()
    }

    private fun computeHmacSha256(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_SHARED_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
