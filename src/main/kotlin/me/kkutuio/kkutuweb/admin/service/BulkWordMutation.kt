package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.api.request.BulkWordModifyRequest
import me.kkutuio.kkutuweb.utils.WordUtils
import me.kkutuio.kkutuweb.word.Word
import me.kkutuio.kkutuweb.admin.vo.WordVO

internal object BulkWordMutation {
    fun apply(word: Word, request: BulkWordModifyRequest): Word {
        if (request.mode.uppercase() == "SETTINGS") {
            return Word.convertFrom(WordVO(word.id, word.hit, request.flags, request.details)).copy(hit = word.hit)
        }

        val existingDetails = details(word).map { detail ->
            var type = detail.type
            var theme = detail.theme
            if (request.replaceThemeFrom != null && request.replaceThemeTo != null &&
                theme == request.replaceThemeFrom.themeCode) {
                theme = request.replaceThemeTo.themeCode
            }
            if (request.replaceTypeFrom != null && request.replaceTypeTo != null &&
                type == request.replaceTypeFrom.typeCode) {
                type = request.replaceTypeTo.typeCode
            }
            Detail(type, theme, detail.mean)
        }

        val normalizedDetails = existingDetails.ifEmpty { listOf(Detail("0", "0", "")) }
        return word.copy(
            type = normalizedDetails.joinToString(",") { it.type },
            theme = normalizedDetails.joinToString(",") { it.theme }
        )
    }

    fun hasMutation(request: BulkWordModifyRequest): Boolean = when (request.mode.uppercase()) {
        "SETTINGS" -> request.details.isNotEmpty()
        "REPLACE" -> (request.replaceThemeFrom != null && request.replaceThemeTo != null) ||
            (request.replaceTypeFrom != null && request.replaceTypeTo != null)
        else -> false
    }

    private fun details(word: Word): List<Detail> {
        val types = word.type.split(",")
        val themes = word.theme.split(",")
        val means = WordUtils.deserializeMean(word.mean)
        val count = maxOf(types.size, themes.size, means.size)
        return (0 until count).map { index ->
            Detail(
                types.getOrElse(index) { "0" }.ifBlank { "0" },
                themes.getOrElse(index) { "0" }.ifBlank { "0" },
                means.getOrElse(index) { "" }.trim()
            )
        }
    }

    private data class Detail(val type: String, val theme: String, val mean: String)
}
