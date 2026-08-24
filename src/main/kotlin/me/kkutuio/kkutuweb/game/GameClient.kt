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

package me.kkutuio.kkutuweb.game

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.neovisionaries.ws.client.*
import me.kkutuio.kkutuweb.geo.GeoService
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GameClient(
    private val isSecure: Boolean,
    private val host: String,
    private val port: Int,
    private val key: String,
    private val id: Short,
    private val reconnectEnabled: Boolean,
    private val reconnectRetryIntervalSeconds: Long,
    private val geoService: GeoService,
    private val adminDirectoryProvider: () -> Set<String>
) : WebSocketAdapter() {
    companion object {
        private val reconnectScheduler = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "kkutu-game-client-reconnect").apply { isDaemon = true }
        }
    }

    private val logger = LoggerFactory.getLogger(GameClient::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val reconnectScheduled = AtomicBoolean(false)
    @Volatile
    private var webSocket: WebSocket? = null
    var players = 0

    init {
        connectWebSocket()
    }

    @Synchronized
    private fun connectWebSocket() {
        if (webSocket?.isOpen == true) return
        try {
            val protocol = if (isSecure) "wss" else "ws"
            val webSocketUrl = "$protocol://$host:$port/$key:$id"

            webSocket = WebSocketFactory()
                .setConnectionTimeout(5000)
                .setVerifyHostname(false)
                .createSocket(webSocketUrl, 5000)
                .setPingInterval(5000)

            webSocket!!.addListener(this)
            webSocket!!.connectAsynchronously()
        } catch (e: Exception) {
            logger.warn("$port @ 게임서버 연결에 실패했습니다. ${e.message}")
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!reconnectEnabled) return
        if (!reconnectScheduled.compareAndSet(false, true)) return
        logger.info(
            "$port @ 게임서버#${id} 에 ${reconnectRetryIntervalSeconds}초 후 재연결을 시도합니다."
        )
        reconnectScheduler.schedule({
            reconnectScheduled.set(false)
            if (!isConnected()) connectWebSocket()
        }, reconnectRetryIntervalSeconds, TimeUnit.SECONDS)
    }

    private fun invalidateAndReconnect(websocket: WebSocket?) {
        if (websocket == null || webSocket !== websocket) return
        webSocket = null
        runCatching { websocket.disconnect() }
        scheduleReconnect()
    }

    override fun onConnected(websocket: WebSocket, headers: MutableMap<String, MutableList<String>>) {
        reconnectScheduled.set(false)
        logger.info("$port @ 게임서버#${id} 가 연결되었습니다.")
    }

    override fun onDisconnected(
        websocket: WebSocket,
        serverCloseFrame: WebSocketFrame,
        clientCloseFrame: WebSocketFrame,
        closedByServer: Boolean
    ) {
        val disconnectError = IllegalStateException("game websocket disconnected")
        for ((_, future) in pendingRequests) {
            future.completeExceptionally(disconnectError)
        }
        pendingRequests.clear()
        if (webSocket === websocket) webSocket = null
        if (closedByServer) logger.info("서버에 의해 $port @ 게임서버#${id} 의 연결이 끊어졌습니다.")
        else logger.info("$port @ 게임서버#${id} 의 연결이 끊어졌습니다.")
        scheduleReconnect()
    }

    override fun onConnectError(websocket: WebSocket, exception: WebSocketException) {
        if (webSocket === websocket) webSocket = null
        logger.warn("$port @ 게임서버#${id} 연결 시도에 실패했습니다.")
        scheduleReconnect()
    }

    override fun onError(websocket: WebSocket, cause: WebSocketException) {
        logger.error("$port @ 게임서버#${id} 에서 오류가 발생했습니다.", cause)
        invalidateAndReconnect(websocket)
    }

    override fun onTextMessage(websocket: WebSocket, text: String) {
        val jsonNode = objectMapper.readTree(text)
        val type = jsonNode["type"]?.stringValue() ?: return

        if (type == "ip-geo-lookup") {
            handleIpGeoLookup(jsonNode)
            return
        }

        if (type == "admin-directory-request") {
            sendAdminDirectory(adminDirectoryProvider())
            return
        }

        if (type == "record-find-game-result" ||
            type == "record-find-user-history-result" ||
            type == "record-find-user-mode-stats-result"
        ) {
            val requestId = jsonNode["requestId"]?.stringValue() ?: return
            val future = pendingRequests.remove(requestId) ?: return
            future.complete(text)
            return
        }

        if (type == "seek") {
            players = jsonNode["value"].intValue()
        }
    }

    fun send(data: String) {
        if (!isConnected()) return
        webSocket!!.sendText(data)
    }

    fun sendAdminDirectory(accountUuids: Set<String>) {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "admin-directory")
        val ids = payload.putArray("accountUuids")
        accountUuids.sorted().forEach(ids::add)
        send(objectMapper.writeValueAsString(payload))
    }

    fun isConnected(): Boolean {
        val connected = webSocket?.isOpen == true
        if (!connected) scheduleReconnect()
        return connected
    }

    fun requestReplayByGameId(
        gameId: String,
        includePayload: Boolean,
        requesterId: String?,
        requesterAccountUuid: String?,
        includeAdminKeyTrace: Boolean
    ): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "record-find-game")
        payload.put("gameId", gameId)
        payload.put("includePayload", includePayload)
        payload.put("includeAdminKeyTrace", includeAdminKeyTrace)
        if (!requesterId.isNullOrBlank()) payload.put("requesterId", requesterId)
        if (!requesterAccountUuid.isNullOrBlank()) payload.put("requesterAccountUuid", requesterAccountUuid)
        return requestReply(payload)
    }

    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int, canViewAll: Boolean): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "record-find-user-history")
        payload.put("userId", userId)
        payload.put("page", page)
        payload.put("pageSize", pageSize)
        payload.put("canViewAll", canViewAll)
        return requestReply(payload)
    }

    fun requestReplayUserModeStats(userId: String, canViewAll: Boolean): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "record-find-user-mode-stats")
        payload.put("userId", userId)
        payload.put("canViewAll", canViewAll)
        return requestReply(payload)
    }

    private fun handleIpGeoLookup(request: JsonNode) {
        val response = objectMapper.createObjectNode()
        response.put("type", "ip-geo-lookup-result")
        request["requestId"]?.stringValue()?.let { response.put("requestId", it) }
        val ip = request["ip"]?.stringValue()?.trim()
        if (ip.isNullOrEmpty() || ip.length > 45) {
            response.put("ok", false)
            response.put("code", 400)
            response.put("error", "invalid-ip")
        } else {
            val geo = runCatching { geoService.getGeoInfo(ip) }.getOrNull()
            if (geo == null) {
                response.put("ok", false)
                response.put("code", 503)
                response.put("error", "geo-unavailable")
            } else {
                response.put("ok", true)
                response.put("code", 200)
                response.set("geo", objectMapper.valueToTree(geo))
            }
        }
        send(objectMapper.writeValueAsString(response))
    }

    private fun requestReply(payload: ObjectNode, timeoutMs: Long = 2500): String? {
        if (!isConnected()) return null
        val requestId = UUID.randomUUID().toString()
        payload.put("requestId", requestId)
        val future = CompletableFuture<String>()
        pendingRequests[requestId] = future
        return try {
            send(objectMapper.writeValueAsString(payload))
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            logger.warn("$port @ 게임서버#${id} 요청 실패: ${e.message}")
            invalidateAndReconnect(webSocket)
            null
        }
    }
}
