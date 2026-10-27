package com.example

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.example.isolation.core.license.DeviceHardwareFingerprint
import com.example.isolation.core.license.MonotonicClockGuard
import com.example.isolation.core.module.HollowModuleRegistry
import com.example.isolation.core.root.LicenseAccountStatus
import com.example.isolation.core.root.RootLicenseVaultManager
import com.example.isolation.core.root.RootVaultLicenseRecord
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.AppSettings
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.LauncherVisibilityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Hollow", appName)
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
    fun `root license vault encrypts hardware bound license and dynamic module engine mounts authorized modules`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hw = DeviceHardwareFingerprint.collect(context)
        assertTrue(hw.fingerprintHex.startsWith("HLW-"))

        RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, "module_hider")
        RootLicenseVaultManager.deleteDownloadedModuleFromVault(context, "module_isolation")

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

        // Release Shell mode (0 bundled modules until downloaded)
        val initialStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = false)
        assertFalse(HollowModuleRegistry.isModuleActive("module_hider", initialStates, devFullBuild = false))

        // Download module_hider into Root Vault
        assertTrue(
            RootLicenseVaultManager.saveDownloadedModuleToVault(
                context,
                "module_hider",
                "{\"moduleId\":\"module_hider\"}"
            )
        )
        val afterDownloadStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = false)
        assertTrue(HollowModuleRegistry.isModuleActive("module_hider", afterDownloadStates, devFullBuild = false))
        assertFalse(HollowModuleRegistry.isModuleActive("module_isolation", afterDownloadStates, devFullBuild = false))

        // Developer Full Build mode -> all 4 modules active
        val devStates = HollowModuleRegistry.resolveModuleStates(context, devFullBuild = true)
        assertTrue(HollowModuleRegistry.isModuleActive("module_isolation", devStates, devFullBuild = true))
        assertTrue(HollowModuleRegistry.isModuleActive("module_network", devStates, devFullBuild = true))
    }
}
