package me.kkutuio.kkutuweb.moderation.policy

import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

@Service
class ModerationPolicyEngine(
    private val loader: ModerationPolicyLoader
) {
    fun preview(
        categoryCodes: List<String>,
        currentCounters: Map<String, Int>,
        startsAt: Instant = Instant.now()
    ): ModerationPolicyPreview {
        require(categoryCodes.isNotEmpty()) { "At least one moderation category is required" }
        require(categoryCodes.distinct().size == categoryCodes.size) { "Duplicate moderation categories are not allowed" }

        val loaded = loader.current()
        val policy = loaded.document
        val zoneId = ZoneId.of(policy.timeZone)
        val candidates = categoryCodes.map { code ->
            val category = policy.category(code)
            require(!category.requiresOverride) { "Category $code requires an explicit override" }
            val offenseNo = (currentCounters[code] ?: 0) + 1
            val step = resolveStep(policy, category, offenseNo)
            Candidate(
                category = category,
                offenseNo = offenseNo,
                effects = resolveEffects(step.effects, startsAt, zoneId),
                requiresApproval = step.requiresApproval
            )
        }

        val selected = candidates.maxWithOrNull(candidateComparator(zoneId))
            ?: throw IllegalStateException("No moderation candidate was selected")

        return ModerationPolicyPreview(
            policyId = policy.policyId,
            policyDigest = loaded.digest,
            primaryCategoryCode = selected.category.code,
            selectionReason = selectionReason(selected),
            violations = candidates.map { candidate ->
                PolicyViolationPreview(
                    categoryCode = candidate.category.code,
                    categoryName = candidate.category.name,
                    offenseNo = candidate.offenseNo,
                    selectedAsPrimary = candidate.category.code == selected.category.code,
                    candidateEffects = candidate.effects
                )
            },
            effects = selected.effects,
            requiresApproval = selected.requiresApproval
        )
    }

    private fun resolveStep(
        policy: ModerationPolicy,
        category: PolicyCategory,
        offenseNo: Int
    ): PolicyStep {
        category.steps.firstOrNull { it.offense == offenseNo }?.let { return it }
        return when (category.overflowMode) {
            "REPEAT_LAST" -> category.steps.lastOrNull()
                ?: throw IllegalArgumentException("Category ${category.code} has no policy steps")
            "USE_DEFAULT_SECTION7_OVERFLOW" -> PolicyStep(
                offense = offenseNo,
                requiresApproval = false,
                effects = policy.defaultSection7OverflowEffects
            )
            else -> throw IllegalArgumentException("Category ${category.code} requires an explicit override")
        }
    }

    private fun resolveEffects(
        effects: List<PolicyEffect>,
        startsAt: Instant,
        zoneId: ZoneId
    ): List<ResolvedPolicyEffect> {
        return effects.map { effect ->
            ResolvedPolicyEffect(
                type = effect.type,
                startsAt = startsAt,
                endsAt = if (effect.duration == null) null else PolicyTime.add(startsAt, effect.duration, zoneId),
                permanent = effect.permanent,
                parameters = effect.parameters
            )
        }
    }

    private fun candidateComparator(zoneId: ZoneId): Comparator<Candidate> {
        return compareBy<Candidate>(
            { candidate -> if (candidate.effects.any { it.type == "GAME_RESTRICTION" && it.permanent }) 1 else 0 },
            { candidate -> maxDuration(candidate.effects, "GAME_RESTRICTION") },
            { candidate -> resourcePercent(candidate.effects) },
            { candidate -> maxDuration(candidate.effects, "CHAT_RESTRICTION") },
            { candidate -> if (candidate.effects.any { it.type == "NICKNAME_CHANGE_RESTRICTION" }) 1 else 0 },
            { candidate -> if (candidate.effects.any { it.type == "NICKNAME_RESET" }) 1 else 0 },
            { candidate -> candidate.category.code }
        )
    }

    private fun maxDuration(effects: List<ResolvedPolicyEffect>, type: String): Long {
        return effects
            .filter { it.type == type && it.endsAt != null }
            .maxOfOrNull { it.endsAt!!.epochSecond - it.startsAt.epochSecond }
            ?: 0
    }

    private fun resourcePercent(effects: List<ResolvedPolicyEffect>): Int {
        return effects
            .filter { it.type == "RESOURCE_ADJUSTMENT" }
            .mapNotNull { (it.parameters["percent"] as? Number)?.toInt() }
            .maxOrNull()
            ?: 0
    }

    private fun selectionReason(candidate: Candidate): String {
        val effect = candidate.effects.firstOrNull {
            it.type == "GAME_RESTRICTION" && it.permanent
        } ?: candidate.effects.maxByOrNull {
            (it.endsAt?.epochSecond ?: it.startsAt.epochSecond) - it.startsAt.epochSecond
        }
        return "${candidate.category.name}: ${effect?.type ?: "WARNING"}"
    }

    private data class Candidate(
        val category: PolicyCategory,
        val offenseNo: Int,
        val effects: List<ResolvedPolicyEffect>,
        val requiresApproval: Boolean
    )
}
