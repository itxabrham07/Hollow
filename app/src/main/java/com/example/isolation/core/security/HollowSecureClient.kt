package com.example.isolation.core.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.example.isolation.core.license.DeviceHardwareFingerprint
import org.json.JSONArray
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

    fun downloadEncryptedModule(
        context: Context,
        serverBaseUrl: String,
        moduleId: String
    ): JSONObject? {
        val hw = DeviceHardwareFingerprint.collect(context)
        return try {
            val cleanBase = serverBaseUrl.trimEnd('/')
            val url = URL("$cleanBase/api/v1/modules/download/$moduleId")
            val conn = (url.openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("X-Hollow-HW", hw.fingerprintHex)
            }
            if (conn.responseCode !in 200..299) return null
            val bodyStr = conn.inputStream.bufferedReader().readText()
            decryptPayloadEnvelope(JSONObject(bodyStr))
        } catch (_: Throwable) {
            null
        }
    }

    fun buildSignedSimulatedModuleBundle(context: Context, moduleId: String): String {
        val hw = DeviceHardwareFingerprint.collect(context)
        val obj = JSONObject()
            .put("moduleId", moduleId)
            .put("boundHardwareFingerprint", hw.fingerprintHex)
            .put("issuedAtMs", System.currentTimeMillis())
            .put("dexHeaderMagic", "dex\n039\u0000")
            .put("signatureB64", computeHmacSha256("$moduleId:${hw.fingerprintHex}"))

        if (moduleId == "module_sensor_firewall") {
            obj.put("name", "Sensor & Mic Hardware Firewall")
                .put("cardTitle", "Sensor Firewall")
                .put(
                    "description",
                    "Zero-touch server-shipped module that blanks camera/microphone buffers, zeroes gyroscope/accelerometer telemetry, and blocks background ultrasonic beacons."
                )
                .put("version", 1)
                .put("sizeKb", 395)
                .put("accentColorHex", "0xFFF59E0B")
                .put("entryClass", "com.example.isolation.module.sensorfirewall.SensorFirewallPayload")
                .put(
                    "features",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("key", "mic_null_pcm_stream")
                                .put("title", "Silent PCM Microphone Guard")
                                .put("description", "Injects zeroed PCM audio frames into AudioRecord when background apps attempt covert recording.")
                                .put("defaultEnabled", true)
                        )
                        .put(
                            JSONObject()
                                .put("key", "camera_black_frame_shield")
                                .put("title", "Camera2 Black Frame Shield")
                                .put("description", "Replaces CameraDevice capture surfaces with synthetic blank frames unless explicitly whitelisted.")
                                .put("defaultEnabled", true)
                        )
                        .put(
                            JSONObject()
                                .put("key", "gyro_motion_fingerprint_scrub")
                                .put("title", "Gyroscope & Step Counter Scrubber")
                                .put("description", "Prevents gait and micro-vibration fingerprinting across SensorManager listeners.")
                                .put("defaultEnabled", true)
                        )
                )
                .put(
                    "hookTargets",
                    JSONArray()
                        .put("android.media.AudioRecord")
                        .put("android.hardware.camera2.CameraManager")
                        .put("android.hardware.SystemSensorManager")
                )
        }
        return obj.toString()
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
