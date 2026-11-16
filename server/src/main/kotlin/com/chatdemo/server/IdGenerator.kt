package com.chatdemo.server

/**
 * Thành phần: ID Generator (Chương 12 - Alex Xu)
 *
 * Trong hệ thống phân tán, không thể dùng AUTO_INCREMENT của 1 database duy nhất.
 * Thay vào đó cần tạo `message_id` thỏa mãn 2 tính chất:
 * 1. Duy nhất trên toàn hệ thống (Unique).
 * 2. Sắp xếp được theo thời gian (Time-sortable) để sắp xếp thứ tự tin nhắn.
 */
interface IdGenerator {
    fun nextId(): Long
}

/**
 * Cài đặt mô phỏng thuật toán Snowflake (64-bit):
 * [41 bit timestamp tính từ epoch] | [10 bit nodeId] | [12 bit sequence trong cùng 1 ms]
 */
class SnowflakeIdGenerator(
    private val nodeId: Long = 1L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : IdGenerator {

    private val customEpoch = 1700000000000L // Mốc thời gian cố định
    private var lastTimestamp = -1L
    private var sequence = 0L

    @Synchronized
    override fun nextId(): Long {
        var now = timeProvider()
        if (now < lastTimestamp) {
            now = lastTimestamp
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) and 0xFFFL // Tối đa 4096 ID / 1 ms
            if (sequence == 0L) {
                while (now <= lastTimestamp) {
                    now = timeProvider()
                }
            }
        } else {
            sequence = 0L
        }

        lastTimestamp = now
        val timestampPart = (now - customEpoch) shl 22
        val nodePart = (nodeId and 0x3FFL) shl 12
        return timestampPart or nodePart or sequence
    }
}
