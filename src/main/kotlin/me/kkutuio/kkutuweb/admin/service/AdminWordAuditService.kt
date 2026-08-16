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
import me.kkutuio.kkutuweb.admin.dao.TableStatisticsDAO
import me.kkutuio.kkutuweb.admin.vo.WordAuditLogVO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AdminWordAuditService(
    @Autowired private val wordAuditLogDAO: WordAuditLogDAO,
    @Autowired private val tableStatisticsDAO: TableStatisticsDAO
) {
    fun getWordAuditListRes(
        lang: String,
        page: Int,
        pageSize: Int,
        sortData: String,
        searchFilters: Map<String, String>
    ): ListResponse<WordAuditLogVO> {
        require(lang in LANGUAGES) { "지원하지 않는 언어입니다." }
        require(page >= 0) { "페이지는 0 이상이어야 합니다." }
        require(pageSize in 1..500) { "페이지 크기는 1에서 500 사이여야 합니다." }
        val split = sortData.split(",")
        require(split.size == 2 && split[0] in SORT_FIELDS) { "정렬할 수 없는 항목입니다." }
        val sortField = split[0]
        val sortType = runCatching { SortType.valueOf(split[1]) }
            .getOrElse { throw IllegalArgumentException("정렬 방향이 올바르지 않습니다.") }

        val dbSearchFilters = searchFilters.filterValues { it.isNotEmpty() }

        val estimated = dbSearchFilters.isEmpty()
        val measuredCount = if (estimated) tableStatisticsDAO.getEstimatedRowCount("kkutu_${lang}_audit_log")
            else wordAuditLogDAO.getDataCount(lang, dbSearchFilters)
        val pageData = wordAuditLogDAO.getPageData(lang, page, pageSize, sortField, sortType, dbSearchFilters)
                .map { WordAuditLogVO.convertFrom(it) }
        val visibleCountFloor = page * pageSize + pageData.size +
            if (estimated && pageData.size == pageSize) 1 else 0

        return ListResponse(maxOf(measuredCount, visibleCountFloor), pageData, estimated)
    }

    companion object {
        private val LANGUAGES = setOf("ko", "en")
        private val SORT_FIELDS = setOf(
            "id", "log_time", "log_type", "word", "admin", "old_type", "new_type",
            "old_flag", "new_flag", "old_theme", "new_theme", "old_mean", "new_mean"
        )
    }
}
