package me.kkutuio.kkutuweb.record

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RecordService(
    @Autowired private val recordDao: RecordDao
) {
    private fun canViewAllHistory(userId: String, requesterId: String?, isAdmin: Boolean): Boolean {
        if (isAdmin) return true
        if (!requesterId.isNullOrBlank() && requesterId == userId) return true
        return recordDao.isRecordHistoryPublic(userId)
    }

    private fun visibilityScope(userId: String, requesterId: String?, isAdmin: Boolean, canViewAll: Boolean): String {
        if (isAdmin) return "admin"
        if (!requesterId.isNullOrBlank() && requesterId == userId) return "self"
        return if (canViewAll) "public" else "public-preview"
    }

    fun findByGameId(
        gameId: String,
        includePayload: Boolean,
        requesterId: String?,
        isAdmin: Boolean
    ): RecordGameLookupResponse {
        return recordDao.findByGameId(gameId, includePayload, requesterId, isAdmin)
            ?: RecordGameLookupResponse(ok = false, code = 761, error = "game-server-unavailable")
    }

    fun findUserHistory(
        userId: String,
        page: Int,
        pageSize: Int,
        requesterId: String?,
        isAdmin: Boolean
    ): RecordUserHistoryResponse {
        val safePage = if (page < 1) 1 else page
        val safePageSize = when (pageSize) {
            30, 50 -> pageSize
            else -> 10
        }
        val canViewAll = canViewAllHistory(userId, requesterId, isAdmin)
        val visibilityScope = visibilityScope(userId, requesterId, isAdmin, canViewAll)
        return recordDao.findUserHistory(userId, safePage, safePageSize, canViewAll, isAdmin, visibilityScope)
            ?: RecordUserHistoryResponse(ok = false, code = 761, error = "game-server-unavailable")
    }

    fun findUserModeStats(
        userId: String,
        requesterId: String?,
        isAdmin: Boolean
    ): RecordUserModeStatsResponse {
        val canViewAll = canViewAllHistory(userId, requesterId, isAdmin)
        val visibilityScope = visibilityScope(userId, requesterId, isAdmin, canViewAll)
        return recordDao.findUserModeStats(userId, canViewAll, isAdmin, visibilityScope)
            ?: RecordUserModeStatsResponse(ok = false, code = 761, error = "game-server-unavailable")
    }
}
