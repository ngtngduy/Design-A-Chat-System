package com.chatdemo.server

import com.chatdemo.shared.ChatMessage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thành phần: Third-Party Integration — Push Notification (APNS / FCM).
 *
 * Khi người nhận đang Offline (không có kết nối WebSocket ở cả 2 Chat Server),
 * hệ thống sẽ đẩy thông báo tới thiết bị của họ qua APNS (iOS) hoặc FCM (Android).
 */
interface PushNotifier {
    fun notifyOfflineUser(targetUserId: String, message: ChatMessage)
    fun recentLogs(): List<String>
}

class ConsolePushNotifier : PushNotifier {
    private val logs = CopyOnWriteArrayList<String>()

    override fun notifyOfflineUser(targetUserId: String, message: ChatMessage) {
        val preview = if (message.content.length > 40) {
            message.content.take(40) + "..."
        } else {
            message.content
        }
        val entry = "[PUSH -> APNS/FCM] Gửi thông báo tới '$targetUserId' " +
            "(vì đang OFFLINE): từ ${message.messageFrom}: \"$preview\" (msgId=#${message.messageId})"
        logs.add(entry)
        println(entry)
    }

    override fun recentLogs(): List<String> = logs.toList()
}
