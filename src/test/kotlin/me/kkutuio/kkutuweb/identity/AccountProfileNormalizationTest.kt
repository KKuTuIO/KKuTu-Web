package me.kkutuio.kkutuweb.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountProfileNormalizationTest {
    @Test
    fun `profile API rows have unique non-empty identities`() {
        val first = mapOf<String, Any?>("id" to "profile-1", "nickname" to "first")
        val rows = listOf(
            first,
            mapOf("id" to "profile-1", "nickname" to "duplicate"),
            mapOf("id" to null),
            emptyMap(),
            mapOf("id" to "profile-2")
        )

        assertThat(uniqueProfileRows(rows)).containsExactly(
            first,
            mapOf("id" to "profile-2")
        )
    }
}
