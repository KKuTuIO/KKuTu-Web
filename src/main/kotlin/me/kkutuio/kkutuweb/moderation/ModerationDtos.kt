package me.kkutuio.kkutuweb.moderation

import me.kkutuio.kkutuweb.moderation.policy.ModerationPolicyPreview
import java.time.Instant
import java.util.UUID

data class ModerationUserSummary(
    val id: String,
    val nickname: String?,
    val score: Long,
    val money: Long,
    val rank: Long?,
    val level: Int,
    val currentlyRestricted: Boolean
)

data class ModerationUserDetail(
    val user: ModerationUserSummary,
    val counters: Map<String, Int>,
    val history: List<ModerationCaseSummary>,
    val reports: List<ModerationReportSummary>
)

data class ModerationCaseSummary(
    val caseId: Long,
    val primaryCategoryCode: String,
    val summary: String,
    val occurredAt: Instant,
    val issuedAt: Instant,
    val issuedBy: String,
    val revokedAt: Instant?,
    val effects: List<ModerationEffectSummary>
)

data class ModerationEffectSummary(
    val type: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val permanent: Boolean,
    val status: String
)

data class ModerationReportSummary(
    val reportId: Long,
    val categoryCode: String?,
    val reason: String,
    val detail: String?,
    val status: String,
    val time: Instant
)

data class SanctionPreviewRequest(
    val userId: String,
    val categoryCodes: List<String>,
    val occurredAt: Instant? = null
)

data class SanctionIssueRequest(
    val requestId: UUID,
    val userId: String,
    val categoryCodes: List<String>,
    val occurredAt: Instant,
    val evidenceText: String,
    val summary: String,
    val reportIds: List<Long> = emptyList(),
    val relatedUserIds: List<String> = emptyList()
)

data class SanctionIssueResponse(
    val caseId: Long,
    val preview: ModerationPolicyPreview,
    val duplicated: Boolean
)

data class RevokeSanctionRequest(val reason: String)

data class CounterResetRequest(val requestId: UUID, val reason: String)

data class ReportResolutionRequest(val status: String, val note: String)

data class ModerationPolicySummary(
    val policyId: String,
    val digest: String,
    val sourceUrl: String,
    val categories: List<ModerationCategorySummary>
)

data class ModerationCategorySummary(
    val code: String,
    val name: String,
    val requiresOverride: Boolean
)
