package me.kkutuio.kkutuweb.record

/**
 * Bridge used by replay APIs to query the game server over the existing
 * web-game socket stream.
 */
interface RecordSocketBridge {
    fun requestReplayByGameId(
        gameId: String,
        includePayload: Boolean,
        requesterId: String?,
        includeAdminKeyTrace: Boolean
    ): String?
    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int, canViewAll: Boolean): String?
    fun requestReplayUserModeStats(userId: String, canViewAll: Boolean): String?
}
