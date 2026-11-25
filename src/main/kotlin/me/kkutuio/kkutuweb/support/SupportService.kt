package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.identity.Account
import me.kkutuio.kkutuweb.identity.AccountRateLimiter
import me.kkutuio.kkutuweb.identity.AccountService
import me.kkutuio.kkutuweb.identity.IdentityDao
import me.kkutuio.kkutuweb.identity.IdentityType
import me.kkutuio.kkutuweb.identity.IdpException
import me.kkutuio.kkutuweb.identity.SecretTools
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class Requester(
    val gameUserId: String,
    val nickname: String?,
    val loginProvider: String?,
    val email: String?
)

data class CreateTicketCommand(
    val category: String?,
    val subject: String?,
    val body: String?,
    val details: Map<String, String?>,
    val notifyEmail: Boolean,
    val consentVersion: String?,
    val agreed: Boolean,
    val attachmentCount: Int
)

@Service
class SupportService(
    private val dao: SupportDao,
    private val catalog: SupportCatalog,
    private val access: SupportAccess,
    private val accounts: AccountService,
    private val identityDao: IdentityDao,
    private val users: UserDao,
    private val limiter: AccountRateLimiter,
    private val notifier: SupportNotifier
) {
    /** 접수 화면에 자동으로 보여 주는 계정 정보. */
    fun requester(account: Account): Requester {
        val gameUserId = accounts.selectedGameProfileId(account)
        val identities = identityDao.listIdentities(account.id).filter { it.revokedAt == null }
        return Requester(
            gameUserId = gameUserId,
            nickname = users.getUser(gameUserId)?.nickname,
            // 대표 로그인 수단의 제공자. 비밀번호 계정은 `LOCAL` 이다.
            loginProvider = identities.firstOrNull { it.primary }?.provider,
            email = identities.firstOrNull { it.type == IdentityType.EMAIL && it.verifiedAt != null }?.subject
        )
    }

    /** 접수하고 문의 번호를 돌려준다. 같은 [requestKey] 로 다시 오면 새로 만들지 않고 처음 번호를 준다. */
    @Transactional
    fun create(account: Account, requestKey: UUID, command: CreateTicketCommand): String {
        dao.lockAccount(account.id)
        dao.findCodeByRequestKey(account.id, requestKey)?.let { return it }

        if (!access.canSubmit(account.uuid.toString())) {
            throw IdpException("tickets_disabled", "지금은 문의를 접수할 수 없습니다. 채널톡으로 문의해 주세요.", 403)
        }
        rejectAttachments(command.attachmentCount)
        if (!command.agreed || command.consentVersion != catalog.consentVersion) {
            throw IdpException("consent_required", "개인정보 수집·이용에 동의해 주세요. 안내가 바뀌었다면 새로고침해 주세요.")
        }
        val category = catalog.require(command.category)
        val subject = SupportText.subject(command.subject)
        val body = SupportText.body(command.body)
        val details = catalog.readDetails(category, command.details)
        val requester = requester(account)
        if (command.notifyEmail && requester.email == null) {
            throw IdpException("email_required", "인증된 전자 메일 주소가 없어 알림 메일을 받을 수 없습니다.")
        }
        if (dao.countOpen(account.id) >= SupportPolicy.MAX_OPEN_TICKETS) {
            throw IdpException("too_many_tickets", "처리 중인 문의가 ${SupportPolicy.MAX_OPEN_TICKETS}건입니다. 기존 문의에 이어서 작성해 주세요.", 409)
        }
        limiter.check("support:create:hour:${account.id}", SupportPolicy.CREATE_PER_HOUR, 3600)
        limiter.check("support:create:day:${account.id}", SupportPolicy.CREATE_PER_DAY, 86_400)

        val stored = LinkedHashMap<String, Any?>(details.stored)
        // 보안 코드는 대조 결과만 남긴다. 결과는 작성자에게 알려 주지 않는다.
        details.securityCode?.let { stored[SECURITY_CODE_MATCHED] = securityCodeMatches(details.stored["targetUserId"], it) }

        var code = TicketCode.generate()
        while (dao.codeExists(code)) code = TicketCode.generate()
        val id = UUID.randomUUID()
        dao.insertTicket(
            NewTicket(
                id, code, account.id, account.uuid, requester.gameUserId, requester.nickname, category.code, subject,
                command.notifyEmail, stored, catalog.consentVersion, requestKey,
                if (category.appeal) Instant.now().plus(SupportPolicy.APPEAL_DUE) else null
            )
        )
        dao.insertMessage(id, "USER", account.uuid, body)
        return code
    }

    @Transactional
    fun view(account: Account, code: String): Pair<Ticket, List<TicketMessage>> {
        val ticket = owned(account, code)
        if (ticket.unread) dao.markRead(ticket.id)
        // 내부 메모와 담당자 변경은 작성자에게 보내지 않는다.
        val visible = dao.messages(ticket.id).filter {
            it.kind == "USER" || it.kind == "STAFF" || (it.kind == "EVENT" && it.event?.get("type")?.asString() != "assignee")
        }
        return ticket to visible
    }

    @Transactional
    fun reply(account: Account, code: String, body: String?, attachmentCount: Int) {
        rejectAttachments(attachmentCount)
        val text = SupportText.body(body)
        limiter.check("support:reply:${account.id}", SupportPolicy.REPLY_PER_HOUR, 3600)
        val ticket = owned(account, code, lock = true)
        val status = SupportPolicy.afterUserReply(ticket.status, ticket.resolvedAt, Instant.now())
        dao.insertMessage(ticket.id, "USER", account.uuid, text)
        dao.saveProgress(ticket, status, ticket.assigneeUuid, ticket.category, ticket.dueAt)
        dao.markRead(ticket.id)
    }

    @Transactional
    fun resolve(account: Account, code: String) {
        val ticket = owned(account, code, lock = true)
        if (ticket.status == TicketStatus.CLOSED) throw IdpException("ticket_closed", "이미 종료된 문의입니다.", 409)
        if (ticket.status != TicketStatus.RESOLVED) transition(ticket, TicketStatus.RESOLVED, account.uuid, "user")
    }

    @Transactional
    fun close(account: Account, code: String) {
        val ticket = owned(account, code, lock = true)
        if (ticket.status != TicketStatus.CLOSED) transition(ticket, TicketStatus.CLOSED, account.uuid, "user")
    }

    /** 운영자의 답변([internal] 이면 내부 메모). 공개 답변은 보고 있던 버전이 그대로일 때만 저장한다. */
    @Transactional
    fun staffReply(actorUuid: String, ticketId: UUID, body: String?, internal: Boolean, expectedVersion: Int) {
        val text = SupportText.body(body)
        val profile = access.identityOf(actorUuid)
        val ticket = dao.find(ticketId, lock = true) ?: throw notFound()
        val actor = UUID.fromString(actorUuid)
        if (internal) {
            dao.insertMessage(ticket.id, "NOTE", actor, text, profile)
            return
        }
        requireVersion(ticket, expectedVersion)
        if (ticket.status == TicketStatus.CLOSED) throw IdpException("ticket_closed", "종료된 문의입니다. 상태를 바꾼 뒤 답변해 주세요.", 409)
        dao.insertMessage(ticket.id, "STAFF", actor, text, profile)
        dao.saveProgress(ticket, TicketStatus.ANSWERED, ticket.assigneeUuid ?: actor, ticket.category, ticket.dueAt, staffReplied = true)
        notifier.replyPosted(ticket)
    }

    /** 상태·담당·분류 변경. 값이 null 이면 그대로 두고, [assignee] 의 빈 문자열은 담당 해제다. */
    @Transactional
    fun staffUpdate(actorUuid: String, ticketId: UUID, expectedVersion: Int, status: TicketStatus?, assignee: String?, category: String?) {
        val ticket = dao.find(ticketId, lock = true) ?: throw notFound()
        requireVersion(ticket, expectedVersion)
        val actor = UUID.fromString(actorUuid)

        val nextStatus = status ?: ticket.status
        if (nextStatus != ticket.status && nextStatus !in SupportPolicy.STAFF_SELECTABLE) {
            throw IdpException("invalid_status", "이 상태는 직접 지정할 수 없습니다.")
        }
        val nextAssignee = when {
            assignee == null -> ticket.assigneeUuid
            assignee.isBlank() -> null
            else -> {
                val target = runCatching { UUID.fromString(assignee) }.getOrElse { throw IdpException("invalid_assignee", "담당자 UUID 형식이 올바르지 않습니다.") }
                if (access.roleOf(target.toString()) == null) throw IdpException("invalid_assignee", "관리자만 담당으로 지정할 수 있습니다.")
                target
            }
        }
        val nextCategory = category?.let { catalog.require(it) }
        val categoryChanged = nextCategory != null && nextCategory.code != ticket.category
        val dueAt = when {
            !categoryChanged -> ticket.dueAt
            nextCategory.appeal -> ticket.createdAt.plus(SupportPolicy.APPEAL_DUE)
            else -> null
        }
        if (nextStatus == ticket.status && nextAssignee == ticket.assigneeUuid && !categoryChanged) return

        if (nextStatus != ticket.status) event(ticket, actor, "status", ticket.status.name, nextStatus.name, "staff")
        if (categoryChanged) event(ticket, actor, "category", catalog.label(ticket.category), catalog.label(nextCategory.code), "staff")
        if (nextAssignee != ticket.assigneeUuid) event(ticket, actor, "assignee", ticket.assigneeUuid?.toString(), nextAssignee?.toString(), "staff")
        dao.saveProgress(ticket, nextStatus, nextAssignee, if (categoryChanged) nextCategory.code else ticket.category, dueAt)
    }

    /** 답변 후 응답이 없던 문의를 해결로 바꾼다. 그사이 바뀌었으면 아무것도 하지 않는다. */
    @Transactional
    fun resolveIdle(ticketId: UUID, cutoff: Instant): Boolean {
        val ticket = dao.find(ticketId, lock = true) ?: return false
        if (ticket.status != TicketStatus.ANSWERED || ticket.lastStaffMessageAt?.isBefore(cutoff) != true) return false
        transition(ticket, TicketStatus.RESOLVED, null, "system")
        return true
    }

    /** 다시 열 수 있는 기간이 지난 해결 문의를 종료한다. */
    @Transactional
    fun closeResolved(ticketId: UUID, cutoff: Instant): Boolean {
        val ticket = dao.find(ticketId, lock = true) ?: return false
        if (ticket.status != TicketStatus.RESOLVED || ticket.resolvedAt?.isBefore(cutoff) != true) return false
        transition(ticket, TicketStatus.CLOSED, null, "system")
        return true
    }

    /** 탈퇴한 계정의 문의를 종료한다. 이때부터 보유 기간을 센다. */
    @Transactional
    fun closeAbandoned(ticketId: UUID): Boolean {
        val ticket = dao.find(ticketId, lock = true) ?: return false
        if (ticket.status == TicketStatus.CLOSED) return false
        transition(ticket, TicketStatus.CLOSED, null, "system")
        return true
    }

    private fun transition(ticket: Ticket, status: TicketStatus, actor: UUID?, by: String) {
        event(ticket, actor, "status", ticket.status.name, status.name, by)
        dao.saveProgress(ticket, status, ticket.assigneeUuid, ticket.category, ticket.dueAt)
    }

    private fun event(ticket: Ticket, actor: UUID?, type: String, from: String?, to: String?, by: String) =
        dao.insertMessage(ticket.id, "EVENT", actor, "", event = mapOf("type" to type, "from" to from, "to" to to, "by" to by))

    private fun owned(account: Account, code: String, lock: Boolean = false): Ticket =
        code.takeIf(TicketCode::isValid)?.let { dao.findOwned(account.id, it, lock) } ?: throw notFound()

    private fun requireVersion(ticket: Ticket, expected: Int) {
        if (ticket.version != expected) throw IdpException("conflict", "다른 변경이 먼저 저장되었습니다. 새로 불러온 뒤 다시 시도해 주세요.", 409)
    }

    private fun rejectAttachments(count: Int) {
        if (count > 0) throw IdpException("attachments_disabled", "파일 첨부는 아직 사용할 수 없습니다.")
    }

    private fun securityCodeMatches(targetUserId: String?, securityCode: String): Boolean {
        if (targetUserId.isNullOrBlank()) return false
        return dao.securityCodesOf(targetUserId).any { SecretTools.constantTimeEquals(it, securityCode) }
    }

    private fun notFound() = IdpException("not_found", "문의를 찾을 수 없습니다.", 404)

    companion object {
        const val SECURITY_CODE_MATCHED = "securityCodeMatched"
    }
}
