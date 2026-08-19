package me.kkutuio.kkutuweb.identity

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object Totp {
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun newSecret(): String = ByteArray(20).also(java.security.SecureRandom()::nextBytes).let(::encode)
    fun verify(secret: String, code: String, nowMillis: Long = System.currentTimeMillis()): Boolean = (-1L..1L).any { offset ->
        constantTime(code, generate(secret, nowMillis / 30_000L + offset))
    }
    fun generate(secret: String, counter: Long): String {
        val message = ByteArray(8) { index -> (counter ushr (56 - index * 8)).toByte() }
        val digest = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(decode(secret), "HmacSHA1")) }.doFinal(message)
        val offset = digest.last().toInt() and 0x0f
        val binary = ((digest[offset].toInt() and 0x7f) shl 24) or ((digest[offset + 1].toInt() and 0xff) shl 16) or ((digest[offset + 2].toInt() and 0xff) shl 8) or (digest[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }
    fun encode(bytes: ByteArray): String {
        var buffer = 0; var bits = 0
        return buildString {
            bytes.forEach { byte ->
                buffer = (buffer shl 8) or (byte.toInt() and 0xff); bits += 8
                while (bits >= 5) { append(alphabet[(buffer shr (bits - 5)) and 31]); bits -= 5 }
            }
            if (bits > 0) append(alphabet[(buffer shl (5 - bits)) and 31])
        }
    }
    fun decode(value: String): ByteArray {
        var buffer = 0; var bits = 0
        val out = ArrayList<Byte>()
        value.uppercase().filter { it != '=' && !it.isWhitespace() }.forEach { char ->
            val valueAt = alphabet.indexOf(char); require(valueAt >= 0) { "Invalid Base32 secret" }
            buffer = (buffer shl 5) or valueAt; bits += 5
            if (bits >= 8) { out += (buffer shr (bits - 8)).toByte(); bits -= 8 }
        }
        return out.toByteArray()
    }
    private fun constantTime(a: String, b: String) = java.security.MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
