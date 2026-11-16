package com.chatdemo.shared

import kotlinx.serialization.Serializable

/**
 * Các model cốt lõi dùng chung giữa Server (Ktor) và Client (Android + Desktop).
 * Bám sát Data Model trong Chương 12 sách "System Design Interview" của Alex Xu.
 */

@Serializable
data class User(
    val id: String,
    val displayName: String,
    val avatarColorHex: Long
)

@Serializable
enum class ChannelType {
    DIRECT, // Chat 1-1
    GROUP   // Chat nhóm (tối đa 100 thành viên theo đề bài)
}

@Serializable
data class GroupChannel(
    val channelId: String,
    val name: String,
    val memberIds: List<String>
)

/**
 * Mô hình dữ liệu tin nhắn:
 * - Với 1-1: tương ứng bảng (message_id, message_from, message_to, content, created_at)
 * - Với Nhóm: dùng khóa ghép (channel_id, message_id) trong KV Store (HBase/Cassandra)
 *
 * Trường [routedViaServer] là thông tin phụ trợ phục vụ demo để người xem thấy rõ
 * tin nhắn đã đi qua Chat Server nào (ví dụ: chat-server-1 -> chat-server-2).
 */
@Serializable
data class ChatMessage(
    val messageId: Long,
    val channelId: String,
    val channelType: ChannelType,
    val messageFrom: String,
    val messageTo: String,
    val content: String,
    val createdAt: Long,
    val routedViaServer: String = ""
)

@Serializable
enum class PresenceStatus {
    ONLINE,
    OFFLINE
}

@Serializable
data class UserPresence(
    val userId: String,
    val status: PresenceStatus,
    val lastSeenAt: Long
)

/**
 * Thông tin một Chat Server đang hoạt động để Service Discovery (vai Zookeeper) chọn.
 */
@Serializable
data class ServerNodeInfo(
    val serverId: String,
    val port: Int,
    val activeConnections: Int
)
