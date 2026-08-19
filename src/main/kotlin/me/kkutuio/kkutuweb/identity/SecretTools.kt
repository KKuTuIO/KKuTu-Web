package me.kkutuio.kkutuweb.identity

import de.mkammerer.argon2.Argon2Factory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object SecretTools {
    private val random = SecureRandom()
    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    fun randomToken(bytes: Int = 32): String = ByteArray(bytes).also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun hashPassword(value: CharArray): String = try {
        argon2.hash(3, 65_536, 1, value)
    } finally {
        value.fill('\u0000')
    }

    fun verifyPassword(hash: String, value: CharArray): Boolean = try {
        argon2.verify(hash, value)
    } finally {
        value.fill('\u0000')
    }

    fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8)
    )
}
