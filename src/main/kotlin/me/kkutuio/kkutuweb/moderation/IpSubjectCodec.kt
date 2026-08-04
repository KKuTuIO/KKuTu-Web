package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.AES256
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class IpSubjectCodec(
    private val aes256: AES256,
    private val setting: KKuTuSetting
) {
    fun encrypt(ip: String): ByteArray = aes256.encrypt(ip).toByteArray(StandardCharsets.UTF_8)

    fun decrypt(value: ByteArray): String = aes256.decrypt(String(value, StandardCharsets.UTF_8))

    fun hash(ip: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(setting.getCryptoKey().toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(ip.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
