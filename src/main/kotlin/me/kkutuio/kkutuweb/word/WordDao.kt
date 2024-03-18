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

    fun getDataCount(
        tableName: String,
        searchFilters: Map<String, String>
    ): Int {
        val whereQuery = whereQuery(searchFilters)
        val whereValues = whereValues(searchFilters)

        val sql = countQuery(tableName, whereQuery)
        val list = jdbcTemplate.query(sql, singleNumberMapper, *whereValues)
        return list[0]
    }

    fun getPageData(
        tableName: String,
        page: Int,
        pageSize: Int,
        sortField: String,
        sortType: SortType,
        searchFilters: Map<String, String>
    ): List<Word> {
        val whereQuery = whereQuery(searchFilters)
        val whereValues = whereValues(searchFilters)

        val sql = selectQuery(tableName, whereQuery, sortField, sortType, pageSize, page)
        return jdbcTemplate.query(sql, wordMapper, *whereValues)
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

    fun update(tableName: String, wordName: String, values: Map<String, Any?>) {
        val setString = values.entries.joinToString(",") {
            "${it.key}=?"
        }

        val sql = "UPDATE $tableName SET $setString WHERE _id = ?"
        val valueString = values.map { it.value }.toMutableList()
        valueString.add(wordName)

        jdbcTemplate.update(sql, *valueString.toTypedArray())
    }

    fun remove(tableName: String, wordName: String) {
        val sql = "DELETE FROM $tableName WHERE _id = ?;"
        jdbcTemplate.update(sql, wordName)
    }

    fun isDuplicate(tableName: String, wordName: String): Boolean {
        val sql = "SELECT 1 FROM $tableName WHERE _id = ?"
        val list = jdbcTemplate.query(sql, singleNumberMapper, wordName)
        return list.isNotEmpty()
    }

    private fun whereQuery(searchFilters: Map<String, String>): String {
        val whereQueryParts = ArrayList<String>()
        for (key in searchFilters.keys) {
            whereQueryParts.add("CAST($key AS TEXT) ILIKE ?")
        }

        return if (whereQueryParts.isEmpty()) "" else "WHERE " + whereQueryParts.joinToString(" AND ")
    }

    private fun whereValues(searchFilters: Map<String, String>): Array<String> {
        return searchFilters.values.toTypedArray()
    }

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