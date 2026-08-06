/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.kkutuio.kkutuweb.admin.api.response

import me.kkutuio.kkutuweb.admin.vo.WordDetailVO

data class BulkWordFailure(
    val word: String,
    val resultCode: String,
    val resultMessage: String
)

data class BulkWordAddPreview(
    val totalCount: Int,
    val additions: List<String>,
    val meaningAdditions: List<String>,
    val duplicates: List<String>,
    val duplicateInputWords: List<String>,
    val failures: List<BulkWordFailure>,
    val details: List<WordDetailVO>
)

data class BulkWordDeleteItem(
    val word: String,
    val themes: List<String>
)

data class BulkWordThemeGroup(
    val themeCode: String,
    val themeName: String,
    val words: List<String>
)

data class BulkWordDeletePreview(
    val totalCount: Int,
    val multiThemeWords: List<BulkWordDeleteItem>,
    val themeGroups: List<BulkWordThemeGroup>,
    val noThemeWords: List<String>,
    val duplicateInputWords: List<String>,
    val failures: List<BulkWordFailure>
)

data class BulkWordResult(
    val requestedCount: Int,
    val successCount: Int,
    val createdCount: Int = 0,
    val meaningAddedCount: Int = 0,
    val deletedCount: Int = 0,
    val skippedCount: Int = 0,
    val failures: List<BulkWordFailure>
)
