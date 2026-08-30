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

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

data class AcademySearchQuery(
    val text: String = "",
    val match: String = "CONTAINS",
    val startChar: String = "",
    val endChar: String = "",
    val mission: String = "",
    val sort: String = "HIT_DESC",
    val page: Int = 0,
    val size: Int = 30
)

@Repository
class AcademyDao(private val jdbcTemplate: JdbcTemplate) {
    private val logger = LoggerFactory.getLogger(AcademyDao::class.java)

    @Volatile
    private var publicationSchemaAvailable: Boolean? = null

    fun refreshSchemaState() {
        publicationSchemaAvailable = null
        ensurePublicationSchema()
    }

    @Synchronized
    fun ensurePublicationSchema(): Boolean {
        publicationSchemaAvailable?.let { return it }
        publicationSchemaAvailable = try {
            jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS dictionary_public_word (
                    lang VARCHAR(2) NOT NULL CHECK (lang IN ('ko', 'en')),
                    word TEXT NOT NULL,
                    reason VARCHAR(200) NOT NULL DEFAULT '관리자 공개',
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    PRIMARY KEY (lang, word)
                )
                """.trimIndent()
            )
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_dictionary_public_word_created_at " +
                    "ON dictionary_public_word (created_at DESC)"
            )
            true
        } catch (error: Exception) {
            logger.error(
                "단어 아카데미 공개 단어 테이블을 준비하지 못했습니다. 비어인정 공개 기능만 사용합니다.",
                error
            )
            false
        }
        return publicationSchemaAvailable == true
    }

    fun loadPublicCorpus(lang: String): List<AcademyCorpusWord> {
        val table = tableName(lang)
        return if (ensurePublicationSchema()) {
            jdbcTemplate.query(
                """
                SELECT w._id, w.hit, w.flag, w.theme, (p.word IS NOT NULL) AS published_override
                FROM $table w
                LEFT JOIN dictionary_public_word p ON p.lang = ? AND p.word = w._id
                WHERE (COALESCE(w.flag, 0) & $INJEONG_FLAG) = 0 OR p.word IS NOT NULL
                """.trimIndent(),
                ::mapCorpusWord,
                lang
            )
        } else {
            jdbcTemplate.query(
                """
                SELECT w._id, w.hit, w.flag, w.theme, FALSE AS published_override
                FROM $table w
                WHERE (COALESCE(w.flag, 0) & $INJEONG_FLAG) = 0
                """.trimIndent(),
                ::mapCorpusWord
            )
        }
    }

    fun search(config: AcademyRuleConfig, query: AcademySearchQuery): List<AcademyWordRecord> {
        val table = tableName(config.lang)
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        clauses += visibilityClause("w", config.lang, config.dictionary)
        appendRuleClauses("w", config, clauses, values)

        val text = query.text.trim()
        if (text.isNotEmpty()) {
            val escaped = escapeLike(text)
            when (query.match.uppercase()) {
                "EXACT" -> {
                    clauses += "w._id = ?"
                    values += text
                }
                "STARTS_WITH" -> {
                    clauses += "w._id ILIKE ? ESCAPE '\\'"
                    values += "$escaped%"
                }
                "ENDS_WITH" -> {
                    clauses += "w._id ILIKE ? ESCAPE '\\'"
                    values += "%$escaped"
                }
                else -> {
                    clauses += "w._id ILIKE ? ESCAPE '\\'"
                    values += "%$escaped%"
                }
            }
        }
        if (query.startChar.isNotBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "${escapeLike(query.startChar.trim())}%"
        }
        if (query.endChar.isNotBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "%${escapeLike(query.endChar.trim())}"
        }
        if (query.mission.isNotBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "%${escapeLike(query.mission.trim())}%"
        }

        val order = when (query.sort.uppercase()) {
            "WORD_DESC" -> "w._id DESC"
            "LENGTH_ASC" -> "CHAR_LENGTH(w._id) ASC, w._id ASC"
            "LENGTH_DESC" -> "CHAR_LENGTH(w._id) DESC, w._id ASC"
            "HIT_ASC" -> "w.hit ASC, w._id ASC"
            "WORD_ASC" -> "w._id ASC"
            else -> "w.hit DESC, w._id ASC"
        }
        val publicationJoin = publicationJoin(config.lang)
        val where = clauses.joinToString(" AND ")
        val safeSize = query.size.coerceIn(1, 100)
        val safePage = query.page.coerceIn(0, 10_000)
        val sql = """
            SELECT w._id, w.mean, w.type, w.hit, w.flag, w.theme,
                   ${publicationProjection()} AS published_override
            FROM $table w
            $publicationJoin
            WHERE $where
            ORDER BY $order
            LIMIT ? OFFSET ?
        """.trimIndent()
        values += safeSize + 1
        values += safePage * safeSize
        return jdbcTemplate.query(sql, ::mapWordRecord, *values.toTypedArray())
    }

    fun getVisibleWord(config: AcademyRuleConfig, word: String): AcademyWordRecord? {
        val table = tableName(config.lang)
        val clauses = mutableListOf(visibilityClause("w", config.lang, config.dictionary), "w._id = ?")
        val values = mutableListOf<Any>(word)
        appendRuleClauses("w", config, clauses, values)
        val rows = jdbcTemplate.query(
            """
            SELECT w._id, w.mean, w.type, w.hit, w.flag, w.theme,
                   ${publicationProjection()} AS published_override
            FROM $table w
            ${publicationJoin(config.lang)}
            WHERE ${clauses.joinToString(" AND ")}
            LIMIT 1
            """.trimIndent(),
            ::mapWordRecord,
            *values.toTypedArray()
        )
        return rows.firstOrNull()
    }

    fun restrictedSearch(lang: String, startChar: String, mission: String?, limit: Int): List<AcademyWordRecord> {
        val table = tableName(lang)
        val clauses = mutableListOf<String>()
        val values = mutableListOf<Any>()
        clauses += "(COALESCE(w.flag, 0) & $INJEONG_FLAG) <> 0"
        if (ensurePublicationSchema()) clauses += "p.word IS NULL"
        clauses += "w._id ILIKE ? ESCAPE '\\'"
        values += "${escapeLike(startChar)}%"
        if (!mission.isNullOrBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "%${escapeLike(mission)}%"
        }
        values += limit.coerceIn(1, 20)
        return jdbcTemplate.query(
            """
            SELECT w._id, w.mean, w.type, w.hit, w.flag, w.theme,
                   ${publicationProjection()} AS published_override
            FROM $table w
            ${publicationJoin(lang)}
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY CHAR_LENGTH(w._id) DESC, w.hit DESC, w._id ASC
            LIMIT ?
            """.trimIndent(),
            ::mapWordRecord,
            *values.toTypedArray()
        )
    }

    fun publish(lang: String, word: String, reason: String, adminId: String) {
        require(ensurePublicationSchema()) { "공개 단어 테이블을 사용할 수 없습니다." }
        val table = tableName(lang)
        val flag = jdbcTemplate.query(
            "SELECT flag FROM $table WHERE _id = ?",
            { rs, _ -> rs.getInt("flag") },
            word
        ).firstOrNull() ?: throw IllegalArgumentException("등록되지 않은 단어입니다.")
        require(flag and INJEONG_FLAG != 0) { "비어인정 단어는 이미 공개 코퍼스에 포함됩니다." }
        jdbcTemplate.update(
            """
            INSERT INTO dictionary_public_word(lang, word, reason, created_by)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(lang, word) DO UPDATE SET
                reason = EXCLUDED.reason,
                created_by = EXCLUDED.created_by,
                created_at = NOW()
            """.trimIndent(),
            lang,
            word,
            reason.take(200),
            adminId
        )
    }

    fun unpublish(lang: String, word: String): Boolean {
        if (!ensurePublicationSchema()) return false
        return jdbcTemplate.update(
            "DELETE FROM dictionary_public_word WHERE lang = ? AND word = ?",
            lang,
            word
        ) > 0
    }

    fun listPublished(lang: String, page: Int, size: Int): List<AcademyPublishedWord> {
        if (!ensurePublicationSchema()) return emptyList()
        val safeSize = size.coerceIn(1, 200)
        return jdbcTemplate.query(
            """
            SELECT lang, word, reason, created_by, created_at
            FROM dictionary_public_word
            WHERE lang = ?
            ORDER BY created_at DESC, word ASC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ ->
                AcademyPublishedWord(
                    lang = rs.getString("lang"),
                    word = rs.getString("word"),
                    reason = rs.getString("reason"),
                    createdBy = rs.getString("created_by"),
                    createdAt = rs.getTimestamp("created_at").toInstant().toString()
                )
            },
            lang,
            safeSize + 1,
            page.coerceAtLeast(0) * safeSize
        )
    }

    private fun appendRuleClauses(
        alias: String,
        config: AcademyRuleConfig,
        clauses: MutableList<String>,
        values: MutableList<Any>
    ) {
        clauses += "CHAR_LENGTH($alias._id) BETWEEN ? AND ?"
        values += config.minLength
        values += config.maxLength
        if (!config.includeLoanword) clauses += "(COALESCE($alias.flag, 0) & 1) = 0"
        if (!config.includeSpaced) clauses += "(COALESCE($alias.flag, 0) & 4) = 0"
        if (!config.includeDialect) clauses += "(COALESCE($alias.flag, 0) & 8) = 0"
        if (!config.includeOld) clauses += "(COALESCE($alias.flag, 0) & 16) = 0"
        if (!config.includeCultural) clauses += "(COALESCE($alias.flag, 0) & 32) = 0"
        if (!config.includeKung) clauses += "(COALESCE($alias.flag, 0) & 64) = 0"

        if (config.themes.isNotEmpty()) {
            val themes = config.themes.distinct().take(30)
            clauses += "string_to_array(COALESCE($alias.theme, ''), ',') && ARRAY[${themes.joinToString(",") { "?" }}]::text[]"
            values.addAll(themes)
        }
        if (config.excludedThemes.isNotEmpty()) {
            val themes = config.excludedThemes.distinct().take(30)
            clauses += "NOT (string_to_array(COALESCE($alias.theme, ''), ',') && ARRAY[${themes.joinToString(",") { "?" }}]::text[])"
            values.addAll(themes)
        }
        if (config.excludedWords.isNotEmpty()) {
            val words = config.excludedWords.distinct().take(1_000)
            clauses += "$alias._id NOT IN (${words.joinToString(",") { "?" }})"
            values.addAll(words)
        }
    }

    private fun visibilityClause(alias: String, lang: String, dictionary: String): String {
        val basic = "COALESCE($alias.flag, 0) = 0"
        val standard = "(COALESCE($alias.flag, 0) & $INJEONG_FLAG) = 0"
        val override = if (ensurePublicationSchema()) {
            "EXISTS (SELECT 1 FROM dictionary_public_word visible_word " +
                "WHERE visible_word.lang = '$lang' AND visible_word.word = $alias._id)"
        } else "FALSE"
        return when (dictionary.uppercase()) {
            "BASIC" -> basic
            "STANDARD" -> standard
            else -> "($standard OR $override)"
        }
    }

    private fun publicationJoin(lang: String): String = if (ensurePublicationSchema()) {
        "LEFT JOIN dictionary_public_word p ON p.lang = '$lang' AND p.word = w._id"
    } else ""

    private fun publicationProjection(): String = if (ensurePublicationSchema()) "(p.word IS NOT NULL)" else "FALSE"

    private fun tableName(lang: String): String = when (lang.lowercase()) {
        "ko" -> "kkutu_ko"
        "en" -> "kkutu_en"
        else -> throw IllegalArgumentException("지원하지 않는 언어입니다.")
    }

    private fun mapCorpusWord(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): AcademyCorpusWord =
        AcademyCorpusWord(
            word = rs.getString("_id"),
            hit = rs.getInt("hit"),
            flags = rs.getInt("flag"),
            theme = rs.getString("theme") ?: "",
            publishedOverride = rs.getBoolean("published_override")
        )

    private fun mapWordRecord(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): AcademyWordRecord =
        AcademyWordRecord(
            word = rs.getString("_id"),
            mean = rs.getString("mean") ?: "",
            type = rs.getString("type") ?: "",
            hit = rs.getInt("hit"),
            flags = rs.getInt("flag"),
            theme = rs.getString("theme") ?: "",
            publishedOverride = rs.getBoolean("published_override")
        )

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}