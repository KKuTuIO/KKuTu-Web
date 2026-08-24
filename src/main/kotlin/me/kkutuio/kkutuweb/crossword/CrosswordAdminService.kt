package me.kkutuio.kkutuweb.crossword

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class CrosswordAdminService(private val jdbcTemplate: JdbcTemplate) {
    fun packs(): List<CrosswordPack> = jdbcTemplate.query(
        """SELECT p._id, p.name, p.lang, p.weight, p.active,
                  CASE p.lang WHEN 'ko' THEN (SELECT COUNT(*) FROM kkutu_cw_ko c WHERE c.pack_id = p._id)
                              ELSE (SELECT COUNT(*) FROM kkutu_cw_en c WHERE c.pack_id = p._id) END AS puzzle_count
           FROM crossword_pack p ORDER BY p.lang, p.created_at, p._id""".trimIndent()
    ) { rs, _ ->
        CrosswordPack(rs.getString("_id"), rs.getString("name"), rs.getString("lang"),
            rs.getDouble("weight"), rs.getBoolean("active"), rs.getInt("puzzle_count"))
    }

    @Transactional
    fun createPack(request: CrosswordPackRequest): CrosswordPack {
        validatePack(request, true)
        jdbcTemplate.update(
            "INSERT INTO crossword_pack (_id, name, lang, weight, active) VALUES (?, ?, ?, ?, ?)",
            request.id.trim(), request.name.trim(), request.lang, request.weight, request.active
        )
        return packs().first { it.id == request.id.trim() }
    }

    @Transactional
    fun updatePack(id: String, request: CrosswordPackRequest): CrosswordPack {
        validatePack(request.copy(id = id), false)
        val changed = jdbcTemplate.update(
            "UPDATE crossword_pack SET name = ?, weight = ?, active = ?, updated_at = NOW() WHERE _id = ?",
            request.name.trim(), request.weight, request.active, id
        )
        require(changed == 1) { "존재하지 않는 팩입니다." }
        val allPacks = packs()
        val languagePacks = allPacks.filter { it.lang == allPacks.first { pack -> pack.id == id }.lang }
        if (languagePacks.any { it.puzzleCount > 0 }) {
            require(languagePacks.any { it.active && it.weight > 0 && it.puzzleCount > 0 }) {
                "퍼즐이 있는 활성 팩의 가중치를 최소 하나는 0보다 크게 유지해야 합니다."
            }
        }
        return allPacks.first { it.id == id }
    }

    @Transactional
    fun deletePack(id: String) {
        val pack = packs().firstOrNull { it.id == id } ?: throw IllegalArgumentException("존재하지 않는 팩입니다.")
        require(pack.puzzleCount == 0) { "퍼즐이 들어 있는 팩은 삭제할 수 없습니다." }
        require(!id.startsWith("original-")) { "오리지널 팩은 삭제할 수 없습니다." }
        jdbcTemplate.update("DELETE FROM crossword_pack WHERE _id = ?", id)
    }

    fun puzzles(packId: String, page: Int, size: Int): List<CrosswordPuzzle> {
        val pack = requirePack(packId)
        val safeSize = size.coerceIn(1, 100)
        val safePage = page.coerceAtLeast(0)
        return jdbcTemplate.query(
            "SELECT _id, map, pack_id, data FROM ${puzzleTable(pack.lang)} WHERE pack_id = ? ORDER BY _id DESC LIMIT ? OFFSET ?",
            { rs, _ -> puzzle(rs.getLong("_id"), rs.getString("map"), rs.getString("pack_id"), rs.getString("data")) },
            packId, safeSize, safePage * safeSize
        )
    }

    @Transactional
    fun deletePuzzle(packId: String, puzzleId: Long) {
        val pack = requirePack(packId)
        val changed = jdbcTemplate.update(
            "DELETE FROM ${puzzleTable(pack.lang)} WHERE _id = ? AND pack_id = ?", puzzleId, packId
        )
        require(changed == 1) { "존재하지 않는 퍼즐입니다." }
    }

    @Transactional
    fun generate(packId: String, request: CrosswordGenerateRequest): CrosswordGenerateResult {
        validateGenerate(request)
        val pack = requirePack(packId)
        val candidates = CrosswordCandidatePolicy.apply(loadCandidates(pack.lang, request), request)
        require(candidates.size >= request.minWords) { "조건에 맞는 사전 단어가 부족합니다." }

        val generator = CrosswordGenerator()
        val generated = ArrayList<GeneratedCrossword>()
        val serialized = jdbcTemplate.queryForList(
            "SELECT data FROM ${puzzleTable(pack.lang)} WHERE pack_id = ? AND data IS NOT NULL",
            String::class.java,
            packId
        ).toHashSet()
        var attempts = 0
        val maxAttempts = request.count * 60
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (generated.size < request.count && attempts++ < maxAttempts && System.nanoTime() < deadline) {
            val puzzle = generator.generate(candidates, request) ?: continue
            if (serialized.add(puzzle.serialize())) generated.add(puzzle)
        }
        require(generated.size == request.count) {
            "30초 내에 요청한 ${request.count}개 중 ${generated.size}개만 생성할 수 있었습니다. 단어 수·길이 조건을 완화해 주세요."
        }

        val ids = ArrayList<Long>()
        for (generatedPuzzle in generated) {
            val id = jdbcTemplate.queryForObject(
                "INSERT INTO ${puzzleTable(pack.lang)} (map, data, pack_id) VALUES (?, ?, ?) RETURNING _id",
                Long::class.java,
                "auto-${UUID.randomUUID()}",
                generatedPuzzle.serialize(),
                packId
            )
            ids.add(id ?: throw IllegalStateException("생성한 십자말풀이 ID를 반환받지 못했습니다."))
        }

        val previews = generated.zip(ids).map { (generatedPuzzle, id) ->
            CrosswordPuzzle(id, "auto", packId, generatedPuzzle.entries)
        }
        return CrosswordGenerateResult(request.count, ids.size, ids, previews, candidates.size)
    }

    private fun loadCandidates(lang: String, request: CrosswordGenerateRequest): List<CrosswordCandidate> {
        val table = if (lang == "ko") "kkutu_ko" else "kkutu_en"
        val wordPattern = if (lang == "ko") "^[가-힣]+$" else "^[A-Za-z]+$"
        val meaningText = "BTRIM(regexp_replace(COALESCE(mean, ''), '＂[0-9]+＂', '', 'g'))"
        return jdbcTemplate.query(
            """SELECT _id, hit, theme, type, flag, ($meaningText <> '') AS has_meaning FROM $table
               WHERE CHAR_LENGTH(_id) BETWEEN ? AND ?
                 AND _id ~ ?
               ORDER BY hit DESC LIMIT 50000""".trimIndent(),
            { rs, _ -> CrosswordCandidate(
                word = rs.getString("_id"),
                hit = rs.getInt("hit"),
                themes = csvSet(rs.getString("theme")),
                types = csvSet(rs.getString("type")),
                flag = rs.getInt("flag"),
                hasMeaning = rs.getBoolean("has_meaning")
            ) },
            request.minWordLength, request.maxWordLength, wordPattern
        )
    }

    private fun validatePack(request: CrosswordPackRequest, creating: Boolean) {
        if (creating) require(request.id.trim().matches(Regex("[a-z0-9][a-z0-9-]{1,47}"))) {
            "팩 ID는 영문 소문자·숫자·하이픈 2~48자로 입력해 주세요."
        }
        require(request.name.trim().length in 1..80) { "팩 이름은 1~80자로 입력해 주세요." }
        require(request.lang == "ko" || request.lang == "en") { "지원하지 않는 언어입니다." }
        require(request.weight.isFinite() && request.weight in 0.0..1_000_000.0) { "가중치는 0 이상이어야 합니다." }
    }

    private fun validateGenerate(request: CrosswordGenerateRequest) {
        require(request.count in 1..200) { "한 번에 1~200개의 퍼즐을 생성할 수 있습니다." }
        require(request.width in 5..15 && request.height in 5..15) { "보드 크기는 5~15칸이어야 합니다." }
        require(request.minWords in 2..40 && request.maxWords in request.minWords..40) { "단어 수 범위가 올바르지 않습니다." }
        require(request.minWordLength in 2..15 && request.maxWordLength in request.minWordLength..minOf(15, maxOf(request.width, request.height))) {
            "단어 길이 범위가 올바르지 않습니다."
        }
        require(request.minHit in 0..Int.MAX_VALUE) { "최소 사용 횟수는 0 이상이어야 합니다." }
        require(request.popularityBias.isFinite() && request.popularityBias in 0.0..1.0) { "인기 단어 선호도는 0~1이어야 합니다." }
        require(request.themeWeights.size <= 100 && request.excludeThemes.size <= 100) { "주제 조건은 각각 최대 100개입니다." }
        require(request.includeTypes.size <= 50 && request.excludeTypes.size <= 50) { "품사 조건은 각각 최대 50개입니다." }
        require(request.excludeWords.size <= 1000) { "금칙어는 최대 1,000개입니다." }
        val codes = request.themeWeights.map { it.theme } + request.excludeThemes + request.includeTypes + request.excludeTypes
        require(codes.all { it.matches(Regex("[A-Za-z0-9_.-]{1,40}")) }) { "주제·품사 코드 형식이 올바르지 않습니다." }
        require(request.themeWeights.map { it.theme }.distinct().size == request.themeWeights.size) { "같은 주제 가중치를 중복 지정할 수 없습니다." }
        require(request.themeWeights.all { it.weight.isFinite() && it.weight in 0.0..1_000_000.0 }) { "주제 가중치는 0 이상이어야 합니다." }
        require(request.themeWeights.isEmpty() || request.themeWeights.any { it.weight > 0 }) { "포함 주제 가중치를 최소 하나는 0보다 크게 설정해야 합니다." }
        require(request.allowedFlags.all { it > 0 && it.countOneBits() == 1 }) { "허용 플래그 값이 올바르지 않습니다." }
    }

    private fun requirePack(id: String): CrosswordPack =
        packs().firstOrNull { it.id == id } ?: throw IllegalArgumentException("존재하지 않는 팩입니다.")

    private fun puzzleTable(lang: String): String = if (lang == "ko") "kkutu_cw_ko" else "kkutu_cw_en"

    private fun csvSet(value: String?): Set<String> = value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private fun puzzle(id: Long, map: String, packId: String, raw: String): CrosswordPuzzle =
        CrosswordPuzzle(id, map, packId, raw.split('|').filter { it.isNotBlank() }.map { item ->
            val values = item.split(',', limit = 5)
            require(values.size == 5) { "손상된 십자말풀이 데이터입니다: $id" }
            CrosswordEntry(values[0].toInt(), values[1].toInt(), values[2].toInt(), values[3].toInt(), values[4])
        })
}
