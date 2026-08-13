package me.kkutuio.kkutuweb.moderation

import com.fasterxml.jackson.databind.JsonNode
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
    val flags: JsonNode,
    val counters: Map<String, Int>,
    val history: List<ModerationCaseSummary>,
    val reports: List<ModerationReportSummary>,
    val reportsHasMore: Boolean,
    val reportsAnchor: Instant
)

data class ModerationIpGeoInfo(
    val countryCode: String?,
    val countryName: String?,
    val asn: String?,
    val asName: String?,
    val isp: String?
)

data class ModerationIpIdentity(
    val id: String,
    val nickname: String?,
    val guest: Boolean,
    val lastSeenAt: Instant,
    val connectionCount: Int
)

data class ModerationNetworkBlockedIp(
    val ip: String,
    val onlyGuest: Boolean,
    val startsAt: Instant,
    val endsAt: Instant?,
    val permanent: Boolean,
    val reason: String
)

data class ModerationCurrentIpBlock(
    val caseId: Long?,
    val onlyGuest: Boolean,
    val startsAt: Instant,
    val endsAt: Instant?,
    val permanent: Boolean,
    val reason: String
)

data class ModerationIpDetail(
    val ip: String,
    val sourceGuestId: String?,
    val lastSeenAt: Instant?,
    val network: String,
    val geo: ModerationIpGeoInfo?,
    val currentBlock: ModerationCurrentIpBlock?,
    val guestIpOffenseCount: Int,
    val counters: Map<String, Int>,
    val identities: List<ModerationIpIdentity>,
    val blockedIpsInNetwork: List<ModerationNetworkBlockedIp>,
    val history: List<ModerationCaseSummary>,
    val reports: List<ModerationReportSummary>,
    val reportsHasMore: Boolean,
    val reportsAnchor: Instant
)

data class ModerationCaseSummary(
    val caseId: Long,
    val primaryCategoryCode: String,
    val categoryCodes: List<String>,
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
    val time: Instant,
    val targetId: String? = null,
    val targetIp: String? = null
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
    val linkedSanctionSubjectType: String?,
    val linkedSanctionRevoked: Boolean,
    val gameReferences: List<ModerationReportGameReference>,
    val suspicionReferences: List<ModerationSuspicionReference>
)

data class ModerationLogAccess(
    val endpoint: String,
    val fileName: String,
    val reportId: Long,
    val expires: Long,
    val signature: String
)

data class ReportGameContextLinkRequest(
    val requestId: UUID,
    val gameId: String,
    val reason: String = ""
)

data class SanctionPreviewRequest(
    val userId: String,
    val categoryCodes: List<String> = emptyList(),
    val occurredAt: Instant? = null,
    val custom: CustomSanctionRequest? = null,
    val overrideCaseId: Long? = null
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
    val custom: CustomSanctionRequest? = null,
    val overrideCaseId: Long? = null
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

data class IpSanctionPreviewRequest(
    val subject: String,
    val categoryCodes: List<String>,
    val occurredAt: Instant? = null,
    val overrideCaseId: Long? = null
)

data class IpSanctionIssueRequest(
    val requestId: UUID,
    val subject: String,
    val categoryCodes: List<String>,
    val occurredAt: Instant,
    val evidenceText: String,
    val summary: String,
    val reportIds: List<Long> = emptyList(),
    val overrideCaseId: Long? = null
)

data class RevokeSanctionRequest(val reason: String = "")

data class CounterAdjustmentRequest(
    val requestId: UUID,
    val categoryCode: String,
    val value: Int,
    val reason: String = ""
)

data class ModerationNicknameChangeRequest(
    val requestId: UUID,
    val nickname: String? = null,
    val reason: String = ""
)

data class ModerationDisconnectRequest(
    val requestId: UUID,
    val reason: String = ""
)

data class ModerationNotificationRequest(
    val requestId: UUID,
    val message: String,
    val reason: String = ""
)

data class ModerationFlagEntryRequest(
    val key: String,
    val value: JsonNode,
    val timed: Boolean = false,
    val time: Long? = null
)

data class ModerationFlagsUpdateRequest(
    val requestId: UUID,
    val flags: List<ModerationFlagEntryRequest>,
    val reason: String = ""
)

data class ReportResolutionRequest(val status: String, val note: String = "")

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

data class ModerationAuditEntry(
    val id: String,
    val logTime: String,
    val logType: String,
    val targetType: String,
    val targetId: String?,
    val detail: String,
    val admin: String,
    val caseId: Long?,
    val reportId: Long?,
    val reports: List<ModerationAuditReportReference> = emptyList()
)

data class ModerationAuditReportReference(
    val reportId: Long,
    val time: String,
    val status: String,
    val categoryCode: String?,
    val reason: String,
    val reporterId: String,
    val targetId: String,
    val targetType: String?
)
