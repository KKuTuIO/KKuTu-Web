package me.kkutuio.kkutuweb.malmoi

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.help.HelpService
import me.kkutuio.kkutuweb.identity.AccountService
import me.kkutuio.kkutuweb.login.LoginService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** 누가 요청하는가: 계정, 쓰고 있는 프로필, 별명과 레벨. */
data class Me(val account: UUID, val profile: String, val name: String, val level: Int)

private fun fail(status: HttpStatus, message: String): Nothing = throw ResponseStatusException(status, message)

/**
 * 말모이: 만들기, 낱말, 등록, 면허, 기여. 규칙은 [MalmoiRules].
 * 로그인한 계정이면 누구나 쓴다(손님은 계정이 없어 401). 쓰기는 모두 한 트랜잭션이다.
 */
@Service
class MalmoiService(
    @Autowired private val dao: MalmoiDao,
    @Autowired private val accounts: AccountService,
    @Autowired private val login: LoginService,
    @Autowired private val help: HelpService
) {
    private val random = SecureRandom()

    fun me(session: HttpSession): Me {
        val account = accounts.requireCurrentAccount(session).also(accounts::requireLoginAllowed)
        val profile = login.gameUserId(session) ?: fail(HttpStatus.UNAUTHORIZED, "프로필이 필요해요.")
        return Me(account.uuid, profile, dao.nicknameOf(profile), levelOf(dao.scoreOf(profile)))
    }

    /** 레벨 = 1 + 끝낸 레벨의 수(누적 경험치가 점수 이하인 레벨). */
    private fun levelOf(score: Long): Int = 1 + help.getLevels().count { (it.totalExp ?: Long.MAX_VALUE) <= score }

    private fun book(id: Long): Book = dao.findBook(id)?.also { dao.settle(id) } ?: fail(HttpStatus.NOT_FOUND, "없는 말모이예요.")

    /** 일부 공개 말모이는 만든 사람, 편집자, 등록한 사람만 본다(링크를 받은 사람은 등록하면 보인다). 공개는 누구나. */
    private fun visible(me: Me, b: Book): Boolean = b.state == "PUBLIC" || b.state == "UNLISTED" && (b.creator == me.account || b.owner == me.account || dao.isEditor(me.account, b.id) || dao.isRegistered(me.account, b.id))

    private fun requireVisible(me: Me, id: Long): Book = book(id).also { if (!visible(me, it)) fail(HttpStatus.NOT_FOUND, "없는 말모이예요.") }

    // ---- rights ----

    /** 상위 기여자(작성 계정): 남은 지 30일 넘은 낱말의 지분이 기준 이상인 사람. 새 말모이는 30일 동안 주인장과 운영팀만 관리한다. */
    fun topContributors(id: Long): List<Pair<UUID?, String>> {
        val rows = dao.survivors(id)
        val total = rows.sumOf { it.third }
        if (total <= 0) return emptyList()
        return rows.filter { it.third.toDouble() / total >= MalmoiRules.TOP_SHARE }.map { it.first to it.second }
    }

    private fun manages(me: Me, b: Book): Boolean = b.owner == me.account || topContributors(b.id).any { it.first == me.account }

    private fun requireEditing(me: Me, b: Book) {
        dao.blockedUntil(me.account, b.id)?.let { fail(HttpStatus.FORBIDDEN, "이 말모이에서 차단되어 있어요.") }
        if (b.locked) fail(HttpStatus.FORBIDDEN, "운영팀이 잠근 말모이예요.")
        if (b.state == "HIDDEN") fail(HttpStatus.FORBIDDEN, "운영팀이 관리 중인 말모이예요.")
        if (b.state == "UNLISTED") {
            if (!(b.owner == me.account || dao.isEditor(me.account, b.id))) fail(HttpStatus.FORBIDDEN, "초대받은 편집자만 고칠 수 있어요.")
        } else {
            if (dao.activeLicense(me.account) == null) fail(HttpStatus.FORBIDDEN, "공개 말모이를 고치려면 말모이 집필 면허가 필요해요.")
            if (dao.editsToday(me.account) >= MalmoiRules.DAILY_EDITS) fail(HttpStatus.TOO_MANY_REQUESTS, "하루 ${MalmoiRules.DAILY_EDITS}회까지 고칠 수 있어요.")
        }
    }

    /** 신고하는 계정(보이는 말모이만 신고할 수 있다). */
    fun reportable(session: HttpSession, id: Long): UUID { val me = me(session); requireVisible(me, id); return me.account }

    // ---- views ----

    private fun cover(b: Book): Any = dao.tree(b.cover).takeIf { it.has("icon") } ?: MalmoiRules.defaultCover(b.kind)

    private fun summary(me: Me, b: Book, registered: Set<Long>): Map<String, Any?> {
        val (total, live) = dao.wordCounts(b.id)
        return linkedMapOf(
            "id" to b.id, "kind" to b.kind.lowercase(), "base" to b.baseTheme, "title" to b.title, "desc" to b.description, "cover" to cover(b), "state" to b.state.lowercase(),
            "owner" to b.ownerName, "words" to total, "live" to live, "registrants" to dao.registrants(b.id), "registered" to (b.id in registered),
            "updatedAt" to b.updatedAt.toEpochMilli(), "preview" to dao.previewWords(b.id, 3), "mine" to (b.owner == me.account)
        )
    }

    fun list(session: HttpSession, scope: String, query: String): Map<String, Any?> {
        val me = me(session)
        val registered = dao.registeredIds(me.account)
        val books = dao.list(me.account, scope, query.trim().take(40), 100)
        return mapOf("items" to books.map { summary(me, it, registered) })
    }

    fun detail(session: HttpSession, id: Long): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        val tops = topContributors(id)
        return summary(me, b, dao.registeredIds(me.account)) + mapOf(
            "criteria" to dao.tree(b.criteria), "creator" to b.creatorName, "tops" to tops.map { it.second }, "manages" to manages(me, b),
            "editor" to (b.owner == me.account || dao.isEditor(me.account, id)),
            "editors" to if (b.owner == me.account) dao.editors(id).map { mapOf("name" to it["name"], "accepted" to it["accepted"]) } else emptyList<Any>(),
            "review" to dao.lastReview(id)?.let { (it["status"] as String).lowercase() }.orEmpty(),
            "promotion" to dao.lastPromotion(id)?.let { (it["status"] as String).lowercase() }.orEmpty(),
            "blocks" to if (manages(me, b)) dao.blocks(id).map { mapOf("name" to it["name"], "reason" to it["reason"], "until" to (it["until_at"] as java.sql.Timestamp).time) } else emptyList<Any>(),
            "invite" to if (b.state == "UNLISTED" && (b.owner == me.account || dao.isEditor(me.account, id))) b.inviteToken else null,
            "locked" to b.locked, "createdAt" to b.createdAt.toEpochMilli()
        )
    }

    fun words(session: HttpSession, id: Long, query: String, page: Int): Map<String, Any?> {
        val me = me(session)
        requireVisible(me, id)
        val rows = dao.words(id, query.trim().take(40), 50, page.coerceAtLeast(0) * 50)
        return mapOf("items" to rows.map { w ->
            linkedMapOf("id" to w.id, "text" to w.word, "meanings" to dao.tree(w.meanings), "source" to w.source, "by" to w.authorName, "appliesAt" to w.appliesAt.toEpochMilli(),
                "removeAt" to w.removeAt?.toEpochMilli(), "next" to dao.tree(w.pending).takeIf { !it.isNull }, "promotion" to w.promotion)
        }, "page" to page)
    }

    fun changes(session: HttpSession, id: Long): Map<String, Any?> {
        val me = me(session)
        requireVisible(me, id)
        return mapOf("items" to dao.revisions(id, 100).map { r ->
            linkedMapOf("id" to r["id"], "word" to r["word"], "type" to r["kind"], "reason" to r["reason"], "who" to r["actor_name"], "at" to (r["created_at"] as java.sql.Timestamp).time, "reverted" to (r["reverted_at"] != null))
        })
    }

    /** 기여: 최근 [days]일의 날짜별 활동과 사람별 지분(남은 지 30일 넘은 낱말 기준). */
    fun contrib(session: HttpSession, id: Long, days: Int): Map<String, Any?> {
        val me = me(session)
        requireVisible(me, id)
        val rows = dao.survivors(id)
        val total = rows.sumOf { it.third }
        return mapOf(
            "activity" to dao.activity(id, days.coerceIn(7, 366)).map { mapOf("day" to it.first, "who" to it.second, "n" to it.third) },
            "people" to rows.map { mapOf("name" to it.second, "count" to it.third, "share" to if (total > 0) it.third.toDouble() / total else 0.0, "top" to (total > 0 && it.third.toDouble() / total >= MalmoiRules.TOP_SHARE)) },
            "total" to total, "topShare" to MalmoiRules.TOP_SHARE, "topDays" to MalmoiRules.TOP_DAYS
        )
    }

    // ---- writes ----

    private fun token(): String = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    data class BookForm(val title: String = "", val desc: String = "", val cover: Map<String, Any?> = emptyMap(), val criteria: Map<String, Any?> = emptyMap(), val state: String = "unlisted")

    private fun clean(form: BookForm): BookForm {
        val title = form.title.trim()
        if (title.length !in 1..40) fail(HttpStatus.BAD_REQUEST, "이름은 1~40자로 적어 주세요.")
        if (form.desc.length > 200) fail(HttpStatus.BAD_REQUEST, "설명은 200자까지 적을 수 있어요.")
        return form.copy(title = title, desc = form.desc.trim())
    }

    @Transactional
    fun create(session: HttpSession, form: BookForm): Map<String, Any?> {
        val me = me(session)
        val f = clean(form)
        val wantsPublic = f.state.equals("public", true)
        if (wantsPublic) {
            if (me.level < MalmoiRules.PUBLISH_LEVEL) fail(HttpStatus.FORBIDDEN, "공개 말모이는 레벨 ${MalmoiRules.PUBLISH_LEVEL}부터 만들 수 있어요.")
            if (dao.activeLicense(me.account) == null) fail(HttpStatus.FORBIDDEN, "공개 말모이를 만들려면 말모이 집필 면허가 필요해요.")
        } else if (dao.unlistedCount(me.account) >= MalmoiRules.UNLISTED_LIMIT) fail(HttpStatus.CONFLICT, "일부 공개 말모이는 ${MalmoiRules.UNLISTED_LIMIT}권까지 만들 수 있어요.")
        if (dao.titleTaken(f.title)) fail(HttpStatus.CONFLICT, "같은 이름의 말모이가 있어요.")
        val id = dao.createBook("NEW", null, f.title, f.desc, dao.json(f.criteria), dao.json(f.cover), me.account, me.name, if (wantsPublic) "PUBLIC" else "UNLISTED", token())
        dao.register(me.account, id)
        dao.log(id, null, "create", "", me.account, me.name)
        return mapOf("id" to id)
    }

    @Transactional
    fun update(session: HttpSession, id: Long, form: BookForm): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        if (b.owner != me.account) fail(HttpStatus.FORBIDDEN, "주인장만 고칠 수 있어요.")
        if (b.kind == "EXT") fail(HttpStatus.FORBIDDEN, "확장팩은 고칠 수 없어요.")
        val f = clean(form)
        if (!f.title.equals(b.title, true) && dao.titleTaken(f.title)) fail(HttpStatus.CONFLICT, "같은 이름의 말모이가 있어요.")
        dao.updateBook(id, f.title, f.desc, dao.json(f.criteria), dao.json(f.cover))
        dao.log(id, null, "settings", "", me.account, me.name)
        return mapOf("id" to id)
    }

    /** 일부 공개 → 공개는 레벨과 면허가 필요하고, 한 번 공개하면 일부 공개로 되돌리지 않는다. */
    @Transactional
    fun publish(session: HttpSession, id: Long): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        if (b.owner != me.account) fail(HttpStatus.FORBIDDEN, "주인장만 바꿀 수 있어요.")
        if (b.state != "UNLISTED") fail(HttpStatus.CONFLICT, "이미 공개된 말모이예요.")
        if (me.level < MalmoiRules.PUBLISH_LEVEL) fail(HttpStatus.FORBIDDEN, "공개는 레벨 ${MalmoiRules.PUBLISH_LEVEL}부터예요.")
        if (dao.activeLicense(me.account) == null) fail(HttpStatus.FORBIDDEN, "공개하려면 말모이 집필 면허가 필요해요.")
        dao.setState(id, "PUBLIC")
        dao.log(id, null, "publish", "", me.account, me.name)
        return mapOf("id" to id, "state" to "public")
    }

    @Transactional
    fun register(session: HttpSession, id: Long, on: Boolean): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        if (on) dao.register(me.account, b.id) else dao.unregister(me.account, b.id)
        return mapOf("id" to id, "registered" to on, "registrants" to dao.registrants(id))
    }

    /** 초대 링크로 등록한다(일부 공개 말모이는 이 링크로만 등록할 수 있다). */
    @Transactional
    fun joinByToken(session: HttpSession, token: String): Map<String, Any?> {
        val me = me(session)
        val b = dao.findByToken(token) ?: fail(HttpStatus.NOT_FOUND, "없는 링크예요.")
        if (b.state == "HIDDEN") fail(HttpStatus.NOT_FOUND, "없는 링크예요.")
        dao.register(me.account, b.id)
        return mapOf("id" to b.id)
    }

    data class WordForm(val text: String = "", val meanings: List<String> = emptyList(), val source: String = "")

    private fun cleanMeanings(list: List<String>): List<String> = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(5).onEach { if (it.length > 200) fail(HttpStatus.BAD_REQUEST, "뜻은 200자까지 적을 수 있어요.") }

    @Transactional
    fun addWord(session: HttpSession, id: Long, form: WordForm): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        requireEditing(me, b)
        val text = MalmoiRules.normalize(form.text)
        MalmoiRules.wordError(text)?.let { fail(HttpStatus.BAD_REQUEST, it) }
        if (b.kind == "EXT" && b.baseTheme != null && dao.inOfficialTheme(b.lang, text, b.baseTheme)) fail(HttpStatus.CONFLICT, "원 주제에 이미 있어요.")
        val meanings = cleanMeanings(form.meanings)
        val source = form.source.trim().take(200)
        val existing = dao.findWordText(id, text)
        val result = if (existing != null && existing.removedAt == null) {
            // 이미 있는 낱말: 새 뜻이면 뜻을 더하고, 같으면 아무것도 하지 않는다
            val have = dao.strings(existing.meanings)
            val added = meanings.filter { it !in have }
            if (added.isEmpty()) return mapOf("status" to "dup", "id" to existing.id)
            applyChange(b, existing, dao.json(have + added), if (source.isNotEmpty()) source else existing.source)
            dao.log(id, text, "edit", "", me.account, me.name)
            "meaning"
        } else {
            val appliesAt = if (b.state == "PUBLIC") Instant.now().plus(Duration.ofHours(MalmoiRules.APPLY_HOURS)) else Instant.now()
            dao.addWord(id, text, dao.json(meanings), source, me.account, me.name, appliesAt)
            dao.log(id, text, "add", "", me.account, me.name)
            "add"
        }
        dao.touch(id)
        return mapOf("status" to result)
    }

    /** 이미 게임에 반영된 낱말의 수정은 24시간 기다리고(그동안 예전 것이 쓰인다), 아직 반영 전이거나 일부 공개면 바로 바뀐다. */
    private fun applyChange(b: Book, w: WordRow, meaningsJson: String, source: String) {
        if (b.state == "PUBLIC" && w.appliesAt <= Instant.now()) {
            val at = Instant.now().plus(Duration.ofHours(MalmoiRules.APPLY_HOURS))
            dao.setPending(w.id, dao.json(mapOf("meanings" to dao.tree(meaningsJson), "source" to source, "applies_at" to at.toString())))
        } else dao.setWord(w.id, meaningsJson, source)
    }

    @Transactional
    fun editWord(session: HttpSession, id: Long, wordId: Long, form: WordForm): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        requireEditing(me, b)
        val w = dao.findWord(id, wordId)?.takeIf { it.removedAt == null } ?: fail(HttpStatus.NOT_FOUND, "없는 낱말이에요.")
        if (w.locked && !manages(me, b)) fail(HttpStatus.FORBIDDEN, "잠긴 낱말이에요.")
        applyChange(b, w, dao.json(cleanMeanings(form.meanings)), form.source.trim().take(200))
        dao.log(id, w.word, "edit", "", me.account, me.name)
        dao.touch(id)
        return mapOf("status" to "edit")
    }

    /** 관리하는 사람(주인장, 상위 기여자)은 바로 지우고, 나머지는 24시간 뒤에 지워진다. */
    @Transactional
    fun removeWord(session: HttpSession, id: Long, wordId: Long, reason: String): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        requireEditing(me, b)
        val w = dao.findWord(id, wordId)?.takeIf { it.removedAt == null } ?: fail(HttpStatus.NOT_FOUND, "없는 낱말이에요.")
        if (b.state == "PUBLIC" && manages(me, b) || b.state == "UNLISTED" || w.appliesAt > Instant.now()) dao.removeNow(w.id)
        else dao.removeLater(w.id, Instant.now().plus(Duration.ofHours(MalmoiRules.APPLY_HOURS)))
        dao.log(id, w.word, "remove", reason.trim().take(80), me.account, me.name)
        dao.touch(id)
        return mapOf("status" to "remove")
    }

    data class Row(val text: String = "", val mean: String = "", val source: String = "")
    data class BulkForm(val rows: List<Row> = emptyList())

    /** 여러 개 추가: 낱말마다 [addWord]와 같은 규칙이고, 하루 한도에 닿으면 거기서 멈춘다. 한 번에 500줄까지. */
    @Transactional
    fun addWords(session: HttpSession, id: Long, form: BulkForm): Map<String, Any?> {
        if (form.rows.size > 500) fail(HttpStatus.BAD_REQUEST, "한 번에 500줄까지 올릴 수 있어요.")
        var added = 0; var meaning = 0; var dup = 0; var failed = 0; var stopped = false
        for (r in form.rows) {
            try {
                val res = addWord(session, id, WordForm(r.text, if (r.mean.isBlank()) emptyList() else listOf(r.mean), r.source))
                when (res["status"]) { "add" -> added++; "meaning" -> meaning++; else -> dup++ }
            } catch (e: ResponseStatusException) {
                if (e.statusCode == HttpStatus.TOO_MANY_REQUESTS || e.statusCode == HttpStatus.FORBIDDEN) { stopped = true; break }
                failed++
            }
        }
        return mapOf("added" to added, "meaning" to meaning, "dup" to dup, "failed" to failed, "stopped" to stopped)
    }

    /** 되돌리기(관리하는 사람): 추가는 지우고, 삭제는 되살린다. */
    @Transactional
    fun revert(session: HttpSession, id: Long, revisionId: Long): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        if (!manages(me, b)) fail(HttpStatus.FORBIDDEN, "주인장이나 상위 기여자만 되돌릴 수 있어요.")
        val rev = dao.findRevision(id, revisionId) ?: fail(HttpStatus.NOT_FOUND, "없는 변경이에요.")
        if (rev["reverted_at"] != null) fail(HttpStatus.CONFLICT, "이미 되돌렸어요.")
        val word = rev["word"] as String? ?: fail(HttpStatus.BAD_REQUEST, "되돌릴 수 없는 변경이에요.")
        when (rev["kind"]) {
            "add" -> dao.removeWordNow(id, word)
            "remove" -> {
                if (b.kind == "EXT" && b.baseTheme != null && dao.inOfficialTheme(b.lang, word, b.baseTheme)) fail(HttpStatus.CONFLICT, "원 주제에 이미 있어요.")
                dao.restoreWord(id, word)
            }
            else -> fail(HttpStatus.BAD_REQUEST, "되돌릴 수 없는 변경이에요.")
        }
        dao.markReverted(revisionId)
        dao.log(id, word, "revert", "", me.account, me.name)
        dao.touch(id)
        return mapOf("status" to "revert")
    }

    /** 이 말모이 안에서만 최대 [MalmoiRules.BLOCK_MAX_DAYS]일 차단한다. 주인장, 상위 기여자, 본인은 차단할 수 없다. */
    @Transactional
    fun block(session: HttpSession, id: Long, revisionId: Long, days: Long, reason: String): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        if (!manages(me, b)) fail(HttpStatus.FORBIDDEN, "주인장이나 상위 기여자만 차단할 수 있어요.")
        val rev = dao.findRevision(id, revisionId) ?: fail(HttpStatus.NOT_FOUND, "없는 변경이에요.")
        val target = (rev["actor_uuid"] as String?)?.let(UUID::fromString) ?: fail(HttpStatus.BAD_REQUEST, "차단할 수 없는 변경이에요.")
        if (target == me.account || target == b.owner || topContributors(id).any { it.first == target }) fail(HttpStatus.FORBIDDEN, "이 사람은 차단할 수 없어요. 운영팀에 알려 주세요.")
        dao.block(id, target, (rev["actor_name"] as String?) ?: "", reason.trim().take(120), me.account, days.coerceIn(1, MalmoiRules.BLOCK_MAX_DAYS))
        dao.log(id, null, "block", "${rev["actor_name"]} ${days.coerceIn(1, MalmoiRules.BLOCK_MAX_DAYS)}일", me.account, me.name)
        return mapOf("status" to "blocked")
    }

    @Transactional
    fun unblock(session: HttpSession, id: Long, name: String): Map<String, Any?> {
        val me = me(session)
        val b = requireVisible(me, id)
        if (!manages(me, b)) fail(HttpStatus.FORBIDDEN, "주인장이나 상위 기여자만 풀 수 있어요.")
        dao.unblock(id, name)
        return mapOf("status" to "unblocked")
    }

    // ---- editors, delegation, link, applications ----

    private fun requireOwner(me: Me, id: Long): Book {
        val b = requireVisible(me, id)
        if (b.owner != me.account) fail(HttpStatus.FORBIDDEN, "주인장만 할 수 있어요.")
        return b
    }

    /** 일부 공개 말모이에 편집자를 초대한다(별명으로). 초대받은 사람이 수락해야 고칠 수 있다. */
    @Transactional
    fun invite(session: HttpSession, id: Long, nickname: String): Map<String, Any?> {
        val me = me(session)
        val b = requireOwner(me, id)
        if (b.state != "UNLISTED") fail(HttpStatus.CONFLICT, "일부 공개 말모이만 편집자를 초대할 수 있어요.")
        val target = dao.accountByNickname(nickname.trim()) ?: fail(HttpStatus.NOT_FOUND, "그런 별명의 사람을 찾지 못했어요.")
        if (target.first == me.account) fail(HttpStatus.BAD_REQUEST, "본인은 초대할 수 없어요.")
        dao.invite(id, target.first, target.second)
        dao.log(id, null, "invite", target.second, me.account, me.name)
        return mapOf("status" to "invited")
    }

    fun invitations(session: HttpSession): Map<String, Any?> {
        val me = me(session)
        return mapOf("items" to dao.invitations(me.account).map { mapOf("id" to it["id"], "title" to it["title"], "owner" to it["owner_name"]) })
    }

    @Transactional
    fun answerInvitation(session: HttpSession, id: Long, accept: Boolean): Map<String, Any?> {
        val me = me(session)
        val n = if (accept) dao.accept(id, me.account) else dao.leaveEditor(id, me.account)
        if (n == 0) fail(HttpStatus.NOT_FOUND, "받은 초대가 없어요.")
        if (accept) dao.register(me.account, id)
        return mapOf("status" to if (accept) "accepted" else "declined")
    }

    /** 주인장을 등록한 사람에게 넘긴다: 공개 말모이라면 그 사람이 면허를 가지고 있어야 한다. */
    @Transactional
    fun delegate(session: HttpSession, id: Long, nickname: String): Map<String, Any?> {
        val me = me(session)
        val b = requireOwner(me, id)
        if (b.kind == "EXT") fail(HttpStatus.FORBIDDEN, "확장팩에는 주인장이 없어요.")
        val target = dao.accountByNickname(nickname.trim()) ?: fail(HttpStatus.NOT_FOUND, "그런 별명의 사람을 찾지 못했어요.")
        if (target.first == me.account) fail(HttpStatus.BAD_REQUEST, "이미 주인장이에요.")
        if (!dao.isRegistered(target.first, id)) fail(HttpStatus.CONFLICT, "이 말모이에 등록한 사람에게만 넘길 수 있어요.")
        if (b.state == "PUBLIC" && dao.activeLicense(target.first) == null) fail(HttpStatus.CONFLICT, "면허가 있는 사람에게만 넘길 수 있어요.")
        dao.setOwner(id, target.first, target.second)
        dao.log(id, null, "delegate", target.second, me.account, me.name)
        return mapOf("status" to "delegated")
    }

    @Transactional
    fun reissueInvite(session: HttpSession, id: Long): Map<String, Any?> {
        val me = me(session)
        val b = requireOwner(me, id)
        if (b.state != "UNLISTED") fail(HttpStatus.CONFLICT, "일부 공개 말모이만 초대 링크가 있어요.")
        val t = token()
        dao.newToken(id, t)
        dao.log(id, null, "link", "", me.account, me.name)
        return mapOf("invite" to t)
    }

    private fun criteriaFilled(b: Book): Boolean = dao.tree(b.criteria).let { c -> listOf("detail", "include", "source").all { !c.path(it).asString("").isBlank() } }

    /** 레벨 30 미만의 공개: 편집자 3명이 수락하고 면허가 있고 기준을 적었을 때 운영팀에 심사를 신청한다. */
    @Transactional
    fun requestReview(session: HttpSession, id: Long): Map<String, Any?> {
        val me = me(session)
        val b = requireOwner(me, id)
        if (b.state != "UNLISTED") fail(HttpStatus.CONFLICT, "일부 공개 말모이만 신청할 수 있어요.")
        if (me.level >= MalmoiRules.PUBLISH_LEVEL) fail(HttpStatus.CONFLICT, "레벨 ${MalmoiRules.PUBLISH_LEVEL} 이상은 심사 없이 공개할 수 있어요.")
        if (dao.activeLicense(me.account) == null) fail(HttpStatus.FORBIDDEN, "면허가 필요해요.")
        if (dao.editorCount(id) < MalmoiRules.EDITORS) fail(HttpStatus.CONFLICT, "수락한 편집자가 ${MalmoiRules.EDITORS}명 필요해요.")
        if (!criteriaFilled(b)) fail(HttpStatus.CONFLICT, "낱말 기준 세 칸을 적어 주세요.")
        if (dao.pendingReview(id)) fail(HttpStatus.CONFLICT, "이미 심사를 기다리고 있어요.")
        dao.requestReview(id, dao.revisionOf(id), me.account)
        dao.log(id, null, "review-request", "", me.account, me.name)
        return mapOf("status" to "requested")
    }

    /** 승계 신청(새 말모이): 레벨 30 이상, 반영된 낱말 50개 이상, 기준 세 칸. 검토는 단어팀이 백오피스에서 한다. */
    @Transactional
    fun requestPromotion(session: HttpSession, id: Long): Map<String, Any?> {
        val me = me(session)
        val b = requireOwner(me, id)
        if (b.kind != "NEW" || b.state != "PUBLIC") fail(HttpStatus.CONFLICT, "공개한 새 말모이만 신청할 수 있어요.")
        if (me.level < MalmoiRules.PROMOTE_LEVEL) fail(HttpStatus.FORBIDDEN, "레벨 ${MalmoiRules.PROMOTE_LEVEL} 이상이어야 해요.")
        val live = dao.wordCounts(id).second
        if (live < MalmoiRules.PROMOTE_WORDS) fail(HttpStatus.CONFLICT, "반영된 낱말이 ${MalmoiRules.PROMOTE_WORDS}개 이상이어야 해요.")
        if (!criteriaFilled(b)) fail(HttpStatus.CONFLICT, "낱말 기준 세 칸을 적어 주세요.")
        if (dao.pendingPromotion(id)) fail(HttpStatus.CONFLICT, "이미 검토를 기다리고 있어요.")
        dao.requestPromotion(id, me.account, live)
        dao.log(id, null, "promotion-request", "", me.account, me.name)
        return mapOf("status" to "requested")
    }

    // ---- license ----

    fun license(session: HttpSession): Map<String, Any?> {
        val me = me(session)
        val now = Instant.now()
        val active = dao.activeLicense(me.account)
        val last = dao.latestLicense(me.account)
        return linkedMapOf(
            "active" to (active != null), "number" to active?.number, "issuedAt" to active?.issuedAt?.toEpochMilli(), "until" to active?.expiresAt?.toEpochMilli(),
            "edits" to dao.editsToday(me.account), "daily" to MalmoiRules.DAILY_EDITS, "fee" to MalmoiRules.LICENSE_FEE, "oath" to MalmoiRules.OATH, "days" to MalmoiRules.LICENSE_DAYS,
            "reissueAfter" to last?.reissueAfter?.takeIf { it.isAfter(now) }?.toEpochMilli(), "level" to me.level
        )
    }

    /** 면허 발급: 선서를 한 줄씩 그대로 쓰고 500핑을 낸다. 취소된 면허는 재발급 유예가 지나야 한다. */
    @Transactional
    fun issueLicense(session: HttpSession, oath: List<String>): Map<String, Any?> {
        val me = me(session)
        if (oath.size != MalmoiRules.OATH.size || oath.indices.any { !MalmoiRules.sameOath(oath[it], MalmoiRules.OATH[it]) }) fail(HttpStatus.BAD_REQUEST, "선서문을 그대로 적어 주세요.")
        if (dao.activeLicense(me.account) != null) fail(HttpStatus.CONFLICT, "이미 면허가 있어요.")
        dao.latestLicense(me.account)?.reissueAfter?.takeIf { it.isAfter(Instant.now()) }?.let { fail(HttpStatus.FORBIDDEN, "재발급은 ${it.toEpochMilli()}까지 기다려야 해요.") }
        if (!dao.spend(me.profile, MalmoiRules.LICENSE_FEE)) fail(HttpStatus.PAYMENT_REQUIRED, "핑이 모자라요.")
        val row = dao.issueLicense(me.account, me.profile, MalmoiRules.LICENSE_FEE, MalmoiRules.LICENSE_DAYS, MalmoiRules.OATH_VERSION)
        return mapOf("number" to row.number, "issuedAt" to row.issuedAt.toEpochMilli(), "until" to row.expiresAt.toEpochMilli(), "money" to dao.moneyOf(me.profile))
    }
}
