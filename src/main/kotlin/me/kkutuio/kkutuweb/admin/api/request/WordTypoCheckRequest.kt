/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package me.kkutuio.kkutuweb.admin.api.request

import me.kkutuio.kkutuweb.word.WordMatch
import me.kkutuio.kkutuweb.word.WordSearchFilter

data class WordTypoCheckRequest(
    val word: String = "",
    val wordMatch: String = "CONTAINS",
    val themes: String = "",
    val themeMatchAll: Boolean = false,
    val types: String = "",
    val flags: String = "",
    val flagMatchAll: Boolean = false,
    val minHit: Int? = null,
    val maxHit: Int? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val hasTheme: Boolean? = null,
    val hasMeaning: Boolean? = null,
    val onlyInjeongWithMeaning: Boolean = false,
    val createdBy: String = "",
    val createdWithinDays: Int? = null
) {
    fun toSearchFilter(): WordSearchFilter = WordSearchFilter(
        word = word,
        wordMatch = WordMatch.parse(wordMatch),
        themes = csv(themes),
        themeMatchAll = themeMatchAll,
        types = csv(types),
        flags = csv(flags).mapNotNull { it.toIntOrNull() },
        flagMatchAll = flagMatchAll,
        minHit = minHit,
        maxHit = maxHit,
        minLength = minLength,
        maxLength = maxLength,
        hasTheme = hasTheme,
        hasMeaning = hasMeaning,
        onlyInjeongWithMeaning = onlyInjeongWithMeaning,
        createdBy = createdBy,
        createdWithinDays = createdWithinDays?.coerceIn(1, 3650)
    )

    private fun csv(value: String): List<String> =
        value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
