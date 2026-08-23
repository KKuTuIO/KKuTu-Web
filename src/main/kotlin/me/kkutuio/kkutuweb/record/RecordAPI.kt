package me.kkutuio.kkutuweb.record

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/replay")
class RecordAPI(
    @Autowired private val recordService: RecordService,
    @Autowired private val recordCheckRateLimiter: RecordCheckRateLimiter,
    @Autowired private val kKuTuSetting: KKuTuSetting,
    @Autowired private val loginService: LoginService
) {
    private fun setPublicImmutablePayloadCache(response: HttpServletResponse) {
        response.setHeader("Cache-Control", "public, max-age=31536000, s-maxage=31536000, immutable")
        response.setHeader("CDN-Cache-Control", "max-age=31536000, immutable")
    }

    private fun resolveRequester(session: HttpSession): Triple<String?, String?, Boolean> {
        if (session.isGuest()) return Triple(null, null, false)
        val userId = loginService.gameUserId(session) ?: return Triple(null, null, false)
        val accountUuid = loginService.accountUuid(session)
        val isAdmin = accountUuid?.let(kKuTuSetting.getAdminIds()::contains) == true
        return Triple(userId, accountUuid, isAdmin)
    }

    @RateLimiter(name = "recordFindByGameId", fallbackMethod = "onGameRateLimited")
    @GetMapping("/game/{gameId}")
    fun findByGameId(
        @PathVariable gameId: String,
        @RequestParam(defaultValue = "false") includePayload: Boolean,
        @RequestParam(defaultValue = "false") includeAdminKeyTrace: Boolean,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): RecordGameLookupResponse {
        response.setHeader("Cache-Control", "private, no-store")
        if (includePayload && !recordCheckRateLimiter.allowReplayPayloadDownload(request, session)) {
            return RecordGameLookupResponse(ok = false, code = 429, error = "payload-rate-limited")
        }
        if (!recordCheckRateLimiter.allow(request, session)) {
            return RecordGameLookupResponse(ok = false, code = 429, error = "rate-limited")
        }
        val (requesterId, requesterAccountUuid, isAdmin) = resolveRequester(session)
        return recordService.findByGameId(gameId, includePayload, includeAdminKeyTrace, requesterId, requesterAccountUuid, isAdmin)
    }

    /** Public replay payload, always using the game server's public redaction policy. */
    @RateLimiter(name = "recordFindByGameId", fallbackMethod = "onPublicPayloadRateLimited")
    @GetMapping("/game/{gameId}/payload")
    fun findPublicReplayPayload(
        @PathVariable gameId: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): RecordGameLookupResponse {
        if (!recordCheckRateLimiter.allowReplayPayloadDownload(request, session)) {
            response.setHeader("Cache-Control", "private, no-store")
            return RecordGameLookupResponse(ok = false, code = 429, error = "payload-rate-limited")
        }
        val result = recordService.findByGameId(gameId, true, false, null, null, false)
        if (result.ok && result.game != null) setPublicImmutablePayloadCache(response)
        else response.setHeader("Cache-Control", "private, no-store")
        return result
    }

    @RateLimiter(name = "recordFindUserHistory", fallbackMethod = "onUserHistoryRateLimited")
    @GetMapping("/user/{userId}")
    fun findUserHistory(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") pageSize: Int,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): RecordUserHistoryResponse {
        response.setHeader("Cache-Control", "private, no-store")
        if (!recordCheckRateLimiter.allow(request, session)) {
            return RecordUserHistoryResponse(ok = false, code = 429, error = "rate-limited")
        }
        val (requesterId, _, isAdmin) = resolveRequester(session)
        return recordService.findUserHistory(userId, page, pageSize, requesterId, isAdmin)
    }

    @RateLimiter(name = "recordFindUserModeStats", fallbackMethod = "onUserModeStatsRateLimited")
    @GetMapping("/user/{userId}/mode-stats")
    fun findUserModeStats(
        @PathVariable userId: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): RecordUserModeStatsResponse {
        response.setHeader("Cache-Control", "private, no-store")
        if (!recordCheckRateLimiter.allow(request, session)) {
            return RecordUserModeStatsResponse(ok = false, code = 429, error = "rate-limited")
        }
        val (requesterId, _, isAdmin) = resolveRequester(session)
        return recordService.findUserModeStats(userId, requesterId, isAdmin)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onGameRateLimited(
        gameId: String,
        includePayload: Boolean,
        includeAdminKeyTrace: Boolean,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordGameLookupResponse {
        return RecordGameLookupResponse(ok = false, code = 429, error = "rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPublicPayloadRateLimited(
        gameId: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordGameLookupResponse {
        return RecordGameLookupResponse(ok = false, code = 429, error = "payload-rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUserHistoryRateLimited(
        userId: String,
        page: Int,
        pageSize: Int,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordUserHistoryResponse {
        return RecordUserHistoryResponse(ok = false, code = 429, error = "rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onUserModeStatsRateLimited(
        userId: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordUserModeStatsResponse {
        return RecordUserModeStatsResponse(ok = false, code = 429, error = "rate-limited")
    }
}
