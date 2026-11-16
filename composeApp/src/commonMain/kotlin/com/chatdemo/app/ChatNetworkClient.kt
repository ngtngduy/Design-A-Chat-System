package com.chatdemo.app

import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.DiscoveryResponse
import com.chatdemo.shared.LoginRequest
import com.chatdemo.shared.LoginResponse
import com.chatdemo.shared.SyncResponse
import com.chatdemo.shared.WsPacket
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Tầng mạng của App Chat (dùng chung cho Android & Desktop).
 * - Gọi HTTP REST tới API Server (cổng 8080): Login, Service Discovery, Sync.
 * - Giữ kết nối WebSocket tới Chat Server (cổng 8081 hoặc 8082) + gửi Heartbeat định kỳ.
 */
class ChatNetworkClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
    }

    private var wsSession: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    suspend fun login(host: String, userId: String): LoginResponse {
        return httpClient.post("http://$host:${DemoCatalog.API_PORT}/api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(userId = userId))
        }.body()
    }

    suspend fun discoverChatServer(host: String): DiscoveryResponse {
        return httpClient.get("http://$host:${DemoCatalog.API_PORT}/api/discovery").body()
    }

    suspend fun syncMessages(host: String, userId: String, afterId: Long): SyncResponse {
        return httpClient.get("http://$host:${DemoCatalog.API_PORT}/api/sync") {
            parameter("userId", userId)
            parameter("afterId", afterId)
        }.body()
    }

    /**
     * Mở kết nối WebSocket tới Chat Server được Service Discovery chỉ định,
     * gửi AuthHello, đồng thời khởi động vòng lặp Heartbeat mỗi 3 giây.
     */
    suspend fun connectWebSocket(
        scope: CoroutineScope,
        host: String,
        port: Int,
        userId: String,
        deviceId: String,
        onPacket: (WsPacket) -> Unit,
        onHeartbeatSent: () -> Unit,
        onDisconnected: (String) -> Unit
    ) {
        disconnectWebSocket()

        val session = httpClient.webSocketSession("ws://$host:$port/ws")
        wsSession = session

        // 1. Gửi gói định danh ngay khi nối thành công
        sendPacket(WsPacket.AuthHello(userId = userId, deviceId = deviceId))

        // 2. Vòng lặp đọc gói tin đẩy xuống từ Chat Server
        readerJob = scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val packet = json.decodeFromString(WsPacket.serializer(), frame.readText())
                        onPacket(packet)
                    }
                }
                onDisconnected("Kết nối WebSocket đã đóng")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onDisconnected(e.message ?: "Mất kết nối WebSocket")
            }
        }

        // 3. Vòng lặp gửi Heartbeat định kỳ cho Presence Server
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(DemoCatalog.HEARTBEAT_INTERVAL_MS)
                try {
                    sendPacket(WsPacket.Heartbeat(userId = userId, sentAt = currentTimeMillis()))
                    onHeartbeatSent()
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    suspend fun sendPacket(packet: WsPacket) {
        val session = wsSession ?: error("Chưa có kết nối WebSocket")
        val raw = json.encodeToString(WsPacket.serializer(), packet)
        session.send(raw)
    }

    suspend fun disconnectWebSocket() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        readerJob?.cancel()
        readerJob = null
        try {
            wsSession?.close()
        } catch (_: Exception) {
        }
        wsSession = null
    }
}
