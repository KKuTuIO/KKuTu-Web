package me.kkutuio.kkutuweb.servers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class RecommendedChannelTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `override keeps the configured channel preferred`() {
        val configuration = RecommendedChannelConfiguration(channel = 1, overrideRecommendedChannel = true)

        assertEquals(1, selectRecommendedChannel(listOf(90, 100, 110), 100, configuration))
    }

    @Test
    fun `override falls back to the first connected channel when preferred channel is down`() {
        val configuration = RecommendedChannelConfiguration(channel = 1, overrideRecommendedChannel = true)

        assertEquals(0, selectRecommendedChannel(listOf(20, null, 80), 100, configuration))
    }

    @Test
    fun `dynamic selection chooses the busiest channel with available capacity`() {
        val configuration = RecommendedChannelConfiguration(channel = 0, overrideRecommendedChannel = false)

        assertEquals(2, selectRecommendedChannel(listOf(20, 40, 70, 60), 100, configuration))
    }

    @Test
    fun `dynamic selection excludes disconnected full and over-capacity channels`() {
        val configuration = RecommendedChannelConfiguration(channel = 3, overrideRecommendedChannel = false)

        assertEquals(3, selectRecommendedChannel(listOf(null, 100, 101, 80), 100, configuration))
    }

    @Test
    fun `dynamic selection uses configured channel when no channel is eligible`() {
        val configuration = RecommendedChannelConfiguration(channel = 2, overrideRecommendedChannel = false)

        assertEquals(2, selectRecommendedChannel(listOf(null, 100, 120), 100, configuration))
    }

    @Test
    fun `configuration reads explicit override flag`() {
        val response = objectMapper.readTree(
            """{"recommendedChannel":2,"overrideRecommendedChannel":false}"""
        )

        assertEquals(
            RecommendedChannelConfiguration(channel = 2, overrideRecommendedChannel = false),
            parseRecommendedChannelConfiguration(response, RecommendedChannelConfiguration())
        )
    }

    @Test
    fun `missing override flag preserves legacy override behavior`() {
        val response = objectMapper.readTree("""{"recommendedChannel":3}""")
        val current = RecommendedChannelConfiguration(channel = 1, overrideRecommendedChannel = false)

        assertEquals(
            RecommendedChannelConfiguration(channel = 3, overrideRecommendedChannel = true),
            parseRecommendedChannelConfiguration(response, current)
        )
    }
}
