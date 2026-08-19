package me.kkutuio.kkutuweb.identity

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class SecretCipher(@Value("\${idp.data-encryption-key:}") encodedKey: String) {
    private val key = if (encodedKey.isBlank()) null else runCatching { Base64.getDecoder().decode(encodedKey) }.getOrNull()
    private val random = SecureRandom()
    fun encrypt(value: String): String {
        val material = key?.takeIf { it.size == 32 } ?: throw IdpException("temporarily_unavailable", "idp.data-encryption-key 설정이 필요합니다.", 503)
        val iv = ByteArray(12).also(random::nextBytes)
        val encrypted = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(material, "AES"), GCMParameterSpec(128, iv)) }.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv + encrypted)
    }
    fun decrypt(value: String): String {
        val material = key?.takeIf { it.size == 32 } ?: throw IdpException("temporarily_unavailable", "idp.data-encryption-key 설정이 필요합니다.", 503)
        val bytes = Base64.getUrlDecoder().decode(value); require(bytes.size > 28) { "Invalid encrypted value" }
        return String(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(material, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
