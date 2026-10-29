package com.example.isolation.module.network

import android.graphics.drawable.Drawable
import android.util.Base64

enum class VpnProtocol(val label: String, val badge: String, val defaultPort: Int) {
    VLESS("VLESS + XTLS Reality", "VLESS", 443),
    VMESS("V2Ray VMess + WS/TLS", "VMess", 443),
    SHADOWSOCKS("Shadowsocks AEAD", "SS", 8388),
    TROJAN("Trojan-GFW TLS", "Trojan", 443),
    SOCKS5("SOCKS5 Proxy", "SOCKS5", 1080),
    HTTP("HTTP / CONNECT Proxy", "HTTP", 8080)
}

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

data class VpnAppRoutingEntry(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isProxyEnabled: Boolean = false
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
        )
    )

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
                else -> VpnProtocol.SOCKS5
            }
            val body = trimmed.substringAfter("://", trimmed)
            val remark = trimmed.substringAfter("#", "").ifBlank { "${protocol.badge} Server" }
            val hostPart = body.substringAfter("@", body).substringBefore("?").substringBefore("#").substringBefore("/")
            val host = hostPart.substringBefore(":").ifBlank { "127.0.0.1" }
            val port = hostPart.substringAfter(":", "${protocol.defaultPort}").toIntOrNull() ?: protocol.defaultPort

            VpnProxyServer(
                id = "custom_${System.currentTimeMillis()}",
                name = remark,
                protocol = protocol,
                host = host,
                port = port,
                exitIp = if (host.any { it.isLetter() }) "198.51.100.22" else host,
                countryCode = "US",
                city = "Custom Proxy Endpoint",
                timezoneId = "America/New_York",
                localeTag = "en-US",
                latitude = 40.7128,
                longitude = -74.0060,
                latencyMs = 45
            )
        } catch (_: Throwable) {
            null
        }
    }
}
