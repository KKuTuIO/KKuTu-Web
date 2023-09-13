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

package me.kkutuio.kkutuweb.admin.service

import me.kkutuio.kkutuweb.admin.SortType
import me.kkutuio.kkutuweb.admin.api.response.ListResponse
import me.kkutuio.kkutuweb.admin.dao.WordAuditLogDAO
import me.kkutuio.kkutuweb.admin.vo.WordAuditLogVO
import me.kkutuio.kkutuweb.admin.domain.WordAuditLog
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AdminWordAuditService(
    @Autowired private val wordAuditLogDAO: WordAuditLogDAO
) {
    fun getWordAuditListRes(
        lang: String,
        page: Int,
        pageSize: Int,
        sortData: String,
        searchFilters: Map<String, String>
    ): ListResponse<WordAuditLogVO> {
        val split = sortData.split(",")
        val sortField = split[0]
        val sortType = SortType.valueOf(split[1])

        val dbSearchFilters = searchFilters.filterValues { it.isNotEmpty() }

        val dataCount = wordAuditLogDAO.getDataCount(lang, emptyMap())
        val pageData = wordAuditLogDAO.getPageData(lang, page, pageSize, sortField, sortType, dbSearchFilters)
                .map { wordAuditLogVO.convertFrom(it) }

        return ListResponse(dataCount, pageData)
    }
}