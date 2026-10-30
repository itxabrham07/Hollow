package com.example.isolation.feature.hider

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.core.data.repository.IsolationRepository
import com.example.isolation.core.data.repository.SettingsRepository
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.AppSettings
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.HollowIconTheme
import com.example.isolation.model.IsolationRuleManager
import com.example.isolation.model.LauncherVisibilityMode
import com.example.isolation.mount.MountManager
import com.example.isolation.pkg.AppPackageRepository
import com.example.isolation.pkg.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Focused ViewModel for App Hider & Stealth Disguise features.
 * Manages package camouflage, system recents suppression, Settings decoy info, and Hollow's own stealth.
 */
class AppHiderViewModel(
    application: Application,
    private val isolationRepository: IsolationRepository,
    private val settingsRepository: SettingsRepository,
    private val packageRepository: AppPackageRepository
) : AndroidViewModel(application) {

    private val app = application as IsoVaultApp

    private val _rawInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val configs: StateFlow<List<AppIsolationConfig>> = isolationRepository.getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInstalledApps: StateFlow<List<InstalledAppInfo>> = combine(
        _rawInstalledApps,
        configs
    ) { apps, cfgList ->
        val cfgMap = cfgList.associateBy { it.packageName }
        apps.map { item ->
            val cfg = cfgMap[item.packageName]
            item.copy(
                isIsolated = cfg?.isIsolated ?: false,
                isolationMode = cfg?.isolationMode ?: com.example.isolation.model.IsolationMode.SHARED_WORKSPACE,
                isHiddenOrDisguised = cfg?.isHiddenOrDisguised ?: false,
                launcherVisibilityMode = cfg?.launcherVisibilityMode ?: LauncherVisibilityMode.VISIBLE,
                customLauncherLabel = cfg?.customLauncherLabel ?: "",
                disguiseIconPreset = cfg?.disguiseIconPreset ?: DisguiseIconPreset.CALCULATOR,
                hideInSystemUiRecents = cfg?.hideInSystemUiRecents ?: true,
                hideFromPlayStore = cfg?.hideFromPlayStore ?: false,
                spoofInSettings = cfg?.spoofInSettings ?: true,
                customIconBase64 = cfg?.customIconBase64 ?: "",
                customIconDonorPkg = cfg?.customIconDonorPkg ?: "",
                isIdentitySpoofed = cfg?.isIdentitySpoofed ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _rawInstalledApps.value = packageRepository.getInstalledApps(configs.value, includeSystemApps = true)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun decodeImageUriToBase64(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, 144, 144, true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) { onResult(b64) }
                    } else {
                        withContext(Dispatchers.Main) { onResult(null) }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("AppHiderVM", "Failed to decode image URI", t)
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun saveAppStealthConfig(
        appInfo: InstalledAppInfo,
        visibilityMode: LauncherVisibilityMode,
        customLabel: String,
        iconPreset: DisguiseIconPreset,
        hideInRecents: Boolean,
        hideFromPlayStore: Boolean = appInfo.hideFromPlayStore,
        spoofInSettings: Boolean = appInfo.spoofInSettings,
        customIconBase64: String = appInfo.customIconBase64,
        customIconDonorPkg: String = appInfo.customIconDonorPkg
    ) {
        viewModelScope.launch {
            val existing = isolationRepository.getConfigForPackage(appInfo.packageName)
            val isEnrolled = visibilityMode != LauncherVisibilityMode.VISIBLE || hideFromPlayStore
            val resolvedLabel = if (visibilityMode == LauncherVisibilityMode.DISGUISED && customLabel.isBlank()) {
                iconPreset.suggestedName.ifBlank { "Calculator" }
            } else {
                customLabel.trim()
            }

            val resolvedIconB64 = withContext(Dispatchers.IO) {
                when (iconPreset) {
                    DisguiseIconPreset.CUSTOM_IMAGE -> customIconBase64
                    DisguiseIconPreset.CLONED_APP -> {
                        if (customIconDonorPkg.isNotBlank()) {
                            try {
                                val d = app.packageManager.getApplicationIcon(customIconDonorPkg)
                                IsolationRuleManager.drawableToBase64Png(d, 144)
                            } catch (t: Throwable) {
                                android.util.Log.e("AppHiderVM", "Failed to get cloned app icon", t)
                                customIconBase64
                            }
                        } else customIconBase64
                    }
                    else -> ""
                }
            }

            val updated = existing?.copy(
                isHiddenOrDisguised = isEnrolled,
                launcherVisibilityMode = visibilityMode,
                customLauncherLabel = resolvedLabel,
                disguiseIconPreset = iconPreset,
                hideInSystemUiRecents = hideInRecents,
                hideFromPlayStore = hideFromPlayStore,
                spoofInSettings = spoofInSettings,
                customIconBase64 = resolvedIconB64,
                customIconDonorPkg = if (iconPreset == DisguiseIconPreset.CLONED_APP) customIconDonorPkg else ""
            ) ?: AppIsolationConfig(
                packageName = appInfo.packageName,
                appName = appInfo.appName,
                isIsolated = false,
                isHiddenOrDisguised = isEnrolled,
                launcherVisibilityMode = visibilityMode,
                customLauncherLabel = resolvedLabel,
                disguiseIconPreset = iconPreset,
                hideInSystemUiRecents = hideInRecents,
                hideFromPlayStore = hideFromPlayStore,
                spoofInSettings = spoofInSettings,
                customIconBase64 = resolvedIconB64,
                customIconDonorPkg = if (iconPreset == DisguiseIconPreset.CLONED_APP) customIconDonorPkg else ""
            )

            isolationRepository.saveStealthConfig(updated)
            withContext(Dispatchers.IO) {
                val allConfigs = isolationRepository.getAllConfigsSync()
                IsolationRuleManager.publishStealthTable(allConfigs, settings.value)
                MountManager.refreshLaunchersAndStealthCaches(
                    app,
                    refreshPlayStore = hideFromPlayStore,
                    refreshSettings = spoofInSettings
                )
            }
            loadInstalledApps()

            when (visibilityMode) {
                LauncherVisibilityMode.HIDDEN -> {
                    _statusMessage.value = "${appInfo.appName} is now hidden & launcher refreshed."
                }
                LauncherVisibilityMode.DISGUISED -> {
                    _statusMessage.value = "${appInfo.appName} disguised as \"$resolvedLabel\"."
                }
                LauncherVisibilityMode.VISIBLE -> {
                    _statusMessage.value = "${appInfo.appName} restored to normal visibility."
                }
            }
        }
    }

    fun removeAppFromStealth(appInfo: InstalledAppInfo) {
        saveAppStealthConfig(
            appInfo = appInfo,
            visibilityMode = LauncherVisibilityMode.VISIBLE,
            customLabel = "",
            iconPreset = DisguiseIconPreset.CALCULATOR,
            hideInRecents = false,
            hideFromPlayStore = false,
            spoofInSettings = false,
            customIconBase64 = "",
            customIconDonorPkg = ""
        )
    }

    fun updateHollowStealthConfig(
        visibilityMode: LauncherVisibilityMode,
        customName: String,
        iconPreset: DisguiseIconPreset,
        systemUiTriggerEnabled: Boolean,
        customIconBase64: String = settings.value.hollowCustomIconBase64,
        customIconDonorPkg: String = settings.value.hollowCustomIconDonorPkg,
        spoofInSettings: Boolean = settings.value.hollowSpoofInSettings
    ) {
        val cleanName = customName.trim().ifBlank { iconPreset.suggestedName.ifBlank { "Calculator" } }
        viewModelScope.launch(Dispatchers.IO) {
            val resolvedIconB64 = when (iconPreset) {
                DisguiseIconPreset.CUSTOM_IMAGE -> customIconBase64
                DisguiseIconPreset.CLONED_APP -> {
                    if (customIconDonorPkg.isNotBlank()) {
                        try {
                            val d = app.packageManager.getApplicationIcon(customIconDonorPkg)
                            IsolationRuleManager.drawableToBase64Png(d, 144)
                        } catch (t: Throwable) {
                            android.util.Log.e("AppHiderVM", "Failed to clone donor icon", t)
                            customIconBase64
                        }
                    } else customIconBase64
                }
                else -> ""
            }
            settingsRepository.updateSettings {
                it.copy(
                    hollowVisibilityMode = visibilityMode,
                    hollowCustomName = cleanName,
                    hollowIconPreset = iconPreset,
                    hollowCustomIconBase64 = resolvedIconB64,
                    hollowCustomIconDonorPkg = if (iconPreset == DisguiseIconPreset.CLONED_APP) customIconDonorPkg else "",
                    hollowSpoofInSettings = spoofInSettings,
                    systemUiTriggerEnabled = systemUiTriggerEnabled
                )
            }
            val allConfigs = isolationRepository.getAllConfigsSync()
            IsolationRuleManager.publishStealthTable(allConfigs, settings.value)
            applyNativeHollowAlias(visibilityMode, iconPreset, settings.value.hollowIconTheme)
            MountManager.refreshLaunchersAndStealthCaches(
                app,
                refreshPlayStore = false,
                refreshSettings = spoofInSettings
            )
        }
        _statusMessage.value = when (visibilityMode) {
            LauncherVisibilityMode.HIDDEN ->
                "Hollow hidden from launchers. Use the SystemUI Date header or Quick Calc tile to open Hollow."
            LauncherVisibilityMode.DISGUISED ->
                "Hollow disguised as \"$cleanName\"."
            LauncherVisibilityMode.VISIBLE ->
                "Hollow launcher visibility restored."
        }
    }

    private fun applyNativeHollowAlias(
        visibilityMode: LauncherVisibilityMode,
        iconPreset: DisguiseIconPreset,
        iconTheme: HollowIconTheme = settings.value.hollowIconTheme
    ) {
        try {
            val pm = app.packageManager
            val mainComp = ComponentName(app, "com.example.MainActivity")
            val cyberAlias = ComponentName(app, "com.example.MainActivityCyberAlias")
            val crimsonAlias = ComponentName(app, "com.example.MainActivityCrimsonAlias")
            val minimalAlias = ComponentName(app, "com.example.MainActivityMinimalAlias")
            val calcAlias = ComponentName(app, "com.example.MainActivityCalculatorAlias")
            val notesAlias = ComponentName(app, "com.example.MainActivityNotesAlias")

            val allThemeAliases = listOf(cyberAlias, crimsonAlias, minimalAlias)
            val allDisguiseAliases = listOf(calcAlias, notesAlias)

            when (visibilityMode) {
                LauncherVisibilityMode.VISIBLE -> {
                    allDisguiseAliases.forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                    val targetAliasClass = iconTheme.aliasClassName
                    if (targetAliasClass == null) {
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        allThemeAliases.forEach {
                            pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        }
                    } else {
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        allThemeAliases.forEach { alias ->
                            val state = if (alias.className == targetAliasClass) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            }
                            pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
                        }
                    }
                }
                LauncherVisibilityMode.DISGUISED -> {
                    allThemeAliases.forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                    val useCustomHookIcon = iconPreset == DisguiseIconPreset.CUSTOM_IMAGE ||
                            iconPreset == DisguiseIconPreset.CLONED_APP
                    if (useCustomHookIcon) {
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        allDisguiseAliases.forEach {
                            pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        }
                    } else {
                        pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        val activeDisguise = when (iconPreset) {
                            DisguiseIconPreset.NOTES -> notesAlias
                            else -> calcAlias
                        }
                        allDisguiseAliases.forEach { alias ->
                            val state = if (alias == activeDisguise) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            }
                            pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
                        }
                    }
                }
                LauncherVisibilityMode.HIDDEN -> {
                    pm.setComponentEnabledSetting(mainComp, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    allThemeAliases.forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                    allDisguiseAliases.forEach {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("AppHiderVM", "Failed to apply native Hollow alias", t)
        }
    }
}
