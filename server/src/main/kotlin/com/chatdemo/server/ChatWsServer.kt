package com.chatdemo.server

import com.chatdemo.shared.WsPacket
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.*
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Thành phần: Stateful Chat Server (chạy 2 instance ở cổng 8081 và 8082).
 *
 * Mỗi Chat Server giữ danh sách kết nối WebSocket cục bộ của riêng mình,
 * đăng ký với [MessageRouter] để nhận tin nhắn định tuyến từ server khác.
 */
fun Application.configureChatWsServer(
    serverId: String,
    port: Int,
    cluster: ChatServerCluster
) {
    val json = Json { ignoreUnknownKeys = true }
    val localDevices = ConcurrentHashMap<String, DefaultWebSocketSession>()

    val node = object : ChatServerNode {
        override val serverId: String = serverId

        override suspend fun deliverToLocalDevice(deviceId: String, packet: WsPacket): Boolean {
            val session = localDevices[deviceId] ?: return false
            return try {
                val payload = json.encodeToString(WsPacket.serializer(), packet)
                session.send(payload)
                true
            } catch (e: Exception) {
                localDevices.remove(deviceId)
                false
            }
        }
    }

    cluster.messageRouter.registerServerNode(node)

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/ws") {
            var currentUserId: String? = null
            var currentDeviceId: String? = null

            try {
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val raw = frame.readText()
                    val packet = json.decodeFromString(WsPacket.serializer(), raw)

                    when (packet) {
                        is WsPacket.AuthHello -> {
                            currentUserId = packet.userId
                            currentDeviceId = packet.deviceId
                            localDevices[packet.deviceId] = this

                            cluster.messageRouter.onClientAuthenticated(
                                userId = packet.userId,
                                deviceId = packet.deviceId,
                                serverId = serverId
                            )

                            node.deliverToLocalDevice(
                                packet.deviceId,
                                WsPacket.ServerNotice(
                                    serverId = serverId,
                                    info = "Đã kết nối thành công tới $serverId (cổng $port)"
                                )
                            )
                        }

                        is WsPacket.SendMessage -> {
                            val uid = currentUserId ?: continue
                            val did = currentDeviceId ?: continue
                            cluster.messageRouter.handleSendMessage(
                                fromUserId = uid,
                                fromDeviceId = did,
                                fromServerId = serverId,
                                request = packet
                            )
                        }

                        is WsPacket.Heartbeat -> {
                            cluster.messageRouter.onClientHeartbeat(packet.userId)
                        }

                        else -> {
                            // Các gói tin do server gửi xuống client không xử lý ở chiều incoming
                        }
                    }
                }
            } finally {
                val uid = currentUserId
                val did = currentDeviceId
                if (uid != null && did != null) {
                    localDevices.remove(did)
                    cluster.messageRouter.onClientDisconnected(
                        userId = uid,
                        deviceId = did,
                        serverId = serverId
                    )
                }
            }
        }
    }
}
