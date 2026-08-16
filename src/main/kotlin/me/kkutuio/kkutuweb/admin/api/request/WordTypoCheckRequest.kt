/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package me.kkutuio.kkutuweb.admin.api.request

import me.kkutuio.kkutuweb.word.WordSearchFilter

data class WordTypoCheckRequest(
    val scope: String,
    val themes: List<String> = emptyList(),
    val createdBy: String = "",
    val createdWithinDays: Int? = null
) {
    fun isValid(): Boolean = when (scope) {
        "THEME" -> themes.any { it.isNotBlank() }
        "ADMIN_HISTORY" -> createdBy.isNotBlank() && createdWithinDays != null && createdWithinDays in 1..3650
        else -> false
    }

    fun toSearchFilter(): WordSearchFilter = when (scope) {
        "THEME" -> WordSearchFilter(themes = themes.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        "ADMIN_HISTORY" -> WordSearchFilter(
            createdBy = createdBy.trim(),
            createdWithinDays = createdWithinDays?.coerceIn(1, 3650)
        )
        else -> WordSearchFilter()
    }
}
