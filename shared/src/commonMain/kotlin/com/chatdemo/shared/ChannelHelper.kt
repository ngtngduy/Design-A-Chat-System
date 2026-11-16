package com.chatdemo.shared

/**
 * Tiện ích tạo mã kênh chat và dữ liệu mẫu dùng chung cho cả Server và Client.
 */
object ChannelHelper {
    /**
     * Tạo ID kênh chat 1-1 cố định giữa 2 user bất kể ai nhắn trước.
     * Ví dụ: directChannelId("bob", "alice") == "dm:alice:bob"
     */
    fun directChannelId(userA: String, userB: String): String {
        val sorted = listOf(userA, userB).sorted()
        return "dm:${sorted[0]}:${sorted[1]}"
    }
}

/**
 * Dữ liệu người dùng và nhóm mẫu cho buổi demo (Alice, Bob, Charlie).
 */
object DemoCatalog {
    val users = listOf(
        User(id = "alice", displayName = "Alice", avatarColorHex = 0xFF2563EB),
        User(id = "bob", displayName = "Bob", avatarColorHex = 0xFF059669),
        User(id = "charlie", displayName = "Charlie", avatarColorHex = 0xFFD97706)
    )

    val defaultGroup = GroupChannel(
        channelId = "group:mobile-team",
        name = "Nhóm KMP & System Design",
        memberIds = listOf("alice", "bob", "charlie")
    )

    const val API_PORT = 8080
    const val CHAT_SERVER_1_PORT = 8081
    const val CHAT_SERVER_2_PORT = 8082

    // Trong sách Alex Xu là Heartbeat 5s / Timeout 30s.
    // Bản demo rút ngắn xuống 3s / 10s để lúc thuyết trình thấy đổi màu trạng thái nhanh hơn.
    const val HEARTBEAT_INTERVAL_MS = 3_000L
    const val HEARTBEAT_TIMEOUT_MS = 10_000L
}
