package com.example.isolation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
import com.example.isolation.model.DeviceProfilePreset
import com.example.isolation.model.DisguiseIconPreset
import com.example.isolation.model.IsolationMode
import com.example.isolation.model.LauncherVisibilityMode

class IsoVaultConverters {
    @TypeConverter
    fun fromIsolationMode(mode: IsolationMode): String = mode.name

    @TypeConverter
    fun toIsolationMode(name: String): IsolationMode = try {
        IsolationMode.valueOf(name)
    } catch (_: Exception) {
        IsolationMode.ENCRYPTED_VAULT
    }

    @TypeConverter
    fun fromAuditAction(action: AuditAction): String = action.name

    @TypeConverter
    fun toAuditAction(name: String): AuditAction = try {
        AuditAction.valueOf(name)
    } catch (_: Exception) {
        AuditAction.REDIRECTED
    }

    @TypeConverter
    fun fromLauncherVisibilityMode(mode: LauncherVisibilityMode): String = mode.name

    @TypeConverter
    fun toLauncherVisibilityMode(name: String): LauncherVisibilityMode = try {
        LauncherVisibilityMode.valueOf(name)
    } catch (_: Exception) {
        LauncherVisibilityMode.VISIBLE
    }

    @TypeConverter
    fun fromDisguiseIconPreset(preset: DisguiseIconPreset): String = preset.name

    @TypeConverter
    fun toDisguiseIconPreset(name: String): DisguiseIconPreset = try {
        DisguiseIconPreset.valueOf(name)
    } catch (_: Exception) {
        DisguiseIconPreset.CALCULATOR
    }

    @TypeConverter
    fun fromDeviceProfilePreset(preset: DeviceProfilePreset): String = preset.name

    @TypeConverter
    fun toDeviceProfilePreset(name: String): DeviceProfilePreset = try {
        DeviceProfilePreset.valueOf(name)
    } catch (_: Exception) {
        DeviceProfilePreset.PIXEL_9_PRO
    }
}

@Database(
    entities = [
        AppIsolationConfig::class,
        AccessAuditLog::class,
        VaultFileItem::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(IsoVaultConverters::class)
abstract class IsoVaultDatabase : RoomDatabase() {
    abstract fun isolationDao(): IsolationDao

    companion object {
        @Volatile
        private var INSTANCE: IsoVaultDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_isolation_configs` RENAME TO `app_isolation_configs_old`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_isolation_configs` (" +
                        "`packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `isIsolated` INTEGER NOT NULL, " +
                        "`isolationMode` TEXT NOT NULL, `encryptMediaOnly` INTEGER NOT NULL, `blockRootPollution` INTEGER NOT NULL, " +
                        "`whitelistedFolders` TEXT NOT NULL, `customMountPath` TEXT NOT NULL, `totalFilesRedirected` INTEGER NOT NULL, " +
                        "`totalBytesEncrypted` INTEGER NOT NULL, `lastAccessedTimestamp` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`isHiddenOrDisguised` INTEGER NOT NULL, `launcherVisibilityMode` TEXT NOT NULL, `customLauncherLabel` TEXT NOT NULL, " +
                        "`disguiseIconPreset` TEXT NOT NULL, `hideInSystemUiRecents` INTEGER NOT NULL, `hideFromPlayStore` INTEGER NOT NULL, " +
                        "`spoofInSettings` INTEGER NOT NULL, `customIconBase64` TEXT NOT NULL, `customIconDonorPkg` TEXT NOT NULL, " +
                        "`isIdentitySpoofed` INTEGER NOT NULL, `deviceProfilePreset` TEXT NOT NULL, `spoofBrand` TEXT NOT NULL, " +
                        "`spoofModel` TEXT NOT NULL, `spoofDevice` TEXT NOT NULL, `spoofAndroidId` TEXT NOT NULL, " +
                        "`spoofImei` TEXT NOT NULL, `spoofAdvertisingId` TEXT NOT NULL, `spoofMacAddress` TEXT NOT NULL, " +
                        "`spoofCarrierName` TEXT NOT NULL, `spoofSimOperator` TEXT NOT NULL, `spoofSerial` TEXT NOT NULL, " +
                        "`spoofLocationEnabled` INTEGER NOT NULL, `spoofLatitude` REAL NOT NULL, `spoofLongitude` REAL NOT NULL, " +
                        "`hideMockLocation` INTEGER NOT NULL, `preventScreenshots` INTEGER NOT NULL, `hideOtherApps` INTEGER NOT NULL, " +
                        "`disableLogcatLogging` INTEGER NOT NULL, `fakeBatteryEnabled` INTEGER NOT NULL, `fakeBatteryLevel` INTEGER NOT NULL, " +
                        "`knoxWarrantyBitSpoof` INTEGER NOT NULL, `hideRootAndXposed` INTEGER NOT NULL, `hideAdbAndDevOptions` INTEGER NOT NULL, " +
                        "`hideVpnAndProxy` INTEGER NOT NULL, `blockClipboardRead` INTEGER NOT NULL, `spoofGpuFingerprint` INTEGER NOT NULL, " +
                        "`spoofBrowserUserAgent` INTEGER NOT NULL, `anonymizeSensors` INTEGER NOT NULL, `blockCellTowerTracking` INTEGER NOT NULL, " +
                        "`autoSyncTimezone` INTEGER NOT NULL, `disableAllNetworking` INTEGER NOT NULL, PRIMARY KEY(`packageName`))"
                )
                try {
                    db.execSQL(
                        "INSERT INTO `app_isolation_configs` (" +
                            "`packageName`, `appName`, `isIsolated`, `isolationMode`, `encryptMediaOnly`, `blockRootPollution`, " +
                            "`whitelistedFolders`, `customMountPath`, `totalFilesRedirected`, `totalBytesEncrypted`, `lastAccessedTimestamp`, `createdAt`, " +
                            "`isHiddenOrDisguised`, `launcherVisibilityMode`, `customLauncherLabel`, `disguiseIconPreset`, `hideInSystemUiRecents`, " +
                            "`hideFromPlayStore`, `spoofInSettings`, `customIconBase64`, `customIconDonorPkg`, `isIdentitySpoofed`, `deviceProfilePreset`, " +
                            "`spoofBrand`, `spoofModel`, `spoofDevice`, `spoofAndroidId`, `spoofImei`, `spoofAdvertisingId`, `spoofMacAddress`, " +
                            "`spoofCarrierName`, `spoofSimOperator`, `spoofSerial`, `spoofLocationEnabled`, `spoofLatitude`, `spoofLongitude`, " +
                            "`hideMockLocation`, `preventScreenshots`, `hideOtherApps`, `disableLogcatLogging`, `fakeBatteryEnabled`, `fakeBatteryLevel`, " +
                            "`knoxWarrantyBitSpoof`, `hideRootAndXposed`, `hideAdbAndDevOptions`, `hideVpnAndProxy`, `blockClipboardRead`, " +
                            "`spoofGpuFingerprint`, `spoofBrowserUserAgent`, `anonymizeSensors`, `blockCellTowerTracking`, `autoSyncTimezone`, `disableAllNetworking`" +
                            ") SELECT " +
                            "`packageName`, `appName`, `isIsolated`, `isolationMode`, `encryptMediaOnly`, `blockRootPollution`, " +
                            "`whitelistedFolders`, `customMountPath`, `totalFilesRedirected`, `totalBytesEncrypted`, `lastAccessedTimestamp`, `createdAt`, " +
                            "`isHiddenOrDisguised`, `launcherVisibilityMode`, `customLauncherLabel`, `disguiseIconPreset`, `hideInSystemUiRecents`, " +
                            "`hideFromPlayStore`, `spoofInSettings`, `customIconBase64`, `customIconDonorPkg`, `isIdentitySpoofed`, `deviceProfilePreset`, " +
                            "`spoofBrand`, `spoofModel`, `spoofDevice`, `spoofAndroidId`, `spoofImei`, `spoofAdvertisingId`, `spoofMacAddress`, " +
                            "`spoofCarrierName`, `spoofSimOperator`, `spoofSerial`, `spoofLocationEnabled`, `spoofLatitude`, `spoofLongitude`, " +
                            "`hideMockLocation`, `preventScreenshots`, `hideOtherApps`, `disableLogcatLogging`, `fakeBatteryEnabled`, `fakeBatteryLevel`, " +
                            "`knoxWarrantyBitSpoof`, `hideRootAndXposed`, `hideAdbAndDevOptions`, `hideVpnAndProxy`, `blockClipboardRead`, " +
                            "1, 1, 1, 1, 1, `disableAllNetworking` FROM `app_isolation_configs_old`"
                    )
                } catch (_: Exception) {
                    // Fallback to empty table if prior schema had different columns
                }
                db.execSQL("DROP TABLE IF EXISTS `app_isolation_configs_old`")
            }
        }

        fun getDatabase(context: Context): IsoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IsoVaultDatabase::class.java,
                    "isovault_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
