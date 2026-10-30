package com.example.isolation.core.di

import android.content.Context
import com.example.isolation.core.data.repository.AuditLogRepository
import com.example.isolation.core.data.repository.DefaultAuditLogRepository
import com.example.isolation.core.data.repository.DefaultDeviceSpoofRepository
import com.example.isolation.core.data.repository.DefaultIsolationRepository
import com.example.isolation.core.data.repository.DefaultMountRepository
import com.example.isolation.core.data.repository.DefaultSettingsRepository
import com.example.isolation.core.data.repository.DefaultVaultRepository
import com.example.isolation.core.data.repository.DefaultVpnProxyRepository
import com.example.isolation.core.data.repository.DeviceSpoofRepository
import com.example.isolation.core.data.repository.IsolationRepository
import com.example.isolation.core.data.repository.MountRepository
import com.example.isolation.core.data.repository.SettingsRepository
import com.example.isolation.core.data.repository.VaultRepository
import com.example.isolation.core.data.repository.VpnProxyRepository
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.pkg.AppPackageRepository

class DefaultAppContainer(
    private val context: Context
) : AppContainer {

    override val database: IsoVaultDatabase by lazy {
        IsoVaultDatabase.getDatabase(context)
    }

    override val isolationDao: IsolationDao by lazy {
        database.isolationDao()
    }

    override val packageRepository: AppPackageRepository by lazy {
        AppPackageRepository(context)
    }

    override val isolationRepository: IsolationRepository by lazy {
        DefaultIsolationRepository(isolationDao)
    }

    override val vaultRepository: VaultRepository by lazy {
        DefaultVaultRepository(context, isolationDao)
    }

    override val auditLogRepository: AuditLogRepository by lazy {
        DefaultAuditLogRepository(isolationDao)
    }

    override val deviceSpoofRepository: DeviceSpoofRepository by lazy {
        DefaultDeviceSpoofRepository(context)
    }

    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(context)
    }

    override val mountRepository: MountRepository by lazy {
        DefaultMountRepository(context)
    }

    override val vpnProxyRepository: VpnProxyRepository by lazy {
        DefaultVpnProxyRepository(settingsRepository)
    }
}
