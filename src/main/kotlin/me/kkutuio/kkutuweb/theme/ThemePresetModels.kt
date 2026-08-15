package me.kkutuio.kkutuweb.theme

data class ThemePreset(
    val id: String,
    val name: String,
    val lang: String,
    val themes: List<String>,
    val startsAt: Long?,
    val endsAt: Long?,
    val enabled: Boolean,
    val sortOrder: Int,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class ThemePresetRequest(
    val id: String = "",
    val name: String = "",
    val lang: String = "ko",
    val themes: List<String> = emptyList(),
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val enabled: Boolean = false,
    val sortOrder: Int = 0
)
