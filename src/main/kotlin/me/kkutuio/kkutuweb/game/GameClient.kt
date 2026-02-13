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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.neovisionaries.ws.client.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GameClient(
    private val isSecure: Boolean,
    private val host: String,
    private val port: Int,
    private val key: String,
    private val id: Short
) : WebSocketAdapter() {
    private val logger = LoggerFactory.getLogger(GameClient::class.java)
    private val objectMapper = ObjectMapper()
    private val pendingRequests = ConcurrentHashMap<String, CompletableFuture<String>>()
    private var webSocket: WebSocket? = null
    var players = 0

    init {
        connectWebSocket()
    }

    private fun connectWebSocket() {
        try {
            val protocol = if (isSecure) "wss" else "ws"
            val webSocketUrl = "$protocol://$host:$port/$key:$id"

            webSocket = WebSocketFactory()
                .setConnectionTimeout(5000)
                .setVerifyHostname(false)
                .createSocket(webSocketUrl, 5000)

            webSocket!!.addListener(this)
            webSocket!!.connectAsynchronously()
        } catch (e: Exception) {
            logger.warn("$port @ 게임서버 연결에 실패했습니다. ${e.message}")
        }
    }

    override fun onConnected(websocket: WebSocket, headers: MutableMap<String, MutableList<String>>) {
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
        if (closedByServer) logger.info("서버에 의해 $port @ 게임서버#${id} 의 연결이 끊어졌습니다.")
        else logger.info("$port @ 게임서버#${id} 의 연결이 끊어졌습니다.")
    }

    override fun onError(websocket: WebSocket, cause: WebSocketException) {
        logger.error("$port @ 게임서버#${id} 에서 오류가 발생했습니다.", cause)
    }

    override fun onTextMessage(websocket: WebSocket, text: String) {
        val jsonNode = objectMapper.readTree(text)
        val type = jsonNode["type"]?.textValue() ?: return

        if (type == "record-find-game-result" ||
            type == "record-find-user-history-result" ||
            type == "record-find-user-mode-stats-result"
        ) {
            val requestId = jsonNode["requestId"]?.textValue() ?: return
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

    fun isConnected(): Boolean {
        return webSocket != null && webSocket!!.isOpen
    }

    fun requestReplayByGameId(gameId: String, includePayload: Boolean): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "record-find-game")
        payload.put("gameId", gameId)
        payload.put("includePayload", includePayload)
        return requestReply(payload)
    }

    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "record-find-user-history")
        payload.put("userId", userId)
        payload.put("page", page)
        payload.put("pageSize", pageSize)
        return requestReply(payload)
    }

    fun requestReplayUserModeStats(userId: String): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("type", "record-find-user-mode-stats")
        payload.put("userId", userId)
        return requestReply(payload)
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
            logger.warn("$port @ 게임서버#${id} 리플레이 요청 실패: ${e.message}")
            null
        }
    }
}
