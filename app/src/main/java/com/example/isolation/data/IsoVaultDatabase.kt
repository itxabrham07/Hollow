package com.example.isolation.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import com.example.isolation.model.AuditAction
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
}

@Database(
    entities = [
        AppIsolationConfig::class,
        AccessAuditLog::class,
        VaultFileItem::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(IsoVaultConverters::class)
abstract class IsoVaultDatabase : RoomDatabase() {
    abstract fun isolationDao(): IsolationDao

    companion object {
        @Volatile
        private var INSTANCE: IsoVaultDatabase? = null

        fun getDatabase(context: Context): IsoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IsoVaultDatabase::class.java,
                    "isovault_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
