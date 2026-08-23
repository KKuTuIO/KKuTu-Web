package me.kkutuio.kkutuweb.identity

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

/** Uses the same ADVBAD expressions that moderation tools use for profile text. */
@Component
class AdvancedBadWordFilter {
    private val pattern = Regex(
        ClassPathResource("advanced-bad-words.txt").inputStream.bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).joinToString("|")
        }
    )

    fun contains(value: String): Boolean = pattern.containsMatchIn(value)
}
