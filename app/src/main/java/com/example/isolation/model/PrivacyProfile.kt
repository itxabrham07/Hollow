package com.example.isolation.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reusable Privacy & Anti-Fingerprinting Profile.
 * Apps can inherit from a profile and optionally define granular per-app overrides.
 */
data class PrivacyProfile(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean = false,
    // Hardware IDs
    val spoofAndroidId: Boolean = true,
    val spoofImei: Boolean = true,
    val spoofAdvertisingId: Boolean = true,
    val spoofMacAddress: Boolean = true,
    val spoofSerial: Boolean = true,
    val spoofDrmWidevine: Boolean = true,
    // Display & Graphics Fingerprint
    val spoofGpuFingerprint: Boolean = true,
    val gpuVendor: String = "Qualcomm",
    val gpuRenderer: String = "Adreno (TM) 750",
    val spoofCanvasNoise: Boolean = true,
    val spoofAudioContext: Boolean = true,
    val spoofScreenResolution: String = "1080x2400",
    val spoofScreenDpi: Int = 420,
    // Sensors & Battery Entropy
    val anonymizeSensors: Boolean = true,
    val sensorBiasNeutralizer: Boolean = true,
    val fakeBatteryEnabled: Boolean = true,
    val fakeBatteryLevel: Int = 85,
    // Network & Connectivity
    val spoofWebRtc: Boolean = true,
    val blockCellTowerTracking: Boolean = true,
    val spoofWifiBssid: Boolean = true,
    val hideVpnAndProxy: Boolean = true,
    // System & Environment
    val hideRootAndXposed: Boolean = true,
    val hideAdbAndDevOptions: Boolean = true,
    val blockClipboardRead: Boolean = true,
    val preventScreenshots: Boolean = false,
    val disableLogcatLogging: Boolean = true,
    val autoSyncTimezone: Boolean = true,
    val spoofBrowserUserAgent: Boolean = true,
    // Total Leak Deadman Switch
    val deadmanSwitchEnabled: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("isBuiltIn", isBuiltIn)
            put("spoofAndroidId", spoofAndroidId)
            put("spoofImei", spoofImei)
            put("spoofAdvertisingId", spoofAdvertisingId)
            put("spoofMacAddress", spoofMacAddress)
            put("spoofSerial", spoofSerial)
            put("spoofDrmWidevine", spoofDrmWidevine)
            put("spoofGpuFingerprint", spoofGpuFingerprint)
            put("gpuVendor", gpuVendor)
            put("gpuRenderer", gpuRenderer)
            put("spoofCanvasNoise", spoofCanvasNoise)
            put("spoofAudioContext", spoofAudioContext)
            put("spoofScreenResolution", spoofScreenResolution)
            put("spoofScreenDpi", spoofScreenDpi)
            put("anonymizeSensors", anonymizeSensors)
            put("sensorBiasNeutralizer", sensorBiasNeutralizer)
            put("fakeBatteryEnabled", fakeBatteryEnabled)
            put("fakeBatteryLevel", fakeBatteryLevel)
            put("spoofWebRtc", spoofWebRtc)
            put("blockCellTowerTracking", blockCellTowerTracking)
            put("spoofWifiBssid", spoofWifiBssid)
            put("hideVpnAndProxy", hideVpnAndProxy)
            put("hideRootAndXposed", hideRootAndXposed)
            put("hideAdbAndDevOptions", hideAdbAndDevOptions)
            put("blockClipboardRead", blockClipboardRead)
            put("preventScreenshots", preventScreenshots)
            put("disableLogcatLogging", disableLogcatLogging)
            put("autoSyncTimezone", autoSyncTimezone)
            put("spoofBrowserUserAgent", spoofBrowserUserAgent)
            put("deadmanSwitchEnabled", deadmanSwitchEnabled)
        }
    }

    companion object {
        const val PROFILE_GHOST_CLOAK = "ghost_cloak"
        const val PROFILE_SOCIAL_MEDIA = "social_media_shield"
        const val PROFILE_ANTI_FRAUD = "anti_fraud_armor"

        fun fromJson(obj: JSONObject): PrivacyProfile {
            return PrivacyProfile(
                id = obj.optString("id", PROFILE_GHOST_CLOAK),
                name = obj.optString("name", "Custom Profile"),
                description = obj.optString("description", ""),
                isBuiltIn = obj.optBoolean("isBuiltIn", false),
                spoofAndroidId = obj.optBoolean("spoofAndroidId", true),
                spoofImei = obj.optBoolean("spoofImei", true),
                spoofAdvertisingId = obj.optBoolean("spoofAdvertisingId", true),
                spoofMacAddress = obj.optBoolean("spoofMacAddress", true),
                spoofSerial = obj.optBoolean("spoofSerial", true),
                spoofDrmWidevine = obj.optBoolean("spoofDrmWidevine", true),
                spoofGpuFingerprint = obj.optBoolean("spoofGpuFingerprint", true),
                gpuVendor = obj.optString("gpuVendor", "Qualcomm"),
                gpuRenderer = obj.optString("gpuRenderer", "Adreno (TM) 750"),
                spoofCanvasNoise = obj.optBoolean("spoofCanvasNoise", true),
                spoofAudioContext = obj.optBoolean("spoofAudioContext", true),
                spoofScreenResolution = obj.optString("spoofScreenResolution", "1080x2400"),
                spoofScreenDpi = obj.optInt("spoofScreenDpi", 420),
                anonymizeSensors = obj.optBoolean("anonymizeSensors", true),
                sensorBiasNeutralizer = obj.optBoolean("sensorBiasNeutralizer", true),
                fakeBatteryEnabled = obj.optBoolean("fakeBatteryEnabled", true),
                fakeBatteryLevel = obj.optInt("fakeBatteryLevel", 85),
                spoofWebRtc = obj.optBoolean("spoofWebRtc", true),
                blockCellTowerTracking = obj.optBoolean("blockCellTowerTracking", true),
                spoofWifiBssid = obj.optBoolean("spoofWifiBssid", true),
                hideVpnAndProxy = obj.optBoolean("hideVpnAndProxy", true),
                hideRootAndXposed = obj.optBoolean("hideRootAndXposed", true),
                hideAdbAndDevOptions = obj.optBoolean("hideAdbAndDevOptions", true),
                blockClipboardRead = obj.optBoolean("blockClipboardRead", true),
                preventScreenshots = obj.optBoolean("preventScreenshots", false),
                disableLogcatLogging = obj.optBoolean("disableLogcatLogging", true),
                autoSyncTimezone = obj.optBoolean("autoSyncTimezone", true),
                spoofBrowserUserAgent = obj.optBoolean("spoofBrowserUserAgent", true),
                deadmanSwitchEnabled = obj.optBoolean("deadmanSwitchEnabled", true)
            )
        }

        fun defaultGhostCloak(): PrivacyProfile = PrivacyProfile(
            id = PROFILE_GHOST_CLOAK,
            name = "Ghost Cloak",
            description = "Maximum anonymity: hardware ID randomization, sensor scrubbing & network privacy shields",
            isBuiltIn = true,
            spoofAndroidId = true,
            spoofImei = true,
            spoofAdvertisingId = true,
            spoofMacAddress = true,
            spoofSerial = false,
            spoofDrmWidevine = false,
            spoofGpuFingerprint = false,
            gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno (TM) 750",
            spoofCanvasNoise = false,
            spoofAudioContext = false,
            spoofScreenResolution = "",
            spoofScreenDpi = 0,
            anonymizeSensors = true,
            sensorBiasNeutralizer = true,
            fakeBatteryEnabled = false,
            fakeBatteryLevel = 88,
            spoofWebRtc = false,
            blockCellTowerTracking = true,
            spoofWifiBssid = true,
            hideVpnAndProxy = true,
            hideRootAndXposed = true,
            hideAdbAndDevOptions = true,
            blockClipboardRead = true,
            preventScreenshots = false,
            disableLogcatLogging = false,
            autoSyncTimezone = false,
            spoofBrowserUserAgent = false,
            deadmanSwitchEnabled = false
        )

        fun defaultSocialMediaShield(): PrivacyProfile = PrivacyProfile(
            id = PROFILE_SOCIAL_MEDIA,
            name = "Social Media Shield",
            description = "Sign-in safe hardware ID & advertising cloak that preserves Play Integrity & WebView login handshakes",
            isBuiltIn = true,
            spoofAndroidId = true,
            spoofImei = true,
            spoofAdvertisingId = true,
            spoofMacAddress = true,
            spoofSerial = false,
            spoofDrmWidevine = false,
            spoofGpuFingerprint = false,
            gpuVendor = "ARM",
            gpuRenderer = "Mali-G715 Immortalis",
            spoofCanvasNoise = false,
            spoofAudioContext = false,
            spoofScreenResolution = "",
            spoofScreenDpi = 0,
            anonymizeSensors = true,
            sensorBiasNeutralizer = true,
            fakeBatteryEnabled = false,
            fakeBatteryLevel = 76,
            spoofWebRtc = false,
            blockCellTowerTracking = true,
            spoofWifiBssid = true,
            hideVpnAndProxy = true,
            hideRootAndXposed = true,
            hideAdbAndDevOptions = true,
            blockClipboardRead = true,
            preventScreenshots = false,
            disableLogcatLogging = false,
            autoSyncTimezone = false,
            spoofBrowserUserAgent = false,
            deadmanSwitchEnabled = false
        )

        fun defaultAntiFraudArmor(): PrivacyProfile = PrivacyProfile(
            id = PROFILE_ANTI_FRAUD,
            name = "Anti-Fraud Armor",
            description = "High-reputation tier-1 device profile matching banking & fintech trust criteria",
            isBuiltIn = true,
            spoofAndroidId = true,
            spoofImei = true,
            spoofAdvertisingId = true,
            spoofMacAddress = true,
            spoofSerial = false,
            spoofDrmWidevine = false,
            spoofGpuFingerprint = false,
            gpuVendor = "Google",
            gpuRenderer = "Mali-G715-Tensor-G4",
            spoofCanvasNoise = false,
            spoofAudioContext = false,
            spoofScreenResolution = "",
            spoofScreenDpi = 0,
            anonymizeSensors = false,
            sensorBiasNeutralizer = false,
            fakeBatteryEnabled = false,
            fakeBatteryLevel = 92,
            spoofWebRtc = false,
            blockCellTowerTracking = false,
            spoofWifiBssid = false,
            hideVpnAndProxy = true,
            hideRootAndXposed = true,
            hideAdbAndDevOptions = true,
            blockClipboardRead = false,
            preventScreenshots = false,
            disableLogcatLogging = false,
            autoSyncTimezone = false,
            spoofBrowserUserAgent = false,
            deadmanSwitchEnabled = false
        )

        fun builtInProfiles(): List<PrivacyProfile> = listOf(
            defaultGhostCloak(),
            defaultSocialMediaShield(),
            defaultAntiFraudArmor()
        )
    }
}

/**
 * Persistence store for reusable Privacy Profiles.
 */
object PrivacyProfileStore {
    private const val PREFS_NAME = "isovault_privacy_profiles"
    private const val KEY_PROFILES = "profiles_json_v1"

    fun loadProfiles(context: Context): List<PrivacyProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROFILES, null) ?: return PrivacyProfile.builtInProfiles()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<PrivacyProfile>()
            for (i in 0 until array.length()) {
                list.add(PrivacyProfile.fromJson(array.getJSONObject(i)))
            }
            if (list.isEmpty()) PrivacyProfile.builtInProfiles() else list
        } catch (_: Exception) {
            PrivacyProfile.builtInProfiles()
        }
    }

    fun saveProfiles(context: Context, profiles: List<PrivacyProfile>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun getProfileById(context: Context, id: String): PrivacyProfile {
        return loadProfiles(context).find { it.id == id } ?: PrivacyProfile.defaultGhostCloak()
    }

    fun resetToDefaults(context: Context): List<PrivacyProfile> {
        val defaults = PrivacyProfile.builtInProfiles()
        saveProfiles(context, defaults)
        return defaults
    }
}
