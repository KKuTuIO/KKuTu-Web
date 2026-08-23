package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.setup.SetupService
import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val NICKNAME_CHANGE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
private const val FIXED_NICKNAME_DORMANCY_MS = 180L * 24 * 60 * 60 * 1000
private const val FIXED_NICKNAME_COST = 100L

data class NicknameChangeResult(
    val nickname: String,
    val fixed: Boolean,
    val pingBalance: Long
)

@Service
class NicknameService(
    private val users: UserDao,
    private val setup: SetupService,
    private val identityDao: IdentityDao,
    private val advancedBadWordFilter: AdvancedBadWordFilter
) {
    fun validationError(nickname: String): String? = setup.nicknameValidationError(nickname)
    fun validationMessage(code: String): String = setup.nicknameValidationMessage(code)

    @Transactional
    fun createInitial(profileId: String, baseNickname: String, nicknameTag: String): String {
        setup.nicknameValidationError(baseNickname)?.let { throw IdpException("invalid_nickname", setup.nicknameValidationMessage(it)) }
        if (advancedBadWordFilter.contains(baseNickname)) {
            throw IdpException("exordial_has_bad_words", "별명에 사용이 제한된 단어가 포함되어 있습니다.", 409)
        }
        val nickname = baseNickname.trim().take(MAX_NICKNAME_LENGTH) + "#" + nicknameTag
        val meanable = normalize(nickname)
        users.lockNicknameKey(meanable)
        if (users.getUser(profileId) != null || users.getExistsSimilarityNick(meanable)) {
            throw IdpException("nickname_in_use", "이미 사용 중인 별명입니다. 다른 별명을 입력해 주세요.", 409)
        }
        users.newUser(profileId, nickname, meanable)
        return nickname
    }

    fun status(account: Account): Map<String, Any?> {
        val userId = identityDao.selectedProfileId(account.id) ?: account.legacyUserId
        val user = users.nicknameState(userId)
        if (user == null) return mapOf(
            "nickname" to null,
            "suffix" to "00000",
            "last_modified_at" to null,
            "fixed" to false,
            "ping_balance" to 0,
            "change_restricted" to false,
            "game_connected" to false,
            "can_change" to false,
            "profile_deleted" to true
        )
        val nextChangeAt = user.lastModifiedAt?.plus(NICKNAME_CHANGE_INTERVAL_MS)
        return mapOf(
            "nickname" to user.nickname,
            "suffix" to identityDao.nicknameSuffix(account.id),
            "last_modified_at" to user.lastModifiedAt,
            "fixed" to (user.nickname?.contains('#') == false),
            "ping_balance" to user.money,
            "change_restricted" to user.changeRestricted,
            "game_connected" to (user.gameServer != null),
            "next_change_at" to nextChangeAt,
            "can_change" to (user.gameServer == null && !user.changeRestricted && (nextChangeAt == null || nextChangeAt <= System.currentTimeMillis()))
        )
    }

    @Transactional
    fun change(account: Account, requestedNickname: String, fixed: Boolean): NicknameChangeResult {
        val baseNickname = requestedNickname.trim().take(MAX_NICKNAME_LENGTH)
        setup.nicknameValidationError(baseNickname)?.let { throw IdpException("invalid_nickname", setup.nicknameValidationMessage(it)) }
        val userId = identityDao.selectedProfileId(account.id) ?: account.legacyUserId
        val profileLegacyUserId = identityDao.selectedProfileLegacyUserId(account.id) ?: account.legacyUserId
        val state = users.lockNicknameState(userId)
            ?: throw IdpException("not_found", "게임 프로필을 찾을 수 없습니다.", 404)
        val now = System.currentTimeMillis()
        if (state.gameServer != null) throw IdpException("nickname_change_game_connected", "게임 접속 중에는 게임 내 프로필에서 별명을 변경해 주세요.", 409)
        if (state.changeRestricted) throw IdpException("nickname_change_restricted", "운영정책 위반으로 별명 변경을 이용할 수 없습니다.", 403)
        if (state.lastModifiedAt?.plus(NICKNAME_CHANGE_INTERVAL_MS)?.let { it > now } == true) {
            throw IdpException("nickname_change_cooldown", "별명은 7일에 한 번만 변경할 수 있습니다.")
        }
        if (fixed && state.money < FIXED_NICKNAME_COST) throw IdpException("insufficient_ping", "별명 고정에는 100핑이 필요합니다.")

        val nextNickname = if (fixed) baseNickname else "$baseNickname#${identityDao.nicknameSuffix(account.id)}"
        if (state.nickname == nextNickname) throw IdpException("nickname_unchanged", "현재 사용 중인 별명과 일치합니다.")
        val nextMeanable = normalize(nextNickname)

        if (advancedBadWordFilter.contains(baseNickname)) {
            throw IdpException("exordial_has_bad_words", "별명에 사용이 제한된 단어가 포함되어 있습니다.", 409)
        }

        // Lock by canonical name so concurrent Web requests cannot both acquire
        // the same fixed nickname. PostgreSQL is the authority; this account
        // management path must not alter the legacy nickname_cache contract.
        if (fixed) {
            users.lockNicknameKey(nextMeanable)
            users.lockFixedNicknameOwner(nextMeanable)?.takeIf { it.id != state.id }?.let { owner ->
                if ((owner.lastLogin ?: 0L) + FIXED_NICKNAME_DORMANCY_MS > now) {
                    throw IdpException("nickname_in_use", "이미 사용 중인 별명입니다. 다른 별명을 입력해 주세요.")
                }
                val releasedNickname = "$baseNickname#${identityDao.nicknameSuffixForUserId(owner.id)}"
                val collisionOwner = users.nicknameOwner(releasedNickname)
                if (collisionOwner != null && collisionOwner != owner.id) {
                    throw IdpException("nickname_release_collision", "이미 사용 중인 별명입니다. 다른 별명을 입력해 주세요.")
                }
                users.releaseDormantFixedNickname(owner.id, releasedNickname, normalize(releasedNickname))
                identityDao.audit(account.id, "DORMANT_FIXED_NICKNAME_RELEASED", metadata = mapOf("previous_owner" to owner.id))
            }
        } else {
            val collisionOwner = users.nicknameOwner(nextNickname)
            if (collisionOwner != null && collisionOwner != state.id) {
                throw IdpException("nickname_suffix_collision", "이미 사용 중인 별명입니다. 다른 별명을 입력해 주세요.")
            }
        }

        val updatedMoney = state.money - if (fixed) FIXED_NICKNAME_COST else 0L
        users.updateNickname(state.id, nextNickname, nextMeanable, updatedMoney, now)
        identityDao.updateProfileNickname(account.id, profileLegacyUserId, nextNickname)
        identityDao.updateNicknameTimestamp(account.id)
        identityDao.audit(account.id, "NICKNAME_CHANGED", metadata = mapOf("fixed" to fixed, "ping_spent" to (if (fixed) FIXED_NICKNAME_COST else 0)))
        return NicknameChangeResult(nextNickname, fixed, updatedMoney)
    }

    @Transactional
    fun changeExordial(account: Account, requestedExordial: String): String {
        val userId = identityDao.selectedProfileId(account.id) ?: account.legacyUserId
        val state = users.lockNicknameState(userId)
            ?: throw IdpException("not_found", "게임 프로필을 찾을 수 없습니다.", 404)
        if (state.changeRestricted) {
            throw IdpException("nickname_change_restricted", "운영정책 위반으로 별명 변경을 이용할 수 없습니다.", 403)
        }
        val exordial = requestedExordial.trim().take(MAX_EXORDIAL_LENGTH)
        if (advancedBadWordFilter.contains(exordial)) {
            throw IdpException("exordial_has_bad_words", "소개 한마디에 사용이 제한된 단어가 포함되어 있습니다.", 409)
        }
        users.updateUser(userId, mapOf("exordial" to if (exordial.isEmpty()) null else exordial))
        identityDao.audit(account.id, "EXORDIAL_CHANGED")
        return exordial
    }

    private fun normalize(nickname: String): String = nickname.replace(Regex("[-_ ]*"), "").lowercase()

    private companion object {
        const val MAX_NICKNAME_LENGTH = 15
        const val MAX_EXORDIAL_LENGTH = 100
    }
}
