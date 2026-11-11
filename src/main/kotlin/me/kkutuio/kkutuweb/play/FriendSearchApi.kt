package me.kkutuio.kkutuweb.play

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.academy.AcademyRateLimitService
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Finding someone to ask as a friend: by nickname (with or without the #tag, any part of it) or by 식별번호. Members only, a few
 * lookups a minute, and only what the lobby already shows of a player (nickname, level, outfit) comes back.
 */
@RestController
class FriendSearchApi(
    @Autowired private val userDao: UserDao,
    @Autowired private val loginService: LoginService,
    @Autowired private val rateLimit: AcademyRateLimitService
) {
    @GetMapping("/api/play/users/search")
    fun search(@RequestParam q: String, session: HttpSession): ResponseEntity<Any> {
        if (session.isGuest()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "LOGIN_REQUIRED"))
        val me = loginService.gameUserId(session) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "LOGIN_REQUIRED"))
        val query = q.trim().take(64)
        if (query.length < 2) return ResponseEntity.ok(emptyList<Any>())
        if (!rateLimit.allowPublic("friend-search", me, 30, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("error" to "RATE_LIMITED"))
        }
        val found = userDao.searchUsers(query, 12)
            .filter { it.id != me && !it.nickname.isNullOrBlank() }
            .map { mapOf("id" to it.id, "nickname" to it.nickname, "score" to (it.kkutu.get("score")?.asLong() ?: 0L), "equip" to it.equip) }
        return ResponseEntity.ok(found)
    }

    /**
     * Players by (part of) a nickname, one page at a time, for the replay page: the same people the friend search finds (members only), but
     * oneself is among them and there are more pages. `{users: [...], hasNext}`.
     */
    @GetMapping("/api/play/users/lookup")
    fun lookup(@RequestParam q: String, @RequestParam(defaultValue = "1") page: Int, session: HttpSession): ResponseEntity<Any> {
        if (session.isGuest()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "LOGIN_REQUIRED"))
        val me = loginService.gameUserId(session) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "LOGIN_REQUIRED"))
        val query = q.trim().take(64)
        if (query.length < 2) return ResponseEntity.ok(mapOf("users" to emptyList<Any>(), "hasNext" to false))
        if (!rateLimit.allowPublic("user-lookup", me, 60, 60)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("error" to "RATE_LIMITED"))
        }
        val size = 10
        val rows = userDao.searchUsersPage(query, size + 1, (page.coerceIn(1, 100) - 1) * size)
        val users = rows.take(size)
            .filter { !it.nickname.isNullOrBlank() }
            .map { mapOf("id" to it.id, "nickname" to it.nickname, "score" to (it.kkutu.get("score")?.asLong() ?: 0L), "equip" to it.equip) }
        return ResponseEntity.ok(mapOf("users" to users, "hasNext" to (rows.size > size)))
    }
}
