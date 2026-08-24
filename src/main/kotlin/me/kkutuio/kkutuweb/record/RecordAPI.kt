package me.kkutuio.kkutuweb.record

import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession

@RestController
@RequestMapping("/api/replay")
class RecordAPI(
    @Autowired private val recordService: RecordService,
    @Autowired private val recordCheckRateLimiter: RecordCheckRateLimiter,
    @Autowired private val kKuTuSetting: KKuTuSetting,
    @Autowired private val loginService: LoginService
) {
    private val detailPayloadFields = arrayOf("rm", "p", "w", "x", "i", "mv", "rs", "a")

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

    /**
     * Keep the accordion's display data separate from the replay artifact.
     * This avoids transferring the encoded payload until the replay button is used.
     */
    private fun projectGameDetail(result: RecordGameLookupResponse): RecordGameLookupResponse {
        val game = result.game ?: return result
        val payload = game.payloadDecoded
        if (payload == null || !payload.isObject) return result.copy(game = game.copy(payload = null, payloadDecoded = null))

        val detailPayload = (payload as ObjectNode).objectNode()
        detailPayloadFields.forEach { field ->
            payload.get(field)?.let { detailPayload.set<JsonNode>(field, it) }
        }
        return result.copy(game = game.copy(detailPayload = detailPayload, payload = null, payloadDecoded = null))
    }

    @RateLimiter(name = "recordFindByGameId", fallbackMethod = "onGameRateLimited")
    @GetMapping("/game/{gameId}")
    fun findByGameId(
        @PathVariable gameId: String,
        @RequestParam(defaultValue = "false") includePayload: Boolean,
        @RequestParam(defaultValue = "false") includeDetail: Boolean,
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
        val result = recordService.findByGameId(gameId, includePayload || includeDetail, includeAdminKeyTrace, requesterId, requesterAccountUuid, isAdmin)
        return if (includeDetail && !includePayload) projectGameDetail(result) else result
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

    /**
     * Administrator-only input trace.  This has its own URI so it can never
     * overlap the long-lived public replay-payload cache rule.
     */
    @RateLimiter(name = "recordFindByGameId", fallbackMethod = "onAdminKeyTraceRateLimited")
    @GetMapping("/admin/game/{gameId}/key-trace")
    fun findAdminKeyTrace(
        @PathVariable gameId: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): RecordGameLookupResponse {
        response.setHeader("Cache-Control", "private, no-store")
        response.setHeader("CDN-Cache-Control", "no-store")
        if (!recordCheckRateLimiter.allowReplayPayloadDownload(request, session)) {
            return RecordGameLookupResponse(ok = false, code = 429, error = "payload-rate-limited")
        }
        val (requesterId, requesterAccountUuid, isAdmin) = resolveRequester(session)
        if (!isAdmin) return RecordGameLookupResponse(ok = false, code = 403, error = "admin-required")

        // Older game servers return key traces only when includePayload is set.
        // Strip that payload before the response leaves the web tier.
        val result = recordService.findByGameId(gameId, true, true, requesterId, requesterAccountUuid, true)
        val game = result.game ?: return result
        return result.copy(game = game.copy(detailPayload = null, payload = null, payloadDecoded = null))
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
        includeDetail: Boolean,
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
        response.setHeader("Cache-Control", "private, no-store")
        response.setHeader("CDN-Cache-Control", "no-store")
        return RecordGameLookupResponse(ok = false, code = 429, error = "payload-rate-limited")
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAdminKeyTraceRateLimited(
        gameId: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession,
        throwable: RequestNotPermitted
    ): RecordGameLookupResponse {
        response.setHeader("Cache-Control", "private, no-store")
        response.setHeader("CDN-Cache-Control", "no-store")
        return RecordGameLookupResponse(ok = false, code = 429, error = "rate-limited")
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
