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

package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.SortType
import me.kkutuio.kkutuweb.admin.api.request.UpdateLogRequest
import me.kkutuio.kkutuweb.admin.api.request.WordEditRequest
import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.admin.api.response.WordResult
import me.kkutuio.kkutuweb.admin.dao.WordAuditLogDAO
import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import me.kkutuio.kkutuweb.admin.vo.WordVO
import me.kkutuio.kkutuweb.word.Word
import me.kkutuio.kkutuweb.word.WordDao
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AdminWordService(
    @Autowired private val wordDao: WordDao,
    @Autowired private val wordAuditLogDAO: WordAuditLogDAO
) {
    private val logger = LoggerFactory.getLogger(AdminWordService::class.java)

    fun getWordListRes(
        lang: String,
        page: Int,
        pageSize: Int,
        sortData: String,
        searchFilters: Map<String, String>
    ): ListResponse<WordVO> {
        val tableName = getTableName(lang)
        if (tableName.isEmpty()) {
            return ListResponse(0, emptyList())
        }

        val split = sortData.split(",")
        val sortField = when (split[0]) {
            "word" -> "_id"
            "hit" -> "hit"
            "flag" -> "flag"
            else -> ""
        }
        val sortType = SortType.valueOf(split[1])

        val dbSearchFilters = searchFilters.filterValues { it.isNotEmpty() }

        val dataCount = wordDao.getDataCount(tableName, dbSearchFilters)
        val pageData = wordDao.getPageData(tableName, page, pageSize, sortField, sortType, dbSearchFilters).map {
            WordVO.convertFrom(it)
        }

        return ListResponse(dataCount, pageData)
    }

    fun getWords(lang: String, wordName: String): ListResponse<WordVO> {
        val tableName = getTableName(lang)
        if (tableName.isEmpty()) {
            return ListResponse(0, emptyList())
        }

        val words = wordDao.getWords(tableName, wordName).map {
            WordVO.convertFrom(it)
        }
        return ListResponse(words.size, words)
    }

    fun editWord(
        adminId: String,
        lang: String,
        wordName: String,
        wordEditRequest: WordEditRequest
    ): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty()) {
            return ActionResponse.rest(success = false, restResult = RestResult.INTERNAL_ERROR)
        }

        val words = wordDao.getWords(tableName, wordName)
        if (words.size != 1) {
            logger.error("수정하려는 단어 데이터가 1개가 아닙니다. 언어: $lang 단어: $wordName")
            return ActionResponse.word(success = false, wordResult = WordResult.NON_UNIQUE)
        }

        val oldWord = words[0]
        val newWord = Word.convertFrom(
            WordVO(
                word = wordName,
                hit = 0,
                flags = wordEditRequest.flags,
                details = wordEditRequest.details
            )
        )

        wordDao.update(
            tableName, wordName, mapOf(
                "type" to newWord.type,
                "mean" to newWord.mean,
                "flag" to newWord.flag,
                "theme" to newWord.theme
            )
        )
        wordAuditLogDAO.insert(
            lang, WordAuditLog(
                time = LocalDateTime.now(),
                word = wordName,
                type = WordAuditLog.WordAuditLogType.UPDATE,
                oldType = oldWord.type,
                oldMean = oldWord.mean,
                oldFlag = oldWord.flag,
                oldTheme = oldWord.theme,
                newType = newWord.type,
                newMean = newWord.mean,
                newFlag = newWord.flag,
                newTheme = newWord.theme,
                updateLogIgnore = wordEditRequest.updateLogIgnore,
                updateLogIncludeDetail = wordEditRequest.updateLogIncludeDetail,
                admin = adminId
            )
        )

        return ActionResponse.success()
    }

    fun deleteWord(
        adminId: String,
        lang: String,
        wordName: String,
        updateLogRequest: UpdateLogRequest
    ): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty()) {
            return ActionResponse.rest(success = false, restResult = RestResult.INTERNAL_ERROR)
        }

        val words = wordDao.getWords(tableName, wordName)
        if (words.size != 1) {
            logger.error("삭제하려는 단어 데이터가 1개가 아닙니다. 언어: $lang 단어: $wordName")
            return ActionResponse.word(success = false, wordResult = WordResult.NON_UNIQUE)
        }

        val oldWord = words[0]
        wordDao.remove(tableName, wordName)
        wordAuditLogDAO.insert(
            lang, WordAuditLog(
                time = LocalDateTime.now(),
                word = wordName,
                type = WordAuditLog.WordAuditLogType.DELETE,
                oldType = oldWord.type,
                oldMean = oldWord.mean,
                oldFlag = oldWord.flag,
                oldTheme = oldWord.theme,
                updateLogIgnore = updateLogRequest.updateLogIgnore,
                updateLogIncludeDetail = updateLogRequest.updateLogIncludeDetail,
                admin = adminId
            )
        )

        return ActionResponse.success()
    }

    fun addWord(
        adminId: String,
        lang: String,
        wordName: String,
        wordEditRequest: WordEditRequest
    ): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty()) {
            return ActionResponse.rest(success = false, restResult = RestResult.INTERNAL_ERROR)
        }

        val isDuplicate = wordDao.isDuplicate(tableName, wordName)
        if (isDuplicate) {
            logger.warn("중복된 단어를 추가하려 했습니다. 언어: $lang 단어: $wordName")
            wordAuditLogDAO.insert(
                lang, WordAuditLog(
                    time = LocalDateTime.now(),
                    word = wordName,
                    type = WordAuditLog.WordAuditLogType.ERROR_DUPLICATE,
                    updateLogIgnore = wordEditRequest.updateLogIgnore,
                    updateLogIncludeDetail = wordEditRequest.updateLogIncludeDetail,
                    admin = adminId
                )
            )
            return ActionResponse.word(success = false, wordResult = WordResult.DUPLICATED)
        }

        val newWord = Word.convertFrom(
            WordVO(
                word = wordName,
                hit = 0,
                flags = wordEditRequest.flags,
                details = wordEditRequest.details
            )
        )

        wordDao.insert(tableName, newWord)
        wordAuditLogDAO.insert(
            lang, WordAuditLog(
                time = LocalDateTime.now(),
                word = wordName,
                type = WordAuditLog.WordAuditLogType.CREATE,
                newType = newWord.type,
                newMean = newWord.mean,
                newFlag = newWord.flag,
                newTheme = newWord.theme,
                updateLogIgnore = wordEditRequest.updateLogIgnore,
                updateLogIncludeDetail = wordEditRequest.updateLogIncludeDetail,
                admin = adminId
            )
        )

        return ActionResponse.success()
    }

    private fun getTableName(lang: String): String {
        return when (lang) {
            "ko" -> "kkutu_ko"
            "en" -> "kkutu_en"
            else -> ""
        }
    }
}