package me.kkutuio.kkutuweb.crossword

data class CrosswordPack(
    val id: String,
    val name: String,
    val lang: String,
    val weight: Double,
    val active: Boolean,
    val puzzleCount: Int
)

data class CrosswordEntry(
    val x: Int,
    val y: Int,
    val dir: Int,
    val length: Int,
    val word: String
)

data class CrosswordPuzzle(
    val id: Long,
    val map: String,
    val packId: String,
    val entries: List<CrosswordEntry>
)

data class CrosswordPackRequest(
    val id: String = "",
    val name: String = "",
    val lang: String = "ko",
    val weight: Double = 1.0,
    val active: Boolean = true
)

data class CrosswordGenerateRequest(
    val count: Int = 1,
    val width: Int = 8,
    val height: Int = 8,
    val minWords: Int = 10,
    val maxWords: Int = 20,
    val minWordLength: Int = 2,
    val maxWordLength: Int = 6,
    val themeWeights: List<CrosswordThemeWeight> = emptyList(),
    val excludeThemes: List<String> = emptyList(),
    val includeTypes: List<String> = emptyList(),
    val excludeTypes: List<String> = emptyList(),
    val requireMeaning: Boolean = true,
    val minHit: Int = 0,
    val allowedFlags: List<Int> = listOf(1, 2, 4),
    val popularityBias: Double = 0.35,
    val excludeWords: List<String> = emptyList()
)

data class CrosswordThemeWeight(
    val theme: String = "",
    val weight: Double = 1.0
)

data class CrosswordGenerateResult(
    val requested: Int,
    val created: Int,
    val puzzleIds: List<Long>,
    val puzzles: List<CrosswordPuzzle>,
    val candidateCount: Int
)

internal data class CrosswordCandidate(
    val word: String,
    val hit: Int,
    val themes: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val flag: Int = 0,
    val hasMeaning: Boolean = true,
    val selectionWeight: Double = 1.0
)

internal data class GeneratedCrossword(
    val width: Int,
    val height: Int,
    val entries: List<CrosswordEntry>
) {
    fun serialize(): String = entries.joinToString("|") {
        "${it.x},${it.y},${it.dir},${it.length},${it.word}"
    }
}
