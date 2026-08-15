package me.kkutuio.kkutuweb.crossword

import kotlin.math.ln

internal object CrosswordCandidatePolicy {
    fun apply(rows: List<CrosswordCandidate>, request: CrosswordGenerateRequest): List<CrosswordCandidate> {
        val themeWeights = request.themeWeights
            .filter { it.weight > 0 }
            .associate { it.theme to it.weight }
        val excludedThemes = request.excludeThemes.toSet()
        val includedTypes = request.includeTypes.toSet()
        val excludedTypes = request.excludeTypes.toSet()
        val excludedWords = request.excludeWords.map(String::trim).filter(String::isNotEmpty).toSet()
        val allowedFlagMask = request.allowedFlags.fold(0) { mask, flag -> mask or flag }

        val eligible = rows.filter { candidate ->
            candidate.word !in excludedWords &&
                (!request.requireMeaning || candidate.hasMeaning) &&
                candidate.hit >= request.minHit &&
                (candidate.flag and allowedFlagMask.inv()) == 0 &&
                candidate.themes.none(excludedThemes::contains) &&
                (includedTypes.isEmpty() || candidate.types.any(includedTypes::contains)) &&
                candidate.types.none(excludedTypes::contains)
        }
        val themeCandidateCounts = themeWeights.keys.associateWith { theme ->
            eligible.count { theme in it.themes }.coerceAtLeast(1)
        }

        return eligible.mapNotNull { candidate ->
            val themeWeight = if (themeWeights.isEmpty()) 1.0 else
                candidate.themes.sumOf { theme ->
                    (themeWeights[theme] ?: 0.0) / (themeCandidateCounts[theme] ?: 1)
                }
            if (themeWeight <= 0) return@mapNotNull null

            // 0 means uniform selection; 1 gives frequently used words up to
            // roughly a tenfold preference without making rare words impossible.
            val popularity = 1.0 + request.popularityBias * ln(1.0 + candidate.hit.coerceAtLeast(0).toDouble())
            candidate.copy(selectionWeight = (themeWeight * popularity).coerceIn(0.0001, 1_000_000.0))
        }
    }
}
