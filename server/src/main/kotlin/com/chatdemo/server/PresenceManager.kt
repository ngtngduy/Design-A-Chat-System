package com.chatdemo.server

import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.PresenceStatus
import com.chatdemo.shared.UserPresence
import java.util.concurrent.ConcurrentHashMap

/**
 * Thành phần: Presence Server (Chương 12 - Alex Xu).
 *
 * Cơ chế hoạt động:
 * 1. User kết nối WebSocket / gửi Heartbeat định kỳ -> chuyển sang ONLINE.
 * 2. Khi mất kết nối đột ngột (mất Wi-Fi, tắt app bất ngờ), server không biết ngay.
 *    Sau thời gian [timeoutMs] không thấy Heartbeat mới, Presence Server tự chuyển
 *    user sang OFFLINE.
 * 3. Khi trạng thái thay đổi, dùng mô hình Pub/Sub phát thông báo tới các bạn bè.
 */
class PresenceManager(
    private val timeoutMs: Long = DemoCatalog.HEARTBEAT_TIMEOUT_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    private data class PresenceState(
        var status: PresenceStatus = PresenceStatus.OFFLINE,
        var lastHeartbeatAt: Long = 0L,
        var lastSeenAt: Long = 0L
    )

    private val states = ConcurrentHashMap<String, PresenceState>().apply {
        DemoCatalog.users.forEach { user ->
            put(user.id, PresenceState())
        }
    }

    /**
     * Ghi nhận tín hiệu kết nối hoặc Heartbeat.
     * Trả về [UserPresence] nếu trạng thái vừa chuyển từ OFFLINE -> ONLINE (để phát Pub/Sub).
     */
    @Synchronized
    fun recordHeartbeat(userId: String): UserPresence? {
        val now = timeProvider()
        val state = states.computeIfAbsent(userId) { PresenceState() }
        val wasOffline = state.status == PresenceStatus.OFFLINE
        state.status = PresenceStatus.ONLINE
        state.lastHeartbeatAt = now
        state.lastSeenAt = now

        return if (wasOffline) {
            println("[PRESENCE] '$userId' vừa chuyển sang ONLINE")
            UserPresence(userId = userId, status = PresenceStatus.ONLINE, lastSeenAt = now)
        } else {
            null
        }
    }

    /**
     * Xử lý khi một thiết bị đóng WebSocket chủ động.
     * Chỉ chuyển sang OFFLINE nếu user không còn thiết bị nào khác đang kết nối.
     */
    @Synchronized
    fun onDeviceDisconnected(userId: String, remainingSessions: Int): UserPresence? {
        if (remainingSessions > 0) return null
        val now = timeProvider()
        val state = states[userId] ?: return null
        if (state.status == PresenceStatus.OFFLINE) return null

        state.status = PresenceStatus.OFFLINE
        state.lastSeenAt = now
        println("[PRESENCE] '$userId' đã ngắt hết thiết bị -> OFFLINE")
        return UserPresence(userId = userId, status = PresenceStatus.OFFLINE, lastSeenAt = now)
    }

    /**
     * Quét định kỳ tìm các user quá hạn Heartbeat (mất mạng đột ngột).
     * Trả về danh sách các user vừa chuyển sang OFFLINE để phát Pub/Sub.
     */
    @Synchronized
    fun checkTimeouts(): List<UserPresence> {
        val now = timeProvider()
        val expired = mutableListOf<UserPresence>()

        states.forEach { (userId, state) ->
            if (state.status == PresenceStatus.ONLINE && (now - state.lastHeartbeatAt) > timeoutMs) {
                state.status = PresenceStatus.OFFLINE
                state.lastSeenAt = state.lastHeartbeatAt
                val update = UserPresence(
                    userId = userId,
                    status = PresenceStatus.OFFLINE,
                    lastSeenAt = state.lastSeenAt
                )
                expired.add(update)
                println("[PRESENCE] Heartbeat timeout (${timeoutMs / 1000}s) cho '$userId' -> chuyển sang OFFLINE")
            }
        }
        return expired
    }

    @Synchronized
    fun getAllPresences(): List<UserPresence> {
        return states.entries
            .sortedBy { it.key }
            .map { (userId, state) ->
                UserPresence(
                    userId = userId,
                    status = state.status,
                    lastSeenAt = state.lastSeenAt
                )
            }
    }
}
