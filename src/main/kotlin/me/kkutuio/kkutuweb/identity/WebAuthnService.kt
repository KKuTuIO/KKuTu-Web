package me.kkutuio.kkutuweb.identity

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.upokecenter.cbor.CBORObject
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class WebAuthnService(private val dao: IdentityDao, private val settings: IdentityProviderSettings, private val objectMapper: ObjectMapper) {
    fun registrationOptions(account: Account): Map<String, Any> {
        if (dao.listPasskeys(account.id).size >= 10) throw IdpException("passkey_limit", "패스키는 계정당 10개까지 등록할 수 있습니다.")
        val challenge = SecretTools.randomToken(); val token = SecretTools.randomToken()
        dao.saveOneTimeToken(SecretTools.sha256(token), account.id, "PASSKEY_REGISTER", mapOf("challenge" to challenge), Instant.now().plusSeconds(300))
        return mapOf("operation_token" to token, "publicKey" to mapOf(
            "challenge" to challenge, "rp" to mapOf("id" to settings.rpId, "name" to "KKuTuIO"),
            "user" to mapOf("id" to b64(account.id.toString().toByteArray()), "name" to account.legacyUserId, "displayName" to account.legacyUserId),
            "pubKeyCredParams" to listOf(mapOf("type" to "public-key", "alg" to -7), mapOf("type" to "public-key", "alg" to -257)),
            "timeout" to 300000, "attestation" to "none", "authenticatorSelection" to mapOf("userVerification" to "preferred")
        ))
    }
    @Transactional
    fun completeRegistration(account: Account, operationToken: String, credential: JsonNode, deviceName: String) {
        val row = dao.consumeOneTimeToken(SecretTools.sha256(operationToken), "PASSKEY_REGISTER") ?: throw IdpException("invalid_token", "만료되었거나 이미 사용된 패스키 요청입니다.")
        if (row["account_id"].toString() != account.id.toString()) throw IdpException("forbidden", "다른 계정의 패스키 요청입니다.", 403)
        dao.lockAccount(account.id) ?: throw IdpException("not_found", "계정을 찾을 수 없습니다.", 404)
        if (dao.listPasskeys(account.id).size >= 10) throw IdpException("passkey_limit", "패스키는 계정당 10개까지 등록할 수 있습니다.")
        val expected = payloadValue(row["payload"].toString(), "challenge") ?: throw IdpException("invalid_token", "잘못된 패스키 요청입니다.")
        val clientData = decodeJson(credential.path("response").path("clientDataJSON").asText())
        validateClientData(clientData, expected, "webauthn.create")
        val attestation = CBORObject.DecodeFromBytes(decode(credential.path("response").path("attestationObject").asText()))
        val authData = attestation[CBORObject.FromObject("authData")].GetByteString()
        validateAuthenticatorData(authData, requireUserVerification = false)
        val flags = authData[32].toInt() and 0xff
        if (flags and 0x40 == 0) throw IdpException("invalid_credential", "Attested credential data가 없습니다.")
        val credentialLength = ((authData[53].toInt() and 0xff) shl 8) or (authData[54].toInt() and 0xff)
        val credentialId = b64(authData.copyOfRange(55, 55 + credentialLength))
        if (dao.findPasskeyByCredential(credentialId) != null) throw IdpException("identity_conflict", "이미 등록된 패스키입니다.")
        val cose = authData.copyOfRange(55 + credentialLength, authData.size)
        // Decoding confirms the supplied credential key is a valid COSE key before persistence.
        cosePublicKey(CBORObject.DecodeFromBytes(cose))
        val identity = dao.insertIdentity(account.id, IdentityType.PASSKEY, "PASSKEY", credentialId, displayName = deviceName.trim().take(255).ifBlank { "Passkey" }, verified = true)
        dao.insertPasskey(account.id, identity.id, credentialId, b64(cose), identity.displayName ?: "Passkey")
        dao.audit(account.id, "PASSKEY_REGISTERED", identity.id)
    }
    fun authenticationOptions(): Map<String, Any> {
        val challenge = SecretTools.randomToken(); val token = SecretTools.randomToken()
        dao.saveOneTimeToken(SecretTools.sha256(token), null, "PASSKEY_AUTH", mapOf("challenge" to challenge), Instant.now().plusSeconds(300))
        return mapOf("operation_token" to token, "publicKey" to mapOf("challenge" to challenge, "rpId" to settings.rpId, "timeout" to 300000, "userVerification" to "preferred"))
    }
    @Transactional
    fun completeAuthentication(operationToken: String, credential: JsonNode): Account {
        val row = dao.consumeOneTimeToken(SecretTools.sha256(operationToken), "PASSKEY_AUTH") ?: throw IdpException("invalid_token", "만료되었거나 이미 사용된 패스키 요청입니다.")
        val expected = payloadValue(row["payload"].toString(), "challenge") ?: throw IdpException("invalid_token", "잘못된 패스키 요청입니다.")
        val clientDataRaw = decode(credential.path("response").path("clientDataJSON").asText()); val clientData = objectMapper.readTree(clientDataRaw)
        validateClientData(clientData, expected, "webauthn.get")
        val rawId = credential.path("rawId").asText().ifBlank { credential.path("id").asText() }
        val passkey = dao.findPasskeyByCredential(rawId) ?: throw IdpException("invalid_credential", "등록되지 않은 패스키입니다.", 401)
        val authData = decode(credential.path("response").path("authenticatorData").asText()); validateAuthenticatorData(authData, requireUserVerification = false)
        val signature = decode(credential.path("response").path("signature").asText())
        val signed = authData + MessageDigest.getInstance("SHA-256").digest(clientDataRaw)
        val publicKey = cosePublicKey(CBORObject.DecodeFromBytes(decode(passkey["public_key_cose"].toString())))
        val algorithm = coseAlgorithm(CBORObject.DecodeFromBytes(decode(passkey["public_key_cose"].toString())))
        if (!Signature.getInstance(algorithm).run { initVerify(publicKey); update(signed); verify(signature) }) throw IdpException("invalid_credential", "패스키 서명이 올바르지 않습니다.", 401)
        val counter = ((authData[33].toLong() and 0xff) shl 24) or ((authData[34].toLong() and 0xff) shl 16) or ((authData[35].toLong() and 0xff) shl 8) or (authData[36].toLong() and 0xff)
        val savedCounter = (passkey["sign_count"] as Number).toLong()
        if (counter != 0L && counter <= savedCounter) throw IdpException("credential_clone_detected", "패스키 복제가 감지되었습니다.", 401)
        dao.updatePasskeyCounter((passkey["id"] as Number).toLong(), counter)
        val account = dao.findAccount(UUID.fromString(passkey["account_id"].toString())) ?: throw IdpException("invalid_credential", "계정을 찾을 수 없습니다.", 401)
        if (account.status != AccountStatus.ACTIVE) throw IdpException("invalid_credential", "로그인 정보가 올바르지 않습니다.", 401)
        dao.touchIdentity((passkey["identity_id"] as Number).toLong())
        dao.audit(account.id, "PASSKEY_LOGIN_SUCCESS", passkey["identity_id"] as Long)
        return account
    }
    private fun validateClientData(data: JsonNode, challenge: String, expectedType: String) {
        if (data.path("type").asText() != expectedType || data.path("challenge").asText() != challenge || data.path("origin").asText().trimEnd('/') !in settings.allowedOrigins) throw IdpException("invalid_credential", "WebAuthn client data가 일치하지 않습니다.")
    }
    private fun validateAuthenticatorData(data: ByteArray, requireUserVerification: Boolean) {
        if (data.size < 37 || !MessageDigest.isEqual(data.copyOfRange(0, 32), MessageDigest.getInstance("SHA-256").digest(settings.rpId.toByteArray()))) throw IdpException("invalid_credential", "WebAuthn RP ID가 일치하지 않습니다.")
        val flags = data[32].toInt() and 0xff
        if (flags and 0x01 == 0 || (requireUserVerification && flags and 0x04 == 0)) throw IdpException("invalid_credential", "사용자 확인이 완료되지 않았습니다.")
    }
    private fun coseAlgorithm(cose: CBORObject): String = when (cose[CBORObject.FromObject(3)].AsInt32()) { -7 -> "SHA256withECDSA"; -257 -> "SHA256withRSA"; else -> throw IdpException("invalid_credential", "지원하지 않는 패스키 알고리즘입니다.") }
    private fun cosePublicKey(cose: CBORObject): PublicKey = when (cose[CBORObject.FromObject(1)].AsInt32()) {
        2 -> { val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }; val spec = parameters.getParameterSpec(ECParameterSpec::class.java); KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(BigInteger(1, cose[CBORObject.FromObject(-2)].GetByteString()), BigInteger(1, cose[CBORObject.FromObject(-3)].GetByteString())), spec)) }
        3 -> KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1, cose[CBORObject.FromObject(-1)].GetByteString()), BigInteger(1, cose[CBORObject.FromObject(-2)].GetByteString())))
        else -> throw IdpException("invalid_credential", "지원하지 않는 패스키 형식입니다.")
    }
    private fun decodeJson(value: String): JsonNode = objectMapper.readTree(decode(value))
    private fun decode(value: String): ByteArray = try { Base64.getUrlDecoder().decode(value) } catch (_: IllegalArgumentException) { throw IdpException("invalid_credential", "잘못된 Base64URL 값입니다.") }
    private fun b64(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun payloadValue(payload: String, key: String): String? = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.get(1)
}
