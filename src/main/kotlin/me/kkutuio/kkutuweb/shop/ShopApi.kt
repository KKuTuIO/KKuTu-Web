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

package me.kkutuio.kkutuweb.shop

import me.kkutuio.kkutuweb.shop.response.ShopResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpSession

@RestController
class ShopApi(
    @Autowired private val shopService: ShopService
) {
    @Cacheable(value = ["shopCache"], key = "'shop'")
    @GetMapping("/shop")
    fun getGoods(): ShopResponse {
        return shopService.getGoods()
    }

    @CacheEvict(value = ["shopCache"], allEntries = true)
    fun evictShopCache() {}

    @PostMapping("/buy/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun buyGood(
        @PathVariable id: String,
        session: HttpSession
    ): String {
        return shopService.buyGood(id, session)
    }

    @PostMapping("/payback/{id}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun paybackGood(
        @PathVariable id: String,
        session: HttpSession
    ): String {
        return shopService.paybackGood(id, session)
    }

    @Scheduled(fixedDelay = 3600000)
    fun evictCachePeriodically() {
        evictShopCache()
    }
}
