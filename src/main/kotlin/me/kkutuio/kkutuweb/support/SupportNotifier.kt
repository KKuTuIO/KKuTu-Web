package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.identity.AccountStatus
import me.kkutuio.kkutuweb.identity.IdentityDao
import me.kkutuio.kkutuweb.identity.IdentityProviderSettings
import me.kkutuio.kkutuweb.identity.IdentityType
import org.slf4j.LoggerFactory
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import jakarta.annotation.PreDestroy

/** 답변 알림 메일. 문의 번호와 링크만 보내고 본문은 싣지 않는다. 보내지 못해도 답변 저장에는 영향이 없다. */
@Component
class SupportNotifier(
    private val mailSender: JavaMailSender,
    private val identity: IdentityProviderSettings,
    private val identityDao: IdentityDao,
    private val settings: SupportSettings
) {
    private val logger = LoggerFactory.getLogger(SupportNotifier::class.java)
    // 메일 서버가 멈춰도 대기열이 끝없이 늘지 않게 한다. 넘친 알림은 버리고 기록한다(답변은 고객센터에서 볼 수 있다).
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.SECONDS, ArrayBlockingQueue(QUEUE_LIMIT),
        { Thread(it, "support-mail").apply { isDaemon = true } },
        { _, _ -> logger.warn("답변 알림 메일 대기열이 가득 차 알림 하나를 보내지 않았습니다.") }
    )

    /** 지금 트랜잭션이 커밋된 뒤에 보낸다. */
    fun replyPosted(ticket: Ticket) {
        if (!ticket.notifyEmail) return
        val send = { executor.execute { deliver(ticket.accountId, ticket.code) } }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return send()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    private fun deliver(accountId: UUID, code: String) {
        try {
            // 접수 뒤에 탈퇴했거나 메일을 지웠으면 보내지 않는다.
            if (identityDao.findAccount(accountId)?.status != AccountStatus.ACTIVE) return
            val to = identityDao.listIdentities(accountId)
                .firstOrNull { it.type == IdentityType.EMAIL && it.verifiedAt != null && it.revokedAt == null }?.subject ?: return
            mailSender.send(SimpleMailMessage().apply {
                setFrom(identity.mailFrom)
                setTo(to)
                setSubject("끄투리오 고객센터 답변 알림")
                setText("문의 ${code}에 답변이 등록되었습니다.\n아래 주소에서 확인해 주세요.\n\n${settings.portalUrl}/support/tickets/$code")
            })
        } catch (error: Exception) {
            logger.warn("문의 답변 알림 메일을 보내지 못했습니다: {}", code, error)
        }
    }

    @PreDestroy
    fun close() = executor.shutdown()

    private companion object {
        const val QUEUE_LIMIT = 500
    }
}
