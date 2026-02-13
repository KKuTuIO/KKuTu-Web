package me.kkutuio.kkutuweb.record

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RecordDao(
    @Autowired private val recordClientManager: RecordClientManager,
    @Autowired private val recordMapper: RecordMapper
) {
    fun findByGameId(gameId: String, includePayload: Boolean): RecordGameLookupResponse? {
        val raw = recordClientManager.requestReplayByGameId(gameId, includePayload) ?: return null
        return recordMapper.toGameLookupResponse(raw)
    }

    fun findUserHistory(userId: String, page: Int, pageSize: Int): RecordUserHistoryResponse? {
        val raw = recordClientManager.requestReplayUserHistory(userId, page, pageSize) ?: return null
        return recordMapper.toUserHistoryResponse(raw)
    }
}
