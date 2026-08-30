/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package me.kkutuio.kkutuweb.academy

/**
 * Matches the head-sound conversion used by the live Korean chain modes.
 * The input and output are complete Hangul syllables, not compatibility jamo.
 */
object AcademyDuum {
    private val rieulToNieun = setOf(4449, 4450, 4457, 4460, 4462, 4467)
    private val rieulToIeung = setOf(4451, 4455, 4456, 4461, 4466, 4469)
    private val nieunToIeung = setOf(4455, 4461, 4466, 4469)

    fun transform(value: String): String? {
        val character = value.firstOrNull() ?: return null
        val code = character.code
        val offset = code - 0xAC00
        if (offset !in 0..11171) return null

        val initial = offset / (28 * 21)
        val medial = (offset / 28) % 21
        val final = offset % 28
        var initialJamo = initial + 0x1100
        val medialJamo = medial + 0x1161

        val transformed = when (initialJamo) {
            0x1105 -> when {
                medialJamo in rieulToNieun -> 0x1102
                medialJamo in rieulToIeung -> 0x110B
                else -> null
            }
            0x1102 -> if (medialJamo in nieunToIeung) 0x110B else null
            else -> null
        } ?: return null

        initialJamo = transformed
        val composed = ((initialJamo - 0x1100) * 21 + medial) * 28 + final + 0xAC00
        return composed.toChar().toString()
    }

    fun acceptedSources(required: String, enabled: Boolean, lang: String): Set<String> {
        if (!enabled || lang != "ko") return setOf(required)
        val transformed = transform(required)
        return if (transformed == null || transformed == required) setOf(required)
        else linkedSetOf(required, transformed)
    }

    fun connects(required: String, actual: String, enabled: Boolean, lang: String): Boolean =
        actual in acceptedSources(required, enabled, lang)
}
