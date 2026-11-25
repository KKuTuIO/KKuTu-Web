package me.kkutuio.kkutuweb.admin

import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * 관리자 조회의 `from`/`to`(`YYYY-MM-DD`, 한국 시간 달력 날짜). 양 끝 날짜를 모두 포함하며,
 * SQL에는 `[from 00:00, to 다음 날 00:00)` 범위로 넣는다.
 */
data class KstDateRange(val from: LocalDate?, val to: LocalDate?) {
    val isEmpty: Boolean get() = from == null && to == null

    val fromInclusive: Timestamp? get() = from?.let(::startOfDay)

    val toExclusive: Timestamp? get() = to?.let { startOfDay(it.plusDays(1)) }

    /** 인덱스를 쓸 수 있도록 컬럼은 변환하지 않고 경계 값만 바인딩한다. */
    fun appendConditions(column: String, clauses: MutableList<String>, values: MutableList<Any>) {
        fromInclusive?.let {
            clauses.add("$column >= ?")
            values.add(it)
        }
        toExclusive?.let {
            clauses.add("$column < ?")
            values.add(it)
        }
    }

    private fun startOfDay(date: LocalDate): Timestamp = Timestamp.from(date.atStartOfDay(ZONE).toInstant())

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val EMPTY = KstDateRange(null, null)
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        /** 빈 값은 해당 방향의 제한이 없는 것으로 본다. 시작일이 종료일보다 늦으면 서로 바꾼다. */
        fun parse(from: String?, to: String?): KstDateRange {
            val fromDate = parseDate(from)
            val toDate = parseDate(to)
            return if (fromDate != null && toDate != null && fromDate > toDate) {
                KstDateRange(toDate, fromDate)
            } else {
                KstDateRange(fromDate, toDate)
            }
        }

        private fun parseDate(value: String?): LocalDate? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null
            require(DATE_REGEX.matches(text)) { INVALID_DATE_MESSAGE }
            return try {
                LocalDate.parse(text)
            } catch (error: DateTimeParseException) {
                throw IllegalArgumentException(INVALID_DATE_MESSAGE, error)
            }
        }

        private const val INVALID_DATE_MESSAGE = "날짜는 YYYY-MM-DD 형식의 올바른 날짜여야 합니다."
    }
}
