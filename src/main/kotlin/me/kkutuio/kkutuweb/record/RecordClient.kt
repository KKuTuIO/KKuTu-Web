package me.kkutuio.kkutuweb.record

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketAdapter
import com.neovisionaries.ws.client.WebSocketException
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketFrame
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class RecordClient(
    private val isSecure: Boolean,
    private val host: String,
    private val port: Int,
    private val key: String,
    private val id: Short
) : WebSocketAdapter() {
    private val logger = LoggerFactory.getLogger(RecordClient::class.java)
    private val objectMapper = ObjectMapper()
    private val pendingReplayRequests = ConcurrentHashMap<String, CompletableFuture<String>>()
    private var webSocket: WebSocket? = null

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
            logger.warn("$port @ 리플레이 조회용 게임서버 연결 실패: ${e.message}")
        }
    }

    override fun onConnected(websocket: WebSocket, headers: MutableMap<String, MutableList<String>>) {
        logger.info("$port @ 리플레이 조회용 게임서버#${id} 연결됨")
    }

    override fun onDisconnected(
        websocket: WebSocket,
        serverCloseFrame: WebSocketFrame,
        clientCloseFrame: WebSocketFrame,
        closedByServer: Boolean
    ) {
        if (closedByServer) logger.info("$port @ 리플레이 조회용 게임서버#${id} 서버에 의해 연결 종료")
        else logger.info("$port @ 리플레이 조회용 게임서버#${id} 연결 종료")
    }

    override fun onError(websocket: WebSocket, cause: WebSocketException) {
        logger.error("$port @ 리플레이 조회용 게임서버#${id} 오류", cause)
    }

    override fun onTextMessage(websocket: WebSocket, text: String) {
        val jsonNode = objectMapper.readTree(text)
        val type = jsonNode["type"]?.textValue() ?: return
        if (type != "record-find-game-result" && type != "record-find-user-history-result") return

        val requestId = jsonNode["requestId"]?.textValue() ?: return
        val future = pendingReplayRequests.remove(requestId) ?: return
        future.complete(text)
    }

    private fun send(data: String) {
        if (!isConnected()) return
        webSocket!!.sendText(data)
    }

    fun isConnected(): Boolean {
        return webSocket != null && webSocket!!.isOpen
    }

    fun requestReplayByGameId(gameId: String, includePayload: Boolean): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("gameId", gameId)
        payload.put("includePayload", includePayload)
        return requestReplay("record-find-game", payload)
    }

    fun requestReplayUserHistory(userId: String, page: Int, pageSize: Int): String? {
        val payload = objectMapper.createObjectNode()
        payload.put("userId", userId)
        payload.put("page", page)
        payload.put("pageSize", pageSize)
        return requestReplay("record-find-user-history", payload)
    }

    private fun requestReplay(type: String, payload: ObjectNode, timeoutMs: Long = 2500): String? {
        if (!isConnected()) return null
        val requestId = UUID.randomUUID().toString()
        payload.put("type", type)
        payload.put("requestId", requestId)

        val future = CompletableFuture<String>()
        pendingReplayRequests[requestId] = future
        return try {
            send(objectMapper.writeValueAsString(payload))
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            pendingReplayRequests.remove(requestId)
            logger.warn("$port @ 리플레이 요청 실패: ${e.message}")
            null
        }
    }
}
