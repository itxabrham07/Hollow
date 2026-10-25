package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.isolation.crypto.AesVaultCipher
import com.example.isolation.ui.IsoVaultMainShell
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    companion object {
        @JvmStatic
        fun isLsposedModuleActiveReal(): Boolean {
            return false // Hooked by StorageIsolationHook when Vector / LSPosed is active
        }
    }

    private var pendingSharedUris by mutableStateOf<List<Uri>>(emptyList())

    private fun isStreamingEmulatorEnvironment(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val prod = Build.PRODUCT.lowercase()
        val dev = Build.DEVICE.lowercase()
        val board = Build.BOARD.lowercase()
        val brand = Build.BRAND.lowercase()
        return fp.startsWith("generic") ||
            fp.contains("emulator") ||
            fp.contains("sdk") ||
            fp.contains("cuttlefish") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            model.contains("cuttlefish") ||
            model.contains("droid4x") ||
            hw.contains("ranchu") ||
            hw.contains("goldfish") ||
            hw.contains("cuttlefish") ||
            hw.contains("vsoc") ||
            prod.contains("sdk") ||
            prod.contains("emulator") ||
            prod.contains("cuttlefish") ||
            prod.contains("aosp") ||
            dev.contains("generic") ||
            dev.contains("emulator") ||
            dev.contains("cuttlefish") ||
            dev.contains("vsoc") ||
            board.contains("goldfish") ||
            board.contains("ranchu") ||
            board.contains("cuttlefish") ||
            brand.startsWith("generic")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize hardware-backed Android Keystore Master Key
        AesVaultCipher.initializeMasterKey(applicationContext)
        // Ensure FLAG_SECURE is cleared initially so streaming emulators never render a black surface
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        extractSharedUrisFromIntent(intent)

        setContent {
            val viewModel: IsoVaultViewModel = viewModel()
            val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()

            LaunchedEffect(securitySettings.blockScreenshots) {
                if (securitySettings.blockScreenshots && !isStreamingEmulatorEnvironment()) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Handle "Share to Vault" incoming URIs from external apps
            LaunchedEffect(pendingSharedUris) {
                if (pendingSharedUris.isNotEmpty()) {
                    val urisToImport = pendingSharedUris
                    pendingSharedUris = emptyList()
                    val sharedPicturesDir = File(viewModel.getSharedSpaceDir(), "Pictures").apply {
                        if (!exists()) mkdirs()
                    }
                    viewModel.importUrisToVault(
                        uris = urisToImport,
                        destinationDir = sharedPicturesDir,
                        packageName = "shared.vault",
                        appName = "Shared Space"
                    ) { count ->
                        Toast.makeText(
                            this@MainActivity,
                            "Encrypted & saved $count shared file(s) into Shared Space",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            IsoVaultMainShell(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedUrisFromIntent(intent)
    }

    private fun extractSharedUrisFromIntent(intent: Intent?) {
        if (intent == null) return
        val extracted = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (single != null) extracted.add(single)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!list.isNullOrEmpty()) extracted.addAll(list)
            }
        }
        if (extracted.isNotEmpty()) {
            pendingSharedUris = extracted
        }
    }
}
