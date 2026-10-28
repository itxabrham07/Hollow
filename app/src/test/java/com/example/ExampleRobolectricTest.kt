package com.example

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import com.example.isolation.core.license.DeviceHardwareFingerprint
import com.example.isolation.core.license.MonotonicClockGuard
import com.example.isolation.core.module.HollowDexModuleLoader
import com.example.isolation.core.module.HollowModuleRegistry
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.core.root.RootVaultLicenseRecord
import com.example.isolation.core.security.HollowSecureClient
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.AppSettings
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.module.ModuleCatalog
import com.example.isolation.ui.screens.DashboardScreen
import com.example.isolation.ui.viewmodel.IsoVaultViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hollow", appName)
    }

    @Test
    fun `core release catalog has zero compiled feature modules`() {
        assertTrue(
            "Release ModuleCatalog.registeredModules must be empty so zero feature-module code ships in core APK",
            ModuleCatalog.registeredModules.isEmpty()
        )
    }

    @Test
    fun `stealth table serializes and parses independent hide and disguise rules`() {
        val configs = listOf(
            AppIsolationConfig(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                isIsolated = false,
                isHiddenOrDisguised = true,
                launcherVisibilityMode = LauncherVisibilityMode.DISGUISED,
                customLauncherLabel = "Daily Notes",
                disguiseIconPreset = DisguiseIconPreset.NOTES,
                hideInSystemUiRecents = true
            ),
            AppIsolationConfig(
                packageName = "com.snapchat.android",
                appName = "Snapchat",
                isIsolated = true,
                isHiddenOrDisguised = true,
                launcherVisibilityMode = LauncherVisibilityMode.HIDDEN
            )
        )
        val settings = AppSettings(
            hollowVisibilityMode = LauncherVisibilityMode.DISGUISED,
            hollowCustomName = "Calc Pro",
            hollowIconPreset = DisguiseIconPreset.CALCULATOR,
            systemUiTriggerEnabled = true
        )

        IsolationRuleManager.publishStealthTable(configs, settings)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = IsolationRuleManager.getReadableSharedPrefs(context)
            .getString(IsolationRuleManager.KEY_STEALTH_TABLE, "")
        val parsed = IsolationRuleManager.parseStealthTable(raw)

        val whatsappRule = parsed["com.whatsapp"]!!
        assertEquals(LauncherVisibilityMode.DISGUISED, whatsappRule.visibilityMode)
        assertEquals("Daily Notes", whatsappRule.customLabel)
        assertEquals(DisguiseIconPreset.NOTES, whatsappRule.iconPreset)
        assertTrue(whatsappRule.hideInRecents)

        val snapchatRule = parsed["com.snapchat.android"]!!
        assertEquals(LauncherVisibilityMode.HIDDEN, snapchatRule.visibilityMode)

        val hollowRule = parsed[IsolationRuleManager.MODULE_PKG]!!
        assertEquals(LauncherVisibilityMode.DISGUISED, hollowRule.visibilityMode)
        assertEquals("Calc Pro", hollowRule.customLabel)
        assertFalse(configs[0].isIsolated)
    }

    @Test
    fun `monotonic clock guard detects system date rollback`() {
        val futureRecordedWallMs = System.currentTimeMillis() + (2 * 86_400_000L)
        val result = MonotonicClockGuard.verifyLicenseWindow(
            issuedServerTimeMs = System.currentTimeMillis() - 86_400_000L,
            expiresServerTimeMs = futureRecordedWallMs + 86_400_000L,
            lastRecordedWallClockMs = futureRecordedWallMs,
            lastRecordedElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
        assertFalse(result.isValid)
        assertTrue(result.isClockRolledBack)
    }

    @Test
    fun `dex module loader verifies sha256 and ed25519 signature mounts 5th module and rejects tampered dex`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HollowDexModuleLoader.clearCacheAndIncidentsForTest()
        val hw = DeviceHardwareFingerprint.collect(context)
        assertTrue(hw.fingerprintHex.startsWith("HLW-"))

        RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, "module_hider")
        RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, "module_isolation")
        RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, "module_sensor_firewall")

        val now = System.currentTimeMillis()
        val record = RootVaultLicenseRecord(
            username = "test_operator",
            email = "operator@hollow.sec",
            status = LicenseAccountStatus.APPROVED,
            licenseToken = "hlw_tok_test123",
            allowedModules = setOf("module_hider", "module_privacy"),
            downloadedModules = emptySet(),
            issuedAtMs = now,
            validUntilMs = now + 86_400_000L,
            hardwareFingerprint = hw.fingerprintHex,
            highWaterWallClockMs = now,
            lastElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
        RootLicenseVaultManager.writeVaultRecord(context, record)

        // 1. Release Shell mode (0 bundled modules until downloaded)
        val initialStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = false)
        assertFalse(HollowModuleRegistry.isModuleActive("module_hider", initialStates, devFullBuild = false))

        // 2. Download real compiled & Ed25519-signed module_hider DEX bundle into Root Vault
        val hiderSignedBundle = HollowSecureClient.buildSignedSimulatedModuleBundle(context, "module_hider")
        assertTrue(hiderSignedBundle.isNotBlank())
        assertTrue(
            RootLicenseVaultManager.saveDownloadedModuleToVault(
                context,
                "module_hider",
                hiderSignedBundle
            )
        )
        val afterDownloadStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = false)
        assertTrue(HollowModuleRegistry.isModuleActive("module_hider", afterDownloadStates, devFullBuild = false))
        val loadedHider = HollowModuleRegistry.findModuleById("module_hider", afterDownloadStates)
        assertNotNull(loadedHider)
        assertEquals("com.example.isolation.module.hider.AppHiderModule", loadedHider!!.javaClass.name)
        assertFalse(HollowModuleRegistry.isModuleActive("module_isolation", afterDownloadStates, devFullBuild = false))

        // 3. Developer Full Build mode -> all compiled DEX modules active
        val devStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = true)
        assertTrue(HollowModuleRegistry.isModuleActive("module_isolation", devStates, devFullBuild = true))
        assertTrue(HollowModuleRegistry.isModuleActive("module_network", devStates, devFullBuild = true))

        // 4. End-to-End 5th Module (module_sensor_firewall) compiled into standalone DEX & signed with Ed25519
        val updatedWithFifth = record.copy(
            allowedModules = setOf("module_hider", "module_privacy", "module_sensor_firewall", "module_isolation")
        )
        RootLicenseVaultManager.writeVaultRecord(context, updatedWithFifth)
        val fifthBundle = HollowSecureClient.buildSignedSimulatedModuleBundle(
            context,
            "module_sensor_firewall"
        )
        assertTrue(fifthBundle.isNotBlank())
        assertTrue(
            RootLicenseVaultManager.saveDownloadedModuleToVault(
                context,
                "module_sensor_firewall",
                fifthBundle
            )
        )

        val statesWithFifth = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = false)
        assertTrue(HollowModuleRegistry.isModuleActive("module_sensor_firewall", statesWithFifth, devFullBuild = false))
        val fifthMod = HollowModuleRegistry.findModuleById("module_sensor_firewall", statesWithFifth)!!
        assertEquals("Sensor Firewall", fifthMod.cardTitle)
        assertEquals(
            "com.example.isolation.module.sensorfirewall.SensorFirewallPayload",
            fifthMod.javaClass.name
        )

        // Confirm parent classloader delegation resolves :app classes (IsoVaultViewModel, HollowModule)
        assertSame(
            "Dynamically loaded module must resolve :app's IsoVaultViewModel class via parent ClassLoader without NoClassDefFoundError",
            IsoVaultViewModel::class.java,
            fifthMod.javaClass.classLoader!!.loadClass("com.example.isolation.ui.viewmodel.IsoVaultViewModel")
        )

        // 5. Security Verification: Tampered signature / checksum on module_isolation MUST be refused,
        //    deleted from the Root Vault, and logged as a SecurityIncident!
        val validIsolationBundle = JSONObject(
            HollowSecureClient.buildSignedSimulatedModuleBundle(context, "module_isolation")
        )
        validIsolationBundle.put(
            "signatureB64",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        )
        RootLicenseVaultManager.saveDownloadedModuleToVault(
            context,
            "module_isolation",
            validIsolationBundle.toString()
        )
        assertTrue(RootLicenseVaultManager.listDownloadedModulesInVault(context).contains("module_isolation"))

        val afterTamperStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = false)
        assertFalse(
            "Tampered DEX module must be refused by HollowDexModuleLoader",
            HollowModuleRegistry.isModuleActive("module_isolation", afterTamperStates, devFullBuild = false)
        )
        assertFalse(
            "Tampered DEX module payload must be deleted from Root Vault",
            RootLicenseVaultManager.listDownloadedModulesInVault(context).contains("module_isolation")
        )
        assertTrue(
            "SecurityIncident must be recorded on Ed25519 signature mismatch",
            HollowDexModuleLoader.recordedSecurityIncidents.any {
                it.moduleId == "module_isolation" && it.triggerVector == "ED25519_SIGNATURE_INVALID"
            }
        )
    }

    @Test
    fun `requiredMinCoreVersion gate gracefully skips module requiring newer core without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HollowDexModuleLoader.clearCacheAndIncidentsForTest()

        val validSensorBundle = JSONObject(
            HollowSecureClient.buildSignedSimulatedModuleBundle(context, "module_sensor_firewall")
        )
        // Simulate a future module release that requires Core Engine v99 (while current core is v1)
        validSensorBundle.put("minCoreVersion", 99)

        val loaded = HollowDexModuleLoader.verifyAndLoadFromBundleJson(
            context = context,
            moduleId = "module_sensor_firewall",
            rawBundleJson = validSensorBundle.toString()
        )
        assertNull("Module requiring minCoreVersion=99 must be skipped gracefully", loaded)
        assertTrue(
            "IncompatibleVersion record must be logged when minCoreVersion > CORE_ENGINE_VERSION",
            HollowDexModuleLoader.recordedIncompatibleVersions.any {
                it.moduleId == "module_sensor_firewall" &&
                    it.requiredMinCoreVersion == 99 &&
                    it.currentCoreVersion == HollowDexModuleLoader.CORE_ENGINE_VERSION
            }
        )
        assertTrue(
            "Incompatible version is NOT a tamper incident and must not trigger security incident",
            HollowDexModuleLoader.recordedSecurityIncidents.isEmpty()
        )
    }

    @Test
    fun `downloaded module_sensor_firewall automatically renders card on Dashboard and reads IsoVaultViewModel state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        HollowDexModuleLoader.clearCacheAndIncidentsForTest()

        val hw = DeviceHardwareFingerprint.collect(app)
        val now = System.currentTimeMillis()
        val record = RootVaultLicenseRecord(
            username = "dashboard_verifier",
            email = "verifier@hollow.sec",
            status = LicenseAccountStatus.APPROVED,
            licenseToken = "hlw_tok_dash999",
            allowedModules = setOf("module_sensor_firewall"),
            downloadedModules = emptySet(),
            issuedAtMs = now,
            validUntilMs = now + 86_400_000L,
            hardwareFingerprint = hw.fingerprintHex,
            highWaterWallClockMs = now,
            lastElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
        RootLicenseVaultManager.writeVaultRecord(app, record)

        val fifthBundle = HollowSecureClient.buildSignedSimulatedModuleBundle(app, "module_sensor_firewall")
        assertTrue(RootLicenseVaultManager.saveDownloadedModuleToVault(app, "module_sensor_firewall", fifthBundle))

        val viewModel = IsoVaultViewModel(app)
        viewModel.setDevFullBuildMode(false)
        viewModel.refreshVaultAndModuleState()

        var openedModuleId by mutableStateOf<String?>(null)
        val loadedSensorModule = HollowModuleRegistry.findModuleById(
            "module_sensor_firewall",
            viewModel.moduleStates.value
        )
        assertNotNull("module_sensor_firewall must be mounted in viewModel.moduleStates", loadedSensorModule)

        composeTestRule.setContent {
            MaterialTheme {
                if (openedModuleId == "module_sensor_firewall") {
                    loadedSensorModule!!.RenderPrimaryScreen(
                        viewModel = viewModel,
                        onBack = { openedModuleId = null },
                        onDetailOpenChanged = {}
                    )
                } else {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToApps = {},
                        onNavigateToVault = {},
                        onNavigateToTerminal = {},
                        onNavigateToAudit = {},
                        onOpenModule = { modId -> openedModuleId = modId }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Scroll Dashboard LazyColumn to the dynamically generated module_sensor_firewall card
        composeTestRule
            .onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("module_card_module_sensor_firewall"))
        composeTestRule.waitForIdle()

        val cardNode = composeTestRule.onNodeWithTag("module_card_module_sensor_firewall")
        cardNode.assertIsDisplayed()
        val dashboardCardDump = cardNode.printToString()
        println("=== DASHBOARD DYNAMIC CARD SEMANTIC DUMP ===\n$dashboardCardDump")
        assertTrue(dashboardCardDump.contains("Sensor Firewall"))
        assertTrue(dashboardCardDump.contains("3 hardware hooks"))

        // Click the dynamic card on Dashboard -> opens module_sensor_firewall primary screen
        cardNode.performClick()
        composeTestRule.waitForIdle()
        assertEquals("module_sensor_firewall", openedModuleId)

        // Verify SensorFirewallPayload.RenderPrimaryScreen reads live state from IsoVaultViewModel
        val vmStateNode = composeTestRule.onNodeWithTag("sensor_firewall_vm_state")
        vmStateNode.assertIsDisplayed()
        vmStateNode.assertTextContains("HW=${hw.fingerprintHex}", substring = true)
        vmStateNode.assertTextContains("Mode=RELEASE", substring = true)
        val screenDump = composeTestRule.onNodeWithTag("screen_module_sensor_firewall").printToString()
        println("=== SENSOR FIREWALL PRIMARY SCREEN DUMP ===\n$screenDump")

        // Flip a hardware sensor toggle -> verify it calls viewModel.appendTerminalLog(...)
        composeTestRule.onNodeWithTag("sensor_toggle_mic_null_pcm_stream").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            "Toggling sensor firewall control must write to IsoVaultViewModel.terminalLogs",
            viewModel.terminalLogs.value.any {
                it.contains("[SensorFirewall] mic_null_pcm_stream set to false")
            }
        )
    }
}
