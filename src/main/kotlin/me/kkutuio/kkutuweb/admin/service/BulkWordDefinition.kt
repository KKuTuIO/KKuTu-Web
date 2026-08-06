package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.vo.WordDetailVO
import me.kkutuio.kkutuweb.utils.WordUtils
import me.kkutuio.kkutuweb.word.Word
import me.kkutuio.kkutuweb.word.WordFlag

internal object BulkWordDefinition {
    private val meanMarkerRegex = "＂[0-9]+＂".toRegex()

    fun missingDetails(word: Word, details: List<WordDetailVO>): List<WordDetailVO> {
        val existingKeys = existingDetailKeys(word)
        return details.distinctBy(::detailKey).filter { detailKey(it) !in existingKeys }
    }

    fun appendDetails(oldWord: Word, flags: List<WordFlag>, details: List<WordDetailVO>): Word {
        val requestedFlag = flags.fold(0) { value, flag -> value.or(flag.flag) }
        return oldWord.copy(
            type = appendCsv(oldWord.type, details.map { it.type.typeCode }),
            theme = appendCsv(oldWord.theme, details.map { it.theme.themeCode }),
            mean = appendMeans(oldWord, details.map { it.mean.trim() }),
            flag = oldWord.flag.or(requestedFlag)
        )
    }

    private fun detailKey(detail: WordDetailVO) =
        detailKey(detail.type.typeCode, detail.theme.themeCode, detail.mean)

    private fun detailKey(typeCode: String, themeCode: String, mean: String) =
        Triple(typeCode, themeCode, mean.trim())

    private fun existingDetailKeys(word: Word): Set<Triple<String, String, String>> {
        val types = word.type.split(",")
        val themes = word.theme.split(",")
        val means = WordUtils.deserializeMean(word.mean)
        val count = maxOf(types.size, themes.size, means.size)
        return (0 until count).map {
            detailKey(types.getOrElse(it) { "" }, themes.getOrElse(it) { "" }, means.getOrElse(it) { "" })
        }.toSet()
    }

    private fun appendCsv(original: String, values: List<String>): String =
        (original.split(",").filter { it.isNotEmpty() } + values).joinToString(",")

    private fun appendMeans(oldWord: Word, means: List<String>): String {
        val definitionCount = maxOf(
            oldWord.type.split(",").count { it.isNotEmpty() },
            oldWord.theme.split(",").count { it.isNotEmpty() },
            meanMarkerRegex.findAll(oldWord.mean).count()
        )
        val separator = if (oldWord.mean.isEmpty() || oldWord.mean.endsWith("  ")) "" else "  "
        return buildString {
            append(oldWord.mean)
            append(separator)
            means.forEachIndexed { index, mean ->
                append("＂").append(definitionCount + index + 1).append("＂ ").append(mean).append("  ")
            }
        }
    }
}
