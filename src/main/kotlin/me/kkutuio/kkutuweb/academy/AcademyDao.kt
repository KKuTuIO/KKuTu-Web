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

}
