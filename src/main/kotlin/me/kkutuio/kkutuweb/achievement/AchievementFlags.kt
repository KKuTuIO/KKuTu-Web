package me.kkutuio.kkutuweb.achievement

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 업적(명성)의 카운터 중 웹에서 일어나는 일(상점 구매, 강화, 글자 조합)을 계정 플래그 `account.flags.<key> = {value, time}`에 올린다.
 * 게임이 한 판을 마칠 때 이 값을 읽어 업적 단계를 판정한다. 한 문장으로 행을 잠그고 고치므로 다른 플래그를 쓰는 동시 요청과 섞여도 잃어버리지 않는다.
 *
 * 업적은 덤이라서, 카운터를 올리다 실패해도 구매나 강화 같은 본래 일은 막지 않는다(로그만 남긴다). 값이 숫자가 아니거나 없으면 0에서 시작한다.
 */
@Service
class AchievementFlags(private val jdbc: JdbcTemplate) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** `account.flags.<key>.value`에 [delta]를 더한다(0 이하는 무시). */
    fun add(accountUuid: String?, key: String, delta: Long) {
        if (delta <= 0) return
        write(accountUuid, key, delta, ADD)
    }

    /** `account.flags.<key>.value`를 [value]와 지금 값 중 큰 쪽으로 둔다(줄지 않는다). */
    fun max(accountUuid: String?, key: String, value: Long) {
        if (value <= 0) return
        write(accountUuid, key, value, MAX)
    }

    private fun write(accountUuid: String?, key: String, amount: Long, valueSql: String) {
        val uuid = accountUuid?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        try {
            jdbc.update(
                """
                UPDATE account SET updated_at = CURRENT_TIMESTAMP, flags = jsonb_set(
                    COALESCE(flags, '{}'::jsonb), ARRAY[?], jsonb_build_object('value', $valueSql, 'time', ?::bigint), true)
                WHERE uuid = ?
                """.trimIndent(),
                key, key, key, amount, System.currentTimeMillis(), uuid
            )
        } catch (error: Exception) {
            logger.warn("업적 카운터 {}를 올리지 못했습니다: {}", key, error.message)
        }
    }

    private companion object {
        /** 지금 값(숫자가 아니거나 없으면 0). 파라미터 두 개(key, key)를 쓴다. */
        const val CURRENT = "COALESCE(CASE WHEN jsonb_typeof(flags #> ARRAY[?, 'value']) = 'number' THEN (flags #>> ARRAY[?, 'value'])::bigint END, 0)"
        const val ADD = "$CURRENT + ?::bigint"
        const val MAX = "GREATEST($CURRENT, ?::bigint)"
    }
}
