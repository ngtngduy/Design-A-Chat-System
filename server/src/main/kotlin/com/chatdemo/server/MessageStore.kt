package com.chatdemo.server

import com.chatdemo.shared.ChatMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Thành phần: Key-Value Store (tương ứng HBase / Cassandra trong sách Alex Xu).
 *
 * Lý do sách chọn Key-Value Store thay vì RDBMS để lưu tin nhắn:
 * - Tỉ lệ ghi và đọc tin nhắn gần nhất cực kỳ lớn.
 * - Dễ mở rộng ngang (sharding) theo `channel_id`.
 * - Hỗ trợ truy vấn phạm vi cực nhanh theo cặp khóa ghép `(channel_id, message_id)`.
 */
interface MessageStore {
    fun saveMessage(message: ChatMessage)
    fun getMessagesAfter(channelIds: Collection<String>, afterId: Long): List<ChatMessage>
    fun getChannelMessages(channelId: String, limit: Int = 50): List<ChatMessage>
    fun getMaxMessageId(): Long
}

/**
 * Bản cài đặt in-memory dùng ConcurrentSkipListMap để giữ tin nhắn luôn được sắp xếp
 * theo `messageId` trong từng `channelId`.
 * Bản production chỉ cần viết một class khác implement [MessageStore] nối tới Cassandra/HBase.
 */
class InMemoryKvMessageStore : MessageStore {

    // Cấu trúc: channelId -> (messageId -> ChatMessage)
    private val partitions = ConcurrentHashMap<String, ConcurrentSkipListMap<Long, ChatMessage>>()

    override fun saveMessage(message: ChatMessage) {
        val partition = partitions.computeIfAbsent(message.channelId) { ConcurrentSkipListMap() }
        partition[message.messageId] = message
    }

    override fun getMessagesAfter(channelIds: Collection<String>, afterId: Long): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        for (channelId in channelIds) {
            val partition = partitions[channelId] ?: continue
            // tailMap(afterId, inclusive = false) trả về toàn bộ tin có messageId > afterId
            result.addAll(partition.tailMap(afterId, false).values)
        }
        return result.sortedBy { it.messageId }
    }

    override fun getChannelMessages(channelId: String, limit: Int): List<ChatMessage> {
        val partition = partitions[channelId] ?: return emptyList()
        val all = partition.values.toList()
        return if (all.size <= limit) all else all.takeLast(limit)
    }

    override fun getMaxMessageId(): Long {
        return partitions.values.maxOfOrNull { it.lastEntry()?.key ?: 0L } ?: 0L
    }
}
