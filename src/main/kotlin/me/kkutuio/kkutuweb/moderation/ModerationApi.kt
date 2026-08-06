package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.extension.getIp
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/api/admin/moderation")
class AdminModerationApi(
    private val authorizer: AdminModerationAuthorizer,
    private val service: ModerationService,
    private val auditService: ModerationAuditService
) {
    @GetMapping("/policy")
    fun policy(session: HttpSession): ModerationPolicySummary {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        return service.policySummary()
    }

    @GetMapping("/audits")
    fun audits(
        @RequestParam page: Int,
        @RequestParam(name = "size") pageSize: Int,
        @RequestParam sort: String,
        @RequestParam(name = "log_type", defaultValue = "") logType: String,
        @RequestParam(name = "target_id", defaultValue = "") targetId: String,
        @RequestParam(defaultValue = "") detail: String,
        @RequestParam(defaultValue = "") admin: String,
        session: HttpSession
    ): ListResponse<ModerationAuditEntry> {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        return auditService.getAudits(
            page = page,
            pageSize = pageSize,
            sortData = sort,
            filters = mapOf(
                "log_type" to logType,
                "target_id" to targetId,
                "detail" to detail,
                "admin" to admin
            ),
            includeIpAddress = authorizer.hasPrivilege(session, AdminSetting.Privilege.CONNECTION_LOG)
        )
    }

    @PostMapping("/policy/reload")
    fun reloadPolicy(session: HttpSession): ModerationPolicySummary {
        authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        return service.reloadPolicy()
    }

    @GetMapping("/users")
    fun search(@RequestParam query: String, session: HttpSession): List<ModerationUserSummary> {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        return service.searchUsers(query)
    }

    @GetMapping("/users/{userId}")
    fun detail(@PathVariable userId: String, session: HttpSession): ModerationUserDetail {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        return service.getUserDetail(userId)
    }

    @GetMapping("/users/{userId}/reports")
    fun reports(
        @PathVariable userId: String,
        @RequestParam(defaultValue = "0") window: Int,
        @RequestParam(required = false) anchorMillis: Long?,
        session: HttpSession
    ): ModerationReportPage {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        return service.getUserReports(userId, window, anchorMillis)
    }

    @GetMapping("/ip-subject")
    fun ipDetail(@RequestParam query: String, session: HttpSession): ModerationIpDetail {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        authorizer.require(session, AdminSetting.Privilege.CONNECTION_LOG)
        return service.getIpDetail(query)
    }

    @GetMapping("/ip-subject/reports")
    fun ipReports(
        @RequestParam query: String,
        @RequestParam(defaultValue = "0") window: Int,
        @RequestParam(required = false) anchorMillis: Long?,
        session: HttpSession
    ): ModerationReportPage {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        authorizer.require(session, AdminSetting.Privilege.CONNECTION_LOG)
        return service.getIpReports(query, window, anchorMillis)
    }

    @PostMapping("/sanctions/preview")
    fun preview(
        @RequestBody body: SanctionPreviewRequest,
        session: HttpSession
    ): Any {
        authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_ISSUE)
        if (body.custom != null) {
            authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        }
        if (body.overrideCaseId != null) {
            authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_REVOKE)
        }
        return service.preview(body)
    }

    @PostMapping("/sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    fun issue(
        @RequestBody body: SanctionIssueRequest,
        request: HttpServletRequest,
        session: HttpSession
    ): SanctionIssueResponse {
        val actor = authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_ISSUE)
        if (body.custom != null) {
            authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        }
        if (body.reportIds.isNotEmpty()) {
            authorizer.require(session, AdminSetting.Privilege.REPORT_RESOLVE)
        }
        if (body.overrideCaseId != null) {
            authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_REVOKE)
        }
        return service.issue(body, actor, request.getIp())
    }

    @PostMapping("/ip-sanctions/preview")
    fun previewIp(
        @RequestBody body: IpSanctionPreviewRequest,
        session: HttpSession
    ): Any {
        authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_ISSUE)
        authorizer.require(session, AdminSetting.Privilege.CONNECTION_LOG)
        if (body.overrideCaseId != null) authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_REVOKE)
        return service.previewIp(body)
    }

    @PostMapping("/ip-sanctions")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueIp(
        @RequestBody body: IpSanctionIssueRequest,
        request: HttpServletRequest,
        session: HttpSession
    ): SanctionIssueResponse {
        val actor = authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_ISSUE)
        authorizer.require(session, AdminSetting.Privilege.CONNECTION_LOG)
        if (body.reportIds.isNotEmpty()) authorizer.require(session, AdminSetting.Privilege.REPORT_RESOLVE)
        if (body.overrideCaseId != null) authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_REVOKE)
        return service.issueIp(body, actor, request.getIp())
    }

    @PostMapping("/sanctions/{caseId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(
        @PathVariable caseId: Long,
        @RequestBody body: RevokeSanctionRequest,
        request: HttpServletRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_REVOKE)
        service.revoke(caseId, body.reason, actor, request.getIp(), "USER")
    }

    @PostMapping("/ip-sanctions/{caseId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeIp(
        @PathVariable caseId: Long,
        @RequestBody body: RevokeSanctionRequest,
        request: HttpServletRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.USER_SANCTION_REVOKE)
        authorizer.require(session, AdminSetting.Privilege.CONNECTION_LOG)
        service.revoke(caseId, body.reason, actor, request.getIp(), "IP")
    }

    @PostMapping("/users/{userId}/counters/adjust")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun adjustCounter(
        @PathVariable userId: String,
        @RequestBody body: CounterAdjustmentRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.SANCTION_COUNTER_RESET)
        service.adjustCounter(userId, body, actor)
    }

    @PostMapping("/users/{userId}/nickname")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changeNickname(
        @PathVariable userId: String,
        @RequestBody body: ModerationNicknameChangeRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        service.changeNickname(userId, body, actor)
    }

    @PostMapping("/users/{userId}/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disconnectUser(
        @PathVariable userId: String,
        @RequestBody body: ModerationDisconnectRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        service.disconnectUser(userId, body, actor)
    }

    @PostMapping("/users/{userId}/notifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun queueNotification(
        @PathVariable userId: String,
        @RequestBody body: ModerationNotificationRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        service.queueNotification(userId, body, actor)
    }

    @PostMapping("/users/{userId}/flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateFlags(
        @PathVariable userId: String,
        @RequestBody body: ModerationFlagsUpdateRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.SANCTION_POLICY_OVERRIDE)
        service.updateFlags(userId, body, actor)
    }

    @PostMapping("/reports/{reportId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resolveReport(
        @PathVariable reportId: Long,
        @RequestBody body: ReportResolutionRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.REPORT_RESOLVE)
        service.resolveReport(reportId, body, actor)
    }

    @GetMapping("/reports/{reportId}")
    fun reportDetail(
        @PathVariable reportId: Long,
        session: HttpSession
    ): ModerationReportDetail {
        authorizer.require(session, AdminSetting.Privilege.USER_MODERATION_READ)
        return service.getReportDetail(reportId)
    }

    @PostMapping("/reports/{reportId}/game-context")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun linkReportGameContext(
        @PathVariable reportId: Long,
        @RequestBody body: ReportGameContextLinkRequest,
        session: HttpSession
    ) {
        val actor = authorizer.require(session, AdminSetting.Privilege.REPORT_RESOLVE)
        service.linkReportGameContext(reportId, body, actor)
    }
}
