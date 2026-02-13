package me.kkutuio.kkutuweb.record

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class RecordClientManager(
    @Autowired private val recordSocketBridge: RecordSocketBridge
) {
    fun requestReplayByGameId(gameId: String, includePayload: Boolean): String? {
        return recordSocketBridge.requestReplayByGameId(gameId, includePayload)
    }

    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int): String? {
        return recordSocketBridge.requestReplayUserHistory(userId, page, pageSize)
    }

    fun requestReplayUserModeStats(userId: String): String? {
        return recordSocketBridge.requestReplayUserModeStats(userId)
    }
}
