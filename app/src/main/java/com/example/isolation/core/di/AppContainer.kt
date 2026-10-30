package com.example.isolation.core.di

import com.example.isolation.core.data.repository.AuditLogRepository
import com.example.isolation.core.data.repository.DeviceSpoofRepository
import com.example.isolation.core.data.repository.IsolationRepository
import com.example.isolation.core.data.repository.MountRepository
import com.example.isolation.core.data.repository.SettingsRepository
import com.example.isolation.core.data.repository.VaultRepository
import com.example.isolation.core.data.repository.VpnProxyRepository
import com.example.isolation.data.IsoVaultDatabase
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.pkg.AppPackageRepository

/**
 * Dependency injection container interface for Hollow.
 * Centralizes repository singletons, database access, and clean constructor injection.
 */
interface AppContainer {
    val database: IsoVaultDatabase
    val isolationDao: IsolationDao
    val packageRepository: AppPackageRepository

    val isolationRepository: IsolationRepository
    val vaultRepository: VaultRepository
    val auditLogRepository: AuditLogRepository
    val deviceSpoofRepository: DeviceSpoofRepository
    val settingsRepository: SettingsRepository
    val mountRepository: MountRepository
    val vpnProxyRepository: VpnProxyRepository
}
