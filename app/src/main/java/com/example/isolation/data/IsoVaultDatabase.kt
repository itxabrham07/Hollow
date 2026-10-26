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
    version = 4,
    exportSchema = false
)
@TypeConverters(IsoVaultConverters::class)
abstract class IsoVaultDatabase : RoomDatabase() {
    abstract fun isolationDao(): IsolationDao

    companion object {
        @Volatile
        private var INSTANCE: IsoVaultDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN hideFromPlayStore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofInSettings INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN customIconBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN customIconDonorPkg TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN isIdentitySpoofed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN deviceProfilePreset TEXT NOT NULL DEFAULT 'PIXEL_9_PRO'")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofBrand TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofModel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofDevice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofAndroidId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofImei TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofAdvertisingId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofMacAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofCarrierName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofSimOperator TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN spoofSerial TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN hideRootAndXposed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN hideAdbAndDevOptions INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN hideVpnAndProxy INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN blockClipboardRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): IsoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IsoVaultDatabase::class.java,
                    "isovault_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
