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

package me.kkutuio.kkutuweb.admin.api

import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.game.GameClientManager
import me.kkutuio.kkutuweb.user.UserDao
import me.kkutuio.kkutuweb.ranking.RankDao
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/admin")
class AdminAPI(
    @Autowired private val kKuTuSetting: KKuTuSetting,
    @Autowired private val gameClientManager: GameClientManager,
    @Autowired private val rankDao: RankDao,
    @Autowired private val userDao: UserDao
) {
    private val logger = LoggerFactory.getLogger(AdminAPI::class.java)

    @PostMapping("/membership/{action}")
    fun updateMembership(
            @PathVariable action: String,
            @RequestParam apiKey: String,
            @RequestParam id: String,
            @RequestParam uid: String,
            @RequestParam type: String,
            @RequestParam liveService: Boolean,
            request: HttpServletRequest
    ): ActionResponse {
        if (kKuTuSetting.getApiKey() != apiKey) {
            logger.warn("[${request.getIp()}] API 키가 불일치하여 membership/${id} 요청을 무시합니다.")
            return ActionResponse.rest(success = false, restResult = RestResult.UNAUTHORIZED)
        }

        // TODO: Add Notifications about Subscription Changes
        val user = userDao.getUser(id) ?: return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)

        if(action == "new") {
            val userUid = user.flags.get("uid") ?: return ActionResponse.rest(success = false, restResult = RestResult.INTERNAL_ERROR)
            if(userUid.toString() != uid) return ActionResponse.rest(success = false, restResult = RestResult.UID_MISMATCH)
        }

        if(liveService) userDao.updateUser(
                id, mapOf(
                "membership" to type
            )
        )

        logger.info("$id 계정의 리오패스 구독 상태를 ${type}으(로) 변경하였습니다.")
        return ActionResponse.success()
    }

    @PostMapping("/kickByUserId/{id}")
    fun kickByUserId(
        @PathVariable id: String,
        @RequestBody postApiBody: PostApiBody,
        request: HttpServletRequest
    ) {
        if (kKuTuSetting.getApiKey() != postApiBody.apiKey) {
            logger.warn("[${request.getIp()}] API 키가 불일치하여 kickByUserId/$id 요청을 무시합니다.")
            return
        }

        gameClientManager.kick(id, "")
        logger.info("$id 계정을 끄투리오에서 추방합니다.")
    }

    @PostMapping("/kickByIp/{ip}")
    fun kickByIp(
        @PathVariable ip: String,
        @RequestBody postApiBody: PostApiBody,
        request: HttpServletRequest
    ) {
        if (kKuTuSetting.getApiKey() != postApiBody.apiKey) {
            logger.warn("[${request.getIp()}] API 키가 불일치하여 kickByIp/$ip 요청을 무시합니다.")
            return
        }

        gameClientManager.kick("", ip)
        logger.info("$ip 아이피로 접속 중인 계정을 끄투리오에서 추방합니다.")
    }

    @PostMapping("/resetRank/{id}")
    fun resetRank(
        @PathVariable id: String,
        @RequestBody postApiBody: PostApiBody,
        request: HttpServletRequest
    ) {
        if (kKuTuSetting.getApiKey() != postApiBody.apiKey) {
            logger.warn("[${request.getIp()}] API 키가 불일치하여 resetRank/$id 요청을 무시합니다.")
            return
        }

        rankDao.remove(id)
        logger.info("$id 계정의 랭킹 데이터를 제거했습니다.")
    }

    @PostMapping("/yell")
    fun yell(
        @RequestBody postApiBody: PostApiBody,
        request: HttpServletRequest
    ) {
        if (kKuTuSetting.getApiKey() != postApiBody.apiKey) {
            logger.warn("[${request.getIp()}] API 키가 불일치하여 yell 요청을 무시합니다.")
            return
        }
        val value = postApiBody.value?.replace("\"", "\u005C\u0022")
        gameClientManager.yell(value ?: "")
        logger.info("공지가 전송되었습니다. 내용: ${postApiBody.value}")
    }
}

data class PostApiBody (
    val apiKey: String,
    val value: String?
)