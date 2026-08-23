package me.kkutuio.kkutuweb.identity.oauth

import me.kkutuio.kkutuweb.SessionAttribute
import me.kkutuio.kkutuweb.extension.getAccountId
import me.kkutuio.kkutuweb.identity.*
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession
import java.io.Serializable

data class ConsentRequest(val clientId: String, val redirectUri: String, val scopes: Set<String>, val state: String, val nonce: String?, val challenge: String) : Serializable

@RestController
class OidcMetadataController(private val settings: IdentityProviderSettings, private val signer: TokenSigner) {
    @GetMapping("/.well-known/openid-configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun discovery(): Map<String, Any> {
        val issuer = settings.normalizedIssuer
        return linkedMapOf(
            "issuer" to issuer,
            "authorization_endpoint" to "$issuer/oauth/authorize",
            "token_endpoint" to "$issuer/oauth/token",
            "userinfo_endpoint" to "$issuer/oauth/userinfo",
            "jwks_uri" to "$issuer/.well-known/jwks.json",
            "introspection_endpoint" to "$issuer/oauth/introspect",
            "revocation_endpoint" to "$issuer/oauth/revoke",
            "response_types_supported" to listOf("code"),
            "grant_types_supported" to listOf("authorization_code", "refresh_token"),
            "subject_types_supported" to listOf("public"),
            "id_token_signing_alg_values_supported" to listOf("RS256"),
            "scopes_supported" to listOf("openid", "profile", "email", "account", "offline", "game:kkutu", "admin:access"),
            "code_challenge_methods_supported" to listOf("S256"),
            "token_endpoint_auth_methods_supported" to listOf("client_secret_basic", "client_secret_post", "none")
        )
    }

    @GetMapping("/.well-known/jwks.json", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun jwks(): Map<String, Any> = signer.jwks()
}

@Controller
class OidcAuthorizeController(private val oidc: OidcService, private val accounts: AccountService, private val dao: IdentityDao) {
    @GetMapping("/oauth/authorize")
    fun authorize(
        @RequestParam client_id: String,
        @RequestParam redirect_uri: String,
        @RequestParam response_type: String,
        @RequestParam scope: String,
        @RequestParam state: String,
        @RequestParam code_challenge: String,
        @RequestParam code_challenge_method: String,
        @RequestParam(required = false) nonce: String?,
        @RequestParam(required = false, defaultValue = "") prompt: String,
        session: HttpSession,
        request: HttpServletRequest,
    ): String {
        if (response_type != "code" || state.isBlank()) throw IdpException("invalid_request", "response_type=code 및 state가 필요합니다.")
        val scopes = scope.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        if ("openid" in scopes && nonce.isNullOrBlank()) throw IdpException("invalid_request", "OIDC 요청에는 nonce가 필요합니다.")
        val client = oidc.validateAuthorization(client_id, redirect_uri, scopes, code_challenge, code_challenge_method)
        val account = accounts.currentAccount(session)
        if (account == null) {
            val continuation = ServletUriComponentsBuilder.fromRequest(request).build().toUriString()
            session.setAttribute(SessionAttribute.AFTER_LOGIN_URL.attributeName, continuation.removePrefix(request.scheme + "://" + request.serverName + if (request.serverPort in setOf(80, 443)) "" else ":${request.serverPort}"))
            return "redirect:/login"
        }
        val consent = ConsentRequest(client_id, redirect_uri, scopes, state, nonce, code_challenge)
        if (!dao.consentedScopes(account.id, client.clientId).containsAll(scopes)) {
            if (prompt == "none") {
                return "redirect:" + UriComponentsBuilder.fromUriString(redirect_uri).queryParam("error", "interaction_required").queryParam("state", state).build(true).toUriString()
            }
            session.setAttribute("oidcConsent", consent)
            return "redirect:/oauth/consent"
        }
        val code = oidc.issueAuthorizationCode(account, client, redirect_uri, scopes, nonce, code_challenge)
        val location = UriComponentsBuilder.fromUriString(redirect_uri).queryParam("code", code).queryParam("state", state).build(true).toUriString()
        return "redirect:$location"
    }

    /** Frontpage reads the pending request from the server-side session only. */
    @GetMapping("/oauth/authorize/consent", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun consentDetails(session: HttpSession): Map<String, Any?> {
        val request = session.getAttribute("oidcConsent") as? ConsentRequest
            ?: throw IdpException("invalid_request", "만료된 동의 요청입니다.")
        val account = accounts.requireCurrentAccount(session)
        val client = dao.findClient(request.clientId)
            ?: throw IdpException("unauthorized_client", "등록되지 않은 클라이언트입니다.")
        return mapOf(
            "client_name" to client.clientName,
            "client_logo_uri" to client.logoUri,
            "scopes" to request.scopes.sorted(),
            "profiles" to dao.listProfiles(account.id)
        )
    }

    @PostMapping("/oauth/authorize/consent")
    fun consent(@RequestParam(required = false, defaultValue = "false") approve: Boolean, @RequestParam(name = "profile_id", required = false) profileId: String?, session: HttpSession): String {
        val request = session.getAttribute("oidcConsent") as? ConsentRequest ?: throw IdpException("invalid_request", "만료된 동의 요청입니다.")
        session.removeAttribute("oidcConsent")
        if (!approve) return "redirect:" + UriComponentsBuilder.fromUriString(request.redirectUri).queryParam("error", "access_denied").queryParam("state", request.state).build(true).toUriString()
        val account = accounts.requireCurrentAccount(session)
        dao.grantConsent(account.id, request.clientId, request.scopes)
        val client = dao.findClient(request.clientId) ?: throw IdpException("unauthorized_client", "등록되지 않은 클라이언트입니다.")
        val selectedProfileId = profileId?.takeIf { it.isNotBlank() }?.let {
            runCatching { java.util.UUID.fromString(it) }.getOrElse { throw IdpException("invalid_request", "잘못된 게임 프로필입니다.") }
        }
        val code = oidc.issueAuthorizationCode(account, client, request.redirectUri, request.scopes, request.nonce, request.challenge, selectedProfileId)
        return "redirect:" + UriComponentsBuilder.fromUriString(request.redirectUri).queryParam("code", code).queryParam("state", request.state).build(true).toUriString()
    }
}

@RestController
class OidcTokenController(private val oidc: OidcService) {
    @PostMapping("/oauth/token", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun token(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?, @RequestParam params: MultiValueMap<String, String>): Map<String, Any?> {
        val basic = basicClient(authorization)
        val clientId = basic?.first ?: params.getFirst("client_id") ?: throw IdpException("invalid_request", "client_id가 필요합니다.")
        val secret = basic?.second ?: params.getFirst("client_secret")
        val token = when (params.getFirst("grant_type")) {
            "authorization_code" -> oidc.exchangeCode(clientId, secret, params.getFirst("code") ?: throw IdpException("invalid_request", "code가 필요합니다."),
                params.getFirst("redirect_uri") ?: throw IdpException("invalid_request", "redirect_uri가 필요합니다."),
                params.getFirst("code_verifier") ?: throw IdpException("invalid_request", "code_verifier가 필요합니다."))
            "refresh_token" -> oidc.refresh(clientId, secret, params.getFirst("refresh_token") ?: throw IdpException("invalid_request", "refresh_token이 필요합니다."))
            else -> throw IdpException("unsupported_grant_type", "지원하지 않는 grant_type입니다.")
        }
        return linkedMapOf<String, Any?>("access_token" to token.accessToken, "token_type" to token.tokenType, "expires_in" to token.expiresIn, "scope" to token.scope).also {
            token.idToken?.let { value -> it["id_token"] = value }; token.refreshToken?.let { value -> it["refresh_token"] = value }
        }
    }

    @GetMapping("/oauth/userinfo", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun userInfo(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?): Map<String, Any?> = oidc.userInfo(bearer(authorization))

    @PostMapping("/oauth/introspect", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun introspect(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?, @RequestParam params: MultiValueMap<String, String>): Map<String, Any?> {
        val basic = basicClient(authorization)
        return oidc.introspect(basic?.first ?: params.getFirst("client_id") ?: throw IdpException("invalid_client", "client_id가 필요합니다.", 401), basic?.second ?: params.getFirst("client_secret"), params.getFirst("token") ?: throw IdpException("invalid_request", "token이 필요합니다."))
    }

    @PostMapping("/oauth/revoke", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun revoke(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?, @RequestParam params: MultiValueMap<String, String>): ResponseEntity<Void> {
        val basic = basicClient(authorization)
        oidc.revoke(basic?.first ?: params.getFirst("client_id") ?: throw IdpException("invalid_client", "client_id가 필요합니다.", 401), basic?.second ?: params.getFirst("client_secret"), params.getFirst("token") ?: throw IdpException("invalid_request", "token이 필요합니다."))
        return ResponseEntity.ok().build()
    }

    private fun bearer(value: String?): String = value?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.takeIf { it.isNotBlank() }
        ?: throw IdpException("invalid_token", "Bearer token이 필요합니다.", 401)
    private fun basicClient(value: String?): Pair<String, String>? {
        if (value?.startsWith("Basic ") != true) return null
        val decoded = String(Base64.getDecoder().decode(value.removePrefix("Basic ")), StandardCharsets.UTF_8)
        val separator = decoded.indexOf(':')
        if (separator < 1) throw IdpException("invalid_client", "잘못된 client 인증입니다.", 401)
        return decoded.substring(0, separator) to decoded.substring(separator + 1)
    }
}
