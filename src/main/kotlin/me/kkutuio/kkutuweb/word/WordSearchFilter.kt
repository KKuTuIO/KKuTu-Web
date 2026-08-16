package me.kkutuio.kkutuweb.word

data class WordSearchFilter(
    val word: String = "",
    val wordMatch: WordMatch = WordMatch.LEGACY,
    val themes: List<String> = emptyList(),
    val themeMatchAll: Boolean = false,
    val types: List<String> = emptyList(),
    val flags: List<Int> = emptyList(),
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
)

enum class WordMatch {
    LEGACY,
    CONTAINS,
    EXACT,
    STARTS_WITH,
    ENDS_WITH;

    companion object {
        fun parse(value: String): WordMatch = values().find { it.name.equals(value, ignoreCase = true) } ?: LEGACY
    }
}
