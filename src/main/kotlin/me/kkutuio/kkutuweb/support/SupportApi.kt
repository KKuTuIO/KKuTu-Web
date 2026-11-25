package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.identity.Account
import me.kkutuio.kkutuweb.identity.IdpException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import jakarta.servlet.http.HttpServletRequest

data class TicketCreateRequest(
    val category: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val details: Map<String, String?>? = null,
    val notifyEmail: Boolean = false,
    val consentVersion: String? = null,
    val agreed: Boolean = false,
    val attachments: List<AttachmentUpload>? = null
)

data class TicketReplyRequest(val body: String? = null, val attachments: List<AttachmentUpload>? = null)

data class AttachmentEntry(val id: String, val name: String, val contentType: String, val size: Int)

data class TicketSummary(
    val code: String,
    val subject: String,
    val category: String,
    val categoryLabel: String,
    val status: TicketStatus,
    val createdAt: String,
    val updatedAt: String,
    val unread: Boolean
)

/** 작성자에게 보이는 타임라인 한 줄. 운영자의 계정 식별 정보는 담지 않고 공개 이름과 직함만 담는다. */
data class TimelineEntry(
    val id: Long,
    val kind: String,
    val body: String,
    val staffName: String?,
    val staffTitle: String?,
    val event: TimelineEvent?,
    val attachments: List<AttachmentEntry>,
    val createdAt: String
)

data class TimelineEvent(val type: String, val from: String?, val to: String?, val by: String)

data class TicketDetail(
    val ticket: TicketSummary,
    val details: List<DetailEntry>,
    val notifyEmail: Boolean,
    val canReply: Boolean,
    val canResolve: Boolean,
    val canClose: Boolean,
    val messages: List<TimelineEntry>
)

data class DetailEntry(val label: String, val value: String)

/** 고객센터 앱의 API. 모든 문의는 토큰의 계정 것만 보인다. */
@RestController
@RequestMapping("/api/support")
class SupportApi(
    private val service: SupportService,
    private val dao: SupportDao,
    private val catalog: SupportCatalog,
    private val access: SupportAccess
) {
    /** 로그인 없이 읽는다. */
    @GetMapping("/config")
    fun config(): Map<String, Any?> {
        val switch = access.switch()
        return mapOf(
            "ticketsEnabled" to switch.enabled,
            "closedDates" to switch.closedDates,
            "consent" to catalog.document["consent"],
            "groups" to catalog.document["groups"],
            "attachments" to mapOf(
                "enabled" to true,
                "maxBytes" to SupportFiles.MAX_BYTES,
                "maxCount" to SupportFiles.MAX_PER_MESSAGE,
                "extensions" to SupportFiles.EXTENSIONS
            ),
            "limits" to mapOf("subjectMax" to SupportPolicy.SUBJECT_MAX, "bodyMax" to SupportPolicy.BODY_MAX)
        )
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): Map<String, Any?> {
        val account = account(request)
        val requester = service.requester(account)
        return mapOf(
            "accountUuid" to account.uuid.toString(),
            "gameUserId" to requester.gameUserId,
            "nickname" to requester.nickname,
            "loginProvider" to requester.loginProvider,
            "email" to requester.email?.let(::maskEmail),
            "canSubmit" to access.canSubmit(account.uuid.toString()),
            "unreadCount" to dao.countUnread(account.id)
        )
    }

    @GetMapping("/tickets")
    fun list(
        @RequestParam(defaultValue = "all") filter: String,
        @RequestParam(defaultValue = "0") page: Int,
        request: HttpServletRequest
    ): ListResponse<TicketSummary> {
        val statuses = when (filter) {
            "all" -> emptySet()
            "open" -> setOf(TicketStatus.RECEIVED, TicketStatus.IN_PROGRESS)
            "answered" -> setOf(TicketStatus.ANSWERED)
            "done" -> setOf(TicketStatus.RESOLVED, TicketStatus.CLOSED)
            else -> throw IdpException("invalid_filter", "알 수 없는 상태 필터입니다.")
        }
        val (total, tickets) = dao.listOwned(account(request).id, statuses, PAGE_SIZE, page.coerceIn(0, 10_000) * PAGE_SIZE)
        return ListResponse(total, tickets.map(::summary))
    }

    @PostMapping("/tickets", consumes = ["application/json"])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestHeader("Idempotency-Key") key: String,
        @RequestBody body: TicketCreateRequest,
        request: HttpServletRequest
    ): Map<String, String> {
        val requestKey = runCatching { UUID.fromString(key) }.getOrElse { throw IdpException("invalid_request", "요청 식별자가 올바르지 않습니다.") }
        val code = service.create(
            account(request), requestKey,
            CreateTicketCommand(
                body.category, body.subject, body.body, body.details ?: emptyMap(), body.notifyEmail,
                body.consentVersion, body.agreed, body.attachments
            )
        )
        return mapOf("code" to code)
    }

    @GetMapping("/tickets/{code}")
    fun detail(@PathVariable code: String, request: HttpServletRequest): TicketDetail = detailOf(account(request), code)

    @PostMapping("/tickets/{code}/messages", consumes = ["application/json"])
    fun reply(@PathVariable code: String, @RequestBody body: TicketReplyRequest, request: HttpServletRequest): TicketDetail {
        val account = account(request)
        service.reply(account, code, body.body, body.attachments)
        return detailOf(account, code)
    }

    @GetMapping("/tickets/{code}/attachments/{attachmentId}")
    fun attachment(@PathVariable code: String, @PathVariable attachmentId: String, request: HttpServletRequest): ResponseEntity<ByteArray> {
        val id = runCatching { UUID.fromString(attachmentId) }.getOrElse { throw IdpException("not_found", "파일을 찾을 수 없습니다.", 404) }
        val file = service.download(account(request), code, id)
        return SupportFiles.download(file.name, file.contentType, file.bytes)
    }

    @PostMapping("/tickets/{code}/resolve")
    fun resolve(@PathVariable code: String, request: HttpServletRequest): TicketDetail {
        val account = account(request)
        service.resolve(account, code)
        return detailOf(account, code)
    }

    @PostMapping("/tickets/{code}/close")
    fun close(@PathVariable code: String, request: HttpServletRequest): TicketDetail {
        val account = account(request)
        service.close(account, code)
        return detailOf(account, code)
    }

    private fun account(request: HttpServletRequest): Account = SupportOAuthBearerFilter.principal(request).account

    private fun detailOf(account: Account, code: String): TicketDetail {
        val (ticket, messages) = service.view(account, code)
        val fields = catalog.find(ticket.category)?.fields.orEmpty()
        val replyable = runCatching { SupportPolicy.afterUserReply(ticket.status, ticket.resolvedAt, java.time.Instant.now()) }.isSuccess
        val attachments = dao.attachments(ticket.id).groupBy { it.messageId }
        return TicketDetail(
            // 방금 열었으므로 읽은 것으로 보여 준다.
            ticket = summary(ticket).copy(unread = false),
            details = fields.mapNotNull { field -> ticket.details[field.key]?.asString()?.let { DetailEntry(field.label, it) } },
            notifyEmail = ticket.notifyEmail,
            canReply = replyable,
            canResolve = ticket.status != TicketStatus.RESOLVED && ticket.status != TicketStatus.CLOSED,
            canClose = ticket.status != TicketStatus.CLOSED,
            messages = messages.map { message ->
                TimelineEntry(
                    id = message.id,
                    kind = message.kind,
                    body = message.body,
                    staffName = message.staffName.takeIf { message.kind == "STAFF" },
                    staffTitle = message.staffTitle.takeIf { message.kind == "STAFF" },
                    event = message.event?.let {
                        TimelineEvent(it["type"].asString(), it["from"]?.asString(null), it["to"]?.asString(null), it["by"].asString())
                    },
                    attachments = attachments[message.id].orEmpty().map {
                        AttachmentEntry(it.id.toString(), it.name, it.contentType, it.size)
                    },
                    createdAt = message.createdAt.toString()
                )
            }
        )
    }

    private fun summary(ticket: Ticket) = TicketSummary(
        ticket.code, ticket.subject, ticket.category, catalog.label(ticket.category), ticket.status,
        ticket.createdAt.toString(), ticket.updatedAt.toString(), ticket.unread
    )

    private fun maskEmail(email: String): String {
        val name = email.substringBefore('@')
        return name.take(2) + "*".repeat((name.length - 2).coerceIn(1, 6)) + "@" + email.substringAfter('@')
    }

    private companion object {
        const val PAGE_SIZE = 10
    }
}
