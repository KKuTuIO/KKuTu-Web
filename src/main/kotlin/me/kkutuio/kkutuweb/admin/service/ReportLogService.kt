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
import me.kkutuio.kkutuweb.admin.dao.ReportLogDAO
import me.kkutuio.kkutuweb.admin.dao.TableStatisticsDAO
import me.kkutuio.kkutuweb.admin.vo.ReportLogVO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ReportLogService(
    @Autowired private val reportLogDAO: ReportLogDAO,
    @Autowired private val tableStatisticsDAO: TableStatisticsDAO
) {
    fun getReportLogRes(
        page: Int,
        pageSize: Int,
        sortData: String,
        searchFilters: Map<String, String>
    ): ListResponse<ReportLogVO> {
        require(page >= 0) { "페이지는 0 이상이어야 합니다." }
        require(pageSize in 1..500) { "페이지 크기는 1에서 500 사이여야 합니다." }
        val split = sortData.split(",")
        require(split.size == 2 && split[0] in SORT_FIELDS) { "정렬할 수 없는 항목입니다." }
        val sortField = split[0]
        val sortType = runCatching { SortType.valueOf(split[1]) }
            .getOrElse { throw IllegalArgumentException("정렬 방향이 올바르지 않습니다.") }

        val dbSearchFilters = searchFilters.filterValues { it.isNotEmpty() }

        val estimated = dbSearchFilters.isEmpty()
        val measuredCount = if (estimated) tableStatisticsDAO.getEstimatedRowCount("report_log")
            else reportLogDAO.getDataCount(dbSearchFilters)
        val pageData = reportLogDAO.getPageData(page, pageSize, sortField, sortType, dbSearchFilters)
            .map { ReportLogVO.convertFrom(it) }
        val visibleCountFloor = page * pageSize + pageData.size +
            if (estimated && pageData.size == pageSize) 1 else 0

        return ListResponse(maxOf(measuredCount, visibleCountFloor), pageData, estimated)
    }

    companion object {
        private val SORT_FIELDS = setOf(
            "report_id", "time", "reporter_id", "reporter_nick",
            "target_id", "status", "reason", "file_name"
        )
    }
}
