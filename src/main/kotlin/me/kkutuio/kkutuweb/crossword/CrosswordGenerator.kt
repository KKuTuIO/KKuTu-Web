package me.kkutuio.kkutuweb.crossword

import kotlin.math.max
import kotlin.math.ln
import java.util.PriorityQueue
import kotlin.random.Random

/** Generates connected crosswords in the legacy x,y,dir,length,word format. */
internal class CrosswordGenerator(private val random: Random = Random.Default) {
    private data class Cell(val char: Char, val directions: MutableSet<Int>)
    private data class Placement(val word: String, val x: Int, val y: Int, val dir: Int, val crossings: Int)
    private data class WeightedCandidate(val order: Double, val candidate: CrosswordCandidate)

    private val weightedCandidateOrder = compareBy<WeightedCandidate>(
        { it.order },
        { it.candidate.word }
    )

    fun generate(
        candidates: List<CrosswordCandidate>,
        request: CrosswordGenerateRequest
    ): GeneratedCrossword? {
        val usable = candidates.filter { it.word.length in request.minWordLength..request.maxWordLength }
        if (usable.isEmpty()) return null

        repeat(40) {
            val board = HashMap<Pair<Int, Int>, Cell>()
            val entries = ArrayList<CrosswordEntry>()
            val used = HashSet<String>()
            val seed = weightedSeed(usable.filter { it.word.length <= request.width }) ?: return null
            val seedX = max(0, (request.width - seed.word.length) / 2)
            val seedY = request.height / 2
            place(board, Placement(seed.word, seedX, seedY, 0, 0))
            entries.add(CrosswordEntry(seedX, seedY, 0, seed.word.length, seed.word))
            used.add(seed.word)

            while (entries.size < request.maxWords) {
                val placement = findPlacement(board, usable, used, request.width, request.height) ?: break
                place(board, placement)
                entries.add(CrosswordEntry(placement.x, placement.y, placement.dir, placement.word.length, placement.word))
                used.add(placement.word)
            }

            if (entries.size >= request.minWords) {
                return GeneratedCrossword(request.width, request.height, entries)
            }
        }
        return null
    }

    private fun weightedSeed(candidates: List<CrosswordCandidate>): CrosswordCandidate? {
        val top = candidates.shuffled(random).take(300)
        if (top.isEmpty()) return null
        val total = top.sumOf { it.selectionWeight }
        var cursor = random.nextDouble() * total
        for (candidate in top) {
            cursor -= candidate.selectionWeight
            if (cursor < 0) return candidate
        }
        return top.last()
    }

    private fun findPlacement(
        board: Map<Pair<Int, Int>, Cell>,
        candidates: List<CrosswordCandidate>,
        used: Set<String>,
        width: Int,
        height: Int
    ): Placement? {
        val cells = board.entries.shuffled(random)
        var best: Placement? = null
        var bestScore = Int.MIN_VALUE

        for ((point, cell) in cells.take(30)) {
            val matching = weightedSample(
                candidates.asSequence()
                .filter { it.word !in used && cell.char in it.word }
            )
            for (candidate in matching) {
                candidate.word.forEachIndexed { index, char ->
                    if (char != cell.char) return@forEachIndexed
                    for (dir in 0..1) {
                        val x = point.first - if (dir == 0) index else 0
                        val y = point.second - if (dir == 1) index else 0
                        val crossings = canPlace(board, candidate.word, x, y, dir, width, height)
                        if (crossings < 1) continue
                        val score = crossings * 100 + random.nextInt(50) + ln(1.0 + candidate.selectionWeight).toInt() * 4 -
                            distanceFromCenter(x, y, candidate.word.length, dir, width, height)
                        if (score > bestScore) {
                            bestScore = score
                            best = Placement(candidate.word, x, y, dir, crossings)
                        }
                    }
                }
            }
            if (bestScore >= 200) break
        }
        return best
    }

    /**
     * Takes the best exponential-race keys without invoking Random from a
     * Comparator. A random key selector inside sortedBy is not stable between
     * comparisons and can make Java TimSort throw
     * "Comparison method violates its general contract".
     */
    private fun weightedSample(candidates: Sequence<CrosswordCandidate>, limit: Int = 80): List<CrosswordCandidate> {
        val worstFirst = PriorityQueue(limit, weightedCandidateOrder.reversed())
        for (candidate in candidates) {
            val weighted = WeightedCandidate(weightedOrder(candidate.selectionWeight), candidate)
            if (worstFirst.size < limit) {
                worstFirst.add(weighted)
            } else if (weightedCandidateOrder.compare(weighted, worstFirst.peek()) < 0) {
                worstFirst.poll()
                worstFirst.add(weighted)
            }
        }
        return worstFirst.sortedWith(weightedCandidateOrder).map { it.candidate }
    }

    private fun weightedOrder(weight: Double): Double =
        -ln(random.nextDouble().coerceAtLeast(0.0000001)) / weight.coerceAtLeast(0.0001)

    private fun canPlace(
        board: Map<Pair<Int, Int>, Cell>,
        word: String,
        x: Int,
        y: Int,
        dir: Int,
        width: Int,
        height: Int
    ): Int {
        val endX = x + if (dir == 0) word.length - 1 else 0
        val endY = y + if (dir == 1) word.length - 1 else 0
        if (x < 0 || y < 0 || endX >= width || endY >= height) return -1

        val before = (x - if (dir == 0) 1 else 0) to (y - if (dir == 1) 1 else 0)
        val after = (endX + if (dir == 0) 1 else 0) to (endY + if (dir == 1) 1 else 0)
        if (board.containsKey(before) || board.containsKey(after)) return -1

        var crossings = 0
        word.forEachIndexed { index, char ->
            val px = x + if (dir == 0) index else 0
            val py = y + if (dir == 1) index else 0
            val existing = board[px to py]
            if (existing != null) {
                if (existing.char != char || dir in existing.directions) return -1
                crossings++
            } else {
                val sideA = if (dir == 0) px to py - 1 else px - 1 to py
                val sideB = if (dir == 0) px to py + 1 else px + 1 to py
                if (board.containsKey(sideA) || board.containsKey(sideB)) return -1
            }
        }
        return crossings
    }

    private fun place(board: MutableMap<Pair<Int, Int>, Cell>, placement: Placement) {
        placement.word.forEachIndexed { index, char ->
            val x = placement.x + if (placement.dir == 0) index else 0
            val y = placement.y + if (placement.dir == 1) index else 0
            val cell = board[x to y]
            if (cell == null) board[x to y] = Cell(char, mutableSetOf(placement.dir))
            else cell.directions.add(placement.dir)
        }
    }

    private fun distanceFromCenter(x: Int, y: Int, length: Int, dir: Int, width: Int, height: Int): Int {
        val centerX = x * 2 + if (dir == 0) length else 1
        val centerY = y * 2 + if (dir == 1) length else 1
        return kotlin.math.abs(centerX - width) + kotlin.math.abs(centerY - height)
    }
}
