/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package me.kkutuio.kkutuweb.admin.api.response

data class WordTypoCheckResult(
    val totalCount: Int,
    val checkedCount: Int,
    val truncated: Boolean,
    val items: List<WordTypoCheckItem>
)

data class WordTypoCheckItem(
    val word: String,
    val suggestions: List<WordTypoSuggestion>,
    val themes: List<String>,
    val createdBy: String? = null,
    val createdAt: String? = null
)

data class WordTypoSuggestion(
    val word: String,
    val start: Int,
    val removed: String,
    val added: String,
    val reason: String
)
