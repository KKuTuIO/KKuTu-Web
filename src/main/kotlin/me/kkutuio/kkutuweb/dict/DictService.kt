/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.dict

import me.kkutuio.kkutuweb.academy.AcademyRequestException
import me.kkutuio.kkutuweb.academy.AcademyRuleConfig
import me.kkutuio.kkutuweb.academy.AcademyService
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * Compatibility wrapper retained for older callers. It must never query the
 * master word tables without the academy visibility policy.
 */
@Service
class DictService(
    private val academyService: AcademyService,
    private val objectMapper: ObjectMapper
) {
    fun getWord(id: String, lang: String): String = try {
        val word = academyService.getWord(AcademyRuleConfig(lang = lang, dictionary = "COMBINED"), id)
        objectMapper.writeValueAsString(
            LegacyDictionaryWord(word.word, word.mean, word.themes.joinToString(","), word.types.joinToString(","))
        )
    } catch (_: IllegalArgumentException) {
        "{\"error\":400}"
    } catch (_: AcademyRequestException) {
        "{\"error\":404}"
    }

    /** Prefix enumeration is intentionally unavailable through this legacy service. */
    fun getWords(@Suppress("UNUSED_PARAMETER") startChar: String, @Suppress("UNUSED_PARAMETER") lang: String, @Suppress("UNUSED_PARAMETER") mission: String?): String =
        "{\"error\":410}"
}
