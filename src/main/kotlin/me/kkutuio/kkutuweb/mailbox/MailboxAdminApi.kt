package me.kkutuio.kkutuweb.mailbox

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin")
class MailboxAdminApi(
    private val authorizer: AdminModerationAuthorizer,
    private val service: MailboxAdminService
) {
    @GetMapping("/mailbox")
    fun list(session: HttpSession): List<MailboxAdminMail> {
        authorizer.requireAny(session, AdminSetting.Privilege.MAILBOX, AdminSetting.Privilege.MAILBOX_NOTICE)
        return service.list()
    }

    @PostMapping("/mailbox")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: MailboxAdminMailRequest, session: HttpSession): MailboxAdminMail {
        val actor = authorizer.requireAny(session, AdminSetting.Privilege.MAILBOX, AdminSetting.Privilege.MAILBOX_NOTICE)
        return service.create(body, actor, authorizer.hasPrivilege(session, AdminSetting.Privilege.MAILBOX))
    }

    @PutMapping("/mailbox/{mailId}")
    fun update(
        @PathVariable mailId: String,
        @RequestBody body: MailboxAdminMailRequest,
        session: HttpSession
    ): MailboxAdminMail {
        val actor = authorizer.requireAny(session, AdminSetting.Privilege.MAILBOX, AdminSetting.Privilege.MAILBOX_NOTICE)
        return service.update(mailId, body, actor, authorizer.hasPrivilege(session, AdminSetting.Privilege.MAILBOX))
    }

    @DeleteMapping("/mailbox/{mailId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable mailId: String, session: HttpSession) {
        val actor = authorizer.requireAny(session, AdminSetting.Privilege.MAILBOX, AdminSetting.Privilege.MAILBOX_NOTICE)
        service.delete(mailId, actor, authorizer.hasPrivilege(session, AdminSetting.Privilege.MAILBOX))
    }

    @GetMapping("/mailbox-audits")
    fun audits(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
        @RequestParam(defaultValue = "created_at,DESC") sort: String,
        @RequestParam(name = "mail_id", defaultValue = "") mailId: String,
        @RequestParam(defaultValue = "") action: String,
        @RequestParam(defaultValue = "") admin: String,
        session: HttpSession
    ): ListResponse<MailboxAuditEntry> {
        authorizer.requireAny(session, AdminSetting.Privilege.MAILBOX, AdminSetting.Privilege.MAILBOX_NOTICE)
        return service.audits(page, size, sort, mailId, action, admin)
    }
}
