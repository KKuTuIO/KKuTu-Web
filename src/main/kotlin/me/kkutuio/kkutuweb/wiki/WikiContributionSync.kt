package me.kkutuio.kkutuweb.wiki

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 기여도 = the number of edits. Every run reads the new mainspace edits of each bound wiki user, then settles the ones that are 24 hours old and still
 * not reverted; the settled count is written into the account flag `wikiContributions` for the game server's `wiki.contribution` mission signal.
 * Minor edits and edits already reverted are never counted.
 */
@Component
class WikiContributionSync(
    private val dao: WikiStore,
    private val wiki: WikiApi,
    @Value("\${wiki.sync.enabled:true}") private val enabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${wiki.sync.interval-ms:600000}", initialDelayString = "\${wiki.sync.initial-delay-ms:60000}")
    fun tick() {
        if (!enabled) return
        try {
            run(Instant.now())
        } catch (error: Exception) {
            logger.warn("리오위키 기여도 동기화 실패: {}", error.message)
        }
    }

    fun run(now: Instant) {
        for (binding in dao.allBindings()) {
            try {
                collect(binding)
            } catch (error: WikiUnavailableException) {
                logger.warn("리오위키 편집 목록을 읽지 못했습니다 ({}): {}", binding.wikiUsername, error.message)
            }
        }
        val touched = try { settle(now) } catch (error: WikiUnavailableException) { logger.warn("리오위키 편집 확인 실패: {}", error.message); emptySet() }
        for (binding in dao.allBindings()) {
            val (settled, _) = dao.counts(binding.accountId)
            if (settled > 0 || binding.accountId in touched) dao.writeFlag(binding.accountId, settled, dao.settledTimes(binding.accountId, MAX_EDIT_TIMES), now.toEpochMilli())
        }
    }

    private fun collect(binding: WikiBinding) {
        val from = maxOf(binding.boundAt, dao.latestEditAt(binding.accountId) ?: binding.boundAt)
        val fresh = wiki.editsSince(binding.wikiUserId, from).filter { countable(it) && !it.editedAt.isBefore(binding.boundAt) }
        if (fresh.isNotEmpty()) dao.addEdits(binding.accountId, binding.wikiUserId, fresh)
    }

    /** Settles what is old enough; an edit that was reverted or deleted in the meantime is dropped. @return the accounts that had edits settled */
    fun settle(now: Instant): Set<java.util.UUID> {
        val due = dao.pendingOlderThan(now.minus(SETTLE_AFTER))
        if (due.isEmpty()) return emptySet()
        val tags = wiki.revisionTags(due.map { it.revisionId })
        val (keep, drop) = due.partition { edit -> tags[edit.revisionId]?.let { REVERTED !in it } == true }
        dao.settle(keep.map { it.revisionId }, now)
        dao.discard(drop.map { it.revisionId })
        return keep.map { it.accountId }.toSet()
    }

    companion object {
        val SETTLE_AFTER: Duration = Duration.ofHours(24)
        const val REVERTED = "mw-reverted"
        const val MAX_EDIT_TIMES = 5000
        fun countable(edit: WikiEdit) = !edit.minor && REVERTED !in edit.tags
    }
}
