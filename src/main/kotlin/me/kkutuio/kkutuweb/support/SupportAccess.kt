package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.login.LoginService
import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import jakarta.servlet.http.HttpSession

data class SupportSwitch(val enabled: Boolean, val closedDates: List<String>)

/** 문의를 누가 다룰 수 있는지, 지금 접수를 받는지. */
@Component
class SupportAccess(
    private val dao: SupportDao,
    private val setting: KKuTuSetting,
    private val loginService: LoginService
) {
    private val logger = LoggerFactory.getLogger(SupportAccess::class.java)

    /**
     * 마스터는 항상 MANAGER 다. 그 밖에는 관리자 권한이 살아 있으면서 문의 담당자로 등록된 계정만 역할이 있다.
     * 관리자 권한 목록(Privilege)에는 값을 더하지 않는다: 이전 버전은 모르는 값이 하나라도 있으면 그 사람의 권한을 전부 비운다.
     */
    fun roleOf(accountUuid: String): StaffRole? {
        if (setting.isAdminMaster(accountUuid)) return StaffRole.MANAGER
        if (accountUuid !in setting.getAdminIds()) return null
        return dao.findStaff(accountUuid)?.takeIf { it.active }?.role
    }

    /** 백오피스 요청의 계정 UUID. [minimum] 보다 낮은 역할이면 403. */
    fun require(session: HttpSession, minimum: StaffRole): Pair<String, StaffRole> {
        loginService.getSessionProfile(session) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val accountUuid = loginService.accountUuid(session) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val role = roleOf(accountUuid)?.takeIf { it >= minimum } ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "문의 업무 권한이 없습니다.")
        return accountUuid to role
    }

    /** 저장된 값이 없거나 읽을 수 없으면 접수를 받지 않는다. */
    fun switch(): SupportSwitch {
        val value = try {
            dao.setting(SWITCH_KEY)
        } catch (error: DataAccessException) {
            logger.warn("문의 접수 설정을 읽지 못했습니다. 접수를 받지 않는 것으로 봅니다.", error)
            null
        } ?: return SupportSwitch(false, emptyList())
        return SupportSwitch(
            value["enabled"]?.asBoolean() == true,
            value["closedDates"]?.toList()?.map { it.asString() } ?: emptyList()
        )
    }

    fun saveSwitch(value: SupportSwitch, actorUuid: String) = dao.saveSetting(SWITCH_KEY, value, actorUuid)

    /** 접수를 닫아 둔 동안에도 문의 담당자는 시험 접수를 할 수 있다. */
    fun canSubmit(accountUuid: String): Boolean = switch().enabled || roleOf(accountUuid) != null

    private companion object {
        const val SWITCH_KEY = "tickets"
    }
}
