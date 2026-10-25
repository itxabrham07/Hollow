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
import com.example.isolation.model.IsolationMode

class IsolationConverters {
    @TypeConverter
    fun fromIsolationMode(mode: IsolationMode): String = mode.name

    @TypeConverter
    fun toIsolationMode(value: String): IsolationMode =
        IsolationMode.entries.find { it.name == value } ?: IsolationMode.ENCRYPTED_VAULT

    @TypeConverter
    fun fromAuditAction(action: AuditAction): String = action.name

    @TypeConverter
    fun toAuditAction(value: String): AuditAction =
        AuditAction.entries.find { it.name == value } ?: AuditAction.REDIRECTED
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
@TypeConverters(IsolationConverters::class)
abstract class IsoVaultDatabase : RoomDatabase() {
    abstract fun isolationDao(): IsolationDao

    companion object {
        @Volatile
        private var INSTANCE: IsoVaultDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN useSharedSpace INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN volumeQuotaMb INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN wrappedVolumeKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_isolation_configs ADD COLUMN activeHooksMask INTEGER NOT NULL DEFAULT 31")
            }
        }

        fun getDatabase(context: Context): IsoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IsoVaultDatabase::class.java,
                    "isovault_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
