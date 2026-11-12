package me.kkutuio.kkutuweb.wiki

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.identity.AccountRateLimiter
import me.kkutuio.kkutuweb.identity.AccountService
import me.kkutuio.kkutuweb.identity.IdentityDao
import me.kkutuio.kkutuweb.identity.IdpException
import org.springframework.web.bind.annotation.*

data class WikiChallengeRequest(val username: String = "")

/**
 * Binding the game account to a 리오위키 account (계정 settings). Members only: a guest has no account, so `requireCurrentAccount` answers 401.
 * No recent-authentication step: the wiki password is never typed here and the binding is not a way to sign in; ownership is proven on the wiki.
 */
@RestController
@RequestMapping("/api/account/wiki")
class WikiAccountApi(
    private val accounts: AccountService,
    private val wiki: WikiBindingService,
    private val limiter: AccountRateLimiter,
    private val identity: IdentityDao
) {
    private fun member(session: HttpSession) = accounts.requireCurrentAccount(session).also(accounts::requireLoginAllowed)

    @GetMapping fun status(session: HttpSession): Map<String, Any?> = view(wiki.status(member(session).id))

    @PostMapping("/challenge") fun challenge(@RequestBody body: WikiChallengeRequest, session: HttpSession): Map<String, Any?> {
        val account = member(session)
        limiter.check("wiki-challenge:${account.id}", 10, 3600)
        val info = wiki.startChallenge(account.id, body.username)
        return mapOf("code" to info.code, "expires_at" to info.expiresAt.toString(), "page" to info.page, "wiki_url" to info.wikiUrl)
    }

    @PostMapping("/verify") fun verify(session: HttpSession): Map<String, Any?> {
        val account = member(session)
        limiter.check("wiki-verify:${account.id}", 30, 3600)
        val status = wiki.verify(account.id)
        identity.audit(account.id, "WIKI_BOUND", metadata = mapOf("wiki_user_id" to status.wikiUserId, "wiki_username" to status.wikiUsername))
        return view(status)
    }

    @DeleteMapping fun unbind(session: HttpSession): Map<String, Any?> {
        val account = member(session)
        limiter.check("wiki-unbind:${account.id}", 10, 3600)
        wiki.unbind(account.id)
        identity.audit(account.id, "WIKI_UNBOUND")
        return view(wiki.status(account.id))
    }

    private fun view(s: WikiStatus): Map<String, Any?> = linkedMapOf(
        "bound" to s.bound,
        "wiki_username" to s.wikiUsername,
        "wiki_user_id" to s.wikiUserId,
        "bound_at" to s.boundAt?.toString(),
        "contributions" to mapOf("settled" to s.settled, "pending" to s.pending)
    )
}
