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
        val initialJamo = initial + 0x1100
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

        val composed = ((transformed - 0x1100) * 21 + medial) * 28 + final + 0xAC00
        return composed.toChar().toString()
    }

    fun acceptedSources(required: String, enabled: Boolean, lang: String): Set<String> {
        if (!enabled || lang != "ko") return setOf(required)
        val transformed = transform(required)
        return if (transformed == null || transformed == required) setOf(required)
        else linkedSetOf(required, transformed)
    }

    /**
     * Returns every required syllable for which a word beginning/ending with
     * [actualSource] is a legal answer. This is the inverse of [acceptedSources]
     * and lets clients decrement dynamic manner counts without another server query.
     */
    fun requiredForms(actualSource: String, enabled: Boolean, lang: String): Set<String> {
        if (!enabled || lang != "ko") return setOf(actualSource)
        val character = actualSource.firstOrNull() ?: return emptySet()
        val offset = character.code - 0xAC00
        if (offset !in 0..11171) return setOf(actualSource)

        val medial = (offset / 28) % 21
        val final = offset % 28
        val result = linkedSetOf(actualSource)
        fun candidate(initial: Int): String {
            val composed = (initial * 21 + medial) * 28 + final + 0xAC00
            return composed.toChar().toString()
        }

        // A transformed source beginning with ㄴ may originate from ㄹ.
        if (offset / (28 * 21) == 2) {
            val rieul = candidate(5)
            if (transform(rieul) == actualSource) result += rieul
        }
        // A transformed source beginning with ㅇ may originate from ㄹ and/or ㄴ.
        if (offset / (28 * 21) == 11) {
            val rieul = candidate(5)
            val nieun = candidate(2)
            if (transform(rieul) == actualSource) result += rieul
            if (transform(nieun) == actualSource) result += nieun
        }
        return result
    }

    fun connects(required: String, actual: String, enabled: Boolean, lang: String): Boolean =
        actual in acceptedSources(required, enabled, lang)
}
