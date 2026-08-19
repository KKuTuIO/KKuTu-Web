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

package me.kkutuio.kkutuweb.dict

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession
import me.kkutuio.kkutuweb.shop.ShopService
import me.kkutuio.kkutuweb.extension.isGuest
import me.kkutuio.kkutuweb.locale.LocalePropertyLoader
import java.util.Locale

@RestController
class DictApi(
    @Autowired private val dictService: DictService,
    @Autowired private val shopService: ShopService,
    @Autowired private val localePropertyLoader: LocalePropertyLoader
) {
    @GetMapping("/dictionary/meta", "/api/dictionary/meta", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMetadata(): DictionaryMetadata {
        val messages = localePropertyLoader.getMessages(Locale.KOREAN)
        return DictionaryMetadata(
            themes = messages.filterKeys { it.startsWith("word.theme.") }
                .mapKeys { it.key.removePrefix("word.theme.") },
            parts = messages.filterKeys { it.startsWith("word.class.") }
                .mapKeys { it.key.removePrefix("word.class.") }
        )
    }
    @GetMapping("/dictionary/{lang}/{word}", "/api/dictionary/{lang}/{word}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getWord(
        @PathVariable word: String,
        @PathVariable lang: String
    ): String {
        return dictService.getWord(word, lang)
    }

    @GetMapping("/wordsheet/{lang}/{startChar}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getWords(
        @PathVariable startChar: String,
        @PathVariable lang: String,
        @RequestParam(required = false) mission: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
        session: HttpSession
    ): String {
        if (request.getHeader("referer") == null || !request.getHeader("referer").contains("kkutu.io")) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            return "{\"error\":403}"
        }
        if (session.isGuest()) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return "{\"error\":400}"
        }
        /*val tokenResult = shopService.consumeToken(session, if (mission != null) 2 else 1)
        if (tokenResult.contains("\"error\"")) {
            response.status = HttpServletResponse.SC_PAYMENT_REQUIRED
            return tokenResult
        }*/
        Thread.sleep((4000..10000).random().toLong())
        // 단어토큰 사용 (단, 토큰이 부족한 경우 오류 반환)
        return dictService.getWords(startChar, lang, mission)
    }
}

data class DictionaryMetadata(
    val themes: Map<String, String>,
    val parts: Map<String, String>
)
