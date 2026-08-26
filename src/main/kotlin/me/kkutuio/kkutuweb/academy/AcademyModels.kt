/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.academy

import com.fasterxml.jackson.annotation.JsonInclude

const val INJEONG_FLAG = 0b00000010

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AcademyRuleConfig(
    val lang: String = "ko",
    val dictionary: String = "COMBINED",
    val direction: String = "FORWARD",
    val duum: Boolean = true,
    val minLength: Int = 2,
    val maxLength: Int = 64,
    val includeLoanword: Boolean = true,
    val includeSpaced: Boolean = true,
    val includeDialect: Boolean = true,
    val includeOld: Boolean = true,
    val includeCultural: Boolean = true,
    val includeKung: Boolean = true,
    val themes: List<String> = emptyList(),
    val excludedThemes: List<String> = emptyList(),
    val excludedWords: List<String> = emptyList()
)

enum class AcademyDictionaryPreset { BASIC, STANDARD, COMBINED }
enum class AcademyDirection { FORWARD, REVERSE }
enum class AcademyPositionState { WIN, LOSS, ROUTE }
enum class AcademyBotLevel { RANDOM, BALANCED, EXPERT }
enum class AcademyPracticeDifficulty { BEGINNER, STANDARD, EXPERT }

data class AcademyMetaResponse(
    val dictionaries: List<AcademyOption>,
    val directions: List<AcademyOption>,
    val botLevels: List<AcademyOption>,
    val difficulties: List<AcademyOption>,
    val themes: Map<String, String>,
    val parts: Map<String, String>,
    val limits: AcademyLimits
)

data class AcademyOption(val value: String, val label: String, val description: String)

data class AcademyLimits(
    val publicSearchPageSize: Int,
    val analysisExcludedWordLimit: Int,
    val analysisDepthLimit: Int,
    val restrictedDailyLimit: Int,
    val restrictedResultLimit: Int
)

data class AcademySearchResponse(
    val items: List<AcademyWordView>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean
)

data class AcademyWordView(
    val word: String,
    val mean: String,
    val themes: List<String>,
    val types: List<String>,
    val hit: Int,
    val flags: Int,
    val length: Int,
    val startChar: String,
    val endChar: String,
    val nextChar: String,
    val defenseCount: Int,
    val attackGrade: String,
    val publishedOverride: Boolean
)

data class AcademyAnalysisRequest(
    val config: AcademyRuleConfig = AcademyRuleConfig(),
    val maxPly: Int = 7,
    val routeGroupLimit: Int = 30,
    val criticalWordLimit: Int = 200
)

data class AcademyAnalysisResponse(
    val corpusSize: Int,
    val syllableCount: Int,
    val states: Map<String, AcademySyllableStateView>,
    val counts: Map<String, Int>,
    val winningWithinPly: Map<Int, List<String>>,
    val criticalWords: List<AcademyCriticalWordView>,
    val routeGroups: List<AcademyRouteGroupView>,
    val generatedAt: Long
)

data class AcademySyllableStateView(
    val syllable: String,
    val state: String,
    val ply: Int?,
    val moveCount: Int,
    val representativeWord: String? = null
)

data class AcademyCriticalWordView(
    val word: String,
    val from: String,
    val to: String,
    val defenseCount: Int,
    val ply: Int?
)

data class AcademyRouteGroupView(
    val syllables: List<String>,
    val edgeCount: Int,
    val sampleWords: List<String>
)

data class AcademyCompareRequest(
    val base: AcademyRuleConfig = AcademyRuleConfig(),
    val compared: AcademyRuleConfig = AcademyRuleConfig()
)

data class AcademyCompareResponse(
    val baseCorpusSize: Int,
    val comparedCorpusSize: Int,
    val changed: List<AcademyStateChangeView>,
    val addedCriticalWords: List<AcademyCriticalWordView>,
    val removedCriticalWords: List<AcademyCriticalWordView>
)

data class AcademyStateChangeView(
    val syllable: String,
    val before: String?,
    val after: String?,
    val beforePly: Int?,
    val afterPly: Int?
)

data class AcademyStrategyRequest(
    val config: AcademyRuleConfig = AcademyRuleConfig(),
    val startChar: String,
    val usedWords: List<String> = emptyList(),
    val depth: Int = 10
)

data class AcademyStrategyResponse(
    val startChar: String,
    val state: String?,
    val ply: Int?,
    val line: List<AcademyStrategyStep>,
    val alternatives: List<AcademyMoveView>,
    val complete: Boolean
)

data class AcademyStrategyStep(
    val turn: Int,
    val from: String,
    val word: String,
    val to: String,
    val beforeState: String,
    val afterState: String,
    val defenseCount: Int
)

data class AcademyMoveView(
    val word: String,
    val from: String,
    val to: String,
    val resultingState: String,
    val resultingPly: Int?,
    val defenseCount: Int,
    val hit: Int
)

data class AcademySimulatorRequest(
    val config: AcademyRuleConfig = AcademyRuleConfig(),
    val chain: List<String> = emptyList(),
    val word: String,
    val shields: Int = 0,
    val botLevel: String? = null
)

data class AcademySimulatorResponse(
    val accepted: Boolean,
    val code: String,
    val message: String,
    val chain: List<String>,
    val requiredChar: String?,
    val nextChar: String?,
    val shieldUsed: Boolean,
    val analysis: AcademyMoveView? = null,
    val alternatives: List<AcademyMoveView> = emptyList(),
    val botMove: AcademyMoveView? = null
)

data class AcademyPracticeRequest(
    val config: AcademyRuleConfig = AcademyRuleConfig(),
    val difficulty: String = "STANDARD",
    val startChar: String? = null,
    val usedWords: List<String> = emptyList()
)

data class AcademyPracticeChallenge(
    val requiredChar: String,
    val difficulty: String,
    val timeLimitSeconds: Int,
    val shieldCount: Int,
    val availableMoveCount: Int,
    val hint: AcademyPracticeHint,
    val objective: String
)

data class AcademyPracticeHint(
    val firstLetter: String? = null,
    val length: Int? = null,
    val theme: String? = null,
    val sample: String? = null
)

data class AcademyQuizQuestion(
    val questionId: String,
    val date: String,
    val index: Int,
    val type: String,
    val prompt: String,
    val options: List<String>,
    val explanationHint: String? = null
)

data class AcademyQuizAnswerRequest(val questionId: String, val answer: String)

data class AcademyQuizAnswerResponse(
    val correct: Boolean,
    val answer: String,
    val explanation: String
)

data class AcademyRestrictedSearchRequest(
    val lang: String = "ko",
    val startChar: String,
    val mission: String? = null
)

data class AcademyRestrictedSearchResponse(
    val items: List<AcademyWordView>,
    val consumedTokens: Int,
    val remainingDailyQueries: Int
)

data class AcademyPublishRequest(val reason: String = "관리자 공개")

data class AcademyBulkPublishRequest(
    val words: List<String>,
    val reason: String = "관리자 일괄 공개"
)

data class AcademyPublishedWord(
    val lang: String,
    val word: String,
    val reason: String,
    val createdBy: String,
    val createdAt: String
)

data class AcademyPublishedListResponse(
    val items: List<AcademyPublishedWord>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean
)

internal data class AcademyCorpusWord(
    val word: String,
    val hit: Int,
    val flags: Int,
    val theme: String,
    val publishedOverride: Boolean
) {
    val startChar: String get() = word.firstOrNull()?.toString().orEmpty()
    val endChar: String get() = word.lastOrNull()?.toString().orEmpty()
    val themes: Set<String> get() = theme.split(',').filter { it.isNotBlank() && it != "0" }.toSet()
}

internal data class AcademyWordRecord(
    val word: String,
    val mean: String,
    val type: String,
    val hit: Int,
    val flags: Int,
    val theme: String,
    val publishedOverride: Boolean
)
