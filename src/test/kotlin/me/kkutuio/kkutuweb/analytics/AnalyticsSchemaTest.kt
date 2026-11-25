package me.kkutuio.kkutuweb.analytics

import me.kkutuio.kkutuweb.schema.AdditiveSchemaMigrator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.ObjectMapper

class AnalyticsSchemaTest {
    private val name = "20261125_analytics_overview.sql"
    private val sql = ClassPathResource("db/additive/$name").inputStream.use { String(it.readAllBytes(), Charsets.UTF_8) }

    @Test
    fun `자동 적용 목록에 있고 새 테이블과 인덱스만 만든다`() {
        assertTrue(name in AdditiveSchemaMigrator.FILES)
        assertEquals(emptyList<String>(), AdditiveSchemaMigrator.violations(sql))
        assertEquals(listOf("analytics_daily_metric", "economy_daily_stat"), AdditiveSchemaMigrator.tablesOf(sql))
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS economy_daily_stat_date_idx ON economy_daily_stat (stat_date DESC)"))
    }

    @Test
    fun `스키마 점검 목록이 파일의 객체와 일치하고 순번이 겹치지 않는다`() {
        val all = ObjectMapper().readTree(javaClass.getResourceAsStream("/schema-expectations.json")).toList()
        val mine = all.filter { it["migration"].asString() == name }
        assertEquals(
            listOf("table" to "analytics_daily_metric", "table" to "economy_daily_stat", "index" to "economy_daily_stat_date_idx"),
            mine.map { it["kind"].asString() to it["object"].asString() }
        )
        assertTrue(mine.all { it["want"].asBoolean() })
        val orders = all.map { it["ord"].asInt() }
        assertEquals(orders.size, orders.toSet().size)
    }

    @Test
    fun `통계 저장 문장의 충돌 기준은 테이블의 기본 키와 같다`() {
        assertTrue(sql.contains("PRIMARY KEY (stat_date, direction, source)"))
        assertTrue(EconomyStatsRecorder.UPSERT.contains("ON CONFLICT (stat_date, direction, source)"))
        assertTrue(sql.contains("stat_date date PRIMARY KEY"))
        // 출처 코드는 열 길이 안에 있어야 한다.
        assertTrue(EconomySource.entries.all { it.name.length <= 64 })
        assertTrue(EconomyDirection.entries.all { it.name.length <= 16 })
    }
}
