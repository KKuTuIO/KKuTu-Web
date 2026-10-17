package me.kkutuio.kkutuweb.event

import jakarta.servlet.http.HttpSession
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.moderation.AdminModerationAuthorizer
import me.kkutuio.kkutuweb.setting.AdminSetting
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class EventAdminApi(
    private val authorizer: AdminModerationAuthorizer,
    private val service: EventAdminService
) {
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalid(error: IllegalArgumentException) = mapOf("message" to (error.message ?: "행사 설정이 올바르지 않습니다."))

    @GetMapping("/events")
    fun list(session: HttpSession): List<EventAdminEvent> {
        authorizer.require(session, AdminSetting.Privilege.EVENT)
        return service.list()
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: EventAdminRequest, session: HttpSession): EventAdminEvent {
        val actor = authorizer.require(session, AdminSetting.Privilege.EVENT)
        return service.create(body, actor)
    }

    @PutMapping("/events/{eventId}")
    fun update(@PathVariable eventId: String, @RequestBody body: EventAdminRequest, session: HttpSession): EventAdminEvent {
        val actor = authorizer.require(session, AdminSetting.Privilege.EVENT)
        return service.update(eventId, body, actor)
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable eventId: String, session: HttpSession) {
        val actor = authorizer.require(session, AdminSetting.Privilege.EVENT)
        service.delete(eventId, actor)
    }

    @GetMapping("/event-audits")
    fun audits(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "15") size: Int,
        @RequestParam(defaultValue = "created_at,DESC") sort: String,
        @RequestParam(name = "event_id", defaultValue = "") eventId: String,
        @RequestParam(defaultValue = "") action: String,
        @RequestParam(defaultValue = "") admin: String,
        session: HttpSession
    ): ListResponse<EventAuditEntry> {
        authorizer.require(session, AdminSetting.Privilege.EVENT)
        return service.audits(page, size, sort, eventId, action, admin)
    }
}
