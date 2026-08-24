package me.kkutuio.kkutuweb.moderation.policy

import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultApplicationArguments
import java.time.Instant

class ModerationPolicyEngineTest {
    private lateinit var engine: ModerationPolicyEngine
    private lateinit var loader: ModerationPolicyLoader

    @BeforeEach
    fun setUp() {
        loader = ModerationPolicyLoader(
            DefaultApplicationArguments(),
            jacksonObjectMapper()
        )
        loader.initialize()
        engine = ModerationPolicyEngine(loader)
    }

    @Test
    fun `default policy contains the complete official matrix`() {
        val policy = loader.current().document

        assertEquals((0..18).map { it.toString().padStart(2, '0') }, policy.categories.map { it.code })
        assertEquals(67, policy.categories.sumOf { it.steps.size })
        assertEquals("https://static.kkutu.io/operation", policy.source.url)
    }

    @Test
    fun `category 11 is permanently restricted on first offense`() {
        val preview = engine.preview(listOf("11"), emptyMap(), Instant.parse("2026-07-26T00:00:00Z"))

        assertEquals(1, preview.violations.single().offenseNo)
        assertTrue(preview.effects.any {
            it.type == "GAME_RESTRICTION" && it.permanent && it.endsAt == null
        })
    }

    @Test
    fun `all selected categories increment while strongest effect of the same type is applied`() {
        val preview = engine.preview(
            listOf("02", "04"),
            mapOf("02" to 1, "04" to 2),
            Instant.parse("2026-07-26T00:00:00Z")
        )

        assertEquals(mapOf("02" to 2, "04" to 3), preview.violations.associate {
            it.categoryCode to it.offenseNo
        })
        assertEquals("02", preview.primaryCategoryCode)
        assertEquals(
            Instant.parse("2026-08-09T00:00:00Z"),
            preview.effects.single { it.type == "GAME_RESTRICTION" }.endsAt
        )
    }

    @Test
    fun `different effect types are merged and chat restriction follows game restriction`() {
        val startsAt = Instant.parse("2026-07-26T00:00:00Z")
        val preview = engine.preview(listOf("01", "03", "10"), emptyMap(), startsAt)

        assertEquals("10", preview.primaryCategoryCode)
        assertEquals(
            "비정상적인 플레이(어뷰징)",
            preview.violations.single { it.selectedAsPrimary }.categoryName
        )
        assertEquals(
            setOf("NICKNAME_RESET", "CHAT_RESTRICTION", "RESOURCE_ADJUSTMENT", "GAME_RESTRICTION"),
            preview.effects.map { it.type }.toSet()
        )
        assertEquals(
            Instant.parse("2026-08-02T00:00:00Z"),
            preview.effects.single { it.type == "GAME_RESTRICTION" }.endsAt
        )
        assertEquals(
            Instant.parse("2026-08-05T00:00:00Z"),
            preview.effects.single { it.type == "CHAT_RESTRICTION" }.endsAt
        )
        assertEquals(
            "GAME_RESTRICTION",
            preview.effects.single { it.type == "CHAT_RESTRICTION" }
                .parameters["durationStackedAfter"]
        )
        assertTrue(preview.requiresApproval)
    }

    @Test
    fun `overflow uses the configured last or section 7 rule`() {
        val repeatLast = engine.preview(listOf("02"), mapOf("02" to 99))
        val sectionSeven = engine.preview(listOf("01"), mapOf("01" to 99))

        assertEquals(100, repeatLast.violations.single().offenseNo)
        assertEquals(100, sectionSeven.violations.single().offenseNo)
        assertTrue(repeatLast.effects.isNotEmpty())
        assertTrue(sectionSeven.effects.isNotEmpty())
    }

    @Test
    fun `custom sanction uses explicit duration without an official counter`() {
        val startsAt = Instant.parse("2026-08-04T00:00:00Z")
        val endsAt = Instant.parse("2026-08-05T00:00:00Z")

        val preview = engine.previewCustom("관리자 직접 제재", startsAt, endsAt, false)

        assertEquals("99", preview.primaryCategoryCode)
        assertEquals("GAME_RESTRICTION", preview.effects.single().type)
        assertEquals(endsAt, preview.effects.single().endsAt)
        assertEquals("관리자 직접 제재", preview.effects.single().parameters["customReason"])
    }

    @Test
    fun `guest policy allows members for first two sanctions and blocks the ip on third`() {
        val startsAt = Instant.parse("2026-08-04T00:00:00Z")

        val first = engine.previewGuestIp(listOf("10"), emptyMap(), 0, startsAt)
        val second = engine.previewGuestIp(listOf("10"), mapOf("10" to 1), 1, startsAt)
        val third = engine.previewGuestIp(listOf("10"), mapOf("10" to 2), 2, startsAt)

        assertEquals("GUEST_ACCESS_RESTRICTION", first.effects.single().type)
        assertEquals("GUEST_ACCESS_RESTRICTION", second.effects.single().type)
        assertEquals("IP_RESTRICTION", third.effects.single().type)
        assertEquals(3, third.effects.single().parameters["guestIpOffenseNo"])
    }

    @Test
    fun `guest restriction keeps the selected violation bundle duration`() {
        val startsAt = Instant.parse("2026-08-04T00:00:00Z")
        val preview = engine.previewGuestIp(listOf("10"), emptyMap(), 0, startsAt)

        assertEquals(
            Instant.parse("2026-08-11T00:00:00Z"),
            preview.effects.single().endsAt
        )
        assertEquals("GAME_RESTRICTION", preview.effects.single().parameters["durationSourceEffect"])
    }

    @Test
    fun `permanent guest sanction skips the second guest-only step`() {
        val preview = engine.previewGuestIp(listOf("11"), mapOf("11" to 1), 1)

        assertEquals("IP_RESTRICTION", preview.effects.single().type)
        assertTrue(preview.effects.single().permanent)
        assertEquals(3, preview.effects.single().parameters["guestIpOffenseNo"])
    }
}
