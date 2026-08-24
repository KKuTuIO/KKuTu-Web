package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpSession

data class IdpClientRequest(
    val clientId: String,
    val clientName: String,
    val logoUri: String? = null,
    val clientType: ClientType,
    val redirectUris: Set<String>,
    val allowedScopes: Set<String>,
    val firstParty: Boolean = false,
    val active: Boolean = true,
    val accessTokenTtlSeconds: Long = 3600,
    val refreshTokenTtlSeconds: Long = 2592000
)

@RestController
@RequestMapping("/api/admin/oauth-clients")
class IdpAdminApi(private val dao: IdentityDao, private val authorizer: AdminModerationAuthorizer) {
    @GetMapping fun list(session: HttpSession): List<Map<String, Any?>> {
        authorizer.require(session, AdminSetting.Privilege.IDP_CLIENT_MANAGE)
        return dao.listClients().map { mapOf("clientId" to it.clientId, "clientName" to it.clientName, "logoUri" to it.logoUri, "clientType" to it.type.name, "redirectUris" to it.redirectUris, "allowedScopes" to it.allowedScopes, "firstParty" to it.firstParty, "active" to it.active, "accessTokenTtlSeconds" to it.accessTokenTtlSeconds, "refreshTokenTtlSeconds" to it.refreshTokenTtlSeconds) }
    }
    @PostMapping fun create(@RequestBody request: IdpClientRequest, session: HttpSession): Map<String, String?> {
        authorizer.require(session, AdminSetting.Privilege.IDP_CLIENT_MANAGE)
        require(request.clientId.matches(Regex("^[A-Za-z0-9._-]{3,128}$"))) { "유효하지 않은 client_id입니다." }
        validateLogoUri(request.logoUri)
        require(request.redirectUris.isNotEmpty() && request.redirectUris.all { runCatching { val uri = java.net.URI(it); uri.isAbsolute && uri.fragment == null && uri.userInfo == null }.getOrDefault(false) }) { "완전한 redirect URI가 필요합니다." }
        val secret = if (request.clientType == ClientType.CONFIDENTIAL) SecretTools.randomToken(32) else null
        dao.saveClient(RegisteredClient(request.clientId, request.clientName.trim().take(255), normalizedLogoUri(request.logoUri), request.clientType, secret?.let { SecretTools.hashPassword(it.toCharArray()) }, request.redirectUris, request.allowedScopes, request.firstParty, request.active, request.accessTokenTtlSeconds.coerceIn(60, 86400), request.refreshTokenTtlSeconds.coerceIn(300, 31_536_000)))
        return mapOf("clientId" to request.clientId, "clientSecret" to secret)
    }
    @PutMapping("/{clientId}") fun update(@PathVariable clientId: String, @RequestBody request: IdpClientRequest, session: HttpSession) {
        authorizer.require(session, AdminSetting.Privilege.IDP_CLIENT_MANAGE)
        val existing = dao.findClient(clientId) ?: throw IdpException("not_found", "OAuth 애플리케이션을 찾을 수 없습니다.", 404)
        require(request.clientId == clientId && request.clientType == existing.type) { "Client ID와 유형은 변경할 수 없습니다." }
        validateLogoUri(request.logoUri)
        require(request.redirectUris.isNotEmpty() && request.redirectUris.all { runCatching { val uri = java.net.URI(it); uri.isAbsolute && uri.fragment == null && uri.userInfo == null }.getOrDefault(false) }) { "완전한 redirect URI가 필요합니다." }
        dao.updateClient(existing.copy(clientName = request.clientName.trim().take(255), logoUri = normalizedLogoUri(request.logoUri), redirectUris = request.redirectUris, allowedScopes = request.allowedScopes, firstParty = request.firstParty, active = request.active, accessTokenTtlSeconds = request.accessTokenTtlSeconds.coerceIn(60, 86400), refreshTokenTtlSeconds = request.refreshTokenTtlSeconds.coerceIn(300, 31_536_000)))
    }
    @PostMapping("/{clientId}/rotate-secret") fun rotateSecret(@PathVariable clientId: String, session: HttpSession): Map<String, String> {
        authorizer.require(session, AdminSetting.Privilege.IDP_CLIENT_MANAGE)
        val client = dao.findClient(clientId) ?: throw IdpException("not_found", "OAuth 애플리케이션을 찾을 수 없습니다.", 404)
        if (client.type != ClientType.CONFIDENTIAL) throw IdpException("invalid_request", "공개 Client는 Secret을 사용하지 않습니다.")
        val secret = SecretTools.randomToken(32); dao.rotateClientSecret(clientId, SecretTools.hashPassword(secret.toCharArray())); return mapOf("clientSecret" to secret)
    }

    private fun normalizedLogoUri(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
    private fun validateLogoUri(value: String?) {
        val uri = normalizedLogoUri(value) ?: return
        require(uri.length <= 2048 && runCatching {
            val parsed = java.net.URI(uri)
            parsed.isAbsolute && parsed.scheme.lowercase() in setOf("http", "https") && parsed.fragment == null && parsed.userInfo == null
        }.getOrDefault(false)) { "HTTP 또는 HTTPS 애플리케이션 아이콘 URL이 필요합니다." }
    }
}
