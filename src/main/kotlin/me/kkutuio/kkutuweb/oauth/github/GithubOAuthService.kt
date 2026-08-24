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

package me.kkutuio.kkutuweb.oauth.github

import tools.jackson.databind.ObjectMapper
import com.github.scribejava.apis.GitHubApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Verb
import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.oauth.OAuthService
import me.kkutuio.kkutuweb.oauth.OAuthUser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GithubOAuthService(
    @Autowired private val objectMapper: ObjectMapper
) : OAuthService() {
    private val protectedResourceUrl = "https://api.github.com/user"

    override fun init(apiKey: String, apiSecret: String, callbackUrl: String, _allowRegister: Boolean) {
        allowRegister = _allowRegister
        oAuth20Service = ServiceBuilder(apiKey)
            .apiSecret(apiSecret)
            .callback(callbackUrl)
            .defaultScope("read:user user:email")
            .build(GitHubApi.instance())
    }

    override fun login(code: String): OAuthUser {
        val accessToken = oAuth20Service.getAccessToken(code)

        val request = OAuthRequest(Verb.GET, protectedResourceUrl)
        oAuth20Service.signRequest(accessToken, request)

        val response = oAuth20Service.execute(request)
        val jsonResponse = objectMapper.readTree(response.body)

        val emailRequest = OAuthRequest(Verb.GET, "https://api.github.com/user/emails")
        oAuth20Service.signRequest(accessToken, emailRequest)
        val verifiedEmail = objectMapper.readTree(oAuth20Service.execute(emailRequest).body)
            .firstOrNull { it.path("primary").asBoolean(false) && it.path("verified").asBoolean(false) }
            ?.path("email")?.asString(null)

        return OAuthUser(
            authVendor = AuthVendor.GITHUB,
            vendorId = jsonResponse["id"].intValue().toString(),
            name = if (jsonResponse.has("name")) jsonResponse["name"].stringValue() else jsonResponse["login"].stringValue(),
            profileImage = jsonResponse["avatar_url"].stringValue(),
            gender = null,
            minAge = null,
            maxAge = null,
            email = verifiedEmail,
            emailVerified = verifiedEmail != null
        )
    }
}
