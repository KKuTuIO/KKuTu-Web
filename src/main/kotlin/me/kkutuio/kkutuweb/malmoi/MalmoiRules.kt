package me.kkutuio.kkutuweb.malmoi

/** 말모이의 규칙과 숫자(기획서 MALMOI_DESIGN.md). 화면 시안의 `malmoi/model.js`와 같은 값이다. */
object MalmoiRules {
    const val PUBLISH_LEVEL = 30
    const val DAILY_EDITS = 50
    const val UNLISTED_LIMIT = 5
    const val WORD_MAX = 80
    const val APPLY_HOURS = 24L
    const val LICENSE_FEE = 500
    const val LICENSE_DAYS = 90L
    const val REISSUE_DELAY_DAYS = 90L
    const val TOP_SHARE = 0.10
    const val TOP_DAYS = 30L
    const val BLOCK_MAX_DAYS = 7L
    const val EDITORS = 3
    const val PROMOTE_LEVEL = 30
    const val PROMOTE_WORDS = 50
    const val OATH_VERSION = 1

    val OATH = listOf(
        "끄투리오 운영정책을 준수하겠습니다.",
        "기준에 어긋나거나 유해한 단어를 등록하지 않겠습니다.",
        "내가 작성한 단어와 뜻이 공식 사전에 승계될 수 있음에 동의합니다.",
        "위반 시 면허 취소와 같은 제재에 동의하며 핑이 환불되지 않음을 이해합니다."
    )

    private val WORD = Regex("^[ㄱ-ㅎ가-힣0-9]+$")

    /** 표제어: 띄어쓰기는 생략해서 적는다. */
    fun normalize(raw: String?): String = (raw ?: "").replace(Regex("\\s+"), "")

    /** @return 저장할 수 없는 이유, 없으면 null */
    fun wordError(word: String): String? = when {
        word.isEmpty() -> "낱말을 입력해 주세요."
        !WORD.matches(word) -> "한글과 숫자만 쓸 수 있어요."
        word.length > WORD_MAX -> "${WORD_MAX}자까지 쓸 수 있어요."
        else -> null
    }

    /** 선서는 띄어쓰기와 쉼표, 마침표를 가리지 않는다. */
    fun sameOath(typed: String, line: String): Boolean {
        val bare = { s: String -> s.replace(Regex("[\\s.,]"), "") }
        return bare(typed) == bare(line)
    }

    /** 면허 번호: 연-월-일련번호-회차 (카드 번호 모양). */
    fun licenseNumber(year: Int, month: Int, serial: Long) =
        "%02d-%02d-%06d-01".format(year % 100, month, serial % 1_000_000)
}
