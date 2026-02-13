package me.kkutuio.kkutuweb.record

import me.kkutuio.kkutuweb.config.CacheConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
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

    @Cacheable(
        value = [CacheConfig.RECORD_USER_HISTORY_CACHE],
        key = "#userId + ':' + #page + ':' + #pageSize",
        unless = "#result == null || #result.ok == false"
    )
    fun findUserHistory(userId: String, page: Int, pageSize: Int): RecordUserHistoryResponse? {
        val raw = recordClientManager.requestReplayUserHistory(userId, page, pageSize) ?: return null
        return recordMapper.toUserHistoryResponse(raw)
    }

    @Cacheable(
        value = [CacheConfig.RECORD_USER_MODE_STATS_CACHE],
        key = "#userId",
        unless = "#result == null || #result.ok == false"
    )
    fun findUserModeStats(userId: String): RecordUserModeStatsResponse? {
        val raw = recordClientManager.requestReplayUserModeStats(userId) ?: return null
        return recordMapper.toUserModeStatsResponse(raw)
    }
}
