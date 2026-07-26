package me.kkutuio.kkutuweb.moderation.policy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModerationPolicy(
    val policyId: String,
    val revision: Int,
    val source: PolicySource,
    val timeZone: String,
    val noticePeriodDays: Int,
    val appeal: AppealPolicy,
    val counterReset: CounterResetPolicy,
    val multipleViolations: MultipleViolationPolicy,
    val relatedServices: RelatedServicesPolicy,
    val defaultSection7OverflowEffects: List<PolicyEffect>,
    val categories: List<PolicyCategory>,
    val guestIpPolicy: GuestIpPolicy
) {
    fun category(code: String): PolicyCategory =
        categories.firstOrNull { it.code == code }
            ?: throw IllegalArgumentException("Unknown moderation category: $code")
}

data class PolicySource(
    val title: String,
    val url: String,
    val publishedAt: String,
    val effectiveFrom: String,
    val retrievedAt: String
)

data class AppealPolicy(
    val windowDays: Int,
    val responseDays: Int
)

data class CounterResetPolicy(
    val eligibleAfter: String,
    val mode: String,
    val scope: String
)

data class MultipleViolationPolicy(
    val incrementAllCounters: Boolean,
    val bundleSelection: String,
    val selectionOrder: List<String>
)

data class RelatedServicesPolicy(
    val services: List<String>,
    val violationHandling: String,
    val applicationMode: String
)

data class PolicyCategory(
    val code: String,
    val name: String,
    val sourceSection: String,
    val requiresOverride: Boolean,
    val overflowMode: String,
    val steps: List<PolicyStep>,
    val allowedOverrides: List<PolicyEffectBundle> = emptyList()
)

data class PolicyStep(
    val offense: Int,
    val requiresApproval: Boolean = false,
    val effects: List<PolicyEffect>
)

data class PolicyEffectBundle(
    val id: String,
    val requiresApproval: Boolean,
    val effects: List<PolicyEffect>
)

data class PolicyEffect(
    val type: String,
    val duration: String? = null,
    val permanent: Boolean = false,
    val parameters: Map<String, Any?> = emptyMap()
)

data class GuestIpPolicy(
    val steps: List<PolicyStep>,
    val permanentUserSkipsSecondStep: Boolean,
    val guestChatConversion: String,
    val publicFacility: PublicFacilityPolicy,
    val separatelyRestrictableNetworks: List<String>
)

data class PublicFacilityPolicy(
    val defaultApplyFullIpRestriction: Boolean,
    val overrideAfterViolations: Int,
    val within: String,
    val requiresApproval: Boolean
)

data class LoadedModerationPolicy(
    val document: ModerationPolicy,
    val digest: String,
    val rawJson: String,
    val sourceDescription: String
)

data class PolicyViolationPreview(
    val categoryCode: String,
    val categoryName: String,
    val offenseNo: Int,
    val selectedAsPrimary: Boolean,
    val candidateEffects: List<ResolvedPolicyEffect>
)

data class ResolvedPolicyEffect(
    val type: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val permanent: Boolean,
    val parameters: Map<String, Any?>
)

data class ModerationPolicyPreview(
    val policyId: String,
    val policyDigest: String,
    val primaryCategoryCode: String,
    val selectionReason: String,
    val violations: List<PolicyViolationPreview>,
    val effects: List<ResolvedPolicyEffect>,
    val requiresApproval: Boolean
)

object PolicyTime {
    fun add(instant: Instant, amount: String, zoneId: ZoneId): Instant {
        return if (amount.contains("T")) {
            instant.plus(Duration.parse(amount))
        } else {
            ZonedDateTime.ofInstant(instant, zoneId).plus(Period.parse(amount)).toInstant()
        }
    }

    fun comparableSeconds(amount: String?, zoneId: ZoneId): Long {
        if (amount == null) return 0
        val start = Instant.EPOCH
        return Duration.between(start, add(start, amount, zoneId)).seconds
    }
}
