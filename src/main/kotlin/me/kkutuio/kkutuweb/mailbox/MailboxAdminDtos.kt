package me.kkutuio.kkutuweb.mailbox

import tools.jackson.databind.JsonNode
import java.time.Instant

data class MailboxAdminMail(
    val id: String,
    val name: String,
    val content: String,
    val distributionStartAt: Long,
    val distributionEndAt: Long,
    val claimEndAt: Long,
    val maxDistributionCount: Int,
    val distributedCount: Int,
    val eligibility: JsonNode,
    val perUserClaimLimit: Int,
    val rewards: JsonNode,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class MailboxAdminMailRequest(
    val id: String,
    val name: String,
    val content: String,
    val distributionStartAt: Long,
    val distributionEndAt: Long,
    val claimEndAt: Long,
    val maxDistributionCount: Int = -1,
    val eligibility: JsonNode,
    val perUserClaimLimit: Int = 1,
    val rewards: JsonNode,
    val active: Boolean = false
)

data class MailboxAuditEntry(
    val id: Long,
    val mailId: String,
    val action: String,
    val beforeData: JsonNode?,
    val afterData: JsonNode?,
    val adminId: String,
    val createdAt: Instant
)
