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

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.ArrayDeque
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.random.Random

internal data class AcademyNodeState(
    val state: AcademyPositionState,
    val ply: Int?,
    val representativeWord: String? = null
)

internal data class AcademyGraphResult(
    val snapshot: AcademyCorpusSnapshot,
    val adjacency: Map<String, List<AcademyCorpusWord>>,
    val states: Map<String, AcademyNodeState>,
    val routeGroups: List<Set<String>>,
    val generatedAt: Long
)

private data class AcademyGraphCacheKey(val config: AcademyRuleConfig, val version: Long)

@Service
class AcademyGraphAnalyzer(private val corpusService: AcademyCorpusService) {
    private val cache = Caffeine.newBuilder()
        .maximumSize(12)
        .expireAfterAccess(Duration.ofMinutes(8))
        .build<AcademyGraphCacheKey, AcademyGraphResult>()

    fun invalidate() = cache.invalidateAll()

    internal fun graph(config: AcademyRuleConfig): AcademyGraphResult {
        val snapshot = corpusService.snapshot(config)
        return cache.get(AcademyGraphCacheKey(snapshot.config, snapshot.version)) { analyze(snapshot) }!!
    }

    fun analysis(request: AcademyAnalysisRequest): AcademyAnalysisResponse {
        val graph = graph(request.config)
        val maxPly = request.maxPly.coerceIn(1, 30)
        val criticalLimit = request.criticalWordLimit.coerceIn(1, 1_000)
        val routeLimit = request.routeGroupLimit.coerceIn(1, 200)
        val stateViews = graph.states.toSortedMap().mapValues { (syllable, state) ->
            AcademySyllableStateView(
                syllable = syllable,
                state = state.state.name,
                ply = state.ply,
                moveCount = graph.adjacency[syllable].orEmpty().size,
                representativeWord = state.representativeWord
            )
        }
        val counts = AcademyPositionState.entries.associate { state ->
            state.name to graph.states.values.count { it.state == state }
        }
        val winningWithinPly = (1..maxPly).associateWith { ply ->
            graph.states.asSequence()
                .filter { it.value.state == AcademyPositionState.WIN && (it.value.ply ?: Int.MAX_VALUE) <= ply }
                .map(Map.Entry<String, AcademyNodeState>::key)
                .sorted()
                .toList()
        }
        return AcademyAnalysisResponse(
            corpusSize = graph.snapshot.words.size,
            syllableCount = graph.states.size,
            states = stateViews,
            counts = counts,
            winningWithinPly = winningWithinPly,
            criticalWords = criticalWords(graph).take(criticalLimit),
            routeGroups = routeGroupViews(graph).take(routeLimit),
            generatedAt = graph.generatedAt
        )
    }

    fun compare(request: AcademyCompareRequest): AcademyCompareResponse {
        val before = graph(request.base)
        val after = graph(request.compared)
        val syllables = (before.states.keys + after.states.keys).toSortedSet()
        val changed = syllables.mapNotNull { syllable ->
            val old = before.states[syllable]
            val new = after.states[syllable]
            if (old?.state == new?.state && old?.ply == new?.ply) null
            else AcademyStateChangeView(
                syllable = syllable,
                before = old?.state?.name,
                after = new?.state?.name,
                beforePly = old?.ply,
                afterPly = new?.ply
            )
        }
        val beforeCritical = criticalWords(before).associateBy(AcademyCriticalWordView::word)
        val afterCritical = criticalWords(after).associateBy(AcademyCriticalWordView::word)
        return AcademyCompareResponse(
            baseCorpusSize = before.snapshot.words.size,
            comparedCorpusSize = after.snapshot.words.size,
            changed = changed,
            addedCriticalWords = (afterCritical.keys - beforeCritical.keys).mapNotNull(afterCritical::get).take(500),
            removedCriticalWords = (beforeCritical.keys - afterCritical.keys).mapNotNull(beforeCritical::get).take(500)
        )
    }

    fun strategy(request: AcademyStrategyRequest): AcademyStrategyResponse {
        val graph = graph(request.config)
        val start = request.startChar.trim().take(1)
        val state = graph.states[start]
        val used = request.usedWords.toMutableSet()
        val line = mutableListOf<AcademyStrategyStep>()
        var current = start
        repeat(request.depth.coerceIn(1, 30)) { turn ->
            val moves = rankedMoves(graph, current, used)
            val chosen = chooseByState(graph.states[current]?.state, moves) ?: return@repeat
            line += AcademyStrategyStep(
                turn = turn + 1,
                from = current,
                word = chosen.word,
                to = graph.snapshot.destination(chosen),
                beforeState = graph.states[current]?.state?.name ?: AcademyPositionState.LOSS.name,
                afterState = graph.states[graph.snapshot.destination(chosen)]?.state?.name ?: AcademyPositionState.LOSS.name,
                defenseCount = graph.adjacency[graph.snapshot.destination(chosen)].orEmpty().size
            )
            used += chosen.word
            current = graph.snapshot.destination(chosen)
        }
        return AcademyStrategyResponse(
            startChar = start,
            state = state?.state?.name,
            ply = state?.ply,
            line = line,
            alternatives = moveViews(graph, start, request.usedWords.toSet()).take(30),
            complete = line.isNotEmpty() && graph.adjacency[current].orEmpty().none { it.word !in used }
        )
    }

    internal fun moveViews(
        graph: AcademyGraphResult,
        requiredChar: String,
        usedWords: Set<String> = emptySet()
    ): List<AcademyMoveView> = rankedMoves(graph, requiredChar, usedWords).map { moveView(graph, requiredChar, it) }

    internal fun pickBotMove(
        graph: AcademyGraphResult,
        requiredChar: String,
        usedWords: Set<String>,
        level: AcademyBotLevel
    ): AcademyMoveView? {
        val ranked = rankedMoves(graph, requiredChar, usedWords)
        if (ranked.isEmpty()) return null
        val selected = when (level) {
            AcademyBotLevel.RANDOM -> ranked[Random.nextInt(ranked.size.coerceAtMost(40))]
            AcademyBotLevel.BALANCED -> ranked.take(10).maxByOrNull { it.hit }
            AcademyBotLevel.EXPERT -> chooseByState(graph.states[requiredChar]?.state, ranked)
        } ?: ranked.first()
        return moveView(graph, requiredChar, selected)
    }

    internal fun attackGrade(defenseCount: Int, resultingState: AcademyPositionState?): String = when {
        defenseCount == 0 -> "FINISH"
        resultingState == AcademyPositionState.LOSS -> "DECISIVE"
        defenseCount <= 2 -> "VERY_HIGH"
        defenseCount <= 5 -> "HIGH"
        defenseCount <= 15 -> "MEDIUM"
        else -> "LOW"
    }

    private fun analyze(snapshot: AcademyCorpusSnapshot): AcademyGraphResult {
        val nodes = linkedSetOf<String>()
        snapshot.words.forEach { word ->
            nodes += snapshot.source(word)
            nodes += snapshot.destination(word)
        }
        val adjacency = nodes.associateWith(snapshot::connectionWords)
        val targets = adjacency.mapValues { (_, words) -> words.map(snapshot::destination).distinct() }
        val reverse = mutableMapOf<String, MutableSet<String>>()
        targets.forEach { (source, destinations) ->
            destinations.forEach { destination -> reverse.getOrPut(destination) { linkedSetOf() } += source }
        }

        val states = mutableMapOf<String, AcademyNodeState>()
        val remaining = targets.mapValuesTo(mutableMapOf()) { it.value.size }
        val maximumWinningChild = mutableMapOf<String, Int>()
        val queue = PriorityQueue(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
        nodes.filter { targets[it].isNullOrEmpty() }.forEach { terminal ->
            states[terminal] = AcademyNodeState(AcademyPositionState.LOSS, 0)
            queue += 0 to terminal
        }

        while (queue.isNotEmpty()) {
            val (_, node) = queue.poll()
            val known = states[node] ?: continue
            reverse[node].orEmpty().forEach { predecessor ->
                if (states.containsKey(predecessor)) return@forEach
                if (known.state == AcademyPositionState.LOSS) {
                    val representative = adjacency[predecessor].orEmpty()
                        .filter { snapshot.destination(it) == node }
                        .maxWithOrNull(compareBy<AcademyCorpusWord> { it.hit }.thenBy { it.word.length })
                        ?.word
                    val distance = (known.ply ?: 0) + 1
                    states[predecessor] = AcademyNodeState(AcademyPositionState.WIN, distance, representative)
                    queue += distance to predecessor
                } else if (known.state == AcademyPositionState.WIN) {
                    remaining[predecessor] = (remaining[predecessor] ?: 1) - 1
                    maximumWinningChild[predecessor] = max(
                        maximumWinningChild[predecessor] ?: 0,
                        known.ply ?: 0
                    )
                    if (remaining[predecessor] == 0) {
                        val distance = (maximumWinningChild[predecessor] ?: 0) + 1
                        states[predecessor] = AcademyNodeState(AcademyPositionState.LOSS, distance)
                        queue += distance to predecessor
                    }
                }
            }
        }

        nodes.filterNot(states::containsKey).forEach { route ->
            states[route] = AcademyNodeState(AcademyPositionState.ROUTE, null)
        }

        val completedStates = states.mapValues { (node, current) ->
            if (current.representativeWord != null) return@mapValues current
            val words = adjacency[node].orEmpty()
            val representative = when (current.state) {
                AcademyPositionState.WIN -> words
                    .filter { states[snapshot.destination(it)]?.state == AcademyPositionState.LOSS }
                    .minWithOrNull(compareBy<AcademyCorpusWord> {
                        states[snapshot.destination(it)]?.ply ?: Int.MAX_VALUE
                    }.thenByDescending { it.hit })
                AcademyPositionState.LOSS -> words
                    .filter { states[snapshot.destination(it)]?.state == AcademyPositionState.WIN }
                    .maxWithOrNull(compareBy<AcademyCorpusWord> {
                        states[snapshot.destination(it)]?.ply ?: 0
                    }.thenBy { it.hit })
                AcademyPositionState.ROUTE -> words
                    .filter { states[snapshot.destination(it)]?.state == AcademyPositionState.ROUTE }
                    .maxByOrNull { it.hit }
            }?.word
            current.copy(representativeWord = representative)
        }

        val preliminary = AcademyGraphResult(
            snapshot = snapshot,
            adjacency = adjacency,
            states = completedStates,
            routeGroups = emptyList(),
            generatedAt = System.currentTimeMillis()
        )
        return preliminary.copy(routeGroups = routeComponents(preliminary))
    }

    private fun rankedMoves(
        graph: AcademyGraphResult,
        requiredChar: String,
        usedWords: Set<String>
    ): List<AcademyCorpusWord> {
        val sourceState = graph.states[requiredChar]?.state
        return graph.adjacency[requiredChar].orEmpty().asSequence()
            .filter { it.word !in usedWords }
            .sortedWith(
                compareBy<AcademyCorpusWord> { word ->
                    val target = graph.states[graph.snapshot.destination(word)]
                    when (sourceState) {
                        AcademyPositionState.WIN -> if (target?.state == AcademyPositionState.LOSS) 0 else 1
                        AcademyPositionState.LOSS -> if (target?.state == AcademyPositionState.WIN) 0 else 1
                        AcademyPositionState.ROUTE -> if (target?.state == AcademyPositionState.ROUTE) 0 else 1
                        else -> 0
                    }
                }.thenBy { word ->
                    val target = graph.states[graph.snapshot.destination(word)]
                    when (sourceState) {
                        AcademyPositionState.WIN -> target?.ply ?: Int.MAX_VALUE
                        AcademyPositionState.LOSS -> -(target?.ply ?: 0)
                        else -> graph.adjacency[graph.snapshot.destination(word)].orEmpty().size
                    }
                }.thenByDescending(AcademyCorpusWord::hit).thenByDescending { it.word.length }
            )
            .toList()
    }

    private fun chooseByState(
        state: AcademyPositionState?,
        ranked: List<AcademyCorpusWord>
    ): AcademyCorpusWord? = when (state) {
        AcademyPositionState.WIN, AcademyPositionState.LOSS, AcademyPositionState.ROUTE, null -> ranked.firstOrNull()
    }

    private fun moveView(
        graph: AcademyGraphResult,
        requiredChar: String,
        word: AcademyCorpusWord
    ): AcademyMoveView {
        val destination = graph.snapshot.destination(word)
        val state = graph.states[destination] ?: AcademyNodeState(AcademyPositionState.LOSS, 0)
        return AcademyMoveView(
            word = word.word,
            from = requiredChar,
            to = destination,
            resultingState = state.state.name,
            resultingPly = state.ply,
            defenseCount = graph.adjacency[destination].orEmpty().size,
            hit = word.hit
        )
    }

    private fun criticalWords(graph: AcademyGraphResult): List<AcademyCriticalWordView> =
        graph.states.asSequence()
            .filter { it.value.state == AcademyPositionState.WIN }
            .flatMap { (source, _) ->
                graph.adjacency[source].orEmpty().asSequence()
                    .filter { graph.states[graph.snapshot.destination(it)]?.state == AcademyPositionState.LOSS }
                    .map { word ->
                        val destination = graph.snapshot.destination(word)
                        AcademyCriticalWordView(
                            word = word.word,
                            from = source,
                            to = destination,
                            defenseCount = graph.adjacency[destination].orEmpty().size,
                            ply = graph.states[source]?.ply
                        )
                    }
            }
            .distinctBy(AcademyCriticalWordView::word)
            .sortedWith(compareBy<AcademyCriticalWordView> { it.ply ?: Int.MAX_VALUE }.thenBy { it.defenseCount }.thenBy { it.word })
            .toList()

    private fun routeComponents(graph: AcademyGraphResult): List<Set<String>> {
        val routes = graph.states.filterValues { it.state == AcademyPositionState.ROUTE }.keys
        if (routes.isEmpty()) return emptyList()
        val edges = routes.associateWith { node ->
            graph.adjacency[node].orEmpty().map(graph.snapshot::destination).filter(routes::contains).distinct()
        }
        val reverse = mutableMapOf<String, MutableList<String>>()
        edges.forEach { (source, destinations) ->
            destinations.forEach { destination -> reverse.getOrPut(destination) { mutableListOf() } += source }
        }

        val visited = mutableSetOf<String>()
        val order = mutableListOf<String>()
        routes.forEach { start ->
            if (start in visited) return@forEach
            val stack = ArrayDeque<Pair<String, Boolean>>()
            stack.addLast(start to false)
            while (stack.isNotEmpty()) {
                val (node, exiting) = stack.removeLast()
                if (exiting) {
                    order += node
                    continue
                }
                if (!visited.add(node)) continue
                stack.addLast(node to true)
                edges[node].orEmpty().forEach { next -> if (next !in visited) stack.addLast(next to false) }
            }
        }

        val assigned = mutableSetOf<String>()
        val components = mutableListOf<Set<String>>()
        order.asReversed().forEach { start ->
            if (!assigned.add(start)) return@forEach
            val component = linkedSetOf<String>()
            val stack = ArrayDeque<String>()
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                component += node
                reverse[node].orEmpty().forEach { previous ->
                    if (assigned.add(previous)) stack.addLast(previous)
                }
            }
            components += component
        }
        return components.sortedByDescending(Set<String>::size)
    }

    private fun routeGroupViews(graph: AcademyGraphResult): List<AcademyRouteGroupView> =
        graph.routeGroups.map { group ->
            val words = group.asSequence().flatMap { source ->
                graph.adjacency[source].orEmpty().asSequence()
                    .filter { graph.snapshot.destination(it) in group }
            }.distinctBy(AcademyCorpusWord::word).toList()
            AcademyRouteGroupView(
                syllables = group.sorted(),
                edgeCount = words.size,
                sampleWords = words.sortedByDescending(AcademyCorpusWord::hit).take(12).map(AcademyCorpusWord::word)
            )
        }
}
