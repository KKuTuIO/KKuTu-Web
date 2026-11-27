package me.kkutuio.kkutuweb.malmoi

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.AdminSetting
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class StateRequest(val state: String = "", val reason: String = "")
data class LockRequest(val locked: Boolean = true)
data class DecideRequest(val approve: Boolean = false, val reason: String = "")
data class OwnerRequest(val account: String? = null, val name: String? = null)
data class RevokeRequest(val account: String = "", val reason: String = "", val delayDays: Long = MalmoiRules.REISSUE_DELAY_DAYS)
data class ExtRequest(val theme: String = "", val lang: String = "ko", val title: String = "")
data class PromoteRequest(val approve: List<Long> = emptyList(), val reject: List<Long> = emptyList(), val reason: String = "")
data class ResolveRequest(val dismiss: Boolean = false)

/**
 * 말모이 백오피스: 목록과 조치(공개 상태, 잠금, 주인장), 공개 심사, 신고, 면허 취소, 확장팩 만들기, 승계.
 * 단어 관리 권한(`WORD`)이 있는 관리자만 쓴다. 모든 조치는 기록을 남긴다.
 */
@RestController
@RequestMapping("/api/admin/malmoi", produces = [MediaType.APPLICATION_JSON_VALUE])
class MalmoiAdminApi(
    private val dao: MalmoiAdminDao,
    private val books: MalmoiDao,
    private val setting: KKuTuSetting,
    private val login: LoginService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private companion object {
        val ALLOWED = setOf(AdminSetting.Privilege.WORD, AdminSetting.Privilege.GAME_SOURCE_MANAGE, AdminSetting.Privilege.ADMIN_MANAGE)
    }

    private fun admin(session: HttpSession): String {
        val account = login.accountUuid(session) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "통합계정 로그인이 필요합니다.")
        if (account !in setting.getAdminIds()) throw ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.")
        val a = setting.getAdmins().find { it.id == account } ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 설정을 찾을 수 없습니다.")
        // 임시: 시험하는 동안 개발팀(게임 소스·관리자 관리 권한)도 쓴다. 시험이 끝나면 WORD만 남긴다.
        if (a.privileges.none { it in ALLOWED }) throw ResponseStatusException(HttpStatus.FORBIDDEN, "단어 관리 권한이 필요합니다.")
        return account
    }

    private fun note(request: HttpServletRequest, who: String, what: String) = logger.info("[{}] {} 님이 말모이를 관리했습니다. {}", request.getIp(), who, what)
    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun book(id: Long) = books.findBook(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "없는 말모이예요.")

    @GetMapping
    fun list(@RequestParam(required = false) state: String?, @RequestParam(defaultValue = "") q: String, session: HttpSession): Map<String, Any?> {
        admin(session)
        return mapOf("items" to dao.books(state?.uppercase()?.takeIf { it in setOf("PUBLIC", "UNLISTED", "HIDDEN") }, q.trim().take(40), 200))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, session: HttpSession): Map<String, Any?> {
        admin(session)
        val b = book(id)
        books.settle(id)
        val (total, live) = books.wordCounts(id)
        return mapOf(
            "id" to b.id, "kind" to b.kind, "base" to b.baseTheme, "title" to b.title, "desc" to b.description, "state" to b.state, "locked" to b.locked,
            "owner" to b.ownerName, "creator" to b.creatorName, "words" to total, "live" to live, "registrants" to books.registrants(id), "criteria" to books.tree(b.criteria),
            "changes" to books.revisions(id, 50)
        )
    }

    /** 승계를 검토할 낱말(100개씩). */
    @GetMapping("/{id}/words")
    fun words(@PathVariable id: Long, @RequestParam(defaultValue = "0") page: Int, session: HttpSession): Map<String, Any?> {
        admin(session)
        book(id)
        books.settle(id)
        return mapOf("items" to dao.promotableWords(id, 100, page.coerceAtLeast(0) * 100))
    }

    @PutMapping("/{id}/state")
    @Transactional
    fun state(@PathVariable id: Long, @RequestBody body: StateRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val next = body.state.uppercase()
        if (next !in setOf("PUBLIC", "UNLISTED", "HIDDEN")) bad("상태가 올바르지 않아요.")
        val b = book(id)
        if (b.kind == "EXT" && next == "UNLISTED") bad("확장팩은 일부 공개로 바꿀 수 없어요.")
        dao.setState(id, next)
        books.log(id, null, "admin-state", "${b.state}→$next ${body.reason}".trim().take(80), null, "운영팀")
        note(request, who, "$id ${b.state} → $next")
        return mapOf("id" to id, "state" to next)
    }

    @PutMapping("/{id}/lock")
    @Transactional
    fun lock(@PathVariable id: Long, @RequestBody body: LockRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        book(id)
        dao.setLocked(id, body.locked)
        books.log(id, null, if (body.locked) "admin-lock" else "admin-unlock", "", null, "운영팀")
        note(request, who, "$id 잠금 ${body.locked}")
        return mapOf("id" to id, "locked" to body.locked)
    }

    /** 주인장을 바꾼다. `account`를 비우면 주인장이 없는 상태(운영팀 관리)가 된다. */
    @PutMapping("/{id}/owner")
    @Transactional
    fun owner(@PathVariable id: Long, @RequestBody body: OwnerRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val b = book(id)
        if (b.kind == "EXT") bad("확장팩에는 주인장이 없어요.")
        val owner = body.account?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() ?: bad("계정 번호가 올바르지 않아요.") }
        dao.setOwner(id, owner, if (owner == null) null else (body.name ?: "").trim().take(40))
        books.log(id, null, "admin-owner", "", null, "운영팀")
        note(request, who, "$id 주인장 ${owner ?: "없음"}")
        return mapOf("id" to id)
    }

    // ---- 공개 심사 ----

    @GetMapping("/reviews")
    fun reviews(@RequestParam(defaultValue = "PENDING") status: String, session: HttpSession): Map<String, Any?> {
        admin(session)
        return mapOf("items" to dao.reviews(status.uppercase()))
    }

    @PostMapping("/reviews/{reviewId}")
    @Transactional
    fun decide(@PathVariable reviewId: Long, @RequestBody body: DecideRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val id = dao.decideReview(reviewId, if (body.approve) "APPROVED" else "REJECTED", body.reason.trim().take(200), who) ?: throw ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 심사예요.")
        if (body.approve) dao.setState(id, "PUBLIC")
        books.log(id, null, "review", if (body.approve) "승인" else "반려 ${body.reason}".trim().take(80), null, "운영팀")
        note(request, who, "심사 $reviewId → ${if (body.approve) "승인" else "반려"}")
        return mapOf("id" to id, "approved" to body.approve)
    }

    // ---- 승계 신청(새 말모이) ----

    @GetMapping("/promotion-requests")
    fun promotionRequests(@RequestParam(defaultValue = "PENDING") status: String, session: HttpSession): Map<String, Any?> {
        admin(session)
        return mapOf("items" to dao.promotionRequests(status.uppercase()))
    }

    @PostMapping("/promotion-requests/{requestId}")
    @Transactional
    fun decidePromotionRequest(@PathVariable requestId: Long, @RequestBody body: DecideRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        if (dao.decidePromotionRequest(requestId, if (body.approve) "ACCEPTED" else "REJECTED", body.reason.trim().take(200), who) == 0) throw ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 신청이에요.")
        note(request, who, "승계 신청 $requestId → ${if (body.approve) "수락" else "반려"}")
        return mapOf("id" to requestId, "approved" to body.approve)
    }

    // ---- 신고 ----

    @GetMapping("/reports")
    fun reports(@RequestParam(defaultValue = "OPEN") status: String, session: HttpSession): Map<String, Any?> {
        admin(session)
        return mapOf("items" to dao.reports(status.uppercase(), 200))
    }

    @PostMapping("/reports/{reportId}")
    @Transactional
    fun resolve(@PathVariable reportId: Long, @RequestBody body: ResolveRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        if (dao.resolveReport(reportId, if (body.dismiss) "DISMISSED" else "DONE", who) == 0) throw ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 신고예요.")
        note(request, who, "신고 $reportId ${if (body.dismiss) "기각" else "처리"}")
        return mapOf("id" to reportId)
    }

    // ---- 면허 ----

    /** 면허를 취소한다: 낸 핑은 돌려주지 않고, 재발급은 유예 기간 뒤에 가능하다. */
    @PostMapping("/licenses/revoke")
    @Transactional
    fun revoke(@RequestBody body: RevokeRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val account = runCatching { UUID.fromString(body.account) }.getOrNull() ?: bad("계정 번호가 올바르지 않아요.")
        if (books.activeLicense(account) == null) throw ResponseStatusException(HttpStatus.CONFLICT, "유효한 면허가 없어요.")
        books.revokeLicense(account, body.reason.trim().take(120), body.delayDays.coerceIn(0, 365))
        note(request, who, "면허 취소 $account ${body.reason}")
        return mapOf("account" to account.toString())
    }

    // ---- 확장팩 ----

    /** 공식 주제 하나의 확장팩을 만든다(주제마다 하나). */
    @PostMapping("/ext")
    @Transactional
    fun createExt(@RequestBody body: ExtRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val theme = body.theme.trim().uppercase()
        if (!Regex("^[A-Z0-9]{2,8}$").matches(theme)) bad("주제 코드가 올바르지 않아요.")
        val title = body.title.trim().take(40).ifEmpty { bad("원 주제의 이름을 적어 주세요.") }
        val id = dao.createExt(theme, if (body.lang == "en") "en" else "ko", title) ?: throw ResponseStatusException(HttpStatus.CONFLICT, "이미 확장팩이 있어요.")
        note(request, who, "확장팩 $theme → $id")
        return mapOf("id" to id)
    }

    /** 공식 주제마다 확장팩을 만든다(이미 있는 주제는 건너뛴다). */
    @PostMapping("/ext/seed")
    @Transactional
    fun seedExt(request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val made = me.kkutuio.kkutuweb.word.WordTheme.entries.count { dao.createExt(it.themeCode, "ko", it.themeName) != null }
        note(request, who, "확장팩 일괄 생성 $made")
        return mapOf("created" to made, "themes" to me.kkutuio.kkutuweb.word.WordTheme.entries.size)
    }

    // ---- 승계 ----

    /**
     * 확장팩 낱말을 원 주제로 승계한다: 승인한 낱말은 공식 사전에 들어가거나(이미 있으면 주제 코드만 보탠다) 확장팩에서 빠지고,
     * 반려한 낱말은 사유와 함께 기록만 하고 확장팩에 남는다. 한 번에 한 트랜잭션이다. 게임 서버의 낱말 캐시는 따로 새로 고쳐야 반영된다.
     */
    @PostMapping("/{id}/promote")
    @Transactional
    fun promote(@PathVariable id: Long, @RequestBody body: PromoteRequest, request: HttpServletRequest, session: HttpSession): Map<String, Any?> {
        val who = admin(session)
        val b = book(id)
        if (b.kind != "EXT" || b.baseTheme == null) bad("확장팩만 승계할 수 있어요.")
        if (body.approve.size + body.reject.size > 500) bad("한 번에 500개까지 처리할 수 있어요.")
        var done = 0
        var existing = 0
        for (row in dao.promotable(id, body.approve)) {
            val word = row["word"] as String
            val had = dao.officialExists(b.lang, word)
            dao.addToOfficial(b.lang, word, (row["mean"] as String?) ?: "", b.baseTheme)
            dao.markPromoted((row["id"] as Number).toLong(), if (had) "EXISTING" else "APPROVED")
            dao.logPromotion(id, word, b.baseTheme, if (had) "EXISTING" else "APPROVED", "", who)
            if (had) existing++ else done++
        }
        for (row in dao.promotable(id, body.reject)) dao.logPromotion(id, row["word"] as String, b.baseTheme, "REJECTED", body.reason.trim().take(200), who)
        books.log(id, null, "promotion", "승인 $done, 기존 $existing, 반려 ${body.reject.size}", null, "운영팀")
        books.touch(id)
        note(request, who, "승계 $id 승인 $done 기존 $existing 반려 ${body.reject.size}")
        return mapOf("approved" to done, "existing" to existing, "rejected" to body.reject.size)
    }
}
