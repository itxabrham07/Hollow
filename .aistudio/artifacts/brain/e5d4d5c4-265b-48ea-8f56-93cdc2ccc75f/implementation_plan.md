# Implementation Plan — Privacy Profiles, Categorized Suite, Total Leak Deadman & Hollow Drive

A robust, enterprise-grade architecture providing **Reusable Privacy Profiles with per-app overrides**, a **categorized rewrite of the Privacy screen**, a **Total Identity Leak Deadman Switch** (process kill + iptables drop + security alert), **zero-exposure private Hollow Drive storage**, and a **Dashboard VPN/Proxy quick card**.

---

## 1. Reusable Privacy Profiles & Per-App Overrides

### A. Predefined Profiles (Editable & Extensible)
1. **Ghost Cloak (Total Anonymity)**:
   - Full hardware randomization: Android ID, IMEI, IMSI, Serial, MAC, GAID, DRM Widevine UUID.
   - GPU Vendor & Renderer spoofing (Adreno 750 / Mali-G715), WebGL Canvas protection.
   - Sensor vendor scrubbing, WebRTC IP leak shield, Clipboard isolation, Battery entropy masking, Timezone coordinate sync.
2. **Social Media Shield (Meta, Snapchat, TikTok Cloak)**:
   - Optimized to break cross-app tracking SDKs without tripping device attestation:
   - GPU Renderer spoofing, Browser User-Agent alignment, Sensor bias removal, Cell Tower trilateration blocking, Clipboard snooping protection.
3. **Anti-Fraud Armor (Fintech & Banking Anti-Fraud)**:
   - Consistent device profile matching: High-reputation device build properties (Pixel 9 Pro / Galaxy S24 Ultra), static Widevine DRM ID, Battery status normalization, strict Timezone lock.
4. **Custom Profiles**:
   - Users can create, clone, rename, edit, and delete custom profiles.
   - Predefined profiles can also be edited or reset to factory defaults.

### B. Per-App Application & Granular Overrides
- When an app is assigned a profile, its settings inherit from that profile.
- Users can toggle individual shields for a specific app (e.g. keeping Ghost Cloak but disabling Battery Masking for a power-monitoring app).
- Clear visual pill in UI: `Default Profile` vs `Customized (2 Overrides)`.

---

## 2. Privacy Screen Rewrite: Clean Categorized Architecture

Restructure `DeviceSpooferScreen.kt` into intuitive, expandable cards matching modern privacy suites:

```
┌────────────────────────────────────────────────────────────────────────┐
│ [Profile Dropdown: Ghost Cloak ▼]  [Save as Profile]  [1-Tap Cloak]   │
├────────────────────────────────────────────────────────────────────────┤
│ ▼ Hardware & Device IDs                                                │
│   • Android ID, IMEI/MEID, IMSI, Serial Number, MAC Address            │
│   • Google Advertising ID (GAID) Reset/Randomizer                     │
│   • DRM Widevine Device Unique ID Spoofing                             │
├────────────────────────────────────────────────────────────────────────┤
│ ▼ Display & Graphics (Anti-Canvas Fingerprinting)                      │
│   • OpenGL ES & WebGL Renderer (Adreno / Mali)                        │
│   • GPU Vendor & Driver Build Tags                                     │
│   • Screen Resolution & Density Masking                                │
├────────────────────────────────────────────────────────────────────────┤
│ ▼ Sensors & Hardware Environment                                       │
│   • Hardware Sensor Vendor Scrubbing (Bosch/InvenSense)                │
│   • Micro-machined Sensor Calibration Bias Neutralizer                 │
│   • Battery Status & Power Entropy Masking                             │
├────────────────────────────────────────────────────────────────────────┤
│ ▼ Network & Connectivity Shields                                       │
│   • WebRTC Local IP Leak Blocker (RFC 1918 & STUN shield)             │
│   • Wi-Fi BSSID / SSID Spoofing                                       │
│   • Cell Tower Trilateration Blocker (TelephonyManager)               │
├────────────────────────────────────────────────────────────────────────┤
│ ▼ System & Environment                                                 │
│   • OS Build Properties (ro.build.version, model, fingerprint)        │
│   • Auto Timezone & Geo-Coordinate Alignment                           │
│   • Browser & WebView User-Agent Cloak                                │
│   • Clipboard Access Guard & Scrubber                                  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Total Identity Leak Deadman Switch (Root + LSPosed)

### A. The "Zero Identity Leak" Threat Model
Modern advanced tracking SDKs probe deeper than just network IP; they attempt to access:
- `/proc/cpuinfo`, `/sys/class/net/wlan0/address`, `/sys/devices/soc0/serial_number`
- `libc __system_property_get` / native properties
- Raw unhooked MediaDrm calls or native JNI evasion
- Real Android ID, IMEI, or real MAC address queries if an LSPosed hook detaches.

### B. Fail-Safe Execution Sequence
1. **Watchdog Interception**:
   - If an app attempts to read real hardware attributes or hook evasion is detected:
2. **Instant Process Termination**:
   - Execute Root: `am force-stop <target_package>` and `kill -9 <target_pid>`.
3. **Root `iptables` Packet Blackhole**:
   - Drop all outbound packets for that app's Linux UID:
     ```bash
     iptables -I OUTPUT -m owner --uid-owner <app_uid> -j DROP
     ```
   - Stops any collected identity payload from leaving the device.
4. **High-Priority Security Notification**:
   - Trigger Android System Notification:
     - Title: **"Identity Leak Attempt Blocked"**
     - Body: *"<App Name> attempted to bypass identity hooks (<Vector Name>). Process killed and network severed."*
     - Action button: *Inspect Event Log* / *Restore Network*.

---

## 4. True Storage Isolation: Zero-Exposure Hollow Drive

### A. Protected Sandboxed Storage
- Hollow Drive is placed exclusively in Hollow's private app directory:
  ```
  /data/user/0/com.aistudio.isovault.xrvf/files/hollow_drive/
  ```
- File permissions enforced as **`0700` (`rwx------`)** with root-level `.nomedia`.
- **Google Files, Google Photos, MediaStore scanner, and host apps are 100% blocked** by kernel sandboxing from discovering or reading these files.
- LSPosed hooks redirect `/sdcard` and `/storage/emulated/0` transparently into this vault for managed apps.
- All apps default to this unified Hollow Drive; isolated private drives can still be assigned per app.

---

## 5. Dashboard VPN & Proxy Quick Card

### A. Interactive Quick Controls
- Located prominently on the Main Dashboard:
  1. Master Quick Connect Switch.
  2. Protocol Selector Chip Group (**V2Ray**, **SOCKS5**, **HTTP**).
  3. Strict Network Kill Switch Toggle.
  4. Live Ping, Status, and Synced Timezone indicator.
  5. Shortcut to full `VpnProxyScreen`.

---

## 6. Android Phone-Style File Manager

### A. Hollow Drive File Explorer
- **Top Category Grid**: Images / Gallery, Videos, Audio, Documents, Downloads (querying only inside the private vault).
- **Primary Internal Storage**: Hollow Drive root folder explorer (`DCIM`, `Download`, `Documents`, `Pictures`, `Music`).
- **External Storage Section**: Per-app assigned private drives listed below like external SD card volumes.
- **Actions**: Create Folder, Rename, Delete, Secure Wipe, and Explicit Import/Export to device.

---

## 7. Verification & Testing
- Validate Room database migrations for Privacy Profiles and expanded config entities.
- Verify compilation with `compile_applet`.
- Execute local unit tests via `gradle :app:testDebugUnitTest`.
- Test UI responsiveness: category expand/collapse, profile switching, quick VPN toggle, and Deadman Switch simulation.
