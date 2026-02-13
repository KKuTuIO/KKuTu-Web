package me.kkutuio.kkutuweb.record

import me.kkutuio.kkutuweb.config.CacheConfig
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component

@Component
class RecordDao(
    @Autowired private val recordClientManager: RecordClientManager,
    @Autowired private val recordMapper: RecordMapper,
    @Autowired private val userDao: UserDao
) {
    fun findByGameId(gameId: String, includePayload: Boolean, requesterId: String?): RecordGameLookupResponse? {
        val raw = recordClientManager.requestReplayByGameId(gameId, includePayload, requesterId) ?: return null
        return recordMapper.toGameLookupResponse(raw)
    }

    @Cacheable(
        value = [CacheConfig.RECORD_USER_INFO_CACHE],
        key = "'recordHistoryPublic:' + #userId"
    )
    fun isRecordHistoryPublic(userId: String): Boolean {
        val user = userDao.getUser(userId) ?: return true
        val flag = user.flags.path("recordHistoryPublic")
        if (flag.isMissingNode || flag.isNull) return true
        val value = flag.path("value")
        return when {
            value.isBoolean -> value.asBoolean()
            value.isNumber -> value.asInt() != 0
            value.isTextual -> {
                val normalized = value.asText("").trim().lowercase()
                when (normalized) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> true
                }
            }
            else -> true
        }
    }

    @Cacheable(
        value = [CacheConfig.RECORD_USER_HISTORY_CACHE],
        key = "#userId + ':' + #page + ':' + #pageSize + ':' + #visibilityScope",
        condition = "#isAdmin == false",
        unless = "#result == null || #result.ok == false"
    )
    fun findUserHistory(
        userId: String,
        page: Int,
        pageSize: Int,
        canViewAll: Boolean,
        isAdmin: Boolean,
        visibilityScope: String
    ): RecordUserHistoryResponse? {
        val raw = recordClientManager.requestReplayUserHistory(userId, page, pageSize, canViewAll) ?: return null
        return recordMapper.toUserHistoryResponse(raw)
    }

    @Cacheable(
        value = [CacheConfig.RECORD_USER_MODE_STATS_CACHE],
        key = "#userId + ':' + #visibilityScope",
        condition = "#isAdmin == false",
        unless = "#result == null || #result.ok == false"
    )
    fun findUserModeStats(
        userId: String,
        canViewAll: Boolean,
        isAdmin: Boolean,
        visibilityScope: String
    ): RecordUserModeStatsResponse? {
        val raw = recordClientManager.requestReplayUserModeStats(userId, canViewAll) ?: return null
        return recordMapper.toUserModeStatsResponse(raw)
    }
}
