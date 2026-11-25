package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.identity.IdpException
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.KKuTuSetting
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

data class StaffReplyRequest(val body: String? = null, val internal: Boolean = false, val expectedVersion: Int? = null)

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
    val dueAt: String?
)

data class AdminTimelineEntry(
    val id: Long,
    val kind: String,
    val body: String,
    val staffName: String?,
    val staffTitle: String?,
    val actorUuid: String?,
    val event: JsonNode?,
    val createdAt: String
)

data class AdminTicketDetail(
    val ticket: AdminTicketRow,
    val version: Int,
    val accountUuid: String,
    val gameUserId: String?,
    val notifyEmail: Boolean,
    val details: List<DetailEntry>,
    val messages: List<AdminTimelineEntry>
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
        val (total, tickets) = dao.search(TicketFilter(statuses, category, assigneeFilter, q), size, page.coerceIn(0, 100_000) * size)
        val names = staffNames()
        return ListResponse(total, tickets.map { row(it, names) })
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
        service.staffReply(accountUuid, ticketId, body.body, body.internal, requireVersion(body.expectedVersion))
        return detailOf(ticketId)
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
        return AdminTicketDetail(
            ticket = row(ticket, staffNames()),
            version = ticket.version,
            accountUuid = ticket.accountUuid.toString(),
            gameUserId = ticket.gameUserId,
            notifyEmail = ticket.notifyEmail,
            details = details,
            messages = dao.messages(id).map {
                AdminTimelineEntry(it.id, it.kind, it.body, it.staffName, it.staffTitle, it.actorUuid?.toString(), it.event, it.createdAt.toString())
            }
        )
    }

    /** 담당자 칸에 보이는 관리자 이름(백오피스 전용). */
    private fun staffNames(): Map<String, String> = setting.getAdmins().associate { it.id to it.name }

    private fun row(ticket: Ticket, names: Map<String, String>) = AdminTicketRow(
        ticket.id.toString(), ticket.code, ticket.subject, ticket.category, catalog.label(ticket.category), ticket.status,
        ticket.nickname, ticket.assigneeUuid?.toString(), ticket.assigneeUuid?.let { names[it.toString()] },
        ticket.createdAt.toString(), ticket.updatedAt.toString(),
        ticket.dueAt?.takeIf { ticket.lastStaffMessageAt == null && ticket.status != TicketStatus.CLOSED }?.toString()
    )

    private fun parseStatus(value: String): TicketStatus = TicketStatus.entries.firstOrNull { it.name == value.trim() }
        ?: throw IdpException("invalid_status", "알 수 없는 상태입니다.")

    private fun parseUuid(value: String): UUID = runCatching { UUID.fromString(value) }
        .getOrElse { throw IdpException("invalid_request", "UUID 형식이 올바르지 않습니다.") }

    private fun requireVersion(value: Int?): Int = value ?: throw IdpException("invalid_request", "expectedVersion 이 필요합니다.")
}
