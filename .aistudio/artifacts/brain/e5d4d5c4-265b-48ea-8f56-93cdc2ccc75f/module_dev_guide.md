# Hollow Modular Architecture & Developer Guide

Hollow is engineered with a **zero-trust modular architecture**. This guide explains the purpose of the `/modules/` and `/modules-src/` directories at the project root and how developers can build, sign, and integrate new dynamic feature modules.

---

## 1. Why Are There `/modules/` and `/modules-src/` Folders?

In the root directory of the workspace, you will find two primary module workspace directories:

### A. `/modules-src/` (Module Source Workspace)
* **Purpose**: This directory contains the raw Kotlin source code for each dynamic feature module (e.g., `modules-src/module_isolation/`, `modules-src/module_privacy/`, `modules-src/module_hider/`, `modules-src/module_network/`, `modules-src/module_sensor_firewall/`).
* **Why it's separate**: These modules are maintained independently from the core APK (`/app/src/main/`). During the build process, their Kotlin source files are compiled into standalone `.class` files, converted into DEX bytecode (`module.dex.bin`), and prepared for dynamic loading.

### B. `/modules/` (Compiled Module Artifacts & Vault Packages)
* **Purpose**: This directory contains the compiled, signed, and packaged runtime artifacts for each module (e.g., `modules/module_isolation/module.dex.bin`, `modules/module_isolation/manifest.json`).
* **Why it's separate**: When the core app runs in root mode or vault sync mode, it packages these pre-compiled DEX modules into encrypted `.dex.enc` vault files or copies them to `/data/adb/.hollow_vault/modules/` so they can be securely verified (via SHA-256 and Ed25519 signatures) and loaded at runtime via `DexClassLoader`.

---

## 2. Module Developer Guide: Adding a New Feature Module

To add a new dynamic feature module to Hollow (e.g., `modules-src/module_firewall`), follow these steps:

### Step 1: Create Source in `/modules-src/module_firewall/`
Implement your module class implementing the `HollowModule` interface:

```kotlin
package com.example.isolation.module.firewall

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import com.example.isolation.core.module.HollowModule
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

class FirewallModule : HollowModule {
    override val id: String = "module_firewall"
    override val displayName: String = "Advanced Network Firewall"
    override val summary: String = "Inspects and blocks unauthorized socket connections per application."
    override val version: Int = 1
    override val requiredMinCoreVersion: Int = 1
    override val icon = Icons.Default.Security
    override val primaryRoute: String = "FIREWALL_SCREEN"
    override val contributesBottomNavTab: Boolean = false
    override val sizeKb: Int = 384

    @Composable
    override fun RenderPrimaryScreen(
        viewModel: IsoVaultViewModel,
        onBack: () -> Unit,
        onDetailOpenChanged: (Boolean) -> Unit
    ) {
        // Build your Jetpack Compose screen here
    }
}
```

### Step 2: Compile & Package as DEX (`/modules/module_firewall/`)
1. Compile your module source code into standard Java bytecode.
2. Convert the compiled `.class` files into a DEX binary (`module.dex.bin`) using the Android SDK `d8` tool and place it in `/modules/module_firewall/`.

### Step 3: Compute SHA-256 and Sign with Ed25519
1. Run the signing script (`scripts/sign-modules.js`) to compute the SHA-256 checksum and sign `module.dex.bin` with the Ed25519 private key.
2. Generate the corresponding `manifest.json` containing `sha256Checksum`, `signatureB64`, and `minCoreVersion`.

### Step 4: Deploy or Test
* **For Release Deployment**: Upload the packaged module from `/modules/module_firewall/` to the root vault at `/data/adb/.hollow_vault/modules/module_firewall.dex.enc`.
* **For Local Debugging**: Place the module JSON descriptor in `app/src/debug/assets/debug_modules/`. `HollowDexModuleLoader` will automatically verify and mount it on startup.
