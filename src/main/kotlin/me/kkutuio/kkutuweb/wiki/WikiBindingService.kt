package me.kkutuio.kkutuweb.wiki

import me.kkutuio.kkutuweb.identity.IdpException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class WikiStatus(val bound: Boolean, val wikiUsername: String?, val wikiUserId: Long?, val boundAt: Instant?, val settled: Int, val pending: Int)
data class WikiChallengeInfo(val code: String, val expiresAt: Instant, val page: String, val wikiUrl: String)

/**
 * Binding a game account to a 리오위키 account without ever handling the wiki password: the player proves the wiki account is theirs by putting a
 * one-time code on their wiki user page (or in an edit summary), and the server reads it back from the wiki. The wiki's numeric user id is bound
 * (it survives a rename); one account <-> one wiki user, both ways. Meant to be replaced by the SSO later behind the same calls.
 */
@Service
class WikiBindingService(
    private val dao: WikiStore,
    private val wiki: WikiApi,
    @Value("\${wiki.origin:https://kkutu.wiki}") origin: String
) {
    private val origin = origin.trim().trimEnd('/')
    private val random = SecureRandom()
    /** Replaceable in tests. */
    var clock: () -> Instant = { Instant.now() }

    fun status(accountId: UUID): WikiStatus {
        val binding = dao.findBinding(accountId)
        val (settled, pending) = dao.counts(accountId)
        return WikiStatus(binding != null, binding?.wikiUsername, binding?.wikiUserId, binding?.boundAt, settled, pending)
    }

    fun startChallenge(accountId: UUID, rawUsername: String): WikiChallengeInfo {
        if (dao.findBinding(accountId) != null) throw IdpException("wiki_already_bound", "이미 리오위키 계정이 연동되어 있습니다. 먼저 연동을 해제해 주세요.", 409)
        val name = normalizeUsername(rawUsername) ?: throw IdpException("invalid_wiki_username", "리오위키 사용자 이름을 확인해 주세요.")
        val user = lookup(name) ?: throw IdpException("wiki_user_not_found", "리오위키에서 '$name' 사용자를 찾을 수 없습니다.", 404)
        if (dao.findBindingByWikiUser(user.id) != null) throw IdpException("wiki_account_taken", "이미 다른 계정에 연동된 리오위키 계정입니다.", 409)
        val now = clock()
        val challenge = WikiChallenge(accountId, user.name, newCode(), now, now.plus(CHALLENGE_TTL))
        dao.saveChallenge(challenge)
        return WikiChallengeInfo(challenge.code, challenge.expiresAt, "User:${user.name}", pageUrl(user.name))
    }

    fun verify(accountId: UUID): WikiStatus {
        if (dao.findBinding(accountId) != null) throw IdpException("wiki_already_bound", "이미 리오위키 계정이 연동되어 있습니다.", 409)
        val challenge = dao.findChallenge(accountId) ?: throw IdpException("challenge_expired", "먼저 인증 코드를 받아 주세요.", 410)
        val now = clock()
        if (!challenge.expiresAt.isAfter(now)) {
            dao.deleteChallenge(accountId)
            throw IdpException("challenge_expired", "인증 코드가 만료되었습니다. 새 코드를 받아 주세요.", 410)
        }
        val user = lookup(challenge.wikiUsername) ?: throw IdpException("wiki_user_not_found", "리오위키에서 '${challenge.wikiUsername}' 사용자를 찾을 수 없습니다.", 404)
        if (!codeShownOnWiki(challenge, user)) throw IdpException("challenge_not_found", "리오위키 사용자 문서나 편집 요약에서 인증 코드를 찾지 못했습니다.", 422)
        if (!dao.bind(accountId, user.id, user.name, now)) throw IdpException("wiki_account_taken", "이미 다른 계정에 연동된 리오위키 계정입니다.", 409)
        dao.deleteChallenge(accountId)
        return status(accountId)
    }

    fun unbind(accountId: UUID) {
        dao.deleteChallenge(accountId)
        dao.unbind(accountId)
    }

    private fun lookup(name: String): WikiUser? = try { wiki.findUser(name) } catch (error: WikiUnavailableException) { throw unavailable() }

    private fun codeShownOnWiki(challenge: WikiChallenge, user: WikiUser): Boolean = try {
        wiki.userPageText(user.name)?.contains(challenge.code) == true ||
            wiki.recentEdits(user.name, 50).any { !it.editedAt.isBefore(challenge.createdAt) && it.comment.contains(challenge.code) }
    } catch (error: WikiUnavailableException) {
        throw unavailable()
    }

    private fun unavailable() = IdpException("wiki_unavailable", "리오위키에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.", 503)
    private fun pageUrl(name: String) = "$origin/w/User:" + java.net.URLEncoder.encode(name.replace(' ', '_'), Charsets.UTF_8).replace("+", "%20")
    private fun newCode(): String = "KKUTUIO-" + (1..12).map { CODE_CHARS[random.nextInt(CODE_CHARS.length)] }.joinToString("")

    companion object {
        val CHALLENGE_TTL: Duration = Duration.ofMinutes(30)
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** MediaWiki's way of writing a user name: underscores are spaces, runs of spaces become one, the first letter is capitalised; null when it can't be a name. */
        fun normalizeUsername(raw: String): String? {
            val name = raw.replace('_', ' ').trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty() || name.length > 85) return null
            if (name.any { it in "#<>[]|{}/@:" || it.isISOControl() }) return null
            return name.replaceFirstChar { it.uppercaseChar() }
        }
    }
}
