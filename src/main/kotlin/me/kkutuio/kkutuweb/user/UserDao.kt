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

package me.kkutuio.kkutuweb.user

import org.postgresql.util.PGobject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.SingleColumnRowMapper
import org.springframework.stereotype.Component

@Component
class UserDao(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val userMapper: UserMapper
) {
    fun getUser(id: String): User? {
        val sql = "SELECT * FROM users WHERE _id = ?"

        val users = jdbcTemplate.query(sql, userMapper, id)
        return if (users.isEmpty()) null else users.first()
    }

    fun getUserFromNick(similarityNick: String, originalNick: String): User? {
        val sql = "SELECT * FROM users WHERE \"meanableNick\" = ? OR nickname = ?"

        val users = jdbcTemplate.query(sql, userMapper, similarityNick, originalNick)
        return if (users.isEmpty()) null else users.first()
    }

    fun searchUsers(query: String, limit: Int = 20): List<User> {
        val sql = """
            SELECT * FROM users
            WHERE _id = ? OR nickname ILIKE ?
            ORDER BY CASE WHEN _id = ? THEN 0 WHEN nickname = ? THEN 1 ELSE 2 END, nickname
            LIMIT ?
        """.trimIndent()
        return jdbcTemplate.query(sql, userMapper, query, "%$query%", query, query, limit.coerceIn(1, 50))
    }

    fun getSimilarityNicks(): List<String> {
        val sql = "SELECT \"meanableNick\" FROM users"
        return jdbcTemplate.query(sql, SingleColumnRowMapper())
    }

    fun getNicks(): List<String> {
        val sql = "SELECT \"nickname\" FROM users"
        return jdbcTemplate.query(sql, SingleColumnRowMapper())
    }

    fun getExistsSimilarityNick(similarityNick: String): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM users WHERE \"meanableNick\" = ?)"
        return jdbcTemplate.queryForObject(sql, Boolean::class.java, similarityNick) ?: false
    }

    fun getExistsNickname(nickname: String): Boolean {
        val sql = "SELECT EXISTS(SELECT 1 FROM users WHERE nickname = ?)"
        return jdbcTemplate.queryForObject(sql, Boolean::class.java, nickname) ?: false
    }

    // meanableNick only checked on Unique Nicknames
    fun newUser(id: String, nick: String, similarityNick: String) {
        val sql = "INSERT INTO users(_id, nickname, money, kkutu, flags, \"meanableNick\") VALUES(?, ?, ?, ?, ?, ?)"

        val kkutuJsonObj = PGobject()
        kkutuJsonObj.type = "json"
        kkutuJsonObj.value =
            "{\"score\":0,\"playTime\":0,\"connectDate\":0,\"record\":{\"EKT\":[0,0,0,0,0],\"ESH\":[0,0,0,0,0],\"KKT\":[0,0,0,0,0],\"KSH\":[0,0,0,0,0],\"CSQ\":[0,0,0,0,0],\"KCW\":[0,0,0,0,0],\"KTY\":[0,0,0,0,0],\"ETY\":[0,0,0,0,0],\"KAP\":[0,0,0,0,0],\"HUN\":[0,0,0,0,0],\"KDA\":[0,0,0,0,0],\"EDA\":[0,0,0,0,0],\"KSS\":[0,0,0,0,0],\"ESS\":[0,0,0,0,0]}}"

        val flagsObj = PGobject()
        flagsObj.type = "json"
        flagsObj.value = "{\"first\":1}"

        jdbcTemplate.update(sql, id, nick, 0, kkutuJsonObj, flagsObj, similarityNick)
    }

    fun updateUser(id: String, values: Map<String, Any?>) {
        val setString = values.entries.joinToString(",") {
            "${it.key}=?"
        }

        val sql = "UPDATE users SET $setString WHERE _id = ?"
        val valueString = values.map { it.value }.toMutableList()
        valueString.add(id)

        jdbcTemplate.update(sql, *valueString.toTypedArray())
    }
}
