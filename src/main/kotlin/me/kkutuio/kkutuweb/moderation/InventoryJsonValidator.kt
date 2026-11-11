package me.kkutuio.kkutuweb.moderation

import tools.jackson.databind.JsonNode

/** 관리자가 직접 고치는 보관함(box)·장착 정보(equip) JSON의 모양 검증. DB에 닿기 전에 잘못된 값을 걸러낸다. */
object InventoryJsonValidator {
    const val MAX_BOX_ITEMS = 2000
    const val MAX_EQUIP_SLOTS = 100
    const val MAX_ITEM_VALUE = 1_000_000_000L

    /** 아이템 ID → 정수(구형) 또는 {value, expire?} 객체. */
    fun validateBox(box: JsonNode) {
        require(box.isObject) { "보관함은 JSON 객체여야 합니다." }
        require(box.size() <= MAX_BOX_ITEMS) { "보관함은 최대 ${MAX_BOX_ITEMS}개 항목까지 수정할 수 있습니다." }
        box.properties().forEach { (key, value) ->
            require(isSafeKey(key)) { "아이템 ID는 제어 문자를 제외한 1~100자로 입력해 주세요: $key" }
            when {
                value.isIntegralNumber -> require(value.asLong() in 0..MAX_ITEM_VALUE) { "$key 수량이 올바르지 않습니다." }
                value.isObject -> {
                    val amount = value.get("value")
                    require(amount != null && amount.isIntegralNumber && amount.asLong() in 0..MAX_ITEM_VALUE) {
                        "$key 의 value 는 0 이상의 정수여야 합니다."
                    }
                    val expire = value.get("expire")
                    require(expire == null || (expire.isIntegralNumber && expire.asLong() >= 0)) {
                        "$key 의 expire 는 0 이상의 정수(초)여야 합니다."
                    }
                    require(value.propertyNames().all { it == "value" || it == "expire" }) {
                        "$key 에는 value, expire 외의 값을 넣을 수 없습니다."
                    }
                }
                else -> throw IllegalArgumentException("$key 항목은 정수 또는 {value, expire} 객체여야 합니다.")
            }
        }
    }

    /** 슬롯 이름 → 아이템 ID 문자열. */
    fun validateEquip(equip: JsonNode) {
        require(equip.isObject) { "장착 정보는 JSON 객체여야 합니다." }
        require(equip.size() <= MAX_EQUIP_SLOTS) { "장착 슬롯은 최대 ${MAX_EQUIP_SLOTS}개까지 수정할 수 있습니다." }
        equip.properties().forEach { (slot, value) ->
            require(isSafeKey(slot)) { "슬롯 이름은 제어 문자를 제외한 1~100자로 입력해 주세요: $slot" }
            require(value.isString && isSafeKey(value.stringValue())) { "$slot 슬롯에는 아이템 ID 문자열만 넣을 수 있습니다." }
        }
    }

    private fun isSafeKey(value: String): Boolean = value.isNotEmpty() && value.length <= 100 && value.none(Char::isISOControl)
}
