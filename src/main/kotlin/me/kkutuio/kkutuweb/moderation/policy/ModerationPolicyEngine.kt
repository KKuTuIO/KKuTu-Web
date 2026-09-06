package me.kkutuio.kkutuweb.moderation.policy

import org.springframework.stereotype.Service
import java.time.Duration
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
                effects = resolveEffects(step.effects, startsAt, zoneId)
            )
        }

        val selected = candidates.maxWithOrNull(
            candidateComparator(policy.multipleViolations.selectionOrder)
        )
            ?: throw IllegalStateException("No moderation candidate was selected")
        val mergedEffects = mergeEffects(candidates, policy.multipleViolations)

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
            effects = mergedEffects,
            requiresApproval = false
        )
    }

    fun previewGuestIp(
        categoryCodes: List<String>,
        currentCounters: Map<String, Int>,
        previousGuestIpSanctions: Int,
        startsAt: Instant = Instant.now()
    ): ModerationPolicyPreview {
        require(previousGuestIpSanctions >= 0) { "Guest/IP offense count cannot be negative" }
        val base = preview(categoryCodes, currentCounters, startsAt)
        val policy = loader.current().document
        val primary = base.violations.first { it.selectedAsPrimary }
        val durationSource = primary.candidateEffects
            .filter { it.type in RESTRICTION_EFFECT_TYPES }
            .maxWithOrNull(effectComparator())
            ?: throw IllegalArgumentException("선택한 위반 사유의 현재 차수에는 손님 이용 제한 기간이 없습니다.")
        var guestIpOffense = previousGuestIpSanctions + 1
        if (
            durationSource.permanent &&
            policy.guestIpPolicy.permanentUserSkipsSecondStep &&
            guestIpOffense == 2
        ) {
            guestIpOffense = 3
        }
        val guestStep = policy.guestIpPolicy.steps
            .firstOrNull { it.offense == guestIpOffense }
            ?: policy.guestIpPolicy.steps.maxByOrNull { it.offense }
            ?: throw IllegalStateException("guestIpPolicy.steps is empty")
        val guestEffectDefinition = guestStep.effects.firstOrNull()
            ?: throw IllegalStateException("Guest/IP policy step has no effect")
        val guestEffect = ResolvedPolicyEffect(
            type = guestEffectDefinition.type,
            startsAt = startsAt,
            endsAt = durationSource.endsAt,
            permanent = durationSource.permanent,
            parameters = guestEffectDefinition.parameters + mapOf(
                "guestIpOffenseNo" to guestIpOffense,
                "durationSourceCategory" to primary.categoryCode,
                "durationSourceEffect" to durationSource.type
            )
        )
        return base.copy(
            selectionReason = "${base.selectionReason} / 손님·IP ${guestIpOffense}차",
            effects = listOf(guestEffect),
            requiresApproval = false
        )
    }

    fun previewCustom(
        reason: String,
        startsAt: Instant,
        endsAt: Instant?,
        permanent: Boolean
    ): ModerationPolicyPreview {
        require(reason.isNotBlank()) { "사용자 지정 제재 사유는 필수입니다." }
        require(reason.trim().length <= 500) { "사용자 지정 제재 사유는 500자 이하여야 합니다." }
        require(permanent.xor(endsAt != null)) { "사용자 지정 제재는 종료 시각 또는 영구 제재 중 하나를 지정해야 합니다." }
        require(endsAt == null || endsAt.isAfter(startsAt)) { "제재 종료 시각은 시작 시각 이후여야 합니다." }

        val loaded = loader.current()
        val effect = ResolvedPolicyEffect(
            type = "GAME_RESTRICTION",
            startsAt = startsAt,
            endsAt = endsAt,
            permanent = permanent,
            parameters = mapOf("customReason" to reason.trim())
        )
        return ModerationPolicyPreview(
            policyId = loaded.document.policyId,
            policyDigest = loaded.digest,
            primaryCategoryCode = "99",
            selectionReason = "사용자 지정: ${reason.trim()}",
            violations = listOf(
                PolicyViolationPreview(
                    categoryCode = "99",
                    categoryName = "사용자 지정",
                    offenseNo = 1,
                    selectedAsPrimary = true,
                    candidateEffects = listOf(effect)
                )
            ),
            effects = listOf(effect),
            requiresApproval = false
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

    private fun mergeEffects(
        candidates: List<Candidate>,
        policy: MultipleViolationPolicy
    ): List<ResolvedPolicyEffect> {
        val merged = candidates
            .flatMap { it.effects }
            .groupBy { it.type }
            .mapValues { (_, effects) -> effects.maxWithOrNull(effectComparator())!! }
        val stackingRules = policy.durationStacking.associateBy { it.effectType }
        val resolved = mutableMapOf<String, ResolvedPolicyEffect>()

        fun resolveStacked(type: String, path: Set<String>): ResolvedPolicyEffect {
            resolved[type]?.let { return it }
            val effect = merged.getValue(type)
            val rule = stackingRules[type]
            val preceding = rule?.afterEffectType?.let { precedingType ->
                if (precedingType in merged && precedingType !in path) {
                    resolveStacked(precedingType, path + type)
                } else null
            }
            val stacked = if (
                preceding != null && !effect.permanent && !preceding.permanent &&
                effect.endsAt != null && preceding.endsAt != null
            ) {
                val ownDuration = Duration.between(effect.startsAt, effect.endsAt)
                effect.copy(
                    endsAt = maxOf(effect.endsAt, preceding.endsAt.plus(ownDuration)),
                    parameters = effect.parameters + mapOf("durationStackedAfter" to rule.afterEffectType)
                )
            } else effect
            resolved[type] = stacked
            return stacked
        }

        return merged.keys.map { resolveStacked(it, emptySet()) }
    }

    private fun effectComparator(): Comparator<ResolvedPolicyEffect> {
        return compareBy<ResolvedPolicyEffect>(
            { effect -> if (effect.permanent) 1 else 0 },
            { effect -> effect.endsAt?.epochSecond?.minus(effect.startsAt.epochSecond) ?: 0 },
            { effect -> (effect.parameters["percent"] as? Number)?.toInt() ?: 0 }
        )
    }

    private fun candidateComparator(selectionOrder: List<String>): Comparator<Candidate> {
        return Comparator { left, right ->
            selectionOrder.forEach { criterion ->
                val comparison = candidateStrength(left, criterion)
                    .compareTo(candidateStrength(right, criterion))
                if (comparison != 0) return@Comparator comparison
            }
            left.category.code.compareTo(right.category.code)
        }
    }

    private fun candidateStrength(candidate: Candidate, criterion: String): Long {
        return when (criterion) {
            "PERMANENT_GAME_RESTRICTION" -> if (candidate.effects.any {
                it.type == "GAME_RESTRICTION" && it.permanent
            }) 1 else 0
            "GAME_RESTRICTION_DURATION" -> maxDuration(candidate.effects, "GAME_RESTRICTION")
            "RESOURCE_DEDUCTION_PERCENT" -> resourcePercent(candidate.effects).toLong()
            "CHAT_RESTRICTION_DURATION" -> maxDuration(candidate.effects, "CHAT_RESTRICTION")
            "NICKNAME_CHANGE_RESTRICTION" -> if (candidate.effects.any {
                it.type == "NICKNAME_CHANGE_RESTRICTION"
            }) 1 else 0
            "NICKNAME_RESET" -> if (candidate.effects.any { it.type == "NICKNAME_RESET" }) 1 else 0
            else -> throw IllegalArgumentException("Unknown multiple-violation criterion: $criterion")
        }
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
        val effects: List<ResolvedPolicyEffect>
    )

    companion object {
        private val RESTRICTION_EFFECT_TYPES = setOf(
            "GAME_RESTRICTION", "CHAT_RESTRICTION", "RELATED_SERVICE_RESTRICTION"
        )
    }
}
