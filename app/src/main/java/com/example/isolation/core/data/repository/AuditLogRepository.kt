package com.example.isolation.core.data.repository

import com.example.isolation.core.result.HollowResult
import com.example.isolation.core.result.runSuspendCatchingHollow
import com.example.isolation.data.dao.IsolationDao
import com.example.isolation.data.entity.AccessAuditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface AuditLogRepository {
    fun getRecentAuditLogs(limit: Int = 100): Flow<List<AccessAuditLog>>
    fun getLogsForPackage(pkg: String, limit: Int = 50): Flow<List<AccessAuditLog>>
    fun getTotalAuditCount(): Flow<Int>
    suspend fun insertAuditLog(log: AccessAuditLog): HollowResult<Unit>
    suspend fun clearAuditLogs(): HollowResult<Unit>
}

class DefaultAuditLogRepository(
    private val dao: IsolationDao
) : AuditLogRepository {

    override fun getRecentAuditLogs(limit: Int): Flow<List<AccessAuditLog>> =
        dao.getRecentAuditLogs(limit)

    override fun getLogsForPackage(pkg: String, limit: Int): Flow<List<AccessAuditLog>> =
        dao.getLogsForPackage(pkg, limit)

    override fun getTotalAuditCount(): Flow<Int> = dao.getTotalAuditCount()

    override suspend fun insertAuditLog(log: AccessAuditLog): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "AuditRepo", errorMessage = "Failed to record audit log") {
            withContext(Dispatchers.IO) {
                dao.insertAuditLog(log)
            }
        }

    override suspend fun clearAuditLogs(): HollowResult<Unit> =
        runSuspendCatchingHollow(tag = "AuditRepo", errorMessage = "Failed to clear audit logs") {
            withContext(Dispatchers.IO) {
                dao.clearAuditLogs()
            }
        }
}
