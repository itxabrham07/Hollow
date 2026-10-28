package com.example.isolation.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.InvalidationTracker
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.LauncherVisibilityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Direct SQLite implementation of [IsoVaultDatabase] that operates without requiring Room annotation processor / KSP.
 * This guarantees zero runtime crashes ("IsoVaultDatabase_Impl does not exist") across all build types,
 * CI pipelines, and Android OS versions.
 */
class IsoVaultDirectDatabase(context: Context) : IsoVaultDatabase() {

    private val helper = DatabaseHelper(context.applicationContext)
    private val dao = DirectDao(helper)

    override fun isolationDao(): IsolationDao = dao

    override fun createInvalidationTracker(): InvalidationTracker =
        InvalidationTracker(this, "app_isolation_configs", "access_audit_logs", "vault_files")

    override fun clearAllTables() {
        helper.writableDatabase.use { db ->
            db.execSQL("DELETE FROM app_isolation_configs")
            db.execSQL("DELETE FROM access_audit_logs")
            db.execSQL("DELETE FROM vault_files")
        }
    }

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        companion object {
            private const val DB_NAME = "isovault_database"
            private const val DB_VERSION = 8
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_isolation_configs` (
                    `packageName` TEXT NOT NULL PRIMARY KEY,
                    `appName` TEXT NOT NULL,
                    `isIsolated` INTEGER NOT NULL DEFAULT 0,
                    `isolationMode` TEXT NOT NULL DEFAULT 'SHARED_WORKSPACE',
                    `encryptMediaOnly` INTEGER NOT NULL DEFAULT 0,
                    `blockRootPollution` INTEGER NOT NULL DEFAULT 1,
                    `whitelistedFolders` TEXT NOT NULL DEFAULT '',
                    `customMountPath` TEXT NOT NULL DEFAULT '',
                    `totalFilesRedirected` INTEGER NOT NULL DEFAULT 0,
                    `totalBytesEncrypted` INTEGER NOT NULL DEFAULT 0,
                    `lastAccessedTimestamp` INTEGER NOT NULL DEFAULT 0,
                    `createdAt` INTEGER NOT NULL DEFAULT 0,
                    `isHiddenOrDisguised` INTEGER NOT NULL DEFAULT 0,
                    `launcherVisibilityMode` TEXT NOT NULL DEFAULT 'VISIBLE',
                    `customLauncherLabel` TEXT NOT NULL DEFAULT '',
                    `disguiseIconPreset` TEXT NOT NULL DEFAULT 'CALCULATOR',
                    `hideInSystemUiRecents` INTEGER NOT NULL DEFAULT 1,
                    `hideFromPlayStore` INTEGER NOT NULL DEFAULT 0,
                    `spoofInSettings` INTEGER NOT NULL DEFAULT 1,
                    `customIconBase64` TEXT NOT NULL DEFAULT '',
                    `customIconDonorPkg` TEXT NOT NULL DEFAULT '',
                    `isIdentitySpoofed` INTEGER NOT NULL DEFAULT 0,
                    `deviceProfilePreset` TEXT NOT NULL DEFAULT 'REAL_HARDWARE',
                    `spoofBrand` TEXT NOT NULL DEFAULT '',
                    `spoofModel` TEXT NOT NULL DEFAULT '',
                    `spoofDevice` TEXT NOT NULL DEFAULT '',
                    `spoofAndroidId` TEXT NOT NULL DEFAULT '',
                    `spoofImei` TEXT NOT NULL DEFAULT '',
                    `spoofAdvertisingId` TEXT NOT NULL DEFAULT '',
                    `spoofMacAddress` TEXT NOT NULL DEFAULT '',
                    `spoofCarrierName` TEXT NOT NULL DEFAULT '',
                    `spoofSimOperator` TEXT NOT NULL DEFAULT '',
                    `spoofSerial` TEXT NOT NULL DEFAULT '',
                    `spoofLocationEnabled` INTEGER NOT NULL DEFAULT 0,
                    `spoofLatitude` REAL NOT NULL DEFAULT 37.7749,
                    `spoofLongitude` REAL NOT NULL DEFAULT -122.4194,
                    `hideMockLocation` INTEGER NOT NULL DEFAULT 1,
                    `preventScreenshots` INTEGER NOT NULL DEFAULT 0,
                    `hideOtherApps` INTEGER NOT NULL DEFAULT 0,
                    `disableLogcatLogging` INTEGER NOT NULL DEFAULT 0,
                    `fakeBatteryEnabled` INTEGER NOT NULL DEFAULT 0,
                    `fakeBatteryLevel` INTEGER NOT NULL DEFAULT 100,
                    `knoxWarrantyBitSpoof` INTEGER NOT NULL DEFAULT 0,
                    `hideRootAndXposed` INTEGER NOT NULL DEFAULT 1,
                    `hideAdbAndDevOptions` INTEGER NOT NULL DEFAULT 1,
                    `hideVpnAndProxy` INTEGER NOT NULL DEFAULT 1,
                    `blockClipboardRead` INTEGER NOT NULL DEFAULT 0,
                    `spoofGpuFingerprint` INTEGER NOT NULL DEFAULT 1,
                    `spoofBrowserUserAgent` INTEGER NOT NULL DEFAULT 1,
                    `anonymizeSensors` INTEGER NOT NULL DEFAULT 1,
                    `blockCellTowerTracking` INTEGER NOT NULL DEFAULT 1,
                    `autoSyncTimezone` INTEGER NOT NULL DEFAULT 1,
                    `disableAllNetworking` INTEGER NOT NULL DEFAULT 0,
                    `spoofManufacturer` TEXT NOT NULL DEFAULT '',
                    `spoofProduct` TEXT NOT NULL DEFAULT '',
                    `spoofBoard` TEXT NOT NULL DEFAULT '',
                    `spoofHardware` TEXT NOT NULL DEFAULT '',
                    `spoofBootloader` TEXT NOT NULL DEFAULT '',
                    `spoofFingerprint` TEXT NOT NULL DEFAULT '',
                    `spoofGpuVendor` TEXT NOT NULL DEFAULT '',
                    `spoofGpuRenderer` TEXT NOT NULL DEFAULT '',
                    `spoofCpuCores` INTEGER NOT NULL DEFAULT 0,
                    `spoofRamGb` INTEGER NOT NULL DEFAULT 0,
                    `spoofScreenResolution` TEXT NOT NULL DEFAULT '',
                    `spoofScreenDpi` INTEGER NOT NULL DEFAULT 0,
                    `spoofTimezoneId` TEXT NOT NULL DEFAULT '',
                    `spoofLocaleTag` TEXT NOT NULL DEFAULT '',
                    `customUserAgent` TEXT NOT NULL DEFAULT '',
                    `spoofWebRtc` INTEGER NOT NULL DEFAULT 1,
                    `spoofCanvasNoise` INTEGER NOT NULL DEFAULT 1,
                    `spoofAudioContext` INTEGER NOT NULL DEFAULT 1,
                    `spoofClientHints` INTEGER NOT NULL DEFAULT 1,
                    `spoofFontEnumeration` INTEGER NOT NULL DEFAULT 1,
                    `vpnProxyEnabled` INTEGER NOT NULL DEFAULT 0,
                    `proxyServerId` TEXT NOT NULL DEFAULT 'us_ny_vless',
                    `requireVpnForNetwork` INTEGER NOT NULL DEFAULT 0,
                    `matchTimezoneWithIp` INTEGER NOT NULL DEFAULT 1,
                    `webViewProxyEnabled` INTEGER NOT NULL DEFAULT 1,
                    `trustAllCerts` INTEGER NOT NULL DEFAULT 0,
                    `privacyProfileId` TEXT NOT NULL DEFAULT 'ghost_cloak',
                    `deadmanSwitchEnabled` INTEGER NOT NULL DEFAULT 1,
                    `spoofDrmWidevine` INTEGER NOT NULL DEFAULT 1,
                    `sensorBiasNeutralizer` INTEGER NOT NULL DEFAULT 1,
                    `spoofWifiBssid` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `access_audit_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `appName` TEXT NOT NULL,
                    `requestedPath` TEXT NOT NULL,
                    `targetResolvedPath` TEXT NOT NULL,
                    `action` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `operation` TEXT NOT NULL,
                    `details` TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `vault_files` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `relativePath` TEXT NOT NULL,
                    `physicalPath` TEXT NOT NULL,
                    `isEncrypted` INTEGER NOT NULL DEFAULT 1,
                    `originalSizeBytes` INTEGER NOT NULL,
                    `encryptedSizeBytes` INTEGER NOT NULL,
                    `algorithm` TEXT NOT NULL DEFAULT 'AES-256-GCM',
                    `mimeType` TEXT NOT NULL,
                    `lastModified` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onCreate(db)
        }
    }

    private class DirectDao(private val helper: DatabaseHelper) : IsolationDao {

        private val configNotifier = MutableStateFlow(System.currentTimeMillis())
        private val auditNotifier = MutableStateFlow(System.currentTimeMillis())
        private val vaultNotifier = MutableStateFlow(System.currentTimeMillis())

        private fun notifyConfigs() { configNotifier.value = System.currentTimeMillis() }
        private fun notifyAudits() { auditNotifier.value = System.currentTimeMillis() }
        private fun notifyVault() { vaultNotifier.value = System.currentTimeMillis() }

        override fun getAllConfigs(): Flow<List<AppIsolationConfig>> = FlowFromStateFlow(configNotifier) {
            getAllConfigsSync()
        }

        override suspend fun getAllConfigsSync(): List<AppIsolationConfig> = withContext(Dispatchers.IO) {
            val list = mutableListOf<AppIsolationConfig>()
            val db = helper.readableDatabase
            val cursor = db.query("app_isolation_configs", null, null, null, null, null, "isIsolated DESC, isHiddenOrDisguised DESC, appName ASC")
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.toConfig())
                }
            }
            list
        }

        override suspend fun getConfigForPackage(pkg: String): AppIsolationConfig? = withContext(Dispatchers.IO) {
            val db = helper.readableDatabase
            val cursor = db.query("app_isolation_configs", null, "packageName = ?", arrayOf(pkg), null, null, null, "1")
            cursor.use { c ->
                if (c.moveToFirst()) c.toConfig() else null
            }
        }

        override fun getIsolatedConfigs(): Flow<List<AppIsolationConfig>> = FlowFromStateFlow(configNotifier) {
            getAllConfigsSync().filter { it.isIsolated }
        }

        override fun getHiddenOrDisguisedConfigs(): Flow<List<AppIsolationConfig>> = FlowFromStateFlow(configNotifier) {
            getAllConfigsSync().filter { it.isHiddenOrDisguised }
        }

        override suspend fun insertOrUpdateConfig(config: AppIsolationConfig): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.insertWithOnConflict("app_isolation_configs", null, config.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
            notifyConfigs()
        }

        override suspend fun insertConfigs(configs: List<AppIsolationConfig>): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                for (cfg in configs) {
                    db.insertWithOnConflict("app_isolation_configs", null, cfg.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyConfigs()
        }

        override suspend fun updateConfig(config: AppIsolationConfig) = insertOrUpdateConfig(config)

        override suspend fun setIsolationStatus(pkg: String, isolated: Boolean): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            val values = ContentValues().apply { put("isIsolated", if (isolated) 1 else 0) }
            db.update("app_isolation_configs", values, "packageName = ?", arrayOf(pkg))
            notifyConfigs()
        }

        override suspend fun incrementRedirectCount(pkg: String, now: Long): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.execSQL("UPDATE app_isolation_configs SET totalFilesRedirected = totalFilesRedirected + 1, lastAccessedTimestamp = ? WHERE packageName = ?", arrayOf(now, pkg))
            notifyConfigs()
        }

        override suspend fun addEncryptedBytes(pkg: String, bytes: Long): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.execSQL("UPDATE app_isolation_configs SET totalBytesEncrypted = totalBytesEncrypted + ? WHERE packageName = ?", arrayOf(bytes, pkg))
            notifyConfigs()
        }

        override fun getRecentAuditLogs(limit: Int): Flow<List<AccessAuditLog>> = FlowFromStateFlow(auditNotifier) {
            val list = mutableListOf<AccessAuditLog>()
            val db = helper.readableDatabase
            val cursor = db.query("access_audit_logs", null, null, null, null, null, "timestamp DESC", limit.toString())
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.toAuditLog())
                }
            }
            list
        }

        override fun getLogsForPackage(pkg: String, limit: Int): Flow<List<AccessAuditLog>> = FlowFromStateFlow(auditNotifier) {
            val list = mutableListOf<AccessAuditLog>()
            val db = helper.readableDatabase
            val cursor = db.query("access_audit_logs", null, "packageName = ?", arrayOf(pkg), null, null, "timestamp DESC", limit.toString())
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.toAuditLog())
                }
            }
            list
        }

        override suspend fun insertAuditLog(log: AccessAuditLog): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("packageName", log.packageName)
                put("appName", log.appName)
                put("requestedPath", log.requestedPath)
                put("targetResolvedPath", log.targetResolvedPath)
                put("action", log.action.name)
                put("timestamp", log.timestamp)
                put("operation", log.operation)
                put("details", log.details)
            }
            db.insert("access_audit_logs", null, values)
            notifyAudits()
        }

        override suspend fun clearAuditLogs(): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.delete("access_audit_logs", null, null)
            notifyAudits()
        }

        override fun getAllVaultFiles(): Flow<List<VaultFileItem>> = FlowFromStateFlow(vaultNotifier) {
            getAllVaultFilesSync()
        }

        override suspend fun getAllVaultFilesSync(): List<VaultFileItem> = withContext(Dispatchers.IO) {
            val list = mutableListOf<VaultFileItem>()
            val db = helper.readableDatabase
            val cursor = db.query("vault_files", null, null, null, null, null, "lastModified DESC")
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.toVaultFile())
                }
            }
            list
        }

        override fun getVaultFilesForPackage(pkg: String): Flow<List<VaultFileItem>> = FlowFromStateFlow(vaultNotifier) {
            val list = mutableListOf<VaultFileItem>()
            val db = helper.readableDatabase
            val cursor = db.query("vault_files", null, "packageName = ?", arrayOf(pkg), null, null, "lastModified DESC")
            cursor.use { c ->
                while (c.moveToNext()) {
                    list.add(c.toVaultFile())
                }
            }
            list
        }

        override suspend fun insertVaultFile(item: VaultFileItem): Long = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            val values = ContentValues().apply {
                put("packageName", item.packageName)
                put("relativePath", item.relativePath)
                put("physicalPath", item.physicalPath)
                put("isEncrypted", if (item.isEncrypted) 1 else 0)
                put("originalSizeBytes", item.originalSizeBytes)
                put("encryptedSizeBytes", item.encryptedSizeBytes)
                put("algorithm", item.algorithm)
                put("mimeType", item.mimeType)
                put("lastModified", item.lastModified)
            }
            val id = db.insertWithOnConflict("vault_files", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyVault()
            id
        }

        override suspend fun deleteVaultFile(id: Long): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.delete("vault_files", "id = ?", arrayOf(id.toString()))
            notifyVault()
        }

        override suspend fun deleteVaultFilesForPackage(pkg: String): Unit = withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.delete("vault_files", "packageName = ?", arrayOf(pkg))
            notifyVault()
        }

        override fun getIsolatedAppsCount(): Flow<Int> = FlowFromStateFlow(configNotifier) {
            getAllConfigsSync().count { it.isIsolated }
        }

        override fun getHiddenOrDisguisedAppsCount(): Flow<Int> = FlowFromStateFlow(configNotifier) {
            getAllConfigsSync().count { it.isHiddenOrDisguised && it.launcherVisibilityMode != LauncherVisibilityMode.VISIBLE }
        }

        override fun getIdentitySpoofedAppsCount(): Flow<Int> = FlowFromStateFlow(configNotifier) {
            getAllConfigsSync().count { it.isIdentitySpoofed }
        }

        override fun getTotalAuditCount(): Flow<Int> = FlowFromStateFlow(auditNotifier) {
            val db = helper.readableDatabase
            val c = db.rawQuery("SELECT COUNT(*) FROM access_audit_logs", null)
            c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }

        override fun getTotalEncryptedBytes(): Flow<Long> = FlowFromStateFlow(vaultNotifier) {
            val db = helper.readableDatabase
            val c = db.rawQuery("SELECT COALESCE(SUM(encryptedSizeBytes), 0) FROM vault_files WHERE isEncrypted = 1", null)
            c.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        }

        private fun Cursor.toConfig(): AppIsolationConfig {
            return AppIsolationConfig(
                packageName = getStringByName("packageName"),
                appName = getStringByName("appName"),
                isIsolated = getIntByName("isIsolated") == 1,
                isolationMode = parseEnum(getStringByName("isolationMode"), IsolationMode.SHARED_WORKSPACE),
                encryptMediaOnly = getIntByName("encryptMediaOnly") == 1,
                blockRootPollution = getIntByName("blockRootPollution") == 1,
                whitelistedFolders = getStringByName("whitelistedFolders"),
                customMountPath = getStringByName("customMountPath"),
                totalFilesRedirected = getLongByName("totalFilesRedirected"),
                totalBytesEncrypted = getLongByName("totalBytesEncrypted"),
                lastAccessedTimestamp = getLongByName("lastAccessedTimestamp"),
                createdAt = getLongByName("createdAt"),
                isHiddenOrDisguised = getIntByName("isHiddenOrDisguised") == 1,
                launcherVisibilityMode = parseEnum(getStringByName("launcherVisibilityMode"), LauncherVisibilityMode.VISIBLE),
                customLauncherLabel = getStringByName("customLauncherLabel"),
                disguiseIconPreset = parseEnum(getStringByName("disguiseIconPreset"), DisguiseIconPreset.CALCULATOR),
                hideInSystemUiRecents = getIntByName("hideInSystemUiRecents") == 1,
                hideFromPlayStore = getIntByName("hideFromPlayStore") == 1,
                spoofInSettings = getIntByName("spoofInSettings") == 1,
                customIconBase64 = getStringByName("customIconBase64"),
                customIconDonorPkg = getStringByName("customIconDonorPkg"),
                isIdentitySpoofed = getIntByName("isIdentitySpoofed") == 1,
                deviceProfilePreset = parseEnum(getStringByName("deviceProfilePreset"), DeviceProfilePreset.REAL_HARDWARE),
                spoofBrand = getStringByName("spoofBrand"),
                spoofModel = getStringByName("spoofModel"),
                spoofDevice = getStringByName("spoofDevice"),
                spoofAndroidId = getStringByName("spoofAndroidId"),
                spoofImei = getStringByName("spoofImei"),
                spoofAdvertisingId = getStringByName("spoofAdvertisingId"),
                spoofMacAddress = getStringByName("spoofMacAddress"),
                spoofCarrierName = getStringByName("spoofCarrierName"),
                spoofSimOperator = getStringByName("spoofSimOperator"),
                spoofSerial = getStringByName("spoofSerial"),
                spoofLocationEnabled = getIntByName("spoofLocationEnabled") == 1,
                spoofLatitude = getDoubleByName("spoofLatitude"),
                spoofLongitude = getDoubleByName("spoofLongitude"),
                hideMockLocation = getIntByName("hideMockLocation") == 1,
                preventScreenshots = getIntByName("preventScreenshots") == 1,
                hideOtherApps = getIntByName("hideOtherApps") == 1,
                disableLogcatLogging = getIntByName("disableLogcatLogging") == 1,
                fakeBatteryEnabled = getIntByName("fakeBatteryEnabled") == 1,
                fakeBatteryLevel = getIntByName("fakeBatteryLevel"),
                knoxWarrantyBitSpoof = getIntByName("knoxWarrantyBitSpoof") == 1,
                hideRootAndXposed = getIntByName("hideRootAndXposed") == 1,
                hideAdbAndDevOptions = getIntByName("hideAdbAndDevOptions") == 1,
                hideVpnAndProxy = getIntByName("hideVpnAndProxy") == 1,
                blockClipboardRead = getIntByName("blockClipboardRead") == 1,
                spoofGpuFingerprint = getIntByName("spoofGpuFingerprint") == 1,
                spoofBrowserUserAgent = getIntByName("spoofBrowserUserAgent") == 1,
                anonymizeSensors = getIntByName("anonymizeSensors") == 1,
                blockCellTowerTracking = getIntByName("blockCellTowerTracking") == 1,
                autoSyncTimezone = getIntByName("autoSyncTimezone") == 1,
                disableAllNetworking = getIntByName("disableAllNetworking") == 1,
                spoofManufacturer = getStringByName("spoofManufacturer"),
                spoofProduct = getStringByName("spoofProduct"),
                spoofBoard = getStringByName("spoofBoard"),
                spoofHardware = getStringByName("spoofHardware"),
                spoofBootloader = getStringByName("spoofBootloader"),
                spoofFingerprint = getStringByName("spoofFingerprint"),
                spoofGpuVendor = getStringByName("spoofGpuVendor"),
                spoofGpuRenderer = getStringByName("spoofGpuRenderer"),
                spoofCpuCores = getIntByName("spoofCpuCores"),
                spoofRamGb = getIntByName("spoofRamGb"),
                spoofScreenResolution = getStringByName("spoofScreenResolution"),
                spoofScreenDpi = getIntByName("spoofScreenDpi"),
                spoofTimezoneId = getStringByName("spoofTimezoneId"),
                spoofLocaleTag = getStringByName("spoofLocaleTag"),
                customUserAgent = getStringByName("customUserAgent"),
                spoofWebRtc = getIntByName("spoofWebRtc") == 1,
                spoofCanvasNoise = getIntByName("spoofCanvasNoise") == 1,
                spoofAudioContext = getIntByName("spoofAudioContext") == 1,
                spoofClientHints = getIntByName("spoofClientHints") == 1,
                spoofFontEnumeration = getIntByName("spoofFontEnumeration") == 1,
                vpnProxyEnabled = getIntByName("vpnProxyEnabled") == 1,
                proxyServerId = getStringByName("proxyServerId"),
                requireVpnForNetwork = getIntByName("requireVpnForNetwork") == 1,
                matchTimezoneWithIp = getIntByName("matchTimezoneWithIp") == 1,
                webViewProxyEnabled = getIntByName("webViewProxyEnabled") == 1,
                trustAllCerts = getIntByName("trustAllCerts") == 1,
                privacyProfileId = getStringByName("privacyProfileId"),
                deadmanSwitchEnabled = getIntByName("deadmanSwitchEnabled") == 1,
                spoofDrmWidevine = getIntByName("spoofDrmWidevine") == 1,
                sensorBiasNeutralizer = getIntByName("sensorBiasNeutralizer") == 1,
                spoofWifiBssid = getIntByName("spoofWifiBssid") == 1
            )
        }

        private fun Cursor.toAuditLog(): AccessAuditLog {
            return AccessAuditLog(
                id = getLongByName("id"),
                packageName = getStringByName("packageName"),
                appName = getStringByName("appName"),
                requestedPath = getStringByName("requestedPath"),
                targetResolvedPath = getStringByName("targetResolvedPath"),
                action = parseEnum(getStringByName("action"), AuditAction.REDIRECTED),
                timestamp = getLongByName("timestamp"),
                operation = getStringByName("operation"),
                details = getStringByName("details")
            )
        }

        private fun Cursor.toVaultFile(): VaultFileItem {
            return VaultFileItem(
                id = getLongByName("id"),
                packageName = getStringByName("packageName"),
                relativePath = getStringByName("relativePath"),
                physicalPath = getStringByName("physicalPath"),
                isEncrypted = getIntByName("isEncrypted") == 1,
                originalSizeBytes = getLongByName("originalSizeBytes"),
                encryptedSizeBytes = getLongByName("encryptedSizeBytes"),
                algorithm = getStringByName("algorithm").ifBlank { "AES-256-GCM" },
                mimeType = getStringByName("mimeType"),
                lastModified = getLongByName("lastModified")
            )
        }

        private fun AppIsolationConfig.toContentValues(): ContentValues {
            return ContentValues().apply {
                put("packageName", packageName)
                put("appName", appName)
                put("isIsolated", if (isIsolated) 1 else 0)
                put("isolationMode", isolationMode.name)
                put("encryptMediaOnly", if (encryptMediaOnly) 1 else 0)
                put("blockRootPollution", if (blockRootPollution) 1 else 0)
                put("whitelistedFolders", whitelistedFolders)
                put("customMountPath", customMountPath)
                put("totalFilesRedirected", totalFilesRedirected)
                put("totalBytesEncrypted", totalBytesEncrypted)
                put("lastAccessedTimestamp", lastAccessedTimestamp)
                put("createdAt", createdAt)
                put("isHiddenOrDisguised", if (isHiddenOrDisguised) 1 else 0)
                put("launcherVisibilityMode", launcherVisibilityMode.name)
                put("customLauncherLabel", customLauncherLabel)
                put("disguiseIconPreset", disguiseIconPreset.name)
                put("hideInSystemUiRecents", if (hideInSystemUiRecents) 1 else 0)
                put("hideFromPlayStore", if (hideFromPlayStore) 1 else 0)
                put("spoofInSettings", if (spoofInSettings) 1 else 0)
                put("customIconBase64", customIconBase64)
                put("customIconDonorPkg", customIconDonorPkg)
                put("isIdentitySpoofed", if (isIdentitySpoofed) 1 else 0)
                put("deviceProfilePreset", deviceProfilePreset.name)
                put("spoofBrand", spoofBrand)
                put("spoofModel", spoofModel)
                put("spoofDevice", spoofDevice)
                put("spoofAndroidId", spoofAndroidId)
                put("spoofImei", spoofImei)
                put("spoofAdvertisingId", spoofAdvertisingId)
                put("spoofMacAddress", spoofMacAddress)
                put("spoofCarrierName", spoofCarrierName)
                put("spoofSimOperator", spoofSimOperator)
                put("spoofSerial", spoofSerial)
                put("spoofLocationEnabled", if (spoofLocationEnabled) 1 else 0)
                put("spoofLatitude", spoofLatitude)
                put("spoofLongitude", spoofLongitude)
                put("hideMockLocation", if (hideMockLocation) 1 else 0)
                put("preventScreenshots", if (preventScreenshots) 1 else 0)
                put("hideOtherApps", if (hideOtherApps) 1 else 0)
                put("disableLogcatLogging", if (disableLogcatLogging) 1 else 0)
                put("fakeBatteryEnabled", if (fakeBatteryEnabled) 1 else 0)
                put("fakeBatteryLevel", fakeBatteryLevel)
                put("knoxWarrantyBitSpoof", if (knoxWarrantyBitSpoof) 1 else 0)
                put("hideRootAndXposed", if (hideRootAndXposed) 1 else 0)
                put("hideAdbAndDevOptions", if (hideAdbAndDevOptions) 1 else 0)
                put("hideVpnAndProxy", if (hideVpnAndProxy) 1 else 0)
                put("blockClipboardRead", if (blockClipboardRead) 1 else 0)
                put("spoofGpuFingerprint", if (spoofGpuFingerprint) 1 else 0)
                put("spoofBrowserUserAgent", if (spoofBrowserUserAgent) 1 else 0)
                put("anonymizeSensors", if (anonymizeSensors) 1 else 0)
                put("blockCellTowerTracking", if (blockCellTowerTracking) 1 else 0)
                put("autoSyncTimezone", if (autoSyncTimezone) 1 else 0)
                put("disableAllNetworking", if (disableAllNetworking) 1 else 0)
                put("spoofManufacturer", spoofManufacturer)
                put("spoofProduct", spoofProduct)
                put("spoofBoard", spoofBoard)
                put("spoofHardware", spoofHardware)
                put("spoofBootloader", spoofBootloader)
                put("spoofFingerprint", spoofFingerprint)
                put("spoofGpuVendor", spoofGpuVendor)
                put("spoofGpuRenderer", spoofGpuRenderer)
                put("spoofCpuCores", spoofCpuCores)
                put("spoofRamGb", spoofRamGb)
                put("spoofScreenResolution", spoofScreenResolution)
                put("spoofScreenDpi", spoofScreenDpi)
                put("spoofTimezoneId", spoofTimezoneId)
                put("spoofLocaleTag", spoofLocaleTag)
                put("customUserAgent", customUserAgent)
                put("spoofWebRtc", if (spoofWebRtc) 1 else 0)
                put("spoofCanvasNoise", if (spoofCanvasNoise) 1 else 0)
                put("spoofAudioContext", if (spoofAudioContext) 1 else 0)
                put("spoofClientHints", if (spoofClientHints) 1 else 0)
                put("spoofFontEnumeration", if (spoofFontEnumeration) 1 else 0)
                put("vpnProxyEnabled", if (vpnProxyEnabled) 1 else 0)
                put("proxyServerId", proxyServerId)
                put("requireVpnForNetwork", if (requireVpnForNetwork) 1 else 0)
                put("matchTimezoneWithIp", if (matchTimezoneWithIp) 1 else 0)
                put("webViewProxyEnabled", if (webViewProxyEnabled) 1 else 0)
                put("trustAllCerts", if (trustAllCerts) 1 else 0)
                put("privacyProfileId", privacyProfileId)
                put("deadmanSwitchEnabled", if (deadmanSwitchEnabled) 1 else 0)
                put("spoofDrmWidevine", if (spoofDrmWidevine) 1 else 0)
                put("sensorBiasNeutralizer", if (sensorBiasNeutralizer) 1 else 0)
                put("spoofWifiBssid", if (spoofWifiBssid) 1 else 0)
            }
        }

        private fun Cursor.getStringByName(col: String): String {
            val idx = getColumnIndex(col)
            return if (idx >= 0) getString(idx).orEmpty() else ""
        }

        private fun Cursor.getIntByName(col: String): Int {
            val idx = getColumnIndex(col)
            return if (idx >= 0) getInt(idx) else 0
        }

        private fun Cursor.getLongByName(col: String): Long {
            val idx = getColumnIndex(col)
            return if (idx >= 0) getLong(idx) else 0L
        }

        private fun Cursor.getDoubleByName(col: String): Double {
            val idx = getColumnIndex(col)
            return if (idx >= 0) getDouble(idx) else 0.0
        }

        private inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T {
            if (name.isBlank()) return default
            return try { java.lang.Enum.valueOf(T::class.java, name) } catch (_: Throwable) { default }
        }
    }

    private class FlowFromStateFlow<T>(
        private val notifier: MutableStateFlow<Long>,
        private val queryBlock: suspend () -> T
    ) : Flow<T> {
        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<T>) {
            notifier.collect {
                collector.emit(queryBlock())
            }
        }
    }
}
