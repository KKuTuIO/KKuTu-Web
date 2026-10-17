package me.kkutuio.kkutuweb.event

import tools.jackson.databind.JsonNode
import java.time.Instant

data class EventAdminEvent(
    val id: String,
    val name: String,
    val startsAt: Long,
    val endsAt: Long,
    val expiresAt: Long,
    val forceForAdmin: Boolean,
    val active: Boolean,
    val priority: Int,
    val definition: JsonNode,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class EventAdminRequest(
    val id: String,
    val name: String,
    val startsAt: Long,
    val endsAt: Long,
    val expiresAt: Long,
    val forceForAdmin: Boolean = false,
    val active: Boolean = false,
    val priority: Int = 0,
    val definition: JsonNode
)

data class EventAuditEntry(
    val id: Long,
    val eventId: String,
    val action: String,
    val beforeData: JsonNode?,
    val afterData: JsonNode?,
    val adminId: String,
    val createdAt: Instant
)
