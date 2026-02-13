package me.kkutuio.kkutuweb.record

/**
 * Bridge used by replay APIs to query the game server over the existing
 * web-game socket stream.
 */
interface RecordSocketBridge {
    fun requestReplayByGameId(gameId: String, includePayload: Boolean): String?
    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int): String?
    fun requestReplayUserModeStats(userId: String): String?
}
