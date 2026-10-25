package com.example.isolation.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.isolation.data.entity.AccessAuditLog
import com.example.isolation.data.entity.AppIsolationConfig
import com.example.isolation.data.entity.VaultFileItem
import kotlinx.coroutines.flow.Flow

@Dao
interface IsolationDao {
    // Configs
    @Query("SELECT * FROM app_isolation_configs ORDER BY isIsolated DESC, appName ASC")
    fun getAllConfigs(): Flow<List<AppIsolationConfig>>

    @Query("SELECT * FROM app_isolation_configs WHERE packageName = :pkg LIMIT 1")
    suspend fun getConfigForPackage(pkg: String): AppIsolationConfig?

    @Query("SELECT * FROM app_isolation_configs WHERE isIsolated = 1")
    fun getIsolatedConfigs(): Flow<List<AppIsolationConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: AppIsolationConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<AppIsolationConfig>)

    @Update
    suspend fun updateConfig(config: AppIsolationConfig)

    @Query("UPDATE app_isolation_configs SET isIsolated = :isolated WHERE packageName = :pkg")
    suspend fun setIsolationStatus(pkg: String, isolated: Boolean)

    @Query("UPDATE app_isolation_configs SET useSharedSpace = :useShared WHERE packageName = :pkg")
    suspend fun setSharedSpaceStatus(pkg: String, useShared: Boolean)

    @Query("UPDATE app_isolation_configs SET volumeQuotaMb = :quotaMb WHERE packageName = :pkg")
    suspend fun setVolumeQuota(pkg: String, quotaMb: Int)

    @Query("UPDATE app_isolation_configs SET wrappedVolumeKey = :wrappedKey WHERE packageName = :pkg")
    suspend fun setWrappedVolumeKey(pkg: String, wrappedKey: String)

    @Query("DELETE FROM app_isolation_configs WHERE packageName = :pkg")
    suspend fun deleteConfigForPackage(pkg: String)

    @Query("UPDATE app_isolation_configs SET totalFilesRedirected = totalFilesRedirected + 1, lastAccessedTimestamp = :now WHERE packageName = :pkg")
    suspend fun incrementRedirectCount(pkg: String, now: Long)

    @Query("UPDATE app_isolation_configs SET totalBytesEncrypted = totalBytesEncrypted + :bytes WHERE packageName = :pkg")
    suspend fun addEncryptedBytes(pkg: String, bytes: Long)

    // Audit Logs
    @Query("SELECT * FROM access_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAuditLogs(limit: Int = 100): Flow<List<AccessAuditLog>>

    @Query("SELECT * FROM access_audit_logs WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsForPackage(pkg: String, limit: Int = 50): Flow<List<AccessAuditLog>>

    @Insert
    suspend fun insertAuditLog(log: AccessAuditLog)

    @Query("DELETE FROM access_audit_logs")
    suspend fun clearAuditLogs()

    // Vault Files
    @Query("SELECT * FROM vault_files ORDER BY lastModified DESC")
    fun getAllVaultFiles(): Flow<List<VaultFileItem>>

    @Query("SELECT * FROM vault_files WHERE packageName = :pkg ORDER BY lastModified DESC")
    fun getVaultFilesForPackage(pkg: String): Flow<List<VaultFileItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultFile(item: VaultFileItem): Long

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun deleteVaultFile(id: Long)

    @Query("DELETE FROM vault_files WHERE packageName = :pkg")
    suspend fun deleteVaultFilesForPackage(pkg: String)

    // Statistics
    @Query("SELECT COUNT(*) FROM app_isolation_configs WHERE isIsolated = 1")
    fun getIsolatedAppsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM access_audit_logs")
    fun getTotalAuditCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalBytesEncrypted), 0) FROM app_isolation_configs")
    fun getTotalEncryptedBytes(): Flow<Long>
}
