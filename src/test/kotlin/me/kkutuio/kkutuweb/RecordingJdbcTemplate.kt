package me.kkutuio.kkutuweb

import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet

/** 실행한 SQL과 바인딩 값을 기록하는 JdbcTemplate 대역. 실제 DB에는 접속하지 않는다. */
class RecordingJdbcTemplate(
    /** 특정 조회에 한 행을 돌려주고 싶을 때, 그 행의 ResultSet을 반환한다. */
    private val rowFor: (String) -> ResultSet? = { null }
) {
    data class Call(val method: String, val sql: String, val values: List<Any?>)

    val calls = mutableListOf<Call>()

    val mock: JdbcTemplate = Mockito.mock(JdbcTemplate::class.java) { invocation ->
        val method = invocation.method.name
        val sql = invocation.arguments[0] as String
        // 두 번째 인자는 RowMapper 또는 결과 타입이고, 그 뒤가 바인딩 값이다.
        calls.add(Call(method, sql, invocation.arguments.drop(2)))
        when (method) {
            "query" -> {
                val mapper = invocation.arguments[1] as RowMapper<*>
                rowFor(sql)?.let { listOf(mapper.mapRow(it, 0)) } ?: emptyList<Any>()
            }
            "queryForObject" -> 0L
            else -> error("예상하지 못한 JdbcTemplate 호출입니다: $method")
        }
    }
}
