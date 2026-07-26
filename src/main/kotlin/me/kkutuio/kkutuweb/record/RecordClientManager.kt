package me.kkutuio.kkutuweb.record

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RecordClientManager(
    @Autowired private val recordSocketBridge: RecordSocketBridge
) {
    fun requestReplayByGameId(
        gameId: String,
        includePayload: Boolean,
        requesterId: String?,
        includeAdminKeyTrace: Boolean
    ): String? {
        return recordSocketBridge.requestReplayByGameId(
            gameId,
            includePayload,
            requesterId,
            includeAdminKeyTrace
        )
    }

    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int, canViewAll: Boolean): String? {
        return recordSocketBridge.requestReplayUserHistory(userId, page, pageSize, canViewAll)
    }

    fun requestReplayUserModeStats(userId: String, canViewAll: Boolean): String? {
        return recordSocketBridge.requestReplayUserModeStats(userId, canViewAll)
    }
}
