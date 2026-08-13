package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.api.request.BulkWordModifyRequest
import me.kkutuio.kkutuweb.utils.WordUtils
import me.kkutuio.kkutuweb.word.Word

internal object BulkWordMutation {
    fun apply(word: Word, request: BulkWordModifyRequest): Word {
        val requestedMask = request.flags.fold(0) { value, flag -> value.or(flag.flag) }
        val newFlag = when (request.flagOperation.uppercase()) {
            "ADD" -> word.flag.or(requestedMask)
            "REMOVE" -> word.flag.and(requestedMask.inv())
            "REPLACE" -> requestedMask
            else -> word.flag
        }

        val removeThemeCodes = request.removeThemes.map { it.themeCode }.toSet()
        val existingDetails = details(word).map { detail ->
            var type = detail.type
            var theme = detail.theme
            if (theme in removeThemeCodes) theme = "0"
            if (request.replaceThemeFrom != null && request.replaceThemeTo != null &&
                theme == request.replaceThemeFrom.themeCode) {
                theme = request.replaceThemeTo.themeCode
            }
            if (request.replaceTypeFrom != null && request.replaceTypeTo != null &&
                type == request.replaceTypeFrom.typeCode) {
                type = request.replaceTypeTo.typeCode
            }
            Detail(type, theme, detail.mean)
        }.toMutableList()

        val existingThemeCodes = existingDetails.map { it.theme }.toMutableSet()
        request.addThemes.forEach { theme ->
            if (existingThemeCodes.add(theme.themeCode)) existingDetails.add(Detail("0", theme.themeCode, ""))
        }

        val normalizedDetails = existingDetails.distinct().ifEmpty { listOf(Detail("0", "0", "")) }
        return word.copy(
            type = normalizedDetails.joinToString(",") { it.type },
            theme = normalizedDetails.joinToString(",") { it.theme },
            mean = WordUtils.serializeMean(normalizedDetails.map { it.mean }),
            flag = newFlag
        )
    }

    fun hasMutation(request: BulkWordModifyRequest): Boolean =
        request.flagOperation.uppercase() != "KEEP" || request.addThemes.isNotEmpty() ||
            request.removeThemes.isNotEmpty() ||
            (request.replaceThemeFrom != null && request.replaceThemeTo != null) ||
            (request.replaceTypeFrom != null && request.replaceTypeTo != null)

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
