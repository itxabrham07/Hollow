package com.example.isolation.core.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.IsoVaultApp
import com.example.isolation.feature.dashboard.DashboardViewModel
import com.example.isolation.feature.hider.AppHiderViewModel
import com.example.isolation.feature.isolation.AppIsolationViewModel
import com.example.isolation.feature.settings.SettingsViewModel
import com.example.isolation.feature.spoofer.DeviceSpooferViewModel
import com.example.isolation.feature.vault.VaultViewModel
import com.example.isolation.ui.viewmodel.IsoVaultViewModel

object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as IsoVaultApp
            val container = application.appContainer

            return when {
                modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                    DashboardViewModel(
                        application = application,
                        isolationRepository = container.isolationRepository,
                        vaultRepository = container.vaultRepository,
                        auditLogRepository = container.auditLogRepository,
                        settingsRepository = container.settingsRepository
                    ) as T
                }
                modelClass.isAssignableFrom(AppIsolationViewModel::class.java) -> {
                    AppIsolationViewModel(
                        application = application,
                        isolationRepository = container.isolationRepository,
                        packageRepository = container.packageRepository
                    ) as T
                }
                modelClass.isAssignableFrom(AppHiderViewModel::class.java) -> {
                    AppHiderViewModel(
                        application = application,
                        isolationRepository = container.isolationRepository,
                        settingsRepository = container.settingsRepository,
                        packageRepository = container.packageRepository
                    ) as T
                }
                modelClass.isAssignableFrom(DeviceSpooferViewModel::class.java) -> {
                    DeviceSpooferViewModel(
                        application = application,
                        isolationRepository = container.isolationRepository,
                        deviceSpoofRepository = container.deviceSpoofRepository,
                        settingsRepository = container.settingsRepository,
                        packageRepository = container.packageRepository
                    ) as T
                }
                modelClass.isAssignableFrom(VaultViewModel::class.java) -> {
                    VaultViewModel(
                        application = application,
                        vaultRepository = container.vaultRepository
                    ) as T
                }
                modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                    SettingsViewModel(
                        application = application,
                        settingsRepository = container.settingsRepository,
                        mountRepository = container.mountRepository,
                        vpnProxyRepository = container.vpnProxyRepository,
                        auditLogRepository = container.auditLogRepository
                    ) as T
                }
                modelClass.isAssignableFrom(IsoVaultViewModel::class.java) -> {
                    IsoVaultViewModel(application) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
