package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.identity.IdpException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

enum class TicketStatus { RECEIVED, IN_PROGRESS, ANSWERED, RESOLVED, CLOSED }

/** 아래로 갈수록 할 수 있는 일이 많다. */
enum class StaffRole { VIEWER, AGENT, MANAGER }

object SupportPolicy {
    const val SUBJECT_MIN = 2
    const val SUBJECT_MAX = 100
    const val BODY_MAX = 5000

    /** 답변 후 사용자의 응답이 없으면 해결로 바꾼다. */
    val ANSWERED_IDLE: Duration = Duration.ofDays(14)

    /** 해결 후 이 기간 안에는 답변을 달아 다시 열 수 있고, 지나면 종료한다. */
    val REOPEN_WINDOW: Duration = Duration.ofDays(7)

    /** 운영정책의 이의신청 답변 기한. */
    val APPEAL_DUE: Duration = Duration.ofDays(15)

    /** 개인정보처리방침의 상담 기록 보유 기간(년). */
    const val RETENTION_YEARS = 3

    const val MAX_OPEN_TICKETS = 10
    const val CREATE_PER_HOUR = 5
    const val CREATE_PER_DAY = 20
    const val REPLY_PER_HOUR = 30

    /** 사용자가 답변을 달았을 때의 상태. 달 수 없으면 거절한다. */
    fun afterUserReply(status: TicketStatus, resolvedAt: Instant?, now: Instant): TicketStatus = when (status) {
        TicketStatus.CLOSED -> throw closed()
        TicketStatus.RESOLVED ->
            if (resolvedAt != null && resolvedAt.plus(REOPEN_WINDOW).isBefore(now)) throw closed() else TicketStatus.RECEIVED
        TicketStatus.ANSWERED -> TicketStatus.RECEIVED
        TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS -> status
    }

    /** 운영자가 직접 고를 수 있는 상태. `ANSWERED` 는 공개 답변을 달 때만 된다. */
    val STAFF_SELECTABLE = setOf(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED)

    private fun closed() = IdpException("ticket_closed", "종료된 문의에는 답변을 달 수 없습니다. 새 문의를 접수해 주세요.", 409)
}

/** 본문은 어디에서도 HTML 로 해석하지 않는 일반 텍스트다. 여기서는 제어 문자와 길이만 정리한다. */
object SupportText {
    private val controls = Regex("[\\p{Cc}&&[^\\n\\t]]")
    /** 글자 순서를 뒤집어 보이게 하는 방향 제어 문자. */
    private val directionOverrides = Regex("[\\u202A-\\u202E\\u2066-\\u2069]")

    fun multiLine(value: String?): String = value.orEmpty().replace("\r\n", "\n").replace('\r', '\n')
        .replace(controls, "").replace(directionOverrides, "").trim()

    fun singleLine(value: String?): String = multiLine(value).replace(Regex("[\\n\\t]+"), " ").trim()

    fun body(value: String?): String {
        val text = multiLine(value)
        if (text.isEmpty()) throw IdpException("invalid_body", "내용을 입력해 주세요.")
        if (text.length > SupportPolicy.BODY_MAX) throw IdpException("invalid_body", "내용은 ${SupportPolicy.BODY_MAX}자까지 입력할 수 있습니다.")
        return text
    }

    fun subject(value: String?): String {
        val text = singleLine(value)
        if (text.length !in SupportPolicy.SUBJECT_MIN..SupportPolicy.SUBJECT_MAX) {
            throw IdpException("invalid_subject", "제목은 ${SupportPolicy.SUBJECT_MIN}~${SupportPolicy.SUBJECT_MAX}자로 입력해 주세요.")
        }
        return text
    }
}

object TicketCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    private val pattern = Regex("^T-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$")

    fun generate(): String = buildString(11) {
        append("T-")
        repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        append('-')
        repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun isValid(value: String): Boolean = pattern.matches(value)
}
