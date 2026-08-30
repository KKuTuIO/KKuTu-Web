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

package me.kkutuio.kkutuweb.dict

import me.kkutuio.kkutuweb.word.WordDao
import org.springframework.stereotype.Service

/**
 * Compatibility service for the established dictionary APIs.
 *
 * Word Academy applies its own public-corpus policy through AcademyService;
 * these legacy endpoints continue to query the complete game dictionary and
 * keep their historical JSON response shape.
 */
@Service
class DictService(
    private val wordDao: WordDao
) {
    fun getWord(id: String, lang: String): String {
        val tableName = tableName(lang) ?: return "{\"error\":400}"
        val word = wordDao.getWords(tableName, id).firstOrNull()
            ?: return "{\"error\":404}"

        return "{\"word\":\"${escapeJson(word.id)}\",\"mean\":\"${escapeJson(word.mean)}\",\"theme\":\"${escapeJson(word.theme)}\",\"type\":\"${escapeJson(word.type)}\"}"
    }

    fun getWords(startChar: String, lang: String, mission: String?): String {
        val tableName = tableName(lang) ?: return "{\"error\":400}"
        if (startChar.length != 1) return "{\"error\":400}"
        if (mission != null && mission.length > 1) return "{\"error\":400}"

        val words = wordDao.getWordsFromChar(tableName, startChar, mission)
        if (words.isEmpty()) return "{\"error\":404}"

        return words.joinToString(separator = ",", prefix = "[", postfix = "]") { word ->
            "{\"word\":\"${escapeJson(word.id)}\",\"mean\":\"${escapeJson(word.mean)}\",\"theme\":\"${escapeJson(word.theme)}\",\"type\":\"${escapeJson(word.type)}\"}"
        }
    }

    private fun tableName(lang: String): String? = when (lang) {
        "ko" -> "kkutu_ko"
        "en" -> "kkutu_en"
        else -> null
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) append("\\u%04x".format(char.code))
                    else append(char)
                }
            }
        }
    }
}
