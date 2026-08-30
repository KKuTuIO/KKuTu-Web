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

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class AcademyRestrictedDao(private val jdbcTemplate: JdbcTemplate) {
    fun search(
        lang: String,
        startChar: String?,
        endChar: String?,
        mission: String?,
        limit: Int
    ): List<AcademyWordRecord> {
        val table = when (lang) {
            "ko" -> "kkutu_ko"
            "en" -> "kkutu_en"
            else -> throw IllegalArgumentException("지원하지 않는 언어입니다.")
        }
        val clauses = mutableListOf("(COALESCE(w.flag, 0) & $INJEONG_FLAG) <> 0")
        val values = mutableListOf<Any>()
        val publicationAvailable = runCatching {
            jdbcTemplate.queryForObject(
                "SELECT to_regclass('dictionary_public_word') IS NOT NULL",
                Boolean::class.java
            ) == true
        }.getOrDefault(false)

        if (publicationAvailable) {
            clauses += "NOT EXISTS (SELECT 1 FROM dictionary_public_word p WHERE p.lang = ? AND p.word = w._id)"
            values += lang
        }
        if (!startChar.isNullOrBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "${escapeLike(startChar)}%"
        }
        if (!endChar.isNullOrBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "%${escapeLike(endChar)}"
        }
        if (!mission.isNullOrBlank()) {
            clauses += "w._id ILIKE ? ESCAPE '\\'"
            values += "%${escapeLike(mission)}%"
        }
        values += limit.coerceIn(1, 20)

        val publicationProjection = if (publicationAvailable) {
            "EXISTS (SELECT 1 FROM dictionary_public_word p2 WHERE p2.lang = '$lang' AND p2.word = w._id)"
        } else {
            "FALSE"
        }
        return jdbcTemplate.query(
            """
            SELECT w._id, w.mean, w.type, w.hit, w.flag, w.theme,
                   $publicationProjection AS published_override
            FROM $table w
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY CHAR_LENGTH(w._id) DESC, w.hit DESC, w._id ASC
            LIMIT ?
            """.trimIndent(),
            ::mapWord,
            *values.toTypedArray()
        )
    }

    private fun mapWord(rs: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): AcademyWordRecord =
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
