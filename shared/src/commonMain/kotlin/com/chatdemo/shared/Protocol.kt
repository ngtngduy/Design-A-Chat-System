package com.chatdemo.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Giao thức trao đổi dữ liệu giữa Client và Server:
 * 1. HTTP REST (cổng 8080): Login, Service Discovery, lấy danh sách bạn bè/nhóm, Sync tin nhắn.
 * 2. WebSocket (cổng 8081 & 8082): Nhắn tin real-time, Heartbeat, Presence Pub/Sub.
 */

// ==================== HTTP REST DTOs ====================

@Serializable
data class LoginRequest(
    val userId: String
)

@Serializable
data class LoginResponse(
    val user: User,
    val friends: List<User>,
    val groups: List<GroupChannel>,
    val presences: List<UserPresence>
)

@Serializable
data class DiscoveryResponse(
    val recommendedServer: ServerNodeInfo,
    val allServers: List<ServerNodeInfo>
)

@Serializable
data class SyncResponse(
    val userId: String,
    val requestedAfterId: Long,
    val newMaxMessageId: Long,
    val messages: List<ChatMessage>
)

// ==================== WebSocket Packets ====================

/**
 * Mọi gói tin gửi/nhận qua đường dây WebSocket đều bọc trong [WsPacket].
 */
@Serializable
sealed interface WsPacket {

    /** Client tự giới thiệu danh tính & thiết bị ngay khi mở kết nối WebSocket. */
    @Serializable
    @SerialName("auth_hello")
    data class AuthHello(
        val userId: String,
        val deviceId: String
    ) : WsPacket

    /** Client gửi tin nhắn mới (1-1 hoặc nhóm) kèm [clientTempId] để nhận ACK. */
    @Serializable
    @SerialName("send_message")
    data class SendMessage(
        val clientTempId: String,
        val channelId: String,
        val channelType: ChannelType,
        val toId: String,
        val content: String
    ) : WsPacket

    /** Server xác nhận đã cấp [messageId] và lưu tin nhắn vào KV Store. */
    @Serializable
    @SerialName("message_ack")
    data class MessageAck(
        val clientTempId: String,
        val messageId: Long,
        val handledByServer: String
    ) : WsPacket

    /** Server đẩy tin nhắn mới tới người nhận hoặc tới các thiết bị khác của người gửi. */
    @Serializable
    @SerialName("deliver_message")
    data class DeliverMessage(
        val message: ChatMessage
    ) : WsPacket

    /**
     * Tín hiệu "nhịp tim" (Heartbeat) client gửi định kỳ lên Presence Server.
     * Nếu quá thời gian timeout mà server không nhận được, user chuyển sang OFFLINE.
     */
    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        val userId: String,
        val sentAt: Long
    ) : WsPacket

    /** Server Pub/Sub phát thông báo thay đổi trạng thái Online/Offline tới bạn bè. */
    @Serializable
    @SerialName("presence_update")
    data class PresenceUpdate(
        val presence: UserPresence
    ) : WsPacket

    /** Thông báo trạng thái từ Chat Server (hiện lên thanh debug của app). */
    @Serializable
    @SerialName("server_notice")
    data class ServerNotice(
        val serverId: String,
        val info: String
    ) : WsPacket
}
