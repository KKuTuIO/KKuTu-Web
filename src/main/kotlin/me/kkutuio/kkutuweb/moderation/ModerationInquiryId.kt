package me.kkutuio.kkutuweb.moderation

import java.security.SecureRandom

object ModerationInquiryId {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    private val pattern = Regex("^BLK-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")

    fun generate(): String = buildString(13) {
        append("BLK-")
        repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        append('-')
        repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun normalize(value: String): String {
        val normalized = value.trim().uppercase()
        require(pattern.matches(normalized)) { "올바른 문의번호를 입력해 주세요. 예: BLK-K7M4-Q2PX" }
        return normalized
    }
}
