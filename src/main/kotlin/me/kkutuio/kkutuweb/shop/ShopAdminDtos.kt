package me.kkutuio.kkutuweb.shop

import tools.jackson.databind.JsonNode
import java.time.Instant

data class ShopAdminItem(
    val id: String,
    val nameKoKR: String,
    val descKoKR: String,
    val nameEnUS: String?,
    val descEnUS: String?,
    val cost: Long,
    val hit: Int,
    val term: Int,
    val group: String,
    val updatedAt: Instant?,
    val options: JsonNode
)

data class ShopAdminItemRequest(
    val id: String,
    val nameKoKR: String,
    val descKoKR: String,
    val nameEnUS: String? = null,
    val descEnUS: String? = null,
    val cost: Long,
    val term: Int = 0,
    val group: String,
    val options: JsonNode
)

data class ShopAuditEntry(
    val id: Long,
    val itemId: String,
    val action: String,
    val beforeData: JsonNode?,
    val afterData: JsonNode?,
    val adminId: String,
    val createdAt: Instant
)

data class ShopRefreshResponse(
    val connectedServers: Int
)
