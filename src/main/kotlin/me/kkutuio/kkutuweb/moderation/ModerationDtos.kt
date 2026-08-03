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
    val reports: List<ModerationReportSummary>,
    val reportsHasMore: Boolean,
    val reportsAnchor: Instant
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

data class ModerationReportPage(
    val window: Int,
    val fromDays: Int,
    val toDays: Int,
    val reports: List<ModerationReportSummary>,
    val hasMore: Boolean,
    val anchor: Instant
)

data class ModerationReportParty(
    val id: String,
    val reportedNickname: String?,
    val currentNickname: String?,
    val exists: Boolean
)

data class ModerationReportGameReference(
    val gameId: String,
    val roomId: Int,
    val roomTitle: String,
    val rule: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val logFileName: String,
    val relation: String
)

data class ModerationSuspicionReference(
    val caseId: Long,
    val time: Instant,
    val action: String,
    val doubt: String,
    val extraInfo: String?,
    val reference: String?,
    val relation: String = "TIME_NEARBY"
)

data class ModerationReportDetail(
    val reportId: Long,
    val time: Instant,
    val status: String,
    val targetType: String,
    val categoryCode: String?,
    val categoryName: String?,
    val reason: String,
    val detail: String?,
    val reportedChat: String?,
    val fileName: String?,
    val roomId: Int?,
    val gameId: String?,
    val gameContextSource: String,
    val reporter: ModerationReportParty,
    val target: ModerationReportParty,
    val resolvedAt: Instant?,
    val resolvedBy: String?,
    val resolutionNote: String?,
    val linkedSanctionCaseId: Long?,
    val gameReferences: List<ModerationReportGameReference>,
    val suspicionReferences: List<ModerationSuspicionReference>
)

data class ReportGameContextLinkRequest(
    val requestId: UUID,
    val gameId: String,
    val reason: String
)

data class SanctionPreviewRequest(
    val userId: String,
    val categoryCodes: List<String> = emptyList(),
    val occurredAt: Instant? = null,
    val custom: CustomSanctionRequest? = null
)

data class SanctionIssueRequest(
    val requestId: UUID,
    val userId: String,
    val categoryCodes: List<String> = emptyList(),
    val occurredAt: Instant,
    val evidenceText: String,
    val summary: String,
    val reportIds: List<Long> = emptyList(),
    val relatedUserIds: List<String> = emptyList(),
    val custom: CustomSanctionRequest? = null
)

data class CustomSanctionRequest(
    val reason: String,
    val endsAt: Instant? = null,
    val permanent: Boolean = false
)

data class SanctionIssueResponse(
    val caseId: Long,
    val preview: ModerationPolicyPreview,
    val duplicated: Boolean
)

data class RevokeSanctionRequest(val reason: String)

data class CounterAdjustmentRequest(
    val requestId: UUID,
    val categoryCode: String,
    val value: Int,
    val reason: String
)

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
