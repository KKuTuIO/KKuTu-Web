/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.kkutuio.kkutuweb.word

import me.kkutuio.kkutuweb.admin.SortType
import me.kkutuio.kkutuweb.admin.mapper.SingleNumberMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement

@Component
class WordDao(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val singleNumberMapper: SingleNumberMapper,
    @Autowired private val wordMapper: WordMapper
) {
    fun getWords(tableName: String, id: String): List<Word> {
        val sql = "SELECT * FROM $tableName WHERE _id = ?"
        return jdbcTemplate.query(sql, wordMapper, id)
    }

    fun getWords(tableName: String, ids: Collection<String>): List<Word> {
        if (ids.isEmpty()) return emptyList()

        val placeholders = ids.joinToString(",") { "?" }
        val sql = "SELECT * FROM $tableName WHERE _id IN ($placeholders)"
        return jdbcTemplate.query(sql, wordMapper, *ids.toTypedArray())
    }

    fun getWordsFromChar(tableName: String, startChar: String, mission: String?): List<Word> {
        val baseSql = "SELECT * FROM $tableName WHERE _id LIKE ?"
        val startCharParam = "$startChar%"
        val missionCondition = mission?.let { " AND _id LIKE ?" } ?: ""
        val missionParam = mission?.let { "$startChar%$mission%" }
    
        // 첫 번째 조건: 단어가 startChar로 시작하고, mission 문자가 포함된 경우 (또는 mission이 없는 경우)
        val sql = "$baseSql$missionCondition"
        val words = if (missionParam != null) {
            jdbcTemplate.query(sql, wordMapper, startCharParam, missionParam)
        } else {
            jdbcTemplate.query(sql, wordMapper, startCharParam)
        }
    
        // 전체 단어 검색 결과가 5건 이하인 경우
        if (words.size <= 5) {
            return words
        }
    
        // 두 번째 조건: 10글자 이상의 단어로 제한한 후 필터링
        val longWords = words.filter { it.id.length >= 10 }
    
        // 10글자 이상의 단어 검색 결과가 5건 이하인 경우
        if (longWords.size <= 5) {
            return words
        }
    
        // 10글자 이상의 단어 검색 결과 반환
        return longWords
    }

    fun getDataCount(
        tableName: String,
        searchFilter: WordSearchFilter
    ): Int {
        val (whereQuery, whereValues) = buildWhere(tableName, searchFilter)

        val sql = countQuery(tableName, whereQuery)
        val list = jdbcTemplate.query(sql, singleNumberMapper, *whereValues.toTypedArray())
        return list[0]
    }

    fun getPageData(
        tableName: String,
        page: Int,
        pageSize: Int,
        sortField: String,
        sortType: SortType,
        searchFilter: WordSearchFilter
    ): List<Word> {
        val (whereQuery, whereValues) = buildWhere(tableName, searchFilter)

        val sql = selectQuery(tableName, whereQuery, sortField, sortType, pageSize, page)
        return jdbcTemplate.query(sql, wordMapper, *whereValues.toTypedArray())
    }

    fun getFilteredData(
        tableName: String,
        searchFilter: WordSearchFilter,
        limit: Int
    ): List<Word> {
        val (whereQuery, whereValues) = buildWhere(tableName, searchFilter)
        val sql = "SELECT * FROM $tableName $whereQuery ORDER BY _id ASC LIMIT ?"
        return jdbcTemplate.query(sql, wordMapper, *(whereValues + limit).toTypedArray())
    }

    fun getWordIds(tableName: String, searchFilter: WordSearchFilter = WordSearchFilter()): List<String> {
        val (whereQuery, whereValues) = buildWhere(tableName, searchFilter)
        val sql = "SELECT _id FROM $tableName $whereQuery"
        return jdbcTemplate.query(sql, { rs, _ -> rs.getString("_id") }, *whereValues.toTypedArray())
    }

    fun insert(tableName: String, word: Word) {
        val sql = "INSERT INTO $tableName (_id, type, mean, hit, flag, theme) VALUES (?, ?, ?, ?, ?, ?)"

        jdbcTemplate.update(
            sql,
            word.id,
            word.type,
            word.mean,
            word.hit,
            word.flag,
            word.theme
        )
    }

    fun insertAll(tableName: String, words: List<Word>) {
        if (words.isEmpty()) return

        val sql = "INSERT INTO $tableName (_id, type, mean, hit, flag, theme) VALUES (?, ?, ?, ?, ?, ?)"
        jdbcTemplate.batchUpdate(sql, words, words.size) { statement: PreparedStatement, word: Word ->
            statement.setString(1, word.id)
            statement.setString(2, word.type)
            statement.setString(3, word.mean)
            statement.setInt(4, word.hit)
            statement.setInt(5, word.flag)
            statement.setString(6, word.theme)
        }
    }

    fun update(tableName: String, wordName: String, values: Map<String, Any?>) {
        val setString = values.entries.joinToString(",") {
            "${it.key}=?"
        }

        val sql = "UPDATE $tableName SET $setString WHERE _id = ?"
        val valueString = values.map { it.value }.toMutableList()
        valueString.add(wordName)

        jdbcTemplate.update(sql, *valueString.toTypedArray())
    }

    fun updateAll(tableName: String, words: List<Word>) {
        if (words.isEmpty()) return

        val sql = "UPDATE $tableName SET type = ?, mean = ?, flag = ?, theme = ? WHERE _id = ?"
        jdbcTemplate.batchUpdate(sql, words, words.size) { statement: PreparedStatement, word: Word ->
            statement.setString(1, word.type)
            statement.setString(2, word.mean)
            statement.setInt(3, word.flag)
            statement.setString(4, word.theme)
            statement.setString(5, word.id)
        }
    }

    fun remove(tableName: String, wordName: String) {
        val sql = "DELETE FROM $tableName WHERE _id = ?;"
        jdbcTemplate.update(sql, wordName)
    }

    fun removeAll(tableName: String, wordNames: List<String>) {
        if (wordNames.isEmpty()) return

        val sql = "DELETE FROM $tableName WHERE _id = ?"
        jdbcTemplate.batchUpdate(sql, wordNames, wordNames.size) { statement: PreparedStatement, wordName: String ->
            statement.setString(1, wordName)
        }
    }

    fun isDuplicate(tableName: String, wordName: String): Boolean {
        val sql = "SELECT 1 FROM $tableName WHERE _id = ?"
        val list = jdbcTemplate.query(sql, singleNumberMapper, wordName)
        return list.isNotEmpty()
    }

    private fun buildWhere(tableName: String, filter: WordSearchFilter): Pair<String, List<Any>> {
        val clauses = ArrayList<String>()
        val values = ArrayList<Any>()

        if (filter.word.isNotBlank()) {
            val escaped = escapeLike(filter.word.trim())
            val pattern = when (filter.wordMatch) {
                WordMatch.EXACT -> escaped
                WordMatch.STARTS_WITH -> "$escaped%"
                WordMatch.ENDS_WITH -> "%$escaped"
                WordMatch.CONTAINS -> "%$escaped%"
                WordMatch.LEGACY -> if (filter.word.startsWith("%") || filter.word.endsWith("%")) filter.word else "%${filter.word}%"
            }
            clauses.add("_id ILIKE ?${if (filter.wordMatch == WordMatch.LEGACY) "" else " ESCAPE '\\'"}")
            values.add(pattern)
        }

        if (filter.themes.isNotEmpty()) {
            val themeClauses = filter.themes.distinct().map {
                values.add("%,${escapeLike(it)},%")
                "(',' || COALESCE(theme, '') || ',') ILIKE ? ESCAPE '\\'"
            }
            clauses.add(if (filter.themeMatchAll) themeClauses.joinToString(" AND ", "(", ")") else themeClauses.joinToString(" OR ", "(", ")"))
        }

        if (filter.types.isNotEmpty()) {
            val typeClauses = filter.types.distinct().map {
                values.add("%,${escapeLike(it)},%")
                "(',' || COALESCE(type, '') || ',') ILIKE ? ESCAPE '\\'"
            }
            clauses.add(typeClauses.joinToString(" OR ", "(", ")"))
        }

        if (filter.flags.isNotEmpty()) {
            val wantsNoFlag = filter.flags.contains(0)
            val mask = filter.flags.filter { it > 0 }.fold(0) { total, flag -> total.or(flag) }
            when {
                wantsNoFlag && mask == 0 -> clauses.add("flag = 0")
                wantsNoFlag -> {
                    clauses.add("(flag = 0 OR (flag & ?) ${if (filter.flagMatchAll) "= ?" else "<> 0"})")
                    values.add(mask)
                    if (filter.flagMatchAll) values.add(mask)
                }
                filter.flagMatchAll -> {
                    clauses.add("(flag & ?) = ?")
                    values.add(mask)
                    values.add(mask)
                }
                else -> {
                    clauses.add("(flag & ?) <> 0")
                    values.add(mask)
                }
            }
        }

        filter.minHit?.let { clauses.add("hit >= ?"); values.add(it) }
        filter.maxHit?.let { clauses.add("hit <= ?"); values.add(it) }
        filter.minLength?.let { clauses.add("CHAR_LENGTH(_id) >= ?"); values.add(it) }
        filter.maxLength?.let { clauses.add("CHAR_LENGTH(_id) <= ?"); values.add(it) }
        filter.hasTheme?.let { clauses.add(if (it) "COALESCE(NULLIF(theme, ''), '0') <> '0'" else "COALESCE(NULLIF(theme, ''), '0') = '0'") }
        filter.hasMeaning?.let {
            val meaningfulText = "BTRIM(regexp_replace(COALESCE(mean, ''), '＂[0-9]+＂', '', 'g'))"
            clauses.add(if (it) "$meaningfulText <> ''" else "$meaningfulText = ''")
        }
        if (filter.onlyInjeongWithMeaning) {
            clauses.add(
                """EXISTS (
                    SELECT 1
                    FROM generate_subscripts(string_to_array(COALESCE(type, ''), ','), 1) AS definition_index
                    WHERE (string_to_array(COALESCE(type, ''), ','))[definition_index] = 'INJEONG'
                      AND BTRIM(COALESCE((regexp_split_to_array(COALESCE(mean, ''), '＂[0-9]+＂'))[definition_index + 1], '')) <> ''
                      AND COALESCE((regexp_split_to_array(COALESCE(mean, ''), '＂[0-9]+＂'))[definition_index + 1], '') NOT LIKE '%［%'
                )""".trimIndent()
            )
        }

        if (filter.createdBy.isNotBlank() || filter.createdWithinDays != null) {
            val auditClauses = arrayListOf("word_audit.word = _id", "word_audit.log_type = 'CREATE'")
            if (filter.createdBy.isNotBlank()) {
                auditClauses.add("word_audit.admin ILIKE ? ESCAPE '\\'")
                values.add("%${escapeLike(filter.createdBy.trim())}%")
            }
            filter.createdWithinDays?.let {
                auditClauses.add("word_audit.log_time >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')")
                values.add(it)
            }
            clauses.add("EXISTS (SELECT 1 FROM ${tableName}_audit_log word_audit WHERE ${auditClauses.joinToString(" AND ")})")
        }

        return (if (clauses.isEmpty()) "" else "WHERE ${clauses.joinToString(" AND ")}") to values
    }

    private fun escapeLike(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun countQuery(tableName: String, whereQuery: String): String {
        return "SELECT COUNT(*) FROM $tableName $whereQuery"
    }

    private fun selectQuery(
        tableName: String,
        whereQuery: String,
        sortField: String,
        sortType: SortType,
        pageSize: Int,
        page: Int
    ): String {
        return "SELECT * FROM $tableName $whereQuery ORDER BY $sortField ${sortType.name} LIMIT $pageSize OFFSET ${page * pageSize}"
    }
}
