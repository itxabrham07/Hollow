# Hollow

**Hollow** is an LSPosed module and encrypted storage isolation engine for modern Android (Android 15+ / API 82). It transparently intercepts target application storage I/O and isolates files into private, hardware-backed AES-256 encrypted volumes without granting broad storage permissions.

---

## Architecture & Security Model

### 1. Private Internal Storage & IPC Pipeline
- **Zero External Storage Leaks:** All volume containers are stored strictly inside Hollow's private app directory (`context.filesDir/vaults/<volume>/`). Nothing is ever written to `/sdcard/`, external public directories, or target app sandboxes.
- **Pure IPC Pipe Stream:** Hooked target applications never receive raw filesystem paths. All file interactions (reads, writes, stat queries, directory listings) are piped through `IsoVaultConfigProvider` via `ParcelFileDescriptor` pipes. Encryption and decryption are executed exclusively in the Hollow process.
- **Fail-Closed Access Control:** Every IPC call validates `Binder.getCallingUid()` and verifies the caller's package against active isolation rules. Unmapped callers or locked vaults immediately fail closed.
- **Path Traversal Protection:** Canonical path validation rejects `..`, relative escapes, and symlinks outside the target volume root.

### 2. Streaming AEAD Encryption (AES-256-GCM 4KB)
- **Hardware Keystore Master Key:** Derived and stored in Android KeyStore (`HollowVaultMasterKey`).
- **Per-Volume Keys:** Derived from the master key using HKDF-SHA256 (`Hollow:VolumeKey:v2:<volume>`).
- **Chunked AEAD:** Plaintext is processed in 4KB chunks, each with an authenticated nonce (Base IV + chunk index) and AAD, enabling $O(1)$ random-access seeking for video and audio playback.
- **Passphrase Backup:** Passphrase derivation uses PBKDF2WithHmacSHA256 with 600,000 iterations and random 16-byte salt.
- **Format Migration:** Automatically detects and migrates legacy ISOV `0x01` containers to Hollow `0x02` chunked AEAD format.

### 3. Permissions & Privacy Guardrails
- **Minimal Permissions:** Stripped of `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, and `requestLegacyExternalStorage`. Only uses:
  - `QUERY_ALL_PACKAGES`: To inspect installed apps and enforce per-app rules.
  - `USE_BIOMETRIC`: For biometric vault authentication.
  - `POST_NOTIFICATIONS`: For security alerts.
- **Backup Exclusion:** `android:allowBackup="false"`, with explicit exclusion of all vault data in `backup_rules.xml` and `data_extraction_rules.xml`.
- **Display Security:** `FLAG_SECURE` prevents screenshots and recent apps switcher leaks.
- **Auto-Lock:** Locks vault on background lifecycle events and wipes decrypted in-memory caches.

---

## Application Structure (4 Tabs)

1. **Apps:**
   - Manage isolation status and strategy per app (`Private Locker`, `Separate Folder`, `Blank Gallery`, `View Only`).
   - Per-app hook status indicators.
   - Whitelist specific folders and block root directory clutter.
   - Toggle **Shared Space** access per app.
2. **Files:**
   - Volume switcher (All Volumes, Shared Space, Per-app volumes).
   - Explorer View & Gallery View (Albums, Timeline, Favorites) backed by real files with zero dummy data.
   - Multi-select actions: Move, Copy, Rename, Delete to Recycle Bin, Export.
   - In-app viewers for photos, video player with seeking, audio player, PDF viewer, and text editor.
   - 30-Day auto-purge Recycle Bin.
3. **Activity:**
   - Live audit history of file access attempts and redirections.
   - Severity filtering and audit log clearing.
4. **Settings:**
   - 4-digit PIN setup and emergency recovery key generation.
   - LSPosed hook status inspection.
   - Kernel Mount Diagnostics (clearly labeled as experimental and inactive in userspace mode).

---

## Known Limits

- **Native Code Limitations:** ART/Java hooks intercept framework APIs (`java.io.File`, `MediaStore`, `ContentResolver`, `libcore.io.IoBridge`, `android.system.Os.open`). Applications making raw inline native syscalls directly into the Linux kernel without framework bridges cannot be redirected via Java hooks alone.
- **Framework Detection:** Target apps implementing aggressive anti-tamper or integrity checks may detect the presence of the LSPosed framework or Zygote hooks.
- **Kernel Mounts:** Direct kernel mount namespace injection (`mount --bind`) requires full root and is marked experimental/inactive in default userspace LSPosed operation.
