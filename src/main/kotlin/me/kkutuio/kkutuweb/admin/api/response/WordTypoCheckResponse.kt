/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package me.kkutuio.kkutuweb.admin.api.response

data class WordTypoCheckResult(
    val totalCount: Int,
    val scannedCount: Int,
    val truncated: Boolean,
    val candidates: List<WordTypoCandidate>
)

data class WordTypoCandidate(
    val word: String,
    val hit: Int,
    val issues: List<String>,
    val suggestions: List<WordTypoSuggestion>
)

data class WordTypoSuggestion(
    val word: String,
    val hit: Int,
    val reason: String
)
