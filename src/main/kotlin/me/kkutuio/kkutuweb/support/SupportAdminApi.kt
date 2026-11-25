package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.identity.IdpException
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID
import jakarta.servlet.http.HttpSession

data class StaffReplyRequest(
    val body: String? = null,
    val internal: Boolean = false,
    val expectedVersion: Int? = null,
    val attachments: List<AttachmentUpload>? = null
)

data class ArchiveRequest(val archived: Boolean? = null)

data class TemplateSaveRequest(val title: String? = null, val category: String? = null, val body: String? = null)

data class TemplateEntry(
    val id: String,
    val title: String,
    /** 이 분류의 문의에서 먼저 보여 준다. 없으면 공통. */
    val category: String?,
    val categoryLabel: String?,
    val body: String,
    val updatedAt: String,
    val updatedByName: String?
)

data class TicketUpdateRequest(
    val expectedVersion: Int? = null,
    val status: String? = null,
    /** 없으면 그대로, 빈 문자열이면 담당 해제. */
    val assignee: String? = null,
    val category: String? = null
)

data class StaffSaveRequest(val publicName: String? = null, val title: String? = null, val role: String? = null, val active: Boolean = true)

data class SupportSettingRequest(val enabled: Boolean? = null, val closedDates: List<String>? = null)

data class AdminTicketRow(
    val id: String,
    val code: String,
    val subject: String,
    val category: String,
    val categoryLabel: String,
    val status: TicketStatus,
    val nickname: String?,
    val assigneeUuid: String?,
    val assigneeName: String?,
    val createdAt: String,
    val updatedAt: String,
    /** 아직 답하지 않은 이의신청의 답변 기한. */
    val dueAt: String?,
    val archived: Boolean
)

data class AdminTimelineEntry(
    val id: Long,
    val kind: String,
    val body: String,
    val staffName: String?,
    val staffTitle: String?,
    val actorUuid: String?,
    val event: JsonNode?,
    val createdAt: String,
    val attachments: List<AttachmentEntry>
)

data class AdminTicketDetail(
    val ticket: AdminTicketRow,
    val version: Int,
    val accountUuid: String,
    val gameUserId: String?,
    val notifyEmail: Boolean,
    val details: List<DetailEntry>,
    val messages: List<AdminTimelineEntry>,
    /** 같은 계정의 다른 문의(최근 접수 순, 보관한 것 포함)와 그 전체 수. */
    val history: ListResponse<AdminTicketRow>
)

/** 백오피스의 문의 업무. 토큰 검사는 AdminOAuthBearerFilter, 역할 검사는 [SupportAccess]. */
@RestController
@RequestMapping("/api/admin/support")
class SupportAdminApi(
    private val service: SupportService,
    private val dao: SupportDao,
    private val catalog: SupportCatalog,
    private val access: SupportAccess,
    private val authorizer: AdminModerationAuthorizer,
    private val setting: KKuTuSetting
) {
    @GetMapping("/me")
    fun me(session: HttpSession): Map<String, Any?> {
        val (accountUuid, role) = access.require(session, StaffRole.AGENT)
        return mapOf(
            "accountUuid" to accountUuid,
            "role" to role,
            "master" to setting.isAdminMaster(accountUuid),
            // 내 답변이 작성자에게 보이는 모습.
            "profile" to access.identityOf(accountUuid),
            "registered" to (dao.findStaff(accountUuid)?.active == true),
            "attachments" to mapOf(
                "maxBytes" to SupportFiles.MAX_BYTES, "maxCount" to SupportFiles.MAX_PER_MESSAGE, "extensions" to SupportFiles.EXTENSIONS
            ),
            // 담당으로 지정할 수 있는 사람(관리자 전원). `name` 은 백오피스 안에서만 보이는 관리자 이름이다.
            "assignees" to setting.getAdmins().map { admin ->
                mapOf("accountUuid" to admin.id, "name" to admin.name, "title" to access.identityOf(admin.id).title)
            },
            "categories" to catalog.document["groups"].toList().flatMap { group ->
                group["items"].toList().map { mapOf("code" to it["code"].asString(), "label" to catalog.label(it["code"].asString())) }
            }
        )
    }

    @GetMapping("/tickets")
    fun list(
        @RequestParam(defaultValue = "") status: String,
        @RequestParam(defaultValue = "") category: String,
        @RequestParam(required = false) assignee: String?,
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
        @RequestParam(defaultValue = "exclude") archived: String,
        session: HttpSession
    ): ListResponse<AdminTicketRow> {
        val (accountUuid, _) = access.require(session, StaffRole.AGENT)
        require(size in 1..150) { "페이지 크기는 1~150이어야 합니다." }
        val statuses = status.split(',').filter { it.isNotBlank() }.map(::parseStatus).toSet()
        // `me` 는 내 담당, `none` 은 담당 없음.
        val assigneeFilter = when (assignee) {
            null, "" -> null
            "me" -> accountUuid
            "none" -> ""
            else -> parseUuid(assignee).toString()
        }
        val archivedFilter = when (archived) {
            "exclude" -> false
            "only" -> true
            "all" -> null
            else -> throw IdpException("invalid_filter", "알 수 없는 보관 필터입니다.")
        }
        val filter = TicketFilter(statuses, category, assigneeFilter, q, archivedFilter)
        val (total, tickets) = dao.search(filter, size, page.coerceIn(0, 100_000) * size)
        return ListResponse(total, rows(tickets))
    }

    @GetMapping("/tickets/{id}")
    fun detail(@PathVariable id: String, session: HttpSession): AdminTicketDetail {
        access.require(session, StaffRole.AGENT)
        return detailOf(parseUuid(id))
    }

    @PostMapping("/tickets/{id}/messages")
    fun reply(@PathVariable id: String, @RequestBody body: StaffReplyRequest, session: HttpSession): AdminTicketDetail {
        val (accountUuid, _) = access.require(session, StaffRole.AGENT)
        val ticketId = parseUuid(id)
        service.staffReply(accountUuid, ticketId, body.body, body.internal, requireVersion(body.expectedVersion), body.attachments)
        return detailOf(ticketId)
    }

    @GetMapping("/tickets/{id}/attachments/{attachmentId}")
    fun attachment(@PathVariable id: String, @PathVariable attachmentId: String, session: HttpSession): ResponseEntity<ByteArray> {
        access.require(session, StaffRole.AGENT)
        val file = dao.findAttachment(parseUuid(id), parseUuid(attachmentId))
            ?: throw IdpException("not_found", "파일을 찾을 수 없습니다.", 404)
        return SupportFiles.download(file.name, file.contentType, file.bytes)
    }

    @PutMapping("/tickets/{id}/archive")
    fun archive(@PathVariable id: String, @RequestBody body: ArchiveRequest, session: HttpSession): AdminTicketDetail {
        val (accountUuid, _) = access.require(session, StaffRole.AGENT)
        val ticketId = parseUuid(id)
        val archived = body.archived ?: throw IdpException("invalid_request", "보관 여부를 지정해 주세요.")
        service.setArchived(accountUuid, ticketId, archived)
        return detailOf(ticketId)
    }

    /** 답변 템플릿은 관리자 누구나 함께 쓰고 고친다. */
    @GetMapping("/templates")
    fun templates(session: HttpSession): List<TemplateEntry> {
        access.require(session, StaffRole.AGENT)
        return templateEntries()
    }

    @PostMapping("/templates")
    fun createTemplate(@RequestBody body: TemplateSaveRequest, session: HttpSession): List<TemplateEntry> {
        val (accountUuid, _) = access.require(session, StaffRole.AGENT)
        val (title, category, text) = readTemplate(body)
        if (dao.countTemplates() >= MAX_TEMPLATES) throw IdpException("too_many_templates", "템플릿은 ${MAX_TEMPLATES}개까지 만들 수 있습니다.", 409)
        dao.insertTemplate(UUID.randomUUID(), title, category, text, accountUuid)
        return templateEntries()
    }

    @PutMapping("/templates/{id}")
    fun updateTemplate(@PathVariable id: String, @RequestBody body: TemplateSaveRequest, session: HttpSession): List<TemplateEntry> {
        val (accountUuid, _) = access.require(session, StaffRole.AGENT)
        val (title, category, text) = readTemplate(body)
        if (!dao.updateTemplate(parseUuid(id), title, category, text, accountUuid)) throw templateNotFound()
        return templateEntries()
    }

    @DeleteMapping("/templates/{id}")
    fun deleteTemplate(@PathVariable id: String, session: HttpSession): List<TemplateEntry> {
        access.require(session, StaffRole.AGENT)
        if (!dao.deleteTemplate(parseUuid(id))) throw templateNotFound()
        return templateEntries()
    }

    @PatchMapping("/tickets/{id}")
    fun update(@PathVariable id: String, @RequestBody body: TicketUpdateRequest, session: HttpSession): AdminTicketDetail {
        val (accountUuid, _) = access.require(session, StaffRole.AGENT)
        val ticketId = parseUuid(id)
        service.staffUpdate(
            accountUuid, ticketId, requireVersion(body.expectedVersion),
            body.status?.let(::parseStatus), body.assignee, body.category
        )
        return detailOf(ticketId)
    }

    /** 관리자 전원과 각자의 문의 담당 설정. 마스터 계정 전용. */
    @GetMapping("/staff")
    fun staff(session: HttpSession): List<Map<String, Any?>> {
        authorizer.requireMaster(session)
        val profiles = dao.listStaff().associateBy { it.accountUuid }
        return setting.getAdmins().map { admin ->
            val profile = profiles[admin.id]
            mapOf(
                "accountUuid" to admin.id,
                "adminName" to admin.name,
                "team" to admin.team,
                "master" to setting.isAdminMaster(admin.id),
                "publicName" to profile?.publicName,
                "title" to profile?.title,
                "role" to profile?.role,
                "active" to (profile?.active == true)
            )
        }
    }

    @PutMapping("/staff/{accountUuid}")
    fun saveStaff(@PathVariable accountUuid: String, @RequestBody body: StaffSaveRequest, session: HttpSession): Map<String, Any> {
        val actor = authorizer.requireMaster(session)
        val target = parseUuid(accountUuid).toString()
        if (target !in setting.getAdminIds()) throw IdpException("invalid_staff", "관리자 권한이 있는 계정만 문의 담당자로 등록할 수 있습니다.")
        val publicName = SupportText.singleLine(body.publicName)
        val title = SupportText.singleLine(body.title)
        if (publicName.length !in 1..40 || title.length !in 1..40) throw IdpException("invalid_staff", "표시 이름과 직함은 각각 1~40자로 입력해 주세요.")
        val role = StaffRole.entries.firstOrNull { it.name == body.role } ?: throw IdpException("invalid_staff", "역할을 선택해 주세요.")
        dao.saveStaff(StaffProfile(target, publicName, title, role, body.active), actor)
        return mapOf("ok" to true)
    }

    @GetMapping("/settings")
    fun settings(session: HttpSession): SupportSwitch {
        access.require(session, StaffRole.AGENT)
        return access.switch()
    }

    @PutMapping("/settings")
    fun saveSettings(@RequestBody body: SupportSettingRequest, session: HttpSession): SupportSwitch {
        val (accountUuid, _) = access.require(session, StaffRole.MANAGER)
        val enabled = body.enabled ?: throw IdpException("invalid_setting", "접수 여부를 지정해 주세요.")
        val dates = body.closedDates.orEmpty().map { value ->
            runCatching { LocalDate.parse(value.trim()).toString() }.getOrElse { throw IdpException("invalid_setting", "날짜는 YYYY-MM-DD 형식으로 입력해 주세요.") }
        }.distinct().sorted()
        if (dates.size > 200) throw IdpException("invalid_setting", "휴무일은 200일까지 등록할 수 있습니다.")
        val value = SupportSwitch(enabled, dates)
        access.saveSwitch(value, accountUuid)
        return value
    }

    private fun detailOf(id: UUID): AdminTicketDetail {
        val ticket = dao.find(id) ?: throw IdpException("not_found", "문의를 찾을 수 없습니다.", 404)
        val fields = catalog.find(ticket.category)?.fields.orEmpty()
        val details = fields.mapNotNull { field -> ticket.details[field.key]?.asString()?.let { DetailEntry(field.label, it) } } +
            listOfNotNull(ticket.details[SupportService.SECURITY_CODE_MATCHED]?.let {
                DetailEntry("보안 코드 대조", if (it.asBoolean()) "일치" else "불일치")
            })
        val attachments = dao.attachments(id).groupBy { it.messageId }
        val (otherTotal, others) = dao.otherTicketsOf(ticket.accountId, ticket.id, HISTORY_LIMIT)
        return AdminTicketDetail(
            ticket = rows(listOf(ticket)).single(),
            version = ticket.version,
            accountUuid = ticket.accountUuid.toString(),
            gameUserId = ticket.gameUserId,
            notifyEmail = ticket.notifyEmail,
            details = details,
            messages = dao.messages(id).map { message ->
                AdminTimelineEntry(
                    message.id, message.kind, message.body, message.staffName, message.staffTitle, message.actorUuid?.toString(),
                    message.event, message.createdAt.toString(),
                    attachments[message.id].orEmpty().map { AttachmentEntry(it.id.toString(), it.name, it.contentType, it.size) }
                )
            },
            history = ListResponse(otherTotal, rows(others))
        )
    }

    private fun templateEntries(): List<TemplateEntry> {
        val names = staffNames()
        return dao.listTemplates().map {
            TemplateEntry(
                it.id.toString(), it.title, it.category, it.category?.let(catalog::label), it.body,
                it.updatedAt.toString(), names[it.updatedBy.toString()]
            )
        }
    }

    private fun readTemplate(body: TemplateSaveRequest): Triple<String, String?, String> {
        val title = SupportText.singleLine(body.title)
        if (title.length !in 1..TEMPLATE_TITLE_MAX) throw IdpException("invalid_template", "템플릿 이름은 1~${TEMPLATE_TITLE_MAX}자로 입력해 주세요.")
        val category = body.category?.takeIf { it.isNotBlank() }?.let { catalog.require(it).code }
        return Triple(title, category, SupportText.body(body.body))
    }

    private fun templateNotFound() = IdpException("not_found", "템플릿을 찾을 수 없습니다.", 404)

    private fun rows(tickets: List<Ticket>): List<AdminTicketRow> {
        val names = staffNames()
        val archived = dao.archivedAmong(tickets.map { it.id })
        return tickets.map { row(it, names, it.id in archived) }
    }

    /** 담당자 칸에 보이는 관리자 이름(백오피스 전용). */
    private fun staffNames(): Map<String, String> = setting.getAdmins().associate { it.id to it.name }

    private fun row(ticket: Ticket, names: Map<String, String>, archived: Boolean) = AdminTicketRow(
        ticket.id.toString(), ticket.code, ticket.subject, ticket.category, catalog.label(ticket.category), ticket.status,
        ticket.nickname, ticket.assigneeUuid?.toString(), ticket.assigneeUuid?.let { names[it.toString()] },
        ticket.createdAt.toString(), ticket.updatedAt.toString(),
        ticket.dueAt?.takeIf { ticket.lastStaffMessageAt == null && ticket.status != TicketStatus.CLOSED }?.toString(),
        archived
    )

    private fun parseStatus(value: String): TicketStatus = TicketStatus.entries.firstOrNull { it.name == value.trim() }
        ?: throw IdpException("invalid_status", "알 수 없는 상태입니다.")

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw IdpException("invalid_request", "UUID 형식이 올바르지 않습니다.") }

    private fun requireVersion(value: Int?): Int = value ?: throw IdpException("invalid_request", "expectedVersion 이 필요합니다.")

    private companion object {
        const val HISTORY_LIMIT = 30
        const val MAX_TEMPLATES = 200
        const val TEMPLATE_TITLE_MAX = 80
    }
}
