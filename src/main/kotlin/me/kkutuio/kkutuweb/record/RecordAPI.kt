package me.kkutuio.kkutuweb.record

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/replay")
class RecordAPI(
    @Autowired private val recordService: RecordService
) {
    @RateLimiter(name = "recordFindByGameId", fallbackMethod = "onGameRateLimited")
    @GetMapping("/game/{gameId}")
    fun findByGameId(
        @PathVariable gameId: String,
        @RequestParam(defaultValue = "false") includePayload: Boolean
    ): RecordGameLookupResponse {
        return recordService.findByGameId(gameId, includePayload)
    }

    @RateLimiter(name = "recordFindUserHistory", fallbackMethod = "onUserHistoryRateLimited")
    @GetMapping("/user/{userId}")
    fun findUserHistory(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") pageSize: Int
    ): RecordUserHistoryResponse {
        return recordService.findUserHistory(userId, page, pageSize)
    }

    @RateLimiter(name = "recordFindUserModeStats", fallbackMethod = "onUserModeStatsRateLimited")
    @GetMapping("/user/{userId}/mode-stats")
    fun findUserModeStats(
        @PathVariable userId: String
    ): RecordUserModeStatsResponse {
        return recordService.findUserModeStats(userId)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onGameRateLimited(
        gameId: String,
        includePayload: Boolean,
        throwable: RequestNotPermitted
    ): RecordGameLookupResponse {
        return RecordGameLookupResponse(ok = false, code = 429, error = "rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUserHistoryRateLimited(
        userId: String,
        page: Int,
        pageSize: Int,
        throwable: RequestNotPermitted
    ): RecordUserHistoryResponse {
        return RecordUserHistoryResponse(ok = false, code = 429, error = "rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUserModeStatsRateLimited(
        userId: String,
        throwable: RequestNotPermitted
    ): RecordUserModeStatsResponse {
        return RecordUserModeStatsResponse(ok = false, code = 429, error = "rate-limited")
    }
}
