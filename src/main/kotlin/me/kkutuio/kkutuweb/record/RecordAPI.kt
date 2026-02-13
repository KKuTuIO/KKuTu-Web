package me.kkutuio.kkutuweb.record

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/replay")
class RecordAPI(
    @Autowired private val recordService: RecordService,
    @Autowired private val recordCheckRateLimiter: RecordCheckRateLimiter
) {
    @RateLimiter(name = "recordFindByGameId", fallbackMethod = "onGameRateLimited")
    @GetMapping("/game/{gameId}")
    fun findByGameId(
        @PathVariable gameId: String,
        @RequestParam(defaultValue = "false") includePayload: Boolean,
        request: HttpServletRequest,
        session: HttpSession
    ): RecordGameLookupResponse {
        if (!recordCheckRateLimiter.allow(request, session)) {
            return RecordGameLookupResponse(ok = false, code = 429, error = "rate-limited")
        }
        return recordService.findByGameId(gameId, includePayload)
    }

    @RateLimiter(name = "recordFindUserHistory", fallbackMethod = "onUserHistoryRateLimited")
    @GetMapping("/user/{userId}")
    fun findUserHistory(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") pageSize: Int,
        request: HttpServletRequest,
        session: HttpSession
    ): RecordUserHistoryResponse {
        if (!recordCheckRateLimiter.allow(request, session)) {
            return RecordUserHistoryResponse(ok = false, code = 429, error = "rate-limited")
        }
        return recordService.findUserHistory(userId, page, pageSize)
    }

    @RateLimiter(name = "recordFindUserModeStats", fallbackMethod = "onUserModeStatsRateLimited")
    @GetMapping("/user/{userId}/mode-stats")
    fun findUserModeStats(
        @PathVariable userId: String,
        request: HttpServletRequest,
        session: HttpSession
    ): RecordUserModeStatsResponse {
        if (!recordCheckRateLimiter.allow(request, session)) {
            return RecordUserModeStatsResponse(ok = false, code = 429, error = "rate-limited")
        }
        return recordService.findUserModeStats(userId)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onGameRateLimited(
        gameId: String,
        includePayload: Boolean,
        request: HttpServletRequest,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordGameLookupResponse {
        return RecordGameLookupResponse(ok = false, code = 429, error = "rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUserHistoryRateLimited(
        userId: String,
        page: Int,
        pageSize: Int,
        request: HttpServletRequest,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordUserHistoryResponse {
        return RecordUserHistoryResponse(ok = false, code = 429, error = "rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUserModeStatsRateLimited(
        userId: String,
        request: HttpServletRequest,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordUserModeStatsResponse {
        return RecordUserModeStatsResponse(ok = false, code = 429, error = "rate-limited")
    }
}
