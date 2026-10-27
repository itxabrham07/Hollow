# Implementation Plan: Dynamic Module Engine, MongoDB Atlas Web Backend & Root Vault

## 1. System Architecture Overview

```
+---------------------------------------------------------------------------------------------------+
|                                      MONGODB ATLAS CLOUD                                          |
|  - Users Collection: Username, email, bcrypt password hash, role                                  |
|  - Devices Collection: Hardware fingerprint, allowed modules, validity, approval/ban status       |
|  - Modules Collection: Metadata, signed payload URLs, checksums, enabled state                   |
|  - Incidents Collection: Root Deadman Watchdog leak trigger logs                                  |
+---------------------------------------------------------------------------------------------------+
                                                  ^
                                                  | Mongoose / TLS Connection (MONGODB_URI)
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                  DOCKERIZED WEB SERVER (/web)                                     |
|  - Express.js REST & WebSocket API with JWT Authentication                                        |
|  - Public Web Admin Portal: User approval queue, module toggles, 1-click ban device               |
|  - Anti-MITM Cryptographic Layer: ChaCha20/AES payload encryption + Ed25519 token signatures      |
+---------------------------------------------------------------------------------------------------+
                                                  ^
                                                  | HTTPS + Anti-MITM Encrypted Requests
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                 HOLLOW CORE SHELL (RELEASE APK)                                   |
|  - Zero Feature Modules Bundled in Release APK (Clean, lightweight host shell)                    |
|  - Registration & Login UI: Username, Email, Password -> "Waiting for Admin Approval"            |
|  - Dynamic Module Downloader: In-app download notifications, verified via Ed25519                |
+---------------------------------------------------------------------------------------------------+
                                                  |
                                                  | Root Access (UID 0)
                                                  v
+---------------------------------------------------------------------------------------------------+
|                       ROOT SECRET VAULT (SURVIVES CLEARING APP DATA)                              |
|  Location: /data/adb/.hollow_vault (chmod 700, chown 0:0)                                         |
|  - /data/adb/.hollow_vault/license.enc : Hardware-bound token & monotonic clock log               |
|  - /data/adb/.hollow_vault/modules/     : Encrypted downloaded module bytecode                    |
|  * Survives "Clear App Data / Clear Storage" and app reinstallation!                             |
+---------------------------------------------------------------------------------------------------+
                                                  |
                                                  v (In-Memory Decryption & Dynamic Mounting)
+---------------------------------------------------------------------------------------------------+
|                                   DOWNLOADED FEATURE MODULES                                      |
|  [Module 1] App Hider & SystemUI Disguise                                                         |
|  [Module 2] Storage Isolation, Virtual Volumes & AES-256 Vault                                     |
|  [Module 3] Hardware Profile Spoofer, Virtual GPS & Deadman Watchdog                              |
|  [Module 4] V2Ray, Proxy & Strict Network Kill Switch Suite                                       |
+---------------------------------------------------------------------------------------------------+
```

---

## 2. MongoDB Atlas Database Schema & Backend Stack

### A. Environment Configuration (`/web/.env.example`)
```env
PORT=3000
MONGODB_URI=mongodb+srv://<username>:<password>@cluster0.mongodb.net/hollow_db?retryWrites=true&w=majority
JWT_SECRET=your_super_secret_jwt_key_here
ADMIN_USERNAME=admin
ADMIN_PASSWORD_HASH=$2b$10$...
SIGNING_PRIVATE_KEY_B64=...
```

### B. Mongoose Collections & Data Models
1. **User (`User.js`)**:
   - `username`: String (unique, required)
   - `email`: String (unique, required, lowercase)
   - `passwordHash`: String (bcrypt hashed)
   - `role`: String (`admin` | `user`, default `user`)
   - `createdAt`: Date
2. **Device (`Device.js`)**:
   - `userId`: ObjectId (reference to User)
   - `hardwareFingerprint`: String (unique, indexed; `SHA-256(SoC + AID + CID)`)
   - `socSerial`: String
   - `androidId`: String
   - `storageCid`: String
   - `deviceModel`: String
   - `status`: String (`pending` | `approved` | `revoked` | `banned`, default `pending`)
   - `allowedModules`: [String] (e.g. `["module_hider", "module_isolation", "module_privacy", "module_network"]`)
   - `licenseValidUntil`: Date
   - `lastHeartbeatAt`: Date
   - `lastIp`: String
   - `clientVersion`: String
   - `banReason`: String
3. **Module (`Module.js`)**:
   - `moduleId`: String (unique, e.g. `module_privacy`)
   - `name`: String
   - `description`: String
   - `version`: Number
   - `minCoreVersion`: Number
   - `payloadUrl`: String
   - `sha256Checksum`: String
   - `signatureB64`: String
   - `isEnabled`: Boolean
4. **SecurityIncident (`SecurityIncident.js`)**:
   - `deviceId`: ObjectId (ref to Device)
   - `hardwareFingerprint`: String
   - `triggerVector`: String (e.g., `DRM_TAMPER`, `HOOK_BYPASS`, `LEAK_DETECTED`)
   - `details`: String
   - `timestamp`: Date

---

## 3. Dynamic Module Delivery & Root Vault Persistence

### A. Zero Modules Bundled in Release APK
- The release APK contains only **Hollow Core**, root shell management, registration flow, and the dynamic in-memory loader.
- No feature code is shipped in the public binary.

### B. In-App Dynamic Module Downloads
- Once approved in MongoDB Atlas by the admin, the app receives the authorized module manifest.
- Dashboard shows an animated notification banner: *"Modules Available: Tap to Download & Activate"*.
- Modules are downloaded over the encrypted anti-MITM channel, verified against Ed25519 signatures, encrypted with the device's hardware key, and saved in `/data/adb/.hollow_vault/modules/`.

### C. Immunity to "Clear App Data"
- `/data/adb/.hollow_vault` resides in root system storage outside `/data/data/com.example/`.
- Standard Android "Clear Storage / Clear Data" does NOT delete the root vault.
- When the app is re-opened, it re-authenticates with the root vault and instantly restores the modules with zero re-download needed.

---

## 4. User Registration, Approval Mode & Device Banning

### A. Client Registration Screen
- User registers with **Username**, **Email**, and **Password**.
- App sends registration + hardware fingerprint to `POST /api/v1/auth/register`.
- Screen immediately transitions to a pulsing **"Waiting for Admin Approval"** state.

### B. Admin Web Portal Features
- **Approve Device**: Sets expiration timestamp and checks off permitted modules in MongoDB.
- **Toggle Modules**: Dynamically add or remove modules from an existing user's license.
- **Ban Device**: Instantly updates status to `banned` in MongoDB. Next client heartbeat wipes the local root vault and permanently locks the device.

---

## 5. Codebase Restructuring Plan

```
hollow-project/
├── Dockerfile                             # Production multi-stage Node.js container
├── web/                                   # Admin Web Server & Licensing Backend
│   ├── package.json                       # express, mongoose, bcryptjs, jsonwebtoken
│   ├── server.js                          # Connects to MONGODB_URI & starts API
│   ├── models/                            # User, Device, Module, SecurityIncident
│   ├── routes/                            # /api/v1/auth, /api/v1/admin, /api/v1/modules
│   ├── public/                            # Cyber-emerald Web Admin Dashboard
│   └── certs/                             # Ed25519 signing keys
│
└── app/src/main/java/com/example/isolation/
    ├── core/                              # ROOT & SECURITY ENGINE
    │   ├── root/                          # Su process execution, root volume mount (/data/adb/.hollow_vault)
    │   ├── security/                      # Anti-MITM client, TLS pinning, ChaCha20 payload encryption
    │   ├── license/                       # Monotonic clock guard, hardware fingerprint, token store
    │   └── module/                        # HollowModule contract, HollowModuleRegistry, dynamic loader
    │
    ├── uikit/                             # REUSABLE UI SYSTEM
    │   ├── theme/                         # Centralized colors, shapes, typography (Jakarta)
    │   ├── components/                    # StatusBadge, CyberStatCard, CompactProInput, CompactSwitchRow
    │   └── dialogs/                       # Modals, confirmation prompts
    │
    └── module/                            # MODULAR FEATURE IMPLEMENTATIONS
        ├── hider/                         # App Hider & SystemUI disguise
        ├── isolation/                     # Storage isolation, FUSE virtual volumes, AES-256 vault
        ├── privacy/                       # Flagship hardware spoofer, GPS teleport, sensors, Deadman switch
        └── network/                       # V2Ray (VLESS/VMess), SOCKS5/HTTP proxies, per-app routing
```

---

## 6. Developer Mode (`BuildConfig.DEV_FULL_BUILD`)

- In `DEV_FULL_BUILD = true` mode, the app pre-loads all 4 modules locally and bypasses server approval so you can test all features offline without waiting for server responses.
- In `DEV_FULL_BUILD = false` mode, the app enforces the true zero-module release shell, registration flow, approval waiting mode, and dynamic root-vault module loading.

---

## 7. Phased Implementation Roadmap

### Phase 1: Dockerfile & Node.js/Express Web Server with MongoDB Atlas
- Create `Dockerfile` and `web/package.json` (`express`, `mongoose`, `bcryptjs`, `jsonwebtoken`).
- Create `web/server.js` with MongoDB Atlas connection using `process.env.MONGODB_URI`.
- Implement Mongoose models (`User`, `Device`, `Module`, `SecurityIncident`).
- Build REST APIs: `/api/v1/auth/register`, `/api/v1/auth/status`, `/api/v1/auth/heartbeat`, `/api/v1/admin/*`.

### Phase 2: Web Admin Dashboard UI
- Build dark-themed Cyber-Emerald Admin Portal (`web/public/index.html`, `admin.css`, `admin.js`).
- Admin login, pending device approvals queue, module checkboxes, and 1-click device banning.

### Phase 3: Android Codebase Restructuring
- Restructure packages into `core/`, `uikit/`, and `module/` (`hider/`, `isolation/`, `privacy/`, `network/`).
- Standardize all module implementations on the `HollowModule` contract.
- Implement `HollowModuleRegistry` for dynamic UI and hook dispatching.

### Phase 4: Root Vault Persistence & Anti-MITM Client
- Implement `RootLicenseVaultManager` managing `/data/adb/.hollow_vault` (survives app data clearing).
- Implement hardware key derivation (`SoC Serial + Android ID + Flash CID`).
- Implement `HollowSecureClient` with payload encryption and anti-interception defense.

### Phase 5: Registration Screen & Dynamic Module Downloader
- Build `OnboardingScreen` with Username, Email, Password inputs.
- Build "Waiting for Admin Approval" state with live status polling.
- Build "Modules Available" in-app download banner and dynamic loader.
- Verify compilation with `compile_applet` and verify unit tests.
