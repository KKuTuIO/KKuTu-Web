package me.kkutuio.kkutuweb.wiki

import me.kkutuio.kkutuweb.identity.IdpException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InMemoryWikiStore : WikiStore {
    val bindings = mutableMapOf<UUID, WikiBinding>()
    val challenges = mutableMapOf<UUID, WikiChallenge>()
    data class Row(val revisionId: Long, val accountId: UUID, val editedAt: Instant, var settledAt: Instant? = null)
    val rows = mutableListOf<Row>()
    val flags = mutableMapOf<UUID, Pair<Int, List<Long>>>()

    override fun findBinding(accountId: UUID) = bindings[accountId]
    override fun findBindingByWikiUser(wikiUserId: Long) = bindings.values.firstOrNull { it.wikiUserId == wikiUserId }
    override fun allBindings() = bindings.values.sortedBy { it.boundAt }
    override fun bind(accountId: UUID, wikiUserId: Long, wikiUsername: String, now: Instant): Boolean {
        if (accountId in bindings || findBindingByWikiUser(wikiUserId) != null) return false
        bindings[accountId] = WikiBinding(accountId, wikiUserId, wikiUsername, now)
        return true
    }
    override fun unbind(accountId: UUID) = bindings.remove(accountId) != null
    override fun saveChallenge(c: WikiChallenge) { challenges[c.accountId] = c }
    override fun findChallenge(accountId: UUID) = challenges[accountId]
    override fun deleteChallenge(accountId: UUID) { challenges.remove(accountId) }
    override fun latestEditAt(accountId: UUID) = rows.filter { it.accountId == accountId }.maxOfOrNull { it.editedAt }
    override fun addEdits(accountId: UUID, wikiUserId: Long, edits: List<WikiEdit>): Int {
        var added = 0
        for (e in edits) if (rows.none { it.revisionId == e.revisionId }) { rows += Row(e.revisionId, accountId, e.editedAt); added++ }
        return added
    }
    override fun pendingOlderThan(cutoff: Instant) = rows.filter { it.settledAt == null && !it.editedAt.isAfter(cutoff) }.map { PendingEdit(it.revisionId, it.accountId) }
    override fun settle(revisionIds: Collection<Long>, now: Instant) { rows.filter { it.revisionId in revisionIds && it.settledAt == null }.forEach { it.settledAt = now } }
    override fun discard(revisionIds: Collection<Long>) { rows.removeAll { it.revisionId in revisionIds && it.settledAt == null } }
    override fun counts(accountId: UUID) = rows.filter { it.accountId == accountId }.let { r -> r.count { it.settledAt != null } to r.count { it.settledAt == null } }
    override fun settledTimes(accountId: UUID, limit: Int) = rows.filter { it.accountId == accountId && it.settledAt != null }.map { it.editedAt.toEpochMilli() }.sorted().takeLast(limit)
    override fun writeFlag(accountId: UUID, settled: Int, edits: List<Long>, nowMillis: Long): Boolean {
        flags[accountId] = maxOf(settled, flags[accountId]?.first ?: 0) to edits
        return true
    }
}

class FakeWiki : WikiApi {
    val users = mutableMapOf<String, WikiUser>()
    val pages = mutableMapOf<String, String>()
    var recent = mutableMapOf<String, List<WikiEdit>>()
    var contribs = mutableMapOf<Long, List<WikiEdit>>()
    var tags = mutableMapOf<Long, List<String>>()
    var down = false
    private fun check() { if (down) throw WikiUnavailableException("down") }
    override fun findUser(name: String): WikiUser? { check(); return users[name] }
    override fun userPageText(name: String): String? { check(); return pages[name] }
    override fun recentEdits(name: String, limit: Int): List<WikiEdit> { check(); return recent[name].orEmpty() }
    override fun editsSince(userId: Long, from: Instant): List<WikiEdit> { check(); return contribs[userId].orEmpty().filter { !it.editedAt.isBefore(from) } }
    override fun revisionTags(revisionIds: Collection<Long>): Map<Long, List<String>> { check(); return revisionIds.filter { it in tags }.associateWith { tags.getValue(it) } }
}

class WikiBindingTest {
    private val store = InMemoryWikiStore()
    private val wiki = FakeWiki()
    private val service = WikiBindingService(store, wiki, "https://wiki.test")
    private var now = Instant.parse("2026-11-11T00:00:00Z")
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach fun setUp() {
        service.clock = { now }
        wiki.users["가을"] = WikiUser(7, "가을")
        wiki.users["Rio"] = WikiUser(9, "Rio")
    }

    private fun code(account: UUID, name: String) = service.startChallenge(account, name).code
    private fun error(block: () -> Unit) = assertThatThrownBy(block).isInstanceOf(IdpException::class.java).extracting { (it as IdpException).error }

    @Test fun `user names are written the way MediaWiki writes them`() {
        assertThat(WikiBindingService.normalizeUsername("  rio_wiki  user ")).isEqualTo("Rio wiki user")
        assertThat(WikiBindingService.normalizeUsername("가을")).isEqualTo("가을")
        assertThat(WikiBindingService.normalizeUsername("")).isNull()
        assertThat(WikiBindingService.normalizeUsername("a|b")).isNull()
        assertThat(WikiBindingService.normalizeUsername("x".repeat(86))).isNull()
    }

    @Test fun `an unknown wiki user gets no challenge`() {
        error { service.startChallenge(alice, "Nobody") }.isEqualTo("wiki_user_not_found")
        assertThat(store.challenges).isEmpty()
    }

    @Test fun `a challenge has a one-time code, a page and a 30 minute life, and a new one replaces it`() {
        val first = service.startChallenge(alice, "rio")
        assertThat(first.code).matches("KKUTUIO-[A-Z2-9]{12}")
        assertThat(first.page).isEqualTo("User:Rio")
        assertThat(first.wikiUrl).isEqualTo("https://wiki.test/w/User:Rio")
        assertThat(first.expiresAt).isEqualTo(now.plus(Duration.ofMinutes(30)))
        val second = service.startChallenge(alice, "Rio")
        assertThat(second.code).isNotEqualTo(first.code)
        assertThat(store.challenges).hasSize(1)
    }

    @Test fun `the code on the user page binds the wiki user id`() {
        val code = code(alice, "가을")
        wiki.pages["가을"] = "hello $code"
        val status = service.verify(alice)
        assertThat(status.bound).isTrue()
        assertThat(status.wikiUserId).isEqualTo(7)
        assertThat(store.challenges).isEmpty()
        assertThat(service.status(alice).wikiUsername).isEqualTo("가을")
    }

    @Test fun `the code in an edit summary also proves it, but only a recent one`() {
        val code = code(alice, "Rio")
        val old = WikiEdit(1, "X", now.minusSeconds(3600), false, emptyList(), code)
        wiki.recent["Rio"] = listOf(old)
        error { service.verify(alice) }.isEqualTo("challenge_not_found")
        now = now.plusSeconds(60)
        wiki.recent["Rio"] = listOf(WikiEdit(2, "X", now.minusSeconds(10), false, emptyList(), "check $code"))
        assertThat(service.verify(alice).bound).isTrue()
    }

    @Test fun `no code on the wiki, no binding`() {
        code(alice, "Rio")
        wiki.pages["Rio"] = "nothing here"
        error { service.verify(alice) }.isEqualTo("challenge_not_found")
        assertThat(store.bindings).isEmpty()
    }

    @Test fun `verifying without a challenge or after it expired asks for a new code`() {
        error { service.verify(alice) }.isEqualTo("challenge_expired")
        val code = code(alice, "Rio")
        wiki.pages["Rio"] = code
        now = now.plus(Duration.ofMinutes(31))
        error { service.verify(alice) }.isEqualTo("challenge_expired")
        assertThat(store.challenges).isEmpty()
    }

    @Test fun `a wiki user belongs to one account only`() {
        wiki.pages["Rio"] = code(alice, "Rio")
        service.verify(alice)
        error { service.startChallenge(bob, "Rio") }.isEqualTo("wiki_account_taken")
        // a challenge taken out earlier cannot win the race either
        store.saveChallenge(WikiChallenge(bob, "Rio", "KKUTUIO-RACE", now, now.plusSeconds(60)))
        wiki.pages["Rio"] = "KKUTUIO-RACE"
        error { service.verify(bob) }.isEqualTo("wiki_account_taken")
        assertThat(store.bindings).hasSize(1)
    }

    @Test fun `an account has one wiki user, until it lets go`() {
        wiki.pages["Rio"] = code(alice, "Rio")
        service.verify(alice)
        error { service.startChallenge(alice, "가을") }.isEqualTo("wiki_already_bound")
        service.unbind(alice)
        assertThat(service.status(alice).bound).isFalse()
        wiki.pages["가을"] = code(alice, "가을")
        assertThat(service.verify(alice).wikiUserId).isEqualTo(7)
    }

    @Test fun `a wiki outage is a 503 and binds nothing`() {
        wiki.down = true
        error { service.startChallenge(alice, "Rio") }.isEqualTo("wiki_unavailable")
    }
}

class WikiContributionSyncTest {
    private val store = InMemoryWikiStore()
    private val wiki = FakeWiki()
    private val sync = WikiContributionSync(store, wiki, true)
    private val alice = UUID.randomUUID()
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")

    private fun edit(id: Long, at: Instant, minor: Boolean = false, tags: List<String> = emptyList()) = WikiEdit(id, "문서$id", at, minor, tags, "")

    private fun bound() {
        store.bind(alice, 7, "가을", t0)
    }

    @Test fun `only major, unreverted edits after the binding are collected`() {
        bound()
        wiki.contribs[7] = listOf(
            edit(1, t0.minusSeconds(60)), edit(2, t0.plusSeconds(60)), edit(3, t0.plusSeconds(120), minor = true),
            edit(4, t0.plusSeconds(180), tags = listOf("mw-reverted")), edit(5, t0.plusSeconds(240))
        )
        sync.run(t0.plusSeconds(300))
        assertThat(store.rows.map { it.revisionId }).containsExactly(2, 5)
        assertThat(store.counts(alice)).isEqualTo(0 to 2)
    }

    @Test fun `an edit counts only after 24 hours`() {
        bound()
        wiki.contribs[7] = listOf(edit(1, t0.plusSeconds(60)))
        wiki.tags[1] = emptyList()
        sync.run(t0.plusSeconds(60).plus(Duration.ofHours(23)))
        assertThat(store.counts(alice)).isEqualTo(0 to 1)
        sync.run(t0.plusSeconds(60).plus(Duration.ofHours(24)))
        assertThat(store.counts(alice)).isEqualTo(1 to 0)
        assertThat(store.flags[alice]!!.first).isEqualTo(1)
        assertThat(store.flags[alice]!!.second).containsExactly(t0.plusSeconds(60).toEpochMilli())
    }

    @Test fun `an edit reverted or deleted within the day never counts`() {
        bound()
        wiki.contribs[7] = listOf(edit(1, t0.plusSeconds(60)), edit(2, t0.plusSeconds(61)), edit(3, t0.plusSeconds(62)))
        wiki.tags[1] = listOf("mw-reverted") // undone by someone
        // 2 was deleted: no revision left
        wiki.tags[3] = listOf("visualeditor")
        sync.run(t0.plus(Duration.ofHours(25)))
        assertThat(store.counts(alice)).isEqualTo(1 to 0)
        assertThat(store.rows.map { it.revisionId }).containsExactly(3)
    }

    @Test fun `running again never counts an edit twice, and the flag value does not go down`() {
        bound()
        wiki.contribs[7] = listOf(edit(1, t0.plusSeconds(60)), edit(2, t0.plusSeconds(90)))
        wiki.tags[1] = emptyList(); wiki.tags[2] = emptyList()
        val late = t0.plus(Duration.ofHours(25))
        sync.run(late); sync.run(late.plusSeconds(600))
        assertThat(store.counts(alice)).isEqualTo(2 to 0)
        assertThat(store.flags[alice]!!.first).isEqualTo(2)
    }

    @Test fun `a wiki outage leaves everything as it was`() {
        bound()
        wiki.down = true
        sync.run(t0.plus(Duration.ofHours(25)))
        assertThat(store.rows).isEmpty()
        assertThat(store.flags).isEmpty()
    }
}
