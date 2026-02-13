package me.kkutuio.kkutuweb.record

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RecordService(
    @Autowired private val recordDao: RecordDao
) {
    fun findByGameId(gameId: String, includePayload: Boolean): RecordGameLookupResponse {
        return recordDao.findByGameId(gameId, includePayload)
            ?: RecordGameLookupResponse(ok = false, code = 503, error = "game-server-unavailable")
    }

    fun findUserHistory(userId: String, page: Int, pageSize: Int): RecordUserHistoryResponse {
        val safePage = if (page < 1) 1 else page
        val safePageSize = when (pageSize) {
            30, 50 -> pageSize
            else -> 10
        }
        return recordDao.findUserHistory(userId, safePage, safePageSize)
            ?: RecordUserHistoryResponse(ok = false, code = 503, error = "game-server-unavailable")
    }
}
