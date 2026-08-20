package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.user.UserDao
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Applies the delayed profile/account deletion policy without touching active data. */
@Service
class AccountDeletionService(
    private val dao: IdentityDao,
    private val users: UserDao
) {
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    fun processDueDeletions() {
        dao.dueProfileIds().forEach { row ->
            val profileId = UUID.fromString(row["id"].toString())
            val legacyUserId = row["legacy_user_id"]?.toString()?.takeIf { it.isNotBlank() }
            val profileUserId = row["profile_user_id"]?.toString()?.takeIf { it.isNotBlank() }
            val archived = legacyUserId?.let(users::archiveAndDelete) == true
            if (!archived && profileUserId != legacyUserId) profileUserId?.let(users::archiveAndDelete)
            dao.completeProfileDeletion(profileId)
        }
        dao.dueAccountIds().forEach(dao::completeAccountDeletion)
        users.purgeDeletedUsers()
    }
}
