package me.kkutuio.kkutuweb.support

import me.kkutuio.kkutuweb.identity.IdpException
import me.kkutuio.kkutuweb.moderation.ModerationInquiryId
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

data class SupportField(
    val key: String,
    val label: String,
    /** `text`, `inquiryId`, `securityCode`(저장하지 않고 대조만 한다). */
    val type: String,
    val required: Boolean,
    val maxLength: Int,
    val hint: String?
)

data class SupportCategory(
    val code: String,
    val label: String,
    val groupCode: String,
    val groupLabel: String,
    /** 운영정책의 이의신청: 답변 기한을 둔다. */
    val appeal: Boolean,
    val template: String,
    val fields: List<SupportField>
)

/** 입력받은 분류별 항목. [securityCode] 는 대조에만 쓰고 저장하지 않는다. */
data class SupportDetails(val stored: Map<String, String>, val securityCode: String?)

/** 문의 분류의 원본. `support-categories.json` 하나이고, 고객센터 화면은 이것을 받아 그린다. */
@Component
class SupportCatalog(objectMapper: ObjectMapper) {
    /** 설정 응답에 그대로 싣는 원문. */
    val document: JsonNode = ClassPathResource("support-categories.json").inputStream.use { objectMapper.readTree(it) }

    val consentVersion: String = document["consent"]["version"].asString()

    private val categories: Map<String, SupportCategory> = document["groups"].toList().flatMap { group ->
        group["items"].toList().map { item ->
            SupportCategory(
                code = item["code"].asString(),
                label = item["label"].asString(),
                groupCode = group["code"].asString(),
                groupLabel = group["label"].asString(),
                appeal = item["appeal"]?.asBoolean() == true,
                template = item["template"].asString(),
                fields = item["fields"].toList().map { field ->
                    SupportField(
                        key = field["key"].asString(),
                        label = field["label"].asString(),
                        type = field["type"].asString(),
                        required = field["required"].asBoolean(),
                        maxLength = field["maxLength"].asInt(),
                        hint = field["hint"]?.asString()
                    )
                }
            )
        }
    }.associateBy { it.code }

    fun find(code: String): SupportCategory? = categories[code]

    fun require(code: String?): SupportCategory = code?.let(::find)
        ?: throw IdpException("invalid_category", "문의 분류를 선택해 주세요.")

    fun label(code: String): String = find(code)?.let { "${it.groupLabel} > ${it.label}" } ?: code

    /** 분류에 정의된 항목만 받는다. 정의에 없는 키는 거절한다. */
    fun readDetails(category: SupportCategory, input: Map<String, String?>): SupportDetails {
        val known = category.fields.map { it.key }.toSet()
        if (input.keys.any { it !in known }) throw IdpException("invalid_details", "이 분류에 없는 입력 항목이 있습니다.")
        val stored = linkedMapOf<String, String>()
        var securityCode: String? = null
        category.fields.forEach { field ->
            val value = SupportText.singleLine(input[field.key])
            if (value.isEmpty()) {
                if (field.required) throw IdpException("invalid_details", "필수 항목이 비어 있습니다: ${field.label}")
                return@forEach
            }
            if (value.length > field.maxLength) throw IdpException("invalid_details", "입력이 너무 깁니다: ${field.label}")
            when (field.type) {
                "securityCode" -> securityCode = value
                "inquiryId" -> stored[field.key] = runCatching { ModerationInquiryId.normalize(value) }
                    .getOrElse { throw IdpException("invalid_details", it.message ?: "문의번호가 올바르지 않습니다.") }
                else -> stored[field.key] = value
            }
        }
        return SupportDetails(stored, securityCode)
    }
}
