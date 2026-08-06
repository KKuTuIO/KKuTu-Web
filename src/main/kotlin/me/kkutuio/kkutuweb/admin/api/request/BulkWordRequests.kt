/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.kkutuio.kkutuweb.admin.api.request

import me.kkutuio.kkutuweb.admin.vo.WordDetailVO
import me.kkutuio.kkutuweb.word.WordFlag

data class BulkWordAddRequest(
    val words: List<String>,
    val flags: List<WordFlag>,
    val details: List<WordDetailVO>,
    val meaningAdditionWords: List<String> = emptyList(),
    val updateLogIgnore: Boolean,
    val updateLogIncludeDetail: Boolean
)

data class BulkWordDeleteRequest(
    val words: List<String>,
    val updateLogIgnore: Boolean,
    val updateLogIncludeDetail: Boolean
)
