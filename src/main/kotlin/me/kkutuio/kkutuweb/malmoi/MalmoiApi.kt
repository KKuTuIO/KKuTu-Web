package me.kkutuio.kkutuweb.malmoi

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.identity.AccountRateLimiter
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

data class OathRequest(val oath: List<String> = emptyList())
data class BlockRequest(val days: Long = 1, val reason: String = "")
data class NameRequest(val name: String = "")
data class ReportRequest(val word: String? = null, val reason: String = "", val detail: String = "")

/**
 * 말모이 (사용자가 모은 낱말). 로그인한 계정이면 누구나 쓴다. 신형 클라이언트(`/play`)의 말모이 메뉴가 부른다.
 * 쓰기에는 계정별 속도 제한을 둔다(하루 편집 한도와는 별개의 남용 방지).
 */
@RestController
@RequestMapping("/api/malmoi", produces = [MediaType.APPLICATION_JSON_VALUE])
class MalmoiApi(
    private val service: MalmoiService,
    private val admin: MalmoiAdminDao,
    private val limiter: AccountRateLimiter
) {
    private fun limit(session: HttpSession, what: String, max: Int, seconds: Long) {
        limiter.check("malmoi-$what:${service.me(session).account}", max, seconds)
    }

    @GetMapping
    fun list(@RequestParam(defaultValue = "browse") scope: String, @RequestParam(defaultValue = "") q: String, session: HttpSession) = service.list(session, scope, q)

    @PostMapping
    fun create(@RequestBody form: MalmoiService.BookForm, session: HttpSession): Map<String, Any?> { limit(session, "create", 10, 3600L); return service.create(session, form) }

    @GetMapping("/license")
    fun license(session: HttpSession) = service.license(session)

    @PostMapping("/license")
    fun issueLicense(@RequestBody body: OathRequest, session: HttpSession): Map<String, Any?> { limit(session, "license", 10, 3600L); return service.issueLicense(session, body.oath) }

    @PostMapping("/join/{token}")
    fun join(@PathVariable token: String, session: HttpSession): Map<String, Any?> { limit(session, "join", 60, 3600L); return service.joinByToken(session, token) }

    @GetMapping("/invitations")
    fun invitations(session: HttpSession) = service.invitations(session)

    @PostMapping("/{id}/invitation/accept")
    fun accept(@PathVariable id: Long, session: HttpSession) = service.answerInvitation(session, id, true)

    @PostMapping("/{id}/invitation/decline")
    fun decline(@PathVariable id: Long, session: HttpSession) = service.answerInvitation(session, id, false)

    @PostMapping("/{id}/editors")
    fun invite(@PathVariable id: Long, @RequestBody body: NameRequest, session: HttpSession): Map<String, Any?> { limit(session, "invite", 30, 3600L); return service.invite(session, id, body.name) }

    @PostMapping("/{id}/delegate")
    fun delegate(@PathVariable id: Long, @RequestBody body: NameRequest, session: HttpSession): Map<String, Any?> { limit(session, "delegate", 10, 3600L); return service.delegate(session, id, body.name) }

    @PostMapping("/{id}/invite/reissue")
    fun reissue(@PathVariable id: Long, session: HttpSession): Map<String, Any?> { limit(session, "reissue", 20, 3600L); return service.reissueInvite(session, id) }

    @PostMapping("/{id}/review")
    fun review(@PathVariable id: Long, session: HttpSession): Map<String, Any?> { limit(session, "review", 10, 3600L); return service.requestReview(session, id) }

    @PostMapping("/{id}/promotion")
    fun promotion(@PathVariable id: Long, session: HttpSession): Map<String, Any?> { limit(session, "promotion", 10, 3600L); return service.requestPromotion(session, id) }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, session: HttpSession) = service.detail(session, id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody form: MalmoiService.BookForm, session: HttpSession): Map<String, Any?> { limit(session, "update", 60, 3600L); return service.update(session, id, form) }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long, session: HttpSession): Map<String, Any?> { limit(session, "publish", 10, 3600L); return service.publish(session, id) }

    @PutMapping("/{id}/registration")
    fun register(@PathVariable id: Long, session: HttpSession): Map<String, Any?> { limit(session, "register", 120, 3600L); return service.register(session, id, true) }

    @DeleteMapping("/{id}/registration")
    fun unregister(@PathVariable id: Long, session: HttpSession): Map<String, Any?> { limit(session, "register", 120, 3600L); return service.register(session, id, false) }

    @GetMapping("/{id}/words")
    fun words(@PathVariable id: Long, @RequestParam(defaultValue = "") q: String, @RequestParam(defaultValue = "0") page: Int, session: HttpSession) = service.words(session, id, q, page)

    @PostMapping("/{id}/words")
    fun addWord(@PathVariable id: Long, @RequestBody form: MalmoiService.WordForm, session: HttpSession): Map<String, Any?> { limit(session, "word", 300, 3600L); return service.addWord(session, id, form) }

    @PutMapping("/{id}/words/{wordId}")
    fun editWord(@PathVariable id: Long, @PathVariable wordId: Long, @RequestBody form: MalmoiService.WordForm, session: HttpSession): Map<String, Any?> { limit(session, "word", 300, 3600L); return service.editWord(session, id, wordId, form) }

    @DeleteMapping("/{id}/words/{wordId}")
    fun removeWord(@PathVariable id: Long, @PathVariable wordId: Long, @RequestParam(defaultValue = "") reason: String, session: HttpSession): Map<String, Any?> { limit(session, "word", 300, 3600L); return service.removeWord(session, id, wordId, reason) }

    @PostMapping("/{id}/words/bulk")
    fun addWords(@PathVariable id: Long, @RequestBody form: MalmoiService.BulkForm, session: HttpSession): Map<String, Any?> { limit(session, "bulk", 30, 3600L); return service.addWords(session, id, form) }

    @PostMapping("/{id}/changes/{revisionId}/revert")
    fun revert(@PathVariable id: Long, @PathVariable revisionId: Long, session: HttpSession): Map<String, Any?> { limit(session, "revert", 120, 3600L); return service.revert(session, id, revisionId) }

    @PostMapping("/{id}/changes/{revisionId}/block")
    fun block(@PathVariable id: Long, @PathVariable revisionId: Long, @RequestBody body: BlockRequest, session: HttpSession): Map<String, Any?> { limit(session, "block", 60, 3600L); return service.block(session, id, revisionId, body.days, body.reason) }

    @DeleteMapping("/{id}/blocks")
    fun unblock(@PathVariable id: Long, @RequestParam name: String, session: HttpSession): Map<String, Any?> = service.unblock(session, id, name)

    /** 신고: 말모이 전체나 낱말 하나. 운영팀이 백오피스에서 처리한다. */
    @PostMapping("/{id}/reports")
    fun report(@PathVariable id: Long, @RequestBody body: ReportRequest, session: HttpSession): Map<String, Any?> {
        limit(session, "report", 30, 3600L)
        service.reportable(session, id).also { admin.addReport(id, body.word?.trim()?.take(80)?.ifEmpty { null }, it, body.reason.trim().take(80).ifEmpty { throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "사유를 골라 주세요.") }, body.detail.trim().take(300)) }
        return mapOf("status" to "reported")
    }

    @GetMapping("/{id}/changes")
    fun changes(@PathVariable id: Long, session: HttpSession) = service.changes(session, id)

    @GetMapping("/{id}/contrib")
    fun contrib(@PathVariable id: Long, @RequestParam(defaultValue = "182") days: Int, session: HttpSession) = service.contrib(session, id, days)
}
