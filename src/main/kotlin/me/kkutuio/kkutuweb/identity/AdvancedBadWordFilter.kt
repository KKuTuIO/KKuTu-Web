package me.kkutuio.kkutuweb.identity

import me.kkutuio.kkutuweb.setting.KKuTuSetting
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/** Uses the same ADVBAD expressions that moderation tools use for profile text. */
@Component
class AdvancedBadWordFilter(
    @Autowired private val kkutuSetting: KKuTuSetting
) {
    private val pattern = Regex(
        kkutuSetting.getAdvancedBadWordPatterns().joinToString("|").ifEmpty { "(?!)" }
    )

    fun contains(value: String): Boolean = pattern.containsMatchIn(value)
}
