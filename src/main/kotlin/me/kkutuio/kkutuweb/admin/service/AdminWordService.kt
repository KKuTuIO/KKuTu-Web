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
import me.kkutuio.kkutuweb.admin.api.request.BulkWordAddRequest
import me.kkutuio.kkutuweb.admin.api.request.BulkWordDeleteRequest
import me.kkutuio.kkutuweb.admin.api.request.BulkWordModifyRequest
import me.kkutuio.kkutuweb.admin.api.request.WordEditRequest
import me.kkutuio.kkutuweb.admin.api.response.ActionResponse
import me.kkutuio.kkutuweb.admin.api.response.BulkWordAddPreview
import me.kkutuio.kkutuweb.admin.api.response.BulkWordDeleteItem
import me.kkutuio.kkutuweb.admin.api.response.BulkWordDeletePreview
import me.kkutuio.kkutuweb.admin.api.response.BulkWordFailure
import me.kkutuio.kkutuweb.admin.api.response.BulkWordResult
import me.kkutuio.kkutuweb.admin.api.response.BulkWordModifyItem
import me.kkutuio.kkutuweb.admin.api.response.BulkWordModifyPreview
import me.kkutuio.kkutuweb.admin.api.response.BulkWordThemeGroup
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.admin.api.response.RestResult
import me.kkutuio.kkutuweb.admin.api.response.WordResult
import me.kkutuio.kkutuweb.admin.dao.WordAuditLogDAO
import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import me.kkutuio.kkutuweb.admin.vo.WordVO
import me.kkutuio.kkutuweb.word.Word
import me.kkutuio.kkutuweb.word.WordDao
import me.kkutuio.kkutuweb.word.WordTheme
import me.kkutuio.kkutuweb.word.WordFlag
import me.kkutuio.kkutuweb.word.WordSearchFilter
import me.kkutuio.kkutuweb.word.WordType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AdminWordService(
    @Autowired private val wordDao: WordDao,
    @Autowired private val wordAuditLogDAO: WordAuditLogDAO
) {
    private val logger = LoggerFactory.getLogger(AdminWordService::class.java)

    companion object {
        private const val MAX_BULK_WORDS = 5000
    }

    fun getWordListRes(
        lang: String,
        page: Int,
        pageSize: Int,
        sortData: String,
        searchFilter: WordSearchFilter
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

        val dataCount = wordDao.getDataCount(tableName, searchFilter)
        val pageData = wordDao.getPageData(tableName, page, pageSize, sortField, sortType, searchFilter).map {
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

    fun previewBulkAdd(lang: String, request: BulkWordAddRequest): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty() || !isValidBulkRequest(request.words) || request.details.isEmpty()) {
            return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)
        }

        val (words, duplicateInputWords) = normalizeWords(request.words)
        val groupedExistingWords = wordDao.getWords(tableName, words).groupBy { it.id }
        val additions = ArrayList<String>()
        val meaningAdditions = ArrayList<String>()
        val duplicates = ArrayList<String>()
        val failures = ArrayList<BulkWordFailure>()

        words.forEach { wordName ->
            if (!isValidWord(lang, wordName)) {
                failures.add(invalidWordFailure(wordName))
                return@forEach
            }

            val existingWords = groupedExistingWords[wordName].orEmpty()
            when {
                existingWords.size > 1 -> failures.add(nonUniqueFailure(wordName))
                existingWords.isEmpty() -> additions.add(wordName)
                BulkWordDefinition.missingDetails(existingWords[0], request.details).isEmpty() -> duplicates.add(wordName)
                else -> meaningAdditions.add(wordName)
            }
        }

        return ActionResponse.success(
            BulkWordAddPreview(
                totalCount = words.size,
                additions = additions,
                meaningAdditions = meaningAdditions,
                duplicates = duplicates,
                duplicateInputWords = duplicateInputWords,
                failures = failures,
                details = request.details
            )
        )
    }

    @Transactional
    fun bulkAdd(adminId: String, lang: String, request: BulkWordAddRequest): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty() || !isValidBulkRequest(request.words) || request.details.isEmpty()) {
            return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)
        }

        val (words, _) = normalizeWords(request.words)
        val meaningAdditionWords = request.meaningAdditionWords.toSet()
        val groupedExistingWords = wordDao.getWords(tableName, words).groupBy { it.id }
        val newWords = ArrayList<Word>()
        val updatedWords = ArrayList<Pair<Word, Word>>()
        val failures = ArrayList<BulkWordFailure>()
        var skippedCount = 0

        words.forEach { wordName ->
            if (!isValidWord(lang, wordName)) {
                failures.add(invalidWordFailure(wordName))
                return@forEach
            }

            val existingWords = groupedExistingWords[wordName].orEmpty()
            if (existingWords.size > 1) {
                failures.add(nonUniqueFailure(wordName))
                return@forEach
            }

            if (existingWords.isEmpty()) {
                newWords.add(newWord(wordName, request))
                return@forEach
            }

            val oldWord = existingWords[0]
            val missingDetails = BulkWordDefinition.missingDetails(oldWord, request.details)
            if (wordName !in meaningAdditionWords || missingDetails.isEmpty()) {
                skippedCount++
                return@forEach
            }

            updatedWords.add(oldWord to BulkWordDefinition.appendDetails(oldWord, request.flags, missingDetails))
        }

        wordDao.insertAll(tableName, newWords)
        wordDao.updateAll(tableName, updatedWords.map { it.second })

        val now = LocalDateTime.now()
        val auditLogs = newWords.map { newWord ->
            WordAuditLog(
                time = now,
                word = newWord.id,
                type = WordAuditLog.WordAuditLogType.CREATE,
                newType = newWord.type,
                newMean = newWord.mean,
                newFlag = newWord.flag,
                newTheme = newWord.theme,
                updateLogIgnore = request.updateLogIgnore,
                updateLogIncludeDetail = request.updateLogIncludeDetail,
                admin = adminId
            )
        } + updatedWords.map { (oldWord, newWord) ->
            WordAuditLog(
                time = now,
                word = newWord.id,
                type = WordAuditLog.WordAuditLogType.UPDATE,
                oldType = oldWord.type,
                oldMean = oldWord.mean,
                oldFlag = oldWord.flag,
                oldTheme = oldWord.theme,
                newType = newWord.type,
                newMean = newWord.mean,
                newFlag = newWord.flag,
                newTheme = newWord.theme,
                updateLogIgnore = request.updateLogIgnore,
                updateLogIncludeDetail = request.updateLogIncludeDetail,
                admin = adminId
            )
        }
        wordAuditLogDAO.insertAll(lang, auditLogs)

        val successCount = newWords.size + updatedWords.size
        return ActionResponse.success(
            BulkWordResult(
                requestedCount = words.size,
                successCount = successCount,
                createdCount = newWords.size,
                meaningAddedCount = updatedWords.size,
                skippedCount = skippedCount,
                failures = failures
            )
        )
    }

    fun previewBulkDelete(lang: String, request: BulkWordDeleteRequest): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty() || !isValidBulkRequest(request.words)) {
            return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)
        }

        val (words, duplicateInputWords) = normalizeWords(request.words)
        val groupedExistingWords = wordDao.getWords(tableName, words).groupBy { it.id }
        val multiThemeWords = ArrayList<BulkWordDeleteItem>()
        val themeGroups = LinkedHashMap<String, MutableList<String>>()
        val noThemeWords = ArrayList<String>()
        val failures = ArrayList<BulkWordFailure>()

        words.forEach { wordName ->
            if (!isValidWord(lang, wordName)) {
                failures.add(invalidWordFailure(wordName))
                return@forEach
            }

            val existingWords = groupedExistingWords[wordName].orEmpty()
            if (existingWords.isEmpty()) {
                failures.add(BulkWordFailure(wordName, "NOT_FOUND", "존재하지 않는 단어입니다."))
                return@forEach
            }
            if (existingWords.size > 1) {
                failures.add(nonUniqueFailure(wordName))
                return@forEach
            }

            val themeCodes = existingWords[0].theme.split(",")
                .filter { it.isNotBlank() && it != "0" }
                .distinct()
            when {
                themeCodes.size >= 2 -> multiThemeWords.add(
                    BulkWordDeleteItem(wordName, themeCodes.map(::themeName))
                )
                themeCodes.size == 1 -> themeGroups.getOrPut(themeCodes[0]) { ArrayList() }.add(wordName)
                else -> noThemeWords.add(wordName)
            }
        }

        val groups = themeGroups.map { (themeCode, groupWords) ->
            BulkWordThemeGroup(themeCode, themeName(themeCode), groupWords)
        }
        val totalCount = multiThemeWords.size + groups.sumOf { it.words.size } + noThemeWords.size
        return ActionResponse.success(
            BulkWordDeletePreview(
                totalCount = totalCount,
                multiThemeWords = multiThemeWords,
                themeGroups = groups,
                noThemeWords = noThemeWords,
                duplicateInputWords = duplicateInputWords,
                failures = failures
            )
        )
    }

    @Transactional
    fun bulkDelete(adminId: String, lang: String, request: BulkWordDeleteRequest): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty() || !isValidBulkRequest(request.words)) {
            return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)
        }

        val (words, _) = normalizeWords(request.words)
        val groupedExistingWords = wordDao.getWords(tableName, words).groupBy { it.id }
        val deletingWords = ArrayList<Word>()
        val failures = ArrayList<BulkWordFailure>()

        words.forEach { wordName ->
            if (!isValidWord(lang, wordName)) {
                failures.add(invalidWordFailure(wordName))
                return@forEach
            }

            val existingWords = groupedExistingWords[wordName].orEmpty()
            when {
                existingWords.isEmpty() -> failures.add(BulkWordFailure(wordName, "NOT_FOUND", "존재하지 않는 단어입니다."))
                existingWords.size > 1 -> failures.add(nonUniqueFailure(wordName))
                else -> deletingWords.add(existingWords[0])
            }
        }

        wordDao.removeAll(tableName, deletingWords.map { it.id })
        val now = LocalDateTime.now()
        wordAuditLogDAO.insertAll(lang, deletingWords.map { oldWord ->
            WordAuditLog(
                time = now,
                word = oldWord.id,
                type = WordAuditLog.WordAuditLogType.DELETE,
                oldType = oldWord.type,
                oldMean = oldWord.mean,
                oldFlag = oldWord.flag,
                oldTheme = oldWord.theme,
                updateLogIgnore = request.updateLogIgnore,
                updateLogIncludeDetail = request.updateLogIncludeDetail,
                admin = adminId
            )
        })

        return ActionResponse.success(
            BulkWordResult(
                requestedCount = words.size,
                successCount = deletingWords.size,
                deletedCount = deletingWords.size,
                failures = failures
            )
        )
    }

    fun previewBulkModify(lang: String, request: BulkWordModifyRequest): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty() || !isValidBulkRequest(request.words) || !isValidModifyRequest(request)) {
            return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)
        }

        val (words, duplicateInputWords) = normalizeWords(request.words)
        val groupedExistingWords = wordDao.getWords(tableName, words).groupBy { it.id }
        val changedItems = ArrayList<BulkWordModifyItem>()
        val unchangedWords = ArrayList<String>()
        val failures = ArrayList<BulkWordFailure>()

        words.forEach { wordName ->
            val existingWords = groupedExistingWords[wordName].orEmpty()
            when {
                !isValidWord(lang, wordName) -> failures.add(invalidWordFailure(wordName))
                existingWords.isEmpty() -> failures.add(BulkWordFailure(wordName, "NOT_FOUND", "존재하지 않는 단어입니다."))
                existingWords.size > 1 -> failures.add(nonUniqueFailure(wordName))
                else -> {
                    val oldWord = existingWords[0]
                    val newWord = BulkWordMutation.apply(oldWord, request)
                    if (oldWord == newWord) unchangedWords.add(wordName)
                    else changedItems.add(modifyPreviewItem(oldWord, newWord))
                }
            }
        }

        return ActionResponse.success(BulkWordModifyPreview(
            totalCount = words.size,
            changedItems = changedItems,
            unchangedWords = unchangedWords,
            duplicateInputWords = duplicateInputWords,
            failures = failures
        ))
    }

    @Transactional
    fun bulkModify(adminId: String, lang: String, request: BulkWordModifyRequest): ActionResponse {
        val tableName = getTableName(lang)
        if (tableName.isEmpty() || !isValidBulkRequest(request.words) || !isValidModifyRequest(request)) {
            return ActionResponse.rest(success = false, restResult = RestResult.INVALID_DATA)
        }

        val (words, _) = normalizeWords(request.words)
        val groupedExistingWords = wordDao.getWords(tableName, words).groupBy { it.id }
        val changedWords = ArrayList<Pair<Word, Word>>()
        val failures = ArrayList<BulkWordFailure>()
        var skippedCount = 0

        words.forEach { wordName ->
            val existingWords = groupedExistingWords[wordName].orEmpty()
            when {
                !isValidWord(lang, wordName) -> failures.add(invalidWordFailure(wordName))
                existingWords.isEmpty() -> failures.add(BulkWordFailure(wordName, "NOT_FOUND", "존재하지 않는 단어입니다."))
                existingWords.size > 1 -> failures.add(nonUniqueFailure(wordName))
                else -> {
                    val oldWord = existingWords[0]
                    val newWord = BulkWordMutation.apply(oldWord, request)
                    if (oldWord == newWord) skippedCount++ else changedWords.add(oldWord to newWord)
                }
            }
        }

        wordDao.updateAll(tableName, changedWords.map { it.second })
        val now = LocalDateTime.now()
        wordAuditLogDAO.insertAll(lang, changedWords.map { (oldWord, newWord) ->
            WordAuditLog(
                time = now,
                word = newWord.id,
                type = WordAuditLog.WordAuditLogType.UPDATE,
                oldType = oldWord.type,
                oldMean = oldWord.mean,
                oldFlag = oldWord.flag,
                oldTheme = oldWord.theme,
                newType = newWord.type,
                newMean = newWord.mean,
                newFlag = newWord.flag,
                newTheme = newWord.theme,
                updateLogIgnore = request.updateLogIgnore,
                updateLogIncludeDetail = request.updateLogIncludeDetail,
                admin = adminId
            )
        })

        return ActionResponse.success(BulkWordResult(
            requestedCount = words.size,
            successCount = changedWords.size,
            updatedCount = changedWords.size,
            skippedCount = skippedCount,
            failures = failures
        ))
    }

    private fun isValidBulkRequest(words: List<String>): Boolean =
        words.isNotEmpty() && words.size <= MAX_BULK_WORDS && words.any { it.isNotBlank() }

    private fun isValidModifyRequest(request: BulkWordModifyRequest): Boolean {
        val flagOperation = request.flagOperation.uppercase()
        if (flagOperation !in setOf("KEEP", "ADD", "REMOVE", "REPLACE")) return false
        if (flagOperation != "KEEP" && request.flags.isEmpty() && flagOperation != "REPLACE") return false
        if ((request.replaceThemeFrom == null) != (request.replaceThemeTo == null)) return false
        if ((request.replaceTypeFrom == null) != (request.replaceTypeTo == null)) return false
        return BulkWordMutation.hasMutation(request)
    }

    private fun modifyPreviewItem(oldWord: Word, newWord: Word) = BulkWordModifyItem(
        word = oldWord.id,
        oldFlags = flagNames(oldWord.flag),
        newFlags = flagNames(newWord.flag),
        oldThemes = codeNames(oldWord.theme, { WordTheme.findByCode(it) }) { it.themeName },
        newThemes = codeNames(newWord.theme, { WordTheme.findByCode(it) }) { it.themeName },
        oldTypes = codeNames(oldWord.type, { WordType.findByCode(it) }) { it.typeName },
        newTypes = codeNames(newWord.type, { WordType.findByCode(it) }) { it.typeName }
    )

    private fun flagNames(mask: Int): List<String> {
        val names = WordFlag.values().filter { it.flag > 0 && mask.and(it.flag) != 0 }.map { it.flagName }
        return if (names.isEmpty()) listOf("일반") else names
    }

    private fun <T> codeNames(codes: String, find: (String) -> T?, name: (T) -> String): List<String> =
        codes.split(",").filter { it.isNotBlank() }.distinct().map { code -> find(code)?.let(name) ?: code }

    private fun normalizeWords(words: List<String>): Pair<List<String>, List<String>> {
        val uniqueWords = LinkedHashSet<String>()
        val duplicateWords = LinkedHashSet<String>()
        words.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            if (!uniqueWords.add(it)) duplicateWords.add(it)
        }
        return uniqueWords.toList() to duplicateWords.toList()
    }

    private fun isValidWord(lang: String, word: String): Boolean = when (lang) {
        "ko" -> word.matches("^[ㄱ-ㅎ가-힣0-9]+$".toRegex())
        "en" -> word.matches("^[A-Za-z0-9][A-Za-z0-9 ']*$".toRegex())
        else -> false
    }

    private fun invalidWordFailure(word: String) = BulkWordFailure(
        word,
        "INVALID_WORD",
        "언어별 단어 입력 규칙에 맞지 않습니다."
    )

    private fun nonUniqueFailure(word: String) = BulkWordFailure(
        word,
        WordResult.NON_UNIQUE.name,
        WordResult.NON_UNIQUE.message
    )

    private fun newWord(wordName: String, request: BulkWordAddRequest): Word = Word.convertFrom(
        WordVO(wordName, 0, request.flags, request.details)
    )

    private fun themeName(themeCode: String): String =
        WordTheme.findByCode(themeCode)?.themeName ?: themeCode

    private fun getTableName(lang: String): String {
        return when (lang) {
            "ko" -> "kkutu_ko"
            "en" -> "kkutu_en"
            else -> ""
        }
    }
}
