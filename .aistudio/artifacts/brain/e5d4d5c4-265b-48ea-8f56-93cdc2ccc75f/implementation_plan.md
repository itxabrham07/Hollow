# Hollow — Privacy Engine & Digital Footprint Shield

A system-wide **App Hider, Storage Isolator, Device Identity Spoofer & Digital Footprint Cloak** for Android using LSPosed/Vector hooks and Room persistence.

## 1. Overview & Anti-Tracking Capabilities

Modern anti-fraud and cross-app tracking SDKs (used by Meta/Facebook/Instagram, Snapchat, TikTok, ThreatMetrix, Adjust, AppsFlyer, and web browsers) bypass standard mock GPS and build property hooks by gathering hardware-level entropy:
1. **GPU & WebGL Canvas Fingerprinting**: Querying `GLES20.glGetString(GL_RENDERER)` and `glGetString(GL_VENDOR)` to identify chipsets and driver builds.
2. **Web & In-App WebView Fingerprint**: Querying `WebSettings.getDefaultUserAgent()` / `getUserAgentString()` and JavaScript `navigator.userAgent`.
3. **Hardware Sensor Calibration & Bias**: Iterating `SensorManager.getSensorList()` to inspect proprietary vendor tags (Bosch, InvenSense, STMicroelectronics) and measuring micro-machined sensor bias.
4. **Cell Tower Trilateration**: Querying `TelephonyManager.getAllCellInfo()`, `getNeighboringCellInfo()`, and `getCellLocation()` to locate the device even when GPS is mocked.
5. **Timezone Mismatch Detection**: Comparing GPS longitude against `java.util.TimeZone.getDefault()` and `persist.sys.timezone`.

Hollow neutralizes all five attack vectors with per-app granular toggles and a **1-Tap Digital Footprint Cloak**.

---

## 2. Technical Architecture & Hook Injections

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HOLLOW HOOKING ENGINE                           │
│                                                                        │
│  ┌───────────────────────────┐        ┌─────────────────────────────┐  │
│  │    OpenGL ES & WebGL      │        │   Browser & WebView Cloak   │  │
│  │   `GLES20.glGetString`    │        │  `WebSettings.getUserAgent` │  │
│  │  Returns preset GPU model │        │   Standard Chrome/Android   │  │
│  │  (Adreno 750 / Mali-G715) │        │     matching device model   │  │
│  └─────────────┬─────────────┘        └──────────────┬──────────────┘  │
│                │                                     │                 │
│  ┌─────────────┴─────────────┐        ┌──────────────┴──────────────┐  │
│  │ Hardware Sensor Anonymizer│        │   Cellular Radio Shield     │  │
│  │   `Sensor.getVendor()`    │        │`TelephonyManager.getCell...`│  │
│  │    `Sensor.getName()`     │        │ Returns empty/null to stop  │  │
│  │  Scrubs proprietary tags  │        │   base-station positioning  │  │
│  └─────────────┬─────────────┘        └──────────────┬──────────────┘  │
│                │                                     │                 │
│                └──────────────────┬──────────────────┘                 │
│                                   ▼                                    │
│                    ┌─────────────────────────────┐                     │
│                    │  Location & Timezone Sync   │                     │
│                    │  `TimeZone.getDefault()`    │                     │
│                    │   `persist.sys.timezone`    │                     │
│                    │ Aligns timezone with coords │                     │
│                    └─────────────────────────────┘                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Implemented Components

| Component | Responsibility |
| :--- | :--- |
| **`Enums.DeviceProfilePreset`** | Hardware profiles (Pixel 9 Pro, Galaxy S24 Ultra, OnePlus 12, Xiaomi 14 Ultra, Nothing Phone 2) with respective `glRenderer` and `glVendor`. |
| **`AppIsolationConfig` & `InstalledAppInfo`** | Stores `spoofGpuFingerprint`, `spoofBrowserUserAgent`, `anonymizeSensors`, `blockCellTowerTracking`, `autoSyncTimezone`. |
| **`IsolationRuleManager`** | Serializes digital footprint configuration into the IPC table (`isovault_rules.xml` and ContentProvider). |
| **`DeviceIdentitySpoofHook`** | LSPosed hooks for OpenGL ES (`GLES20`/`GLES30`/`GL10`), WebSettings user-agents, SensorManager anonymization, TelephonyManager cell info blocking, and TimeZone coordinate resolution. |
| **`DeviceSpooferScreen`** | Full-screen app privacy configuration with "1-Tap Digital Footprint Cloak" button, dedicated shield cards, and real-time metadata status chips. |
