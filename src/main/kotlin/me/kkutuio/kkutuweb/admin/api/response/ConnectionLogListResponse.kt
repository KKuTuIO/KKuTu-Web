package me.kkutuio.kkutuweb.admin.api.response

import me.kkutuio.kkutuweb.admin.vo.ConnectionLogVO

data class ConnectionLogListResponse(
    val totalElements: Int,
    val content: List<ConnectionLogVO>,
    val totalEstimated: Boolean
)
