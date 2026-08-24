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

package me.kkutuio.kkutuweb.oauth.naver

import tools.jackson.databind.ObjectMapper
import com.github.scribejava.apis.NaverApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.oauth.Gender
import me.kkutuio.kkutuweb.oauth.OAuthService
import me.kkutuio.kkutuweb.oauth.OAuthUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NaverOAuthService(
    @Autowired private val objectMapper: ObjectMapper
) : OAuthService() {
    private val protectedResourceUrl = "https://openapi.naver.com/v1/nid/me"

    override fun init(apiKey: String, apiSecret: String, callbackUrl: String, _allowRegister: Boolean) {
        allowRegister = _allowRegister
        oAuth20Service = ServiceBuilder(apiKey)
            .apiSecret(apiSecret)
            .callback(callbackUrl)
            .build(NaverApi.instance())
    }

    override fun login(code: String): OAuthUser {
        val accessToken = oAuth20Service.getAccessToken(code)

        val request = OAuthRequest(Verb.GET, protectedResourceUrl)
        oAuth20Service.signRequest(accessToken, request)

        val response = oAuth20Service.execute(request)
        val jsonResponse = objectMapper.readTree(response.body)

        val responseNode = jsonResponse.path("response")
        val vendorId = responseNode.path("id").asString().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("네이버 계정 식별번호를 받지 못했습니다.")
        val splitAge = responseNode.path("age").asString("").split("-")

        return OAuthUser(
            authVendor = AuthVendor.NAVER,
            vendorId = vendorId,
            name = responseNode.path("nickname").asString(null) ?: "네이버 사용자",
            profileImage = responseNode.path("profile_image").asString(null),
            gender = responseNode.path("gender").asString(null)?.let(Gender::fromName),
            minAge = splitAge.getOrNull(0)?.toIntOrNull(),
            maxAge = splitAge.getOrNull(1)?.toIntOrNull(),
            email = responseNode.path("email").asString(null),
            emailVerified = false
        )
    }
}
