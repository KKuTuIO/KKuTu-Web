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

package me.kkutuio.kkutuweb.extension

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import me.kkutuio.kkutuweb.SessionAttribute
import me.kkutuio.kkutuweb.oauth.OAuthUser
import jakarta.servlet.http.HttpSession
import java.time.Instant
import java.util.UUID

private val objectMapper = ObjectMapper().registerKotlinModule()

fun HttpSession.isGuest(): Boolean = (this.getAttribute(SessionAttribute.IS_GUEST.attributeName) ?: true) as Boolean
fun HttpSession.getOAuthUser(): OAuthUser = objectMapper.readValue(
    this.getAttribute(SessionAttribute.OAUTH_USER.attributeName) as String,
    OAuthUser::class.java
)

fun HttpSession.setOAuthUser(oAuthUser: OAuthUser) =
    this.setAttribute(SessionAttribute.OAUTH_USER.attributeName, objectMapper.writeValueAsString(oAuthUser))

fun HttpSession.setAttribute(sessionAttribute: SessionAttribute, value: Any) =
    this.setAttribute(sessionAttribute.attributeName, value)

fun HttpSession.removeAttribute(sessionAttribute: SessionAttribute) =
    this.removeAttribute(sessionAttribute.attributeName)

fun HttpSession.getAccountId(): UUID? = (getAttribute(SessionAttribute.ACCOUNT_ID.attributeName) as? String)?.let {
    runCatching { UUID.fromString(it) }.getOrNull()
}

fun HttpSession.setAccountId(accountId: UUID) = setAttribute(SessionAttribute.ACCOUNT_ID, accountId.toString())

fun HttpSession.markRecentlyAuthenticated() = setAttribute(SessionAttribute.RECENT_AUTH_AT, Instant.now().epochSecond)
fun HttpSession.markStronglyAuthenticated() = setAttribute(SessionAttribute.STRONG_AUTH_AT, Instant.now().epochSecond)
fun HttpSession.markAuthenticated() = setAttribute(SessionAttribute.AUTHENTICATED_AT, Instant.now().epochSecond)
fun HttpSession.authenticatedAt(): Long = (getAttribute(SessionAttribute.AUTHENTICATED_AT.attributeName) as? Long) ?: 0L

fun HttpSession.hasRecentAuthentication(maxAgeSeconds: Long = 300): Boolean =
    ((getAttribute(SessionAttribute.RECENT_AUTH_AT.attributeName) as? Long) ?: 0L) >= Instant.now().epochSecond - maxAgeSeconds

fun HttpSession.hasStrongAuthentication(maxAgeSeconds: Long = 300): Boolean =
    ((getAttribute(SessionAttribute.STRONG_AUTH_AT.attributeName) as? Long) ?: 0L) >= Instant.now().epochSecond - maxAgeSeconds
