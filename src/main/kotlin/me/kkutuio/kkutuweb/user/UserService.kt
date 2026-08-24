/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.kkutuio.kkutuweb.user

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.extension.toJson
import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.identity.AccountService
import me.kkutuio.kkutuweb.identity.NicknameService
import me.kkutuio.kkutuweb.shop.ShopDao
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.config.CacheConfig
import me.kkutuio.kkutuweb.ranking.RankDao
import org.postgresql.util.PGobject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import jakarta.servlet.http.HttpSession
import kotlin.math.roundToInt

private val AVAIL_EQUIP = listOf(
    "NIK", "BDG1", "BDG2", "BDG3", "BDG4", "BAN", "BAD",
    "Mhead", "Meye", "Mmouth", "Mhand", "Mclothes", "Mshoes", "Mback",
    "BON"
)

@Service
class UserService(
    @Autowired private val userDao: UserDao,
    @Autowired private val shopDao: ShopDao,
    @Autowired private val shopService: ShopService,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val cacheManager: CacheManager,
    @Autowired private val loginService: LoginService,
    @Autowired private val accountService: AccountService,
    @Autowired private val nicknameService: NicknameService,
    @Autowired private val rankDao: RankDao
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)
    private val similarityRegex = "[-_ ]*".toRegex()

    fun getBox(session: HttpSession): String {
        if (session.isGuest()) return "{\"error\":400}"
        val userId = loginService.gameUserId(session) ?: return "{\"error\":400}"
        val user = userDao.getUser(userId) ?: return "{\"error\":400}"

        return user.box.toJson()
    }

    fun exordial(data: String, session: HttpSession): String {
        val account = accountService.currentAccount(session) ?: return "{\"error\":400}"
        val userId = loginService.gameUserId(session) ?: return "{\"error\":400}"
        val resultData = nicknameService.changeExordial(account, data)

        cacheManager.getCache(CacheConfig.RECORD_USER_INFO_CACHE)?.evict(userId)
        logger.info("$userId 님이 프로필을 수정했습니다. 소개 한마디: $resultData")
        return objectMapper.writeValueAsString(mapOf("text" to resultData))
    }

    fun equip(id: String, isLeft: Boolean, session: HttpSession): String {
        return objectMapper.writeValueAsString(mapOf("error" to 555))
    }

    @Cacheable(
        value = [CacheConfig.RECORD_USER_INFO_CACHE],
        key = "#id",
        unless = "#result.contains('\\\"error\\\"')"
    )
    fun getUserData(id: String): String {
        val user = userDao.getUser(id) ?: return objectMapper.writeValueAsString(mapOf("error" to 405))
        return objectMapper.writeValueAsString(mapOf(
            "result" to 200,
            "id" to user.id,
            "rank" to rankDao.getRank(user.id),
            "data" to objectMapper.readTree(user.kkutu.toJson()),
            "equip" to objectMapper.readTree(user.equip.toJson()),
            "exordial" to (user.exordial ?: ""),
            "profile" to mapOf(
                "authtype" to "offline",
                "id" to user.id,
                "title" to user.nickname,
                "lastLogin" to (user.lastLogin ?: 0)
            )
        ))
        // NekoP - /user/{id}로 요청하여 회원의 정보를 받아 올 수 있게 함
    }

    fun getIdFromNick(nick: String): String {
        val similarityNick = similarityRegex.replace(nick, "").lowercase()
        val user = userDao.getUserFromNick(similarityNick, nick) ?: return objectMapper.writeValueAsString(mapOf("error" to 405))
        return objectMapper.writeValueAsString(mapOf("result" to 200, "id" to user.id))
    }
}
