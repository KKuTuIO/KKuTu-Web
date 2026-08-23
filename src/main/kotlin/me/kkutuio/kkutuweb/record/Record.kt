package me.kkutuio.kkutuweb.record

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordGameLookupResponse(
    val type: String? = null,
    val requestId: String? = null,
    val ok: Boolean = false,
    val code: Int = 500,
    val error: String? = null,
    val game: RecordGame? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordGame(
    val gameId: String,
    val createdAt: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Int,
    val channel: Int,
    val roomId: Int,
    val mode: Int,
    val modeName: String = "UNKNOWN",
    val rule: String,
    val lang: String,
    val roomTitle: String,
    val playerCount: Int,
    val userIds: List<String>,
    val winnerIds: List<String>,
    val payloadCodec: String,
    val payloadVersion: Int,
    val payloadSize: Int,
    val rawSize: Int,
    val adminKeyTraceAvailable: Boolean = false,
    val payload: String? = null,
    val payloadDecoded: JsonNode? = null,
    val keyTraceDecoded: JsonNode? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordUserHistoryResponse(
    val type: String? = null,
    val requestId: String? = null,
    val ok: Boolean = false,
    val code: Int = 500,
    val error: String? = null,
    val history: List<RecordUserHistory> = emptyList(),
    val pagination: RecordPagination? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordUserHistory(
    val gameId: String,
    val startedAt: Long,
    val mode: Int,
    val modeName: String,
    val rule: String,
    val lang: String,
    val roomTitle: String,
    val placement: Int,
    val score: Int,
    val dim: Int,
    val playerCount: Int,
    val won: Boolean,
    val exp: Int,
    val money: Int,
    val playTime: Int,
    val ep: Int
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordPagination(
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordUserModeStatsResponse(
    val type: String? = null,
    val requestId: String? = null,
    val ok: Boolean = false,
    val code: Int = 500,
    val error: String? = null,
    val stats: List<RecordUserModeStat> = emptyList()
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecordUserModeStat(
    val mode: Int,
    val modeName: String,
    val games: Int,
    val wins: Int,
    val exp: Int,
    val playTime: Long,
    val acceptedWords: Int
)
