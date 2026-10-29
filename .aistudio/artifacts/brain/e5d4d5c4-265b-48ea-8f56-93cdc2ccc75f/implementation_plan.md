# Professional Proxy/V2Ray Suite & Full-Featured Privacy Architecture

This update refines the **Dashboard VPN Card**, overhauls the **Proxy & V2Ray Screen** into a professional 3-tab configuration suite, introduces **Apps | Profiles segmented tabs** on the Privacy screen with clean app cards, and restores **every hardware, GPS, anti-detection, and fingerprint control in fully expanded pro cards** inside each app's configuration page without forcing a default profile.

---

## User Review & Confirmed Decisions

> [!IMPORTANT]
> The following architectural and UX decisions have been locked in based on your feedback:

- **Dashboard VPN/Proxy Card**: No On/Off toggle button on the Dashboard card. It cleanly displays live routing status (e.g., active protocol, server, and routed app count or global status) and opens the dedicated Proxy & V2Ray suite when tapped.
- **Proxy & V2Ray Organization**: Structured into **3 dedicated tabs** (**Servers**, **Per-App Routing**, and **Advanced Proxy**) using Hollow's signature dark/emerald professional aesthetic rather than dummy static placeholders.
- **Privacy Screen & Profiles Access**: Top **Segmented Tabs (`Apps` | `Profiles`)** on the Privacy screen. App cards in the list are clean and minimal (no cluttered `AID`/`GPU` chip rows or inline `Reset`/`Configure` text buttons).
- **App Privacy Configuration Page**: **All sections expanded by default** in clean professional cards with 100% of hardware, build prop, GPS, sensor, and shield features visible. **No predefined profile is applied by default** (`None / Manual` until you explicitly pick one).

---

## 1. Overview & Core Concept

- **What It Does**: Provides a production-grade control center for configuring real SOCKS5, HTTP, and V2Ray (VLESS, VMess, Trojan, Shadowsocks) servers with global or per-app routing, alongside a comprehensive per-app hardware identity and privacy configuration suite with optional reusable profiles.
- **Key Value**: Eliminates visual clutter on summary cards while giving power users complete, unobstructed access to every server parameter, build property, hardware identifier, and anti-tracking shield inside dedicated configuration views.

---

## 2. User Experience & Visual Design

### A. Dashboard Proxy / V2Ray Card (Clean Status Entry Point)
- **No Inline Switch**: Displays the shield icon, **Proxy & V2Ray** title, live status dot, and a single-line summary (e.g., `VLESS • US East (4 apps routed)` or `Whole Device Tunnel Active` or `Configure SOCKS5, HTTP & V2Ray routing`) with a subtle right chevron.
- **Action**: Tapping anywhere on the card navigates to the 3-tab Proxy & V2Ray screen.

### B. Professional Proxy & V2Ray Suite (3 Tabs)
- **Top Global Status Banner**:
  - Displays current tunnel state (`Connected` vs `Standby`), active server summary, exit IP & timezone, and a clean mode selector (**Whole Device** vs **Per-App Routing**) with a primary **Connect / Disconnect** button.
- **Tab 1: `Servers` (Real Proxy & V2Ray Configuration)**:
  - **Quick Actions**: `+ Add Custom Server` (full manual form for SOCKS5, HTTP, VLESS, VMess, Trojan, Shadowsocks with Host, Port, UUID/Username, Password/Security, Exit IP, Country, and IANA Timezone) and `Paste URI` (`vless://`, `vmess://`, `ss://`, `trojan://`, `socks://`).
  - **Server Cards**: Shows protocol badge (`VLESS`, `VMESS`, `SOCKS5`, `HTTP`), server name, host:port, exit IP, synced timezone, latency badge, active selection radio, and delete/edit controls for custom nodes.
- **Tab 2: `Per-App Routing`**:
  - Search input and filter chips (`Routed`, `Browsers & Social`, `Downloaded`, `All`).
  - Batch action buttons (`Route All Apps`, `Clear All`).
  - **Per-App Row**: App icon, title, package name, routing toggle switch, and an expandable per-app server selector (allowing different apps to route through different proxy/V2Ray nodes simultaneously) plus per-app `Kill Switch` and `Sync Timezone with Exit IP` toggles.
- **Tab 3: `Advanced Proxy`**:
  - Global protocol & stealth guards:
    - `Strict VPN Kill Switch` (blocks app network if tunnel drops)
    - `SOCKS5 Socket Hook` & `HTTP/HTTPS JVM Proxy Hook`
    - `WebView Traffic Routing`
    - `Trust Custom SSL/TLS Certificates`
    - `Hide VPN & Proxy Interfaces from Apps` (`tun0`/`ppp0` cloak)
    - `Auto-Sync Timezone & Locale with Exit IP`

### C. Privacy Screen (`Apps` | `Profiles` Segmented Tabs)
- **Tab 1: `Apps` (Clean Professional App List)**:
  - Search bar, filter chips (`Spoofed`, `Downloaded`, `System`, `All`), and `+ Add Apps` button.
  - **Clean App Card**: Displays only the App Icon, App Name, a clean 1-line subtitle (e.g., `Pixel 9 Pro • Custom Shields` or `Profile: Ghost Cloak` or `Not spoofed • Tap to configure`), and the master toggle switch. All messy bottom chips (`AID`, `IMEI`, `GPU`, `UA`) and inline `Reset All` / `Configure Privacy` buttons are removed.
- **Tab 2: `Profiles` (Reusable Profile Manager)**:
  - Lists built-in profiles (`Ghost Cloak`, `Social Media Shield`, `Anti-Fraud Armor`) and user-created custom profiles, plus a `+ Create Profile` button.
  - Each profile card shows its summary and lets you **Edit** its shields/presets, **Duplicate**, **Reset to Default**, or **Delete** custom profiles.

### D. Full-Featured App Privacy Configuration Page (All Sections Expanded)
When you tap any app in the Privacy list, its full configuration page opens with **all cards expanded by default** and **no profile forced by default**:
1. **Top Bar & Master Identity Card**:
   - App title, `Save & Apply` button, Master Identity Spoofing switch, and `Randomize All IDs` button.
   - **Optional Profile Picker Bar**: Defaults to **`None (Manual Configuration)`**. Allows 1-tap loading of any saved profile or `Save Current as Profile`.
2. **Card 1 — Hardware Preset & Full Build Properties**:
   - Preset chips (`Real Device`, `Pixel 9 Pro`, `Galaxy S24 Ultra`, `OnePlus 12`, `Xiaomi 14 Pro`, `Custom`).
   - Editable text fields for `Brand`, `Manufacturer`, `Model`, `Device Codename`, `Product`, `Board`, `Hardware`, `Bootloader`, and `Build Fingerprint`.
3. **Card 2 — Device & Telecom Identifiers**:
   - Individual fields with 1-tap randomize buttons for `Android ID`, `IMEI (Luhn-valid)`, `Google Advertising ID (GAID)`, `Wi-Fi MAC Address`, `Hardware Serial Number`, `Carrier Name`, `SIM Operator (MCC/MNC)`, and `Spoof Widevine DRM ID` toggle.
4. **Card 3 — Display, GPU & Browser Anti-Fingerprinting**:
   - `Spoof GPU Fingerprint` toggle + editable `GPU Vendor` & `GPU Renderer` fields.
   - Editable `Screen Resolution`, `Screen DPI`, `CPU Cores`, and `RAM (GB)`.
   - Toggles for `WebGL & Canvas 2D Noise`, `AudioContext Fingerprint Noise`, `WebRTC Local IP Leak Blocker`, `Client Hints & Font Masking`, `Spoof Browser User-Agent`, and editable `Custom User-Agent`.
5. **Card 4 — Virtual GPS Location & Timezone**:
   - `Enable Virtual GPS Location` switch, Quick City Teleport chips (`San Francisco`, `New York`, `London`, `Tokyo`, `Paris`, `Sydney`, `Zurich`, `Singapore`), `Latitude` & `Longitude` fields, `Hide Mock Location Status` toggle, `Auto-Sync Timezone` toggle, and editable `Timezone ID` & `Locale Tag`.
6. **Card 5 — Sensors & Virtual Battery**:
   - `Anonymize Hardware Sensors` (scrub vendor strings), `MEMS Calibration Bias Neutralizer`, and `Virtual Battery State` switch with interactive `Battery Level %` slider.
7. **Card 6 — Anti-Detection, System & App Shields**:
   - Toggles for `Hide Root, Magisk, KernelSU & LSPosed`, `Hide ADB & Developer Options`, `Hide VPN & Proxy Interfaces`, `Knox Warranty Bit 0x0`, `Block Cell Tower Trilateration`, `Spoof Wi-Fi BSSID / SSID`, `Block Clipboard Snooping`, `Hide Other Installed Apps`, `Prevent Screenshots & Recents Preview`, `Disable Logcat Logging`, and `Network Kill Switch (Block All Outbound Traffic)`.
8. **Card 7 — Root Deadman Switch (Identity Leak Fail-Safe) & Reset**:
   - `Enable Total Identity Leak Deadman Switch` (terminates app + drops UID network via `iptables` + posts system alert on unhooked hardware probes), `Test Leak Watchdog`, `Restore App Network`, and `Reset App to Real Device` button.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Un-collapsed Pro Cards vs. Hidden Accordions in App Privacy**
  - *Chosen Approach*: Keep all 7 configuration cards expanded by default in a smooth scrollable layout.
  - *Why*: Collapsing categories hid critical build properties, GPS controls, and anti-detection toggles, making the app feel like features were removed.
- **Decision 2: Opt-In Privacy Profiles (`None` by Default)**
  - *Chosen Approach*: New and existing apps default to `privacyProfileId = ""` (`None / Manual`). Profiles live in their own `Profiles` tab on the main Privacy screen and can be optionally applied inside an app's page.
  - *Why*: Prevents unexpected overrides of manual per-app settings while keeping reusable templates one tap away.

---

## 4. Technical Architecture & Component Hierarchy

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              DASHBOARD SCREEN                            │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ Proxy & V2Ray Status Card (No inline switch -> Taps to VpnProxy)   │  │
│  └─────────────────────────────────┬──────────────────────────────────┘  │
└────────────────────────────────────┼─────────────────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                     PROXY & V2RAY SUITE (3-Tab Screen)                   │
│  ┌──────────────────────┬───────────────────────┬─────────────────────┐  │
│  │   Tab 1: Servers     │ Tab 2: Per-App Route  │ Tab 3: Adv. Proxy   │  │
│  │ • Global/App Mode    │ • App Search & Filter │ • Strict KillSwitch │  │
│  │ • Add SOCKS/HTTP/V2  │ • Per-App Toggle      │ • SOCKS/HTTP/WebView│  │
│  │ • Paste VLESS/VMess  │ • Per-App Node Picker │ • Certs & TZ Sync   │  │
│  └──────────────────────┴───────────────────────┴─────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                      PRIVACY SCREEN (Segmented Tabs)                     │
│  ┌─────────────────────────────────┬──────────────────────────────────┐  │
│  │          Tab 1: Apps            │         Tab 2: Profiles          │  │
│  │ • Clean App Cards (No chip row) │ • Ghost Cloak / Social / Fraud   │  │
│  │ • 1-Line Status + Master Switch │ • Create / Edit / Reset Profiles │  │
│  └────────────────┬────────────────┴──────────────────────────────────┘  │
└───────────────────┼──────────────────────────────────────────────────────┘
                    ▼ (Tap App Card)
┌──────────────────────────────────────────────────────────────────────────┐
│             APP PRIVACY CONFIGURATION (All Cards Expanded)               │
│  • Optional Profile Picker (Default: None / Manual) + Randomize All      │
│  • Card 1: Hardware Preset & Full Build Props (Brand..Fingerprint)       │
│  • Card 2: Identifiers (AID, IMEI, GAID, MAC, Serial, Carrier, DRM)      │
│  • Card 3: Display, GPU, Resolution, CPU/RAM, Canvas, WebRTC, UA         │
│  • Card 4: Virtual GPS Location (City Presets, Lat/Lng, Mock Cloak, TZ)  │
│  • Card 5: Sensors (Vendor Scrub, MEMS Bias) & Fake Battery Slider       │
│  • Card 6: Anti-Detection & App Shields (Root, ADB, VPN, Knox, Clip...)  │
│  • Card 7: Root Deadman Switch (Kill + iptables + Alert) & Full Reset    │
└──────────────────────────────────────────────────────────────────────────┘
```
