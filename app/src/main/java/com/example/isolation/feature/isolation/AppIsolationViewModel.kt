package com.example.isolation.feature.isolation

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.IsoVaultApp
import com.example.isolation.core.data.repository.IsolationRepository
import com.example.isolation.core.result.onError
import com.example.isolation.core.result.onSuccess
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.model.IsolationMode
import com.example.isolation.pkg.AppPackageRepository
import com.example.isolation.pkg.InstalledAppInfo
import com.example.isolation.ui.viewmodel.AppListFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Focused ViewModel for Storage Isolation screen.
 * Handles per-app workspace isolation, whitelist paths, root pollution, and package filtering.
 */
class AppIsolationViewModel(
    application: Application,
    private val isolationRepository: IsolationRepository,
    private val packageRepository: AppPackageRepository
) : AndroidViewModel(application) {

    private val app = application as IsoVaultApp

    private val _rawInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(AppListFilter.ISOLATED_ONLY)
    val filter: StateFlow<AppListFilter> = _filter.asStateFlow()

    private val _selectedAppForDetail = MutableStateFlow<InstalledAppInfo?>(null)
    val selectedAppForDetail: StateFlow<InstalledAppInfo?> = _selectedAppForDetail.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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
                isolationMode = cfg?.isolationMode ?: IsolationMode.SHARED_WORKSPACE,
                isHiddenOrDisguised = cfg?.isHiddenOrDisguised ?: false,
                launcherVisibilityMode = cfg?.launcherVisibilityMode ?: com.example.isolation.model.LauncherVisibilityMode.VISIBLE,
                customLauncherLabel = cfg?.customLauncherLabel ?: "",
                disguiseIconPreset = cfg?.disguiseIconPreset ?: com.example.isolation.model.DisguiseIconPreset.CALCULATOR,
                hideInSystemUiRecents = cfg?.hideInSystemUiRecents ?: true,
                hideFromPlayStore = cfg?.hideFromPlayStore ?: false,
                spoofInSettings = cfg?.spoofInSettings ?: true,
                customIconBase64 = cfg?.customIconBase64 ?: "",
                customIconDonorPkg = cfg?.customIconDonorPkg ?: "",
                isIdentitySpoofed = cfg?.isIdentitySpoofed ?: false,
                deviceProfilePreset = cfg?.deviceProfilePreset ?: com.example.isolation.model.DeviceProfilePreset.REAL_HARDWARE,
                spoofBrand = cfg?.spoofBrand ?: "",
                spoofModel = cfg?.spoofModel ?: "",
                spoofDevice = cfg?.spoofDevice ?: "",
                spoofAndroidId = cfg?.spoofAndroidId ?: "",
                spoofImei = cfg?.spoofImei ?: "",
                spoofAdvertisingId = cfg?.spoofAdvertisingId ?: "",
                spoofMacAddress = cfg?.spoofMacAddress ?: "",
                spoofCarrierName = cfg?.spoofCarrierName ?: "",
                spoofSimOperator = cfg?.spoofSimOperator ?: "",
                spoofSerial = cfg?.spoofSerial ?: ""
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredApps: StateFlow<List<InstalledAppInfo>> = combine(
        allInstalledApps,
        _searchQuery,
        _filter
    ) { apps, query, flt ->
        apps.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.appName.contains(query, ignoreCase = true) ||
                    item.packageName.contains(query, ignoreCase = true)

            val matchesFilter = when (flt) {
                AppListFilter.ALL -> true
                AppListFilter.ISOLATED_ONLY -> item.isIsolated
                AppListFilter.USER_APPS -> !item.isSystemApp
                AppListFilter.SYSTEM_APPS -> item.isSystemApp
            }

            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = packageRepository.getInstalledApps(configs.value, includeSystemApps = true)
            _rawInstalledApps.value = apps
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(newFilter: AppListFilter) {
        _filter.value = newFilter
    }

    fun selectAppForDetail(appInfo: InstalledAppInfo?) {
        _selectedAppForDetail.value = appInfo
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postStatusMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun toggleAppIsolation(appInfo: InstalledAppInfo, enable: Boolean) {
        viewModelScope.launch {
            isolationRepository.toggleAppIsolation(appInfo, enable)
                .onSuccess {
                    _statusMessage.value = if (enable) {
                        "Isolated storage for ${appInfo.appName}."
                    } else {
                        "Restored shared storage for ${appInfo.appName}."
                    }
                }
                .onError { _, message ->
                    _statusMessage.value = "Isolation update failed: $message"
                }
        }
    }

    fun updateAppIsolationMode(packageName: String, newMode: IsolationMode) {
        viewModelScope.launch {
            isolationRepository.updateAppIsolationMode(packageName, newMode)
                .onSuccess {
                    _statusMessage.value = "Updated isolation mode to ${newMode.title}."
                }
                .onError { _, message ->
                    _statusMessage.value = "Failed to update mode: $message"
                }
        }
    }

    fun updateRootPollution(packageName: String, block: Boolean) {
        viewModelScope.launch {
            isolationRepository.updateRootPollution(packageName, block)
                .onSuccess {
                    _statusMessage.value = if (block) "Root pollution blocked." else "Root pollution allowed."
                }
                .onError { _, message ->
                    _statusMessage.value = "Update failed: $message"
                }
        }
    }

    fun updateWhitelistedFolders(packageName: String, folders: String) {
        viewModelScope.launch {
            isolationRepository.updateWhitelistedFolders(packageName, folders)
                .onSuccess {
                    _statusMessage.value = "Whitelisted folders updated."
                }
                .onError { _, message ->
                    _statusMessage.value = "Whitelist update failed: $message"
                }
        }
    }

    fun launchAppFromHollow(appInfo: InstalledAppInfo) {
        val pm = app.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            _statusMessage.value = "Launched ${appInfo.appName}"
        } else {
            _statusMessage.value = "Could not launch ${appInfo.appName}"
        }
    }
}
