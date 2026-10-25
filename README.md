# Vault-LSPosed (IsoVault)

Per-app and shared encrypted storage volumes and MediaStore isolation module for Android (LSPosed API 82+).

## 1. Overview & Storage Volume Model

Vault-LSPosed isolates how target Android apps read and write files on external storage and `MediaStore`:

1. **Per-App Volume**:
   - Every protected app receives its own private encrypted volume (`sandboxes/<packageName>`).
   - Default Android folders (`DCIM`, `Pictures`, `Download`, `Movies`, `Music`, `Documents`) are created automatically inside each volume without asking the user for paths.
   - Each volume is encrypted with its own independent 256-bit Data Encryption Key (DEK), wrapped by the master key in Android Keystore, so an individual volume can be wiped or rotated without exposing any other volume.
2. **Shared Space Volume (`shared.vault`)**:
   - An optional encrypted volume that can be linked to any protected app via the **"Use shared space"** toggle (off by default).
   - When enabled on an app, that app sees its own private volume **plus** the Shared Space volume (mapped to `/storage/emulated/0/Shared` in `File` hooks and included in its `MediaStore` gallery queries).
   - Apps with **"Use shared space"** turned off never see files in Shared Space.
3. **Combined Vault File Manager & Gallery**:
   - Filter across **All Volumes**, **Shared Space**, or any single app volume.
   - Switch between **Files Explorer** (grid/list, search, category chips, multi-select export/move/copy/rename/delete/share, 30-day Recycle Bin, secondary folder PIN lock) and **Gallery View** (`Albums`, `Timeline`, `Favorites`).
   - Built-in viewers decrypt photos, PDFs, text, audio, and seekable video streams on the fly without writing plaintext to public storage.

## 2. Security Architecture

- **Hardware-Backed Key Hierarchy**:
  - On first run, a random 256-bit Master Key is generated and wrapped with an `AndroidKeyStore` AES-256-GCM key (`isovault_hardware_kek_v2`).
  - Each volume generates its own random 256-bit DEK wrapped by the Master Key.
  - Passphrase-protected backups derive keys using **600,000 iterations of PBKDF2-HMAC-SHA256** with a random 16-byte salt.
- **Streaming Chunked Authenticated Encryption (`ISOV\x02`)**:
  - Uses 4KB plaintext chunks (`AES256_GCM_HKDF_4KB` style) with HKDF-SHA256 per-file subkeys, unique 12-byte chunk nonces (`[noncePrefix(7)][chunkIndex(4)][isLastChunk(1)]`), and 128-bit GCM authentication tags.
  - Supports $O(1)$ random-access chunk decryption (`readDecryptedRange` / `EncryptedChunkMediaDataSource`) for instant video and audio seeking without loading large files into RAM.
  - Transparently migrates legacy `ISOV\x01` files to `ISOV\x02` on first access.
- **Locked-Down `IsoVaultConfigProvider` & Fail-Closed Hooks**:
  - `openFile()` is completely removed; the provider serves configuration metadata only.
  - Every `query()` and `call()` verifies the caller via `Binder.getCallingUid()` and `PackageManager.getPackagesForUid(callingUid)`. Hooked apps can only request their own rule and only if configured.
  - `record_audit` is caller-verified, input-sanitized, and rate-limited per UID.
  - No `MODE_WORLD_READABLE` or `xposedsharedprefs` is used. If the Vault provider is unreachable when a hooked app starts, the hook **fails closed** into an empty isolated directory and never exposes real phone storage.
- **Window & Backup Hardening**:
  - `android:allowBackup="false"` with full exclusions in `backup_rules.xml` and `data_extraction_rules.xml`.
  - `FLAG_SECURE` enabled on app windows by default; ephemeral playback/share cache is overwritten with zeroes and deleted on viewer close and auto-lock.

## 3. Setup Instructions

1. Install the APK on a device with **LSPosed** installed.
2. Open **LSPosed Manager** → **Modules** → Enable **IsoVault** and select the target apps in the module scope.
3. Reboot or force-stop the target apps, then open **IsoVault** and toggle protection on for each app in the **Apps** tab.

## 4. Known Limits

- User-space hooks (`File`, `Os`/`IoBridge`, `MediaStore`, `Activity` picker intents) isolate standard Java/Kotlin and libc file paths used by Android apps. Direct raw syscalls crafted via inline assembly bypass user-space hooks unless root kernel mount namespace isolation (`nsenter -m`) is active.
