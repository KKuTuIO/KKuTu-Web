/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.kkutuio.kkutuweb.word

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class WordTheme(val themeCode: String, val themeName: String) {
    THEME_IMS("IMS", "THE IDOLM@STER"),
    THEME_VOC("VOC", "VOCALOID"),
    THEME_KRR("KRR", "개구리 중사 케로로"),
    THEME_KTV("KTV", "국내 방송 프로그램"),
    THEME_KOT("KOT", "대한민국 철도역"),
    THEME_DOT("DOT", "도타 2"),
    THEME_THP("THP", "동방 프로젝트"),
    THEME_DRR("DRR", "듀라라라!!"),
    THEME_DGM("DGM", "디지몬"),
    THEME_RAG("RAG", "라면/과자/음식"),
    THEME_LVL("LVL", "러브 라이브!"),
    THEME_LOL("LOL", "리그 오브 레전드"),
    THEME_MRN("MRN", "마법소녀 리리컬 나노하"),
    THEME_MMM("MMM", "마법소녀 마도카☆마기카"),
    THEME_MAP("MAP", "메이플스토리"),
    THEME_MOB("MOB", "모바일 게임"),
    THEME_CYP("CYP", "사이퍼즈"),
    THEME_STA("STA", "스타크래프트"),
    THEME_OIJ("OIJ", "신조어"),
    THEME_KGR("KGR", "아지랑이 프로젝트"),
    THEME_ELW("ELW", "엘소드"),
    THEME_OVW("OVW", "오버워치"),
    THEME_NEX("NEX", "온라인 게임"),
    THEME_MOV("MOV", "영화"),
    THEME_WOW("WOW", "월드 오브 워크래프트"),
    THEME_KPO("KPO", "유명인"),
    THEME_JLN("JLN", "라이트 노벨"),
    THEME_JAN("JAN", "만화/애니메이션/웹툰"),
    THEME_ZEL("ZEL", "젤다의 전설"),
    THEME_POK("POK", "포켓몬스터"),
    THEME_HSS("HSS", "하스스톤"),
    THEME_HDC("HDC", "함대 컬렉션"),
    THEME_HOS("HOS", "히어로즈 오브 더 스톰"),
    THEME_BDM("BDM", "Bang Dream!"),
    THEME_KIO("KIO", "끄투리오"),
    THEME_CON("CON", "콘솔 게임"),
    THEME_HRT("HRT", "대한민국 문화재"),
    THEME_BRD("BRD", "브랜드/회사"),
    THEME_e00("e00", "★"),
    THEME_e01("e01", "★"),
    THEME_e02("e02", "★"),
    THEME_e03("e03", "★"),
    THEME_e04("e04", "★"),
    THEME_e05("e05", "동물"),
    THEME_e06("e06", "★"),
    THEME_e07("e07", "★"),
    THEME_e08("e08", "인체"),
    THEME_e09("e09", "★"),
    THEME_e10("e10", "★"),
    THEME_e11("e11", "★"),
    THEME_e12("e12", "감정"),
    THEME_e13("e13", "음식"),
    THEME_e14("e14", "★"),
    THEME_e15("e15", "지명"),
    THEME_e16("e16", "★"),
    THEME_e17("e17", "★"),
    THEME_e18("e18", "사람"),
    THEME_e19("e19", "★"),
    THEME_e20("e20", "식물"),
    THEME_e21("e21", "★"),
    THEME_e22("e22", "★"),
    THEME_e23("e23", "★"),
    THEME_e24("e24", "★"),
    THEME_e25("e25", "★"),
    THEME_e26("e26", "★"),
    THEME_e27("e27", "★"),
    THEME_e28("e28", "★"),
    THEME_e29("e29", "★"),
    THEME_e30("e30", "★"),
    THEME_e31("e31", "★"),
    THEME_e32("e32", "★"),
    THEME_e33("e33", "★"),
    THEME_e34("e34", "★"),
    THEME_e35("e35", "★"),
    THEME_e36("e36", "★"),
    THEME_e37("e37", "★"),
    THEME_e38("e38", "★"),
    THEME_e39("e39", "★"),
    THEME_e40("e40", "★"),
    THEME_e41("e41", "★"),
    THEME_e42("e42", "★"),
    THEME_e43("e43", "날씨"),
    THEME_e44("e44", "★"),
    THEME_e53("e53", "화학"),
    THEME_0("0", "주제없음"),
    THEME_10("10", "가톨릭"),
    THEME_20("20", "건설"),
    THEME_30("30", "경제"),
    THEME_40("40", "고적"),
    THEME_50("50", "고유"),
    THEME_60("60", "공업"),
    THEME_70("70", "광업"),
    THEME_80("80", "교육"),
    THEME_90("90", "교통"),
    THEME_100("100", "군사"),
    THEME_110("110", "기계"),
    THEME_120("120", "기독교"),
    THEME_130("130", "논리"),
    THEME_140("140", "농업"),
    THEME_150("150", "문학"),
    THEME_160("160", "물리"),
    THEME_170("170", "미술"),
    THEME_180("180", "민속"),
    THEME_190("190", "동물"),
    THEME_200("200", "법률"),
    THEME_210("210", "불교"),
    THEME_220("220", "사회"),
    THEME_230("230", "생물"),
    THEME_240("240", "수학"),
    THEME_250("250", "수산"),
    THEME_260("260", "수공"),
    THEME_270("270", "식물"),
    THEME_280("280", "심리"),
    THEME_290("290", "약학"),
    THEME_300("300", "언론"),
    THEME_310("310", "언어"),
    THEME_320("320", "역사"),
    THEME_330("330", "연영"),
    THEME_340("340", "예술"),
    THEME_350("350", "운동"),
    THEME_360("360", "음악"),
    THEME_370("370", "의학"),
    THEME_380("380", "인명"),
    THEME_390("390", "전기"),
    THEME_400("400", "정치"),
    THEME_410("410", "종교"),
    THEME_420("420", "지리"),
    THEME_430("430", "지명"),
    THEME_440("440", "책명"),
    THEME_450("450", "천문"),
    THEME_460("460", "철학"),
    THEME_470("470", "출판"),
    THEME_480("480", "통신"),
    THEME_490("490", "컴퓨터"),
    THEME_500("500", "한의학"),
    THEME_510("510", "항공"),
    THEME_520("520", "해양"),
    THEME_530("530", "화학"),
    THEME_1001("1001", "나라 이름과 수도"),
    THEME_HBW("HBW", "어인정 한방 방어 단어");

    companion object {
        fun findByCode(code: String): WordTheme? {
            for (wordTheme in values()) {
                if (wordTheme.themeCode == code) {
                    return wordTheme
                }
            }
            return null
        }

        /**
         * 비직렬화를 지원하는 함수
         * === 지원하는 JSON 형식 ===
         * 1. {"key": "ID" }
         * 2. {"key": {"flag": "ID"}}
         */
        @JvmStatic
        @JsonCreator
        fun fromObject(node: JsonNode): WordTheme? {
            val identityField = "themeCode"
            val id = if (node.nodeType == JsonNodeType.STRING) {
                node.asText()
            } else {
                require(node.has(identityField))
                node[identityField].asText()
            }

            return fromId(id)
        }

        private fun fromId(themeCode: String): WordTheme? {
            val findFlags = values().filter { it.themeCode == themeCode }

            return if (findFlags.isEmpty()) null
            else findFlags[0]
        }
    }
}