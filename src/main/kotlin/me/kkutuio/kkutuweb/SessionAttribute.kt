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

package me.kkutuio.kkutuweb

enum class SessionAttribute(val attributeName: String) {
    IS_GUEST("isGuest"),
    OAUTH_STATE("oAuthState"),
    OAUTH_USER("oAuthUser"),
    ACCOUNT_ID("accountId"),
    RECENT_AUTH_AT("recentAuthAt"),
    AFTER_LOGIN_URL("afterLoginUrl"),
    AUTHENTICATED_AT("authenticatedAt"),
    OAUTH_LINK_ACCOUNT_ID("oauthLinkAccountId"),
    OAUTH_REAUTH_ACCOUNT_ID("oauthReauthAccountId"),
    PENDING_OAUTH_USER("pendingOAuthUser"),
    LOGIN_LINK_REQUIRED("loginLinkRequired"),
    PENDING_MFA_ACCOUNT_ID("pendingMfaAccountId"),
    PENDING_MFA_OAUTH_USER("pendingMfaOAuthUser"),
    PENDING_MFA_STARTED_AT("pendingMfaStartedAt")
}
