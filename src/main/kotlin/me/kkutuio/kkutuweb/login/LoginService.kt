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

package me.kkutuio.kkutuweb.login

import me.kkutuio.kkutuweb.SessionAttribute
import me.kkutuio.kkutuweb.extension.*
import me.kkutuio.kkutuweb.oauth.AuthVendor
import me.kkutuio.kkutuweb.oauth.OAuthService
import me.kkutuio.kkutuweb.oauth.daldalso.DaldalsoOAuthService
import me.kkutuio.kkutuweb.oauth.discord.DiscordOAuthService
import me.kkutuio.kkutuweb.oauth.facebook.FacebookOAuthService
import me.kkutuio.kkutuweb.oauth.github.GithubOAuthService
import me.kkutuio.kkutuweb.oauth.google.GoogleOAuthService
import me.kkutuio.kkutuweb.oauth.kakao.KakaoOAuthService
import me.kkutuio.kkutuweb.oauth.naver.NaverOAuthService
import me.kkutuio.kkutuweb.session.SessionProfile
import me.kkutuio.kkutuweb.setting.OAuthSetting
import me.kkutuio.kkutuweb.user.UserDao
import me.kkutuio.kkutuweb.identity.AccountService
import me.kkutuio.kkutuweb.identity.Account
import me.kkutuio.kkutuweb.identity.AccountSecurityService
import me.kkutuio.kkutuweb.identity.SecretTools
import me.kkutuio.kkutuweb.oauth.OAuthUser
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import kotlin.streams.asSequence
import java.time.Instant

@Service
class LoginService(
    @Autowired private val oAuthSetting: OAuthSetting,
    @Autowired private val daldalsoOAuthService: DaldalsoOAuthService,
    @Autowired private val facebookOAuthService: FacebookOAuthService,
    @Autowired private val googleOAuthService: GoogleOAuthService,
    @Autowired private val naverOAuthService: NaverOAuthService,
    @Autowired private val githubOAuthService: GithubOAuthService,
    @Autowired private val discordOAuthService: DiscordOAuthService,
    @Autowired private val kakaoOAuthService: KakaoOAuthService,
    @Autowired private val userDao: UserDao,
    @Autowired private val accountService: AccountService,
    @Autowired private val accountSecurity: AccountSecurityService,
    @Autowired private val objectMapper: ObjectMapper
) {
    private companion object {
        const val PENDING_MFA_TTL_SECONDS = 300L
    }
    @PostConstruct
    fun initOAuthServices() {
        for (entry in oAuthSetting.getSetting().entries) {
            val vendorType = entry.key
            val setting = entry.value

            getOAuthService(vendorType).init(setting.clientId, setting.clientSecret, setting.callbackUrl, setting.allowRegister)
        }
    }

    fun getAuthorizationUrl(session: HttpSession, authVendor: AuthVendor): String? {
        val oAuthService = getOAuthService(authVendor)
        if (!oAuthService.isInitialized()) {
            return null
        }

        val randomState = getRandomState()
        session.setAttribute(SessionAttribute.OAUTH_STATE.attributeName, randomState)

        return oAuthService.getAuthorizationUrl(randomState)
    }

    fun login(request: HttpServletRequest, authVendor: AuthVendor, code: String, state: String): Boolean {
        return try {
            var session = request.session
            val oAuthState = session.getAttribute(SessionAttribute.OAUTH_STATE.attributeName)
                ?: return false

            if (oAuthState != state) {
                return false
            }

            val linkAccount = (session.getAttribute(SessionAttribute.OAUTH_LINK_ACCOUNT_ID.attributeName) as? String)
                ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }

            if (linkAccount != null) {
                val account = accountService.currentAccount(session) ?: return false
                if (account.id != linkAccount) return false
                val oAuthUser = getOAuthService(authVendor).login(code)
                accountService.linkExternalIdentity(account, oAuthUser)
                session.removeAttribute(SessionAttribute.OAUTH_STATE)
                session.removeAttribute(SessionAttribute.OAUTH_LINK_ACCOUNT_ID)
                return true
            }

            val pendingIdentityJson = session.getAttribute(SessionAttribute.PENDING_OAUTH_USER.attributeName) as? String
            val continuation = session.getAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName) as? String
            try {
                session.invalidate()
                session = request.session
            } catch (e: Exception) {
            }
            continuation?.let { session.setAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName, it) }

            val oAuthUser = getOAuthService(authVendor).login(code)

            if (pendingIdentityJson != null) {
                // This is the second half of the "no direct registration" flow.
                // Do not let a caller bypass it by selecting another blocked provider directly.
                if (!getOAuthService(authVendor).allowRegister) return false
                val pendingIdentity = objectMapper.readValue(pendingIdentityJson, OAuthUser::class.java)
                val account = accountService.ensureExternalAccount(oAuthUser, request)
                accountService.linkExternalIdentity(account, pendingIdentity)
                establishExternalSession(session, account, oAuthUser)
                return true
            }

            if (!accountService.isKnownExternalAccount(oAuthUser) && !getOAuthService(authVendor).allowRegister) {
                session.setAttribute(SessionAttribute.IS_GUEST, true)
                session.setAttribute(SessionAttribute.PENDING_OAUTH_USER.attributeName, objectMapper.writeValueAsString(oAuthUser))
                session.setAttribute(SessionAttribute.LOGIN_LINK_REQUIRED.attributeName, true)
                return true
            }

            val account = accountService.ensureExternalAccount(oAuthUser, request)

            session.removeAttribute(SessionAttribute.OAUTH_STATE)
            establishExternalSession(session, account, oAuthUser)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun pendingRegistrationProvider(session: HttpSession): String? {
        if (session.getAttribute(SessionAttribute.LOGIN_LINK_REQUIRED.attributeName) != true) return null
        val pendingIdentity = session.getAttribute(SessionAttribute.PENDING_OAUTH_USER.attributeName) as? String ?: return null
        return runCatching { objectMapper.readValue(pendingIdentity, OAuthUser::class.java).authVendor }.getOrNull()
            ?.let(::providerDisplayName)
    }

    fun hasPendingSecondFactor(session: HttpSession): Boolean {
        val startedAt = session.getAttribute(SessionAttribute.PENDING_MFA_STARTED_AT.attributeName) as? Long
        val pending = session.getAttribute(SessionAttribute.PENDING_MFA_ACCOUNT_ID.attributeName) is String &&
            session.getAttribute(SessionAttribute.PENDING_MFA_OAUTH_USER.attributeName) is String
        if (!pending || startedAt == null || startedAt < Instant.now().epochSecond - PENDING_MFA_TTL_SECONDS) {
            clearPendingSecondFactor(session)
            return false
        }
        return true
    }

    fun pendingSecondFactorAccount(session: HttpSession): Account? {
        if (!hasPendingSecondFactor(session)) return null
        val accountId = (session.getAttribute(SessionAttribute.PENDING_MFA_ACCOUNT_ID.attributeName) as? String)
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
            ?: return null
        return accountService.findAccount(accountId)
    }

    fun cancelPendingSecondFactor(session: HttpSession) = clearPendingSecondFactor(session)

    fun beginPendingPasswordSecondFactor(request: HttpServletRequest, account: Account) {
        var session = request.session
        runCatching { session.invalidate() }
        session = request.session
        beginPendingSecondFactor(session, account, localOAuthUser(account))
    }

    fun completePendingSecondFactor(session: HttpSession, totpCode: String?, securityCode: String?, emailCode: String?): Boolean {
        if (!hasPendingSecondFactor(session)) return false
        val accountId = (session.getAttribute(SessionAttribute.PENDING_MFA_ACCOUNT_ID.attributeName) as? String)
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() } ?: return false
        val oauthJson = session.getAttribute(SessionAttribute.PENDING_MFA_OAUTH_USER.attributeName) as? String ?: return false
        val account = accountService.findAccount(accountId) ?: return false
        accountService.requireLoginAllowed(account)
        accountSecurity.verifyExternalSecondFactor(account, totpCode, securityCode, emailCode)
        val oauthUser = objectMapper.readValue(oauthJson, OAuthUser::class.java)
        clearPendingSecondFactor(session)
        session.setAttribute(SessionAttribute.IS_GUEST, false)
        session.setOAuthUser(oauthUser)
        accountService.bindSession(session, account)
        if (oauthUser.authVendor == AuthVendor.LOCAL) accountSecurity.recordPasswordLoginSuccess(account)
        return true
    }

    private fun establishExternalSession(session: HttpSession, account: Account, oauthUser: OAuthUser) {
        if (accountSecurity.requiresExternalSecondFactor(account)) {
            beginPendingSecondFactor(session, account, oauthUser)
            return
        }
        session.setAttribute(SessionAttribute.IS_GUEST, false)
        session.setOAuthUser(oauthUser)
        accountService.bindSession(session, account)
    }

    private fun beginPendingSecondFactor(session: HttpSession, account: Account, oauthUser: OAuthUser) {
        session.setAttribute(SessionAttribute.IS_GUEST, true)
        session.setAttribute(SessionAttribute.PENDING_MFA_ACCOUNT_ID, account.id.toString())
        session.setAttribute(SessionAttribute.PENDING_MFA_OAUTH_USER, objectMapper.writeValueAsString(oauthUser))
        session.setAttribute(SessionAttribute.PENDING_MFA_STARTED_AT, Instant.now().epochSecond)
    }

    private fun clearPendingSecondFactor(session: HttpSession) {
        session.removeAttribute(SessionAttribute.PENDING_MFA_ACCOUNT_ID)
        session.removeAttribute(SessionAttribute.PENDING_MFA_OAUTH_USER)
        session.removeAttribute(SessionAttribute.PENDING_MFA_STARTED_AT)
    }

    private fun localOAuthUser(account: Account): OAuthUser {
        val nickname = userDao.getUser(account.legacyUserId)?.nickname ?: account.legacyUserId
        return OAuthUser(AuthVendor.LOCAL, account.legacyUserId, nickname, null, null, null, null)
    }

    private fun providerDisplayName(vendor: AuthVendor): String = when (vendor) {
        AuthVendor.DALDALSO -> "Daldalso"
        AuthVendor.DISCORD -> "Discord"
        AuthVendor.NAVER -> "네이버"
        AuthVendor.FACEBOOK -> "Facebook"
        AuthVendor.GOOGLE -> "Google"
        AuthVendor.KAKAO -> "카카오"
        AuthVendor.GITHUB -> "GitHub"
        AuthVendor.LOCAL -> "로컬"
    }

    fun beginIdentityLink(session: HttpSession, account: Account, authVendor: AuthVendor): String? {
        session.setAttribute(SessionAttribute.OAUTH_LINK_ACCOUNT_ID, account.id.toString())
        return getAuthorizationUrl(session, authVendor)
    }

    fun getSessionProfile(session: HttpSession): SessionProfile? {
        if (session.isGuest()) return null
        val oAuthUser = session.getOAuthUser()

        val userId = oAuthUser.getUserId()
        val user = userDao.getUser(userId)

        val authType = oAuthUser.authVendor.name.lowercase()
        val title = if (user == null) oAuthUser.name else (user.nickname ?: oAuthUser.name)

        return SessionProfile(
            authType = authType,
            id = authType + "-" + oAuthUser.vendorId,
            name = oAuthUser.name,
            title = title,
            image = oAuthUser.profileImage ?: ""
        )
    }

    fun getOAuthServiceFromSession(session: HttpSession): OAuthService {
        val oAuthUser = session.getOAuthUser()
        val authType = oAuthUser.authVendor.name.lowercase()
        val vendorType = AuthVendor.fromName(authType)!!
        return getOAuthService(vendorType)
    }

    fun loginWithAccount(request: HttpServletRequest, account: Account) {
        accountService.requireLoginAllowed(account)
        var session = request.session
        runCatching { session.invalidate() }
        session = request.session
        session.setAttribute(SessionAttribute.IS_GUEST, false)
        session.setOAuthUser(localOAuthUser(account))
        accountService.bindSession(session, account)
    }

    private fun getOAuthService(authVendor: AuthVendor): OAuthService {
        return when (authVendor) {
            AuthVendor.DALDALSO -> daldalsoOAuthService
            AuthVendor.FACEBOOK -> facebookOAuthService
            AuthVendor.GOOGLE -> googleOAuthService
            AuthVendor.NAVER -> naverOAuthService
            AuthVendor.GITHUB -> githubOAuthService
            AuthVendor.DISCORD -> discordOAuthService
            AuthVendor.KAKAO -> kakaoOAuthService
            AuthVendor.LOCAL -> throw IllegalArgumentException("LOCAL is not an external OAuth provider")
        }
    }

    private fun getRandomState(): String = SecretTools.randomToken(32)
}
