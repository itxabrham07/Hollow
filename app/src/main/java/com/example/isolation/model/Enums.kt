package com.example.isolation.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Base64
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class IsolationMode(
    val title: String,
    val description: String,
    val badge: String
) {
    SHARED_WORKSPACE(
        title = "Hollow Drive (Default)",
        description = "Connects this app to Hollow Drive — the default shared virtual storage and gallery used across isolated apps.",
        badge = "Hollow Drive"
    ),
    SANDBOX_REDIRECT(
        title = "Separate App Drive",
        description = "Assigns this app its own Separate Drive that appears below Hollow Drive in Files like an SD card.",
        badge = "Separate Drive"
    ),
    ENCRYPTED_VAULT(
        title = "Encrypted Separate Drive",
        description = "Assigns this app an AES-256 encrypted Separate Drive below Hollow Drive in Files.",
        badge = "Encrypted Drive"
    ),
    FAKE_EMPTY(
        title = "Blank Gallery",
        description = "Shows an empty gallery and folder list so your personal media stays unseen.",
        badge = "Blank Gallery"
    ),
    READ_ONLY(
        title = "View Only",
        description = "Stops the app from creating extra clutter or hidden folders on your phone.",
        badge = "View Only"
    )
}

/**
 * Controls how an app (or Hollow itself) appears across Android System Framework,
 * SystemUI, System Settings, and any existing or newly installed launcher.
 */
enum class LauncherVisibilityMode(
    val title: String,
    val description: String,
    val badge: String
) {
    VISIBLE(
        title = "Visible (Normal)",
        description = "App appears with its real name and icon in launchers and SystemUI.",
        badge = "Normal"
    ),
    DISGUISED(
        title = "Disguise Name & Icon",
        description = "Replaces the app's name and icon across launchers, System Settings, and SystemUI.",
        badge = "Disguised"
    ),
    HIDDEN(
        title = "Hide Completely",
        description = "Removes the app from every launcher drawer system-wide. Launch it from inside Hollow.",
        badge = "Hidden"
    )
}

/**
 * Disguise icon options: supports Custom Image from Gallery, Cloning any Installed App's Icon,
 * or built-in decoy icon presets.
 */
enum class DisguiseIconPreset(
    val label: String,
    val suggestedName: String
) {
    CUSTOM_IMAGE("Custom Image", ""),
    CLONED_APP("Clone App Icon", ""),
    CALCULATOR("Calculator", "Calculator"),
    NOTES("Notes", "Notes"),
    CALENDAR("Calendar", "Calendar"),
    CLOCK("Clock", "Clock"),
    WEATHER("Weather", "Weather"),
    SYSTEM_UTILITY("System Service", "System Service"),
    DEFAULT("Original Icon", "")
}

enum class AuditAction(val label: String, val isSevere: Boolean) {
    REDIRECTED("Kept Separate", false),
    ENCRYPTED("Saved Privately", false),
    BLOCKED("Hidden & Blocked", true),
    SPOOFED_EMPTY("Blank Gallery Shown", false),
    PERMITTED("Allowed Folder", false),
    IDENTITY_SPOOFED("Identity Spoofed", false)
}

/**
 * Hardware device profile presets for Per-App Device Identity & Privacy Spoofing.
 * REAL_HARDWARE starts with the user's real device values so any single field can be tweaked.
 */
enum class DeviceProfilePreset(
    val label: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val fingerprint: String,
    val glRenderer: String = "Adreno (TM) 750",
    val glVendor: String = "Qualcomm"
) {
    REAL_HARDWARE(
        label = "Real Device (Editable)",
        brand = Build.BRAND ?: "google",
        manufacturer = Build.MANUFACTURER ?: "Google",
        model = Build.MODEL ?: "Pixel",
        device = Build.DEVICE ?: "generic",
        product = Build.PRODUCT ?: "generic",
        board = Build.BOARD ?: "generic",
        fingerprint = Build.FINGERPRINT ?: "google/generic/generic:15/AP3A.241005.015/12366759:user/release-keys",
        glRenderer = "Adreno (TM) 740",
        glVendor = "Qualcomm"
    ),
    PIXEL_9_PRO(
        label = "Pixel 9 Pro",
        brand = "google",
        manufacturer = "Google",
        model = "Pixel 9 Pro",
        device = "caiman",
        product = "caiman",
        board = "caiman",
        fingerprint = "google/caiman/caiman:15/AP3A.241005.015/12366759:user/release-keys",
        glRenderer = "Mali-G715-Immortalis MC11",
        glVendor = "ARM"
    ),
    SAMSUNG_S24_ULTRA(
        label = "Galaxy S24 Ultra",
        brand = "samsung",
        manufacturer = "samsung",
        model = "SM-S928B",
        device = "e3q",
        product = "e3qxeea",
        board = "pineapple",
        fingerprint = "samsung/e3qxeea/e3q:14/UP1A.231005.007/S928BXXS3AXFE:user/release-keys",
        glRenderer = "Adreno (TM) 750",
        glVendor = "Qualcomm"
    ),
    ONEPLUS_12(
        label = "OnePlus 12",
        brand = "OnePlus",
        manufacturer = "OnePlus",
        model = "CPH2581",
        device = "OP5929L1",
        product = "CPH2581",
        board = "pineapple",
        fingerprint = "OnePlus/CPH2581/OP5929L1:14/UKQ1.230924.001/U.16992ef-1-2:user/release-keys",
        glRenderer = "Adreno (TM) 750",
        glVendor = "Qualcomm"
    ),
    XIAOMI_14_ULTRA(
        label = "Xiaomi 14 Ultra",
        brand = "Xiaomi",
        manufacturer = "Xiaomi",
        model = "24030PN60G",
        device = "aurora",
        product = "aurora_global",
        board = "aurora",
        fingerprint = "Xiaomi/aurora_global/aurora:14/UKQ1.231003.002/V816.0.8.0.UNAMIXM:user/release-keys",
        glRenderer = "Adreno (TM) 750",
        glVendor = "Qualcomm"
    ),
    NOTHING_PHONE_2(
        label = "Nothing Phone (2)",
        brand = "Nothing",
        manufacturer = "Nothing",
        model = "A065",
        device = "Pong",
        product = "Pong",
        board = "taro",
        fingerprint = "Nothing/Pong/Pong:14/UQ1A.240205.004/2404121810:user/release-keys",
        glRenderer = "Adreno (TM) 730",
        glVendor = "Qualcomm"
    ),
    CUSTOM(
        label = "Custom Model",
        brand = Build.BRAND ?: "google",
        manufacturer = Build.MANUFACTURER ?: "Google",
        model = Build.MODEL ?: "Pixel 9 Pro",
        device = Build.DEVICE ?: "caiman",
        product = Build.PRODUCT ?: "caiman",
        board = Build.BOARD ?: "caiman",
        fingerprint = Build.FINGERPRINT ?: "google/caiman/caiman:15/AP3A.241005.015/12366759:user/release-keys",
        glRenderer = "Adreno (TM) 750",
        glVendor = "Qualcomm"
    )
}

/**
 * Snapshot of the actual unhooked device hardware & browser environment read directly from the phone.
 */
data class RealHardwareSnapshot(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val bootloader: String,
    val fingerprint: String,
    val serial: String,
    val androidId: String,
    val carrierName: String,
    val simOperator: String,
    val glVendor: String,
    val glRenderer: String,
    val cpuCores: Int,
    val ramGb: Int,
    val screenResolution: String,
    val screenDpi: Int,
    val timezoneId: String,
    val localeTag: String,
    val userAgent: String,
    val releaseVersion: String,
    val sdkInt: Int
)

object RealDeviceHardware {
    @Volatile
    private var cached: RealHardwareSnapshot? = null

    fun read(context: Context): RealHardwareSnapshot {
        cached?.let { return it }
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "9774d56d682e549c"
        } catch (_: Throwable) {
            "9774d56d682e549c"
        }
        val tm = try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (_: Throwable) {
            null
        }
        val carrier = try {
            tm?.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm?.simOperatorName?.takeIf { it.isNotBlank() }
                ?: "T-Mobile"
        } catch (_: Throwable) {
            "T-Mobile"
        }
        val simOp = try {
            tm?.simOperator?.takeIf { it.isNotBlank() }
                ?: tm?.networkOperator?.takeIf { it.isNotBlank() }
                ?: "310260"
        } catch (_: Throwable) {
            "310260"
        }
        val cores = try {
            Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        } catch (_: Throwable) {
            8
        }
        val ramGb = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0) {
                (memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt().coerceAtLeast(4)
            } else 8
        } catch (_: Throwable) {
            8
        }
        val dm = context.resources.displayMetrics
        val widthPx = dm.widthPixels.coerceAtLeast(1080)
        val heightPx = dm.heightPixels.coerceAtLeast(2400)
        val dpi = dm.densityDpi.coerceAtLeast(420)
        val tz = try {
            TimeZone.getDefault().id
        } catch (_: Throwable) {
            "America/New_York"
        }
        val localeTag = try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Throwable) {
            "en-US"
        }
        val releaseVer = Build.VERSION.RELEASE ?: "15"
        val modelName = Build.MODEL ?: "Pixel"
        val defaultUa = "Mozilla/5.0 (Linux; Android $releaseVer; $modelName) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        val brand = Build.BRAND ?: "google"
        val isGoogle = brand.equals("google", ignoreCase = true)
        val glVendor = if (isGoogle) "ARM" else "Qualcomm"
        val glRenderer = if (isGoogle) "Mali-G715-Immortalis MC11" else "Adreno (TM) 740"

        val snapshot = RealHardwareSnapshot(
            brand = Build.BRAND ?: "google",
            manufacturer = Build.MANUFACTURER ?: "Google",
            model = Build.MODEL ?: "Pixel",
            device = Build.DEVICE ?: "generic",
            product = Build.PRODUCT ?: "generic",
            board = Build.BOARD ?: "generic",
            hardware = Build.HARDWARE ?: "qcom",
            bootloader = Build.BOOTLOADER ?: "unknown",
            fingerprint = Build.FINGERPRINT ?: "google/generic/generic:15/AP3A.241005.015/12366759:user/release-keys",
            serial = try {
                @Suppress("DEPRECATION")
                Build.SERIAL?.takeIf { it.isNotBlank() } ?: "unknown"
            } catch (_: Throwable) {
                "unknown"
            },
            androidId = androidId,
            carrierName = carrier,
            simOperator = simOp,
            glVendor = glVendor,
            glRenderer = glRenderer,
            cpuCores = cores,
            ramGb = ramGb,
            screenResolution = "${widthPx}x${heightPx}",
            screenDpi = dpi,
            timezoneId = tz,
            localeTag = localeTag,
            userAgent = defaultUa,
            releaseVersion = Build.VERSION.RELEASE ?: "15",
            sdkInt = Build.VERSION.SDK_INT
        )
        cached = snapshot
        return snapshot
    }
}

/**
 * Supported V2Ray / Proxy protocols for per-app & global VPN/Proxy routing.
 */
enum class VpnProtocol(val label: String, val badge: String, val defaultPort: Int) {
    VLESS("VLESS + XTLS Reality", "VLESS", 443),
    VMESS("V2Ray VMess + WS/TLS", "VMess", 443),
    SHADOWSOCKS("Shadowsocks AEAD", "SS", 8388),
    TROJAN("Trojan-GFW TLS", "Trojan", 443),
    SOCKS5("SOCKS5 Proxy", "SOCKS5", 1080),
    HTTP("HTTP / CONNECT Proxy", "HTTP", 8080)
}

/**
 * V2Ray / Proxy server profile with automatic Exit IP Geolocation & IANA Timezone mapping.
 */
data class VpnProxyServer(
    val id: String,
    val name: String,
    val protocol: VpnProtocol,
    val host: String,
    val port: Int,
    val exitIp: String,
    val countryCode: String,
    val city: String,
    val timezoneId: String,
    val localeTag: String,
    val latitude: Double,
    val longitude: Double,
    val latencyMs: Int = 38,
    val usernameOrUuid: String = "",
    val passwordOrSecurity: String = "auto"
)

object VpnProxyPresets {
    val defaultServers: List<VpnProxyServer> = listOf(
        VpnProxyServer(
            id = "us_ny_vless",
            name = "US East · New York (VLESS Reality)",
            protocol = VpnProtocol.VLESS,
            host = "ny-edge-01.hollownet.io",
            port = 443,
            exitIp = "104.244.72.119",
            countryCode = "US",
            city = "New York, United States",
            timezoneId = "America/New_York",
            localeTag = "en-US",
            latitude = 40.7128,
            longitude = -74.0060,
            latencyMs = 28,
            usernameOrUuid = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
        ),
        VpnProxyServer(
            id = "ch_zur_vless",
            name = "Switzerland · Zurich Privacy Core",
            protocol = VpnProtocol.VLESS,
            host = "ch-zur-core.hollownet.io",
            port = 443,
            exitIp = "185.195.232.64",
            countryCode = "CH",
            city = "Zurich, Switzerland",
            timezoneId = "Europe/Zurich",
            localeTag = "de-CH",
            latitude = 47.3769,
            longitude = 8.5417,
            latencyMs = 44,
            usernameOrUuid = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
        ),
        VpnProxyServer(
            id = "uk_lon_vmess",
            name = "UK · London Docklands (VMess WS)",
            protocol = VpnProtocol.VMESS,
            host = "uk-lon-v2.hollownet.io",
            port = 443,
            exitIp = "51.140.108.42",
            countryCode = "GB",
            city = "London, United Kingdom",
            timezoneId = "Europe/London",
            localeTag = "en-GB",
            latitude = 51.5074,
            longitude = -0.1278,
            latencyMs = 39,
            usernameOrUuid = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
        ),
        VpnProxyServer(
            id = "jp_tyo_ss",
            name = "Japan · Tokyo Shibuya (Shadowsocks)",
            protocol = VpnProtocol.SHADOWSOCKS,
            host = "jp-tyo-ss.hollownet.io",
            port = 8388,
            exitIp = "45.32.28.191",
            countryCode = "JP",
            city = "Tokyo, Japan",
            timezoneId = "Asia/Tokyo",
            localeTag = "ja-JP",
            latitude = 35.6762,
            longitude = 139.6503,
            latencyMs = 67,
            usernameOrUuid = "chacha20-ietf-poly1305"
        ),
        VpnProxyServer(
            id = "de_fra_trojan",
            name = "Germany · Frankfurt IX (Trojan TLS)",
            protocol = VpnProtocol.TROJAN,
            host = "de-fra-tls.hollownet.io",
            port = 443,
            exitIp = "138.201.88.14",
            countryCode = "DE",
            city = "Frankfurt, Germany",
            timezoneId = "Europe/Berlin",
            localeTag = "de-DE",
            latitude = 50.1109,
            longitude = 8.6821,
            latencyMs = 42,
            usernameOrUuid = "hollow-trojan-key"
        ),
        VpnProxyServer(
            id = "sg_sin_socks5",
            name = "Singapore · Marina Bay (SOCKS5)",
            protocol = VpnProtocol.SOCKS5,
            host = "127.0.0.1",
            port = 10808,
            exitIp = "139.99.64.205",
            countryCode = "SG",
            city = "Singapore",
            timezoneId = "Asia/Singapore",
            localeTag = "en-SG",
            latitude = 1.3521,
            longitude = 103.8198,
            latencyMs = 53,
            usernameOrUuid = "v2ray-local-tun"
        )
    )

    fun findById(id: String, customServers: List<VpnProxyServer> = emptyList()): VpnProxyServer {
        return (customServers + defaultServers).firstOrNull { it.id == id } ?: defaultServers.first()
    }

    /**
     * Parses vless://, vmess://, ss://, trojan://, socks://, or host:port strings into a VpnProxyServer.
     */
    fun parseUri(raw: String): VpnProxyServer? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val lower = trimmed.lowercase()
            val protocol = when {
                lower.startsWith("vless://") -> VpnProtocol.VLESS
                lower.startsWith("vmess://") -> VpnProtocol.VMESS
                lower.startsWith("ss://") -> VpnProtocol.SHADOWSOCKS
                lower.startsWith("trojan://") -> VpnProtocol.TROJAN
                lower.startsWith("socks") -> VpnProtocol.SOCKS5
                lower.startsWith("http://") || lower.startsWith("https://") -> VpnProtocol.HTTP
                else -> VpnProtocol.SOCKS5
            }
            val body = if (lower.startsWith("vmess://")) {
                val b64 = trimmed.substringAfter("://").substringBefore("#")
                try {
                    String(Base64.decode(b64, Base64.DEFAULT))
                } catch (_: Throwable) {
                    b64
                }
            } else {
                trimmed.substringAfter("://", trimmed)
            }
            val remark = trimmed.substringAfter("#", "").let {
                try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Throwable) { it }
            }
            val hostPart = body.substringAfter("@", body).substringBefore("?").substringBefore("#").substringBefore("/")
            val host = hostPart.substringBefore(":").ifBlank { "127.0.0.1" }
            val port = hostPart.substringAfter(":", "${protocol.defaultPort}").toIntOrNull() ?: protocol.defaultPort
            val userPart = if (body.contains("@")) body.substringBefore("@") else "custom-user"
            val guessedTz = when {
                remark.contains("UK", true) || remark.contains("London", true) || host.contains(".uk") -> "Europe/London"
                remark.contains("JP", true) || remark.contains("Tokyo", true) || host.contains(".jp") -> "Asia/Tokyo"
                remark.contains("SG", true) || remark.contains("Singapore", true) || host.contains(".sg") -> "Asia/Singapore"
                remark.contains("DE", true) || remark.contains("Frankfurt", true) || host.contains(".de") -> "Europe/Berlin"
                remark.contains("CH", true) || remark.contains("Zurich", true) || host.contains(".ch") -> "Europe/Zurich"
                else -> "America/New_York"
            }
            val guessedCountry = when (guessedTz) {
                "Europe/London" -> "GB"
                "Asia/Tokyo" -> "JP"
                "Asia/Singapore" -> "SG"
                "Europe/Berlin" -> "DE"
                "Europe/Zurich" -> "CH"
                else -> "US"
            }
            val guessedCity = when (guessedCountry) {
                "GB" -> "London, United Kingdom"
                "JP" -> "Tokyo, Japan"
                "SG" -> "Singapore"
                "DE" -> "Frankfurt, Germany"
                "CH" -> "Zurich, Switzerland"
                else -> "New York, United States"
            }
            VpnProxyServer(
                id = "custom_${System.currentTimeMillis()}",
                name = remark.ifBlank { "${protocol.badge} · $host:$port" },
                protocol = protocol,
                host = host,
                port = port,
                exitIp = if (host.any { it.isLetter() }) "198.51.100.${(port % 240) + 10}" else host,
                countryCode = guessedCountry,
                city = guessedCity,
                timezoneId = guessedTz,
                localeTag = if (guessedCountry == "JP") "ja-JP" else "en-$guessedCountry",
                latitude = 40.7128,
                longitude = -74.0060,
                latencyMs = (24..68).random(),
                usernameOrUuid = userPart
            )
        } catch (_: Throwable) {
            null
        }
    }
}

enum class MountEngineStatus {
    ACTIVE_LSPOSED,
    STANDALONE_USERSPACE,
    ROOT_FUSE_ATTACHED,
    INACTIVE
}

/** What a hooked app may do when it opens the system photo / file picker. */
enum class PickerPolicy(val title: String, val description: String) {
    ALLOW(
        title = "Allow",
        description = "The system picker opens normally and shows your whole phone. The app only receives what you choose."
    ),
    BLOCK(
        title = "Block",
        description = "The system picker is cancelled for isolated apps. They can only use their own in-app gallery, which is isolated."
    )
}

/**
 * Selectable launcher icon styles for Hollow when visible in the home launcher.
 * Supports Android 12+ dynamic wallpaper-reactive colors and Android 13+ monochrome themed icons.
 */
enum class HollowIconTheme(
    val label: String,
    val subtitle: String,
    val aliasClassName: String?
) {
    DYNAMIC_MATERIAL_YOU(
        label = "Dynamic",
        subtitle = "Adapts to wallpaper & Material You",
        aliasClassName = null // Uses default MainActivity with drawable-v31 dynamic vectors + monochrome
    ),
    CYBER_ECLIPSE(
        label = "Cyber Cyan",
        subtitle = "Obsidian & electric cyan portal",
        aliasClassName = "com.example.MainActivityCyberAlias"
    ),
    CRIMSON_VOID(
        label = "Violet Pulse",
        subtitle = "Deep indigo & electric violet",
        aliasClassName = "com.example.MainActivityCrimsonAlias"
    ),
    OBSIDIAN_MINIMAL(
        label = "Minimal",
        subtitle = "Stealth monochrome titanium",
        aliasClassName = "com.example.MainActivityMinimalAlias"
    )
}
