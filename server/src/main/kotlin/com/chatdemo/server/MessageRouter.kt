package com.chatdemo.server

import com.chatdemo.shared.ChannelType
import com.chatdemo.shared.ChatMessage
import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.UserPresence
import com.chatdemo.shared.WsPacket
import java.util.concurrent.ConcurrentHashMap

/**
 * Đại diện cho một Chat Server độc lập (ví dụ cổng 8081 hoặc 8082)
 * giữ các kết nối WebSocket cục bộ của riêng nó.
 */
interface ChatServerNode {
    val serverId: String
    suspend fun deliverToLocalDevice(deviceId: String, packet: WsPacket): Boolean
}

/**
 * Thành phần: Message Sync Queue / Cross-Server Router (Chương 12 - Alex Xu).
 *
 * Giải quyết bài toán cốt lõi:
 * - Alice nối vào chat-server-1 (8081), Bob nối vào chat-server-2 (8082).
 * - Khi Alice gửi tin, Chat Server 1 không giữ socket của Bob nên phải nhờ
 *   [MessageRouter] định tuyến gói tin sang Chat Server 2 để đẩy tới Bob.
 * - Đồng thời hỗ trợ Group Fan-out (nhân bản tin tới các thành viên nhóm)
 *   và Multi-device Sync (đẩy sang các thiết bị khác của chính người gửi).
 */
class MessageRouter(
    private val idGenerator: IdGenerator,
    private val messageStore: MessageStore,
    private val serviceDiscovery: ServiceDiscovery,
    private val presenceManager: PresenceManager,
    private val pushNotifier: PushNotifier,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    data class SessionLocation(
        val serverId: String,
        val deviceId: String
    )

    // Danh bạ các Chat Server đang chạy (8081, 8082)
    private val serverNodes = ConcurrentHashMap<String, ChatServerNode>()

    // Theo dõi từng user đang kết nối ở (serverId, deviceId) nào
    private val userSessions = ConcurrentHashMap<String, MutableSet<SessionLocation>>()

    fun registerServerNode(node: ChatServerNode) {
        serverNodes[node.serverId] = node
    }

    suspend fun onClientAuthenticated(userId: String, deviceId: String, serverId: String) {
        val location = SessionLocation(serverId = serverId, deviceId = deviceId)
        val sessions = userSessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }

        // Nếu cùng thiết bị này kết nối lại sang server khác trước khi timeout, dọn session cũ
        val staleSameDevice = sessions.filter { it.deviceId == deviceId && it.serverId != serverId }
        for (old in staleSameDevice) {
            if (sessions.remove(old)) {
                serviceDiscovery.decrementConnections(old.serverId)
            }
        }

        val isNewDevice = sessions.add(location)
        if (isNewDevice) {
            serviceDiscovery.incrementConnections(serverId)
        }
        println("[DISCOVERY/WS] '$userId' (thiết bị=$deviceId) đã kết nối vào $serverId")

        val presenceChanged = presenceManager.recordHeartbeat(userId)
        if (presenceChanged != null) {
            broadcastPresenceToFriends(presenceChanged)
        }
    }

    suspend fun onClientHeartbeat(userId: String) {
        val presenceChanged = presenceManager.recordHeartbeat(userId)
        if (presenceChanged != null) {
            broadcastPresenceToFriends(presenceChanged)
        }
    }

    suspend fun onClientDisconnected(userId: String, deviceId: String, serverId: String) {
        val sessions = userSessions[userId]
        val removed = sessions?.remove(SessionLocation(serverId = serverId, deviceId = deviceId)) == true
        if (removed) {
            serviceDiscovery.decrementConnections(serverId)
            println("[WS] '$userId' (thiết bị=$deviceId) đã ngắt kết nối khỏi $serverId")
        }

        val remaining = sessions?.size ?: 0
        val presenceChanged = presenceManager.onDeviceDisconnected(userId, remaining)
        if (presenceChanged != null) {
            broadcastPresenceToFriends(presenceChanged)
        }
    }

    /**
     * Xử lý luồng gửi tin nhắn (1-1 hoặc Nhóm) nhận được từ một Chat Server.
     */
    suspend fun handleSendMessage(
        fromUserId: String,
        fromDeviceId: String,
        fromServerId: String,
        request: WsPacket.SendMessage
    ): ChatMessage {
        val messageId = idGenerator.nextId()
        val now = timeProvider()

        val targetUserIds = when (request.channelType) {
            ChannelType.DIRECT -> listOf(request.toId)
            ChannelType.GROUP -> {
                val group = if (request.channelId == DemoCatalog.defaultGroup.channelId) {
                    DemoCatalog.defaultGroup
                } else {
                    null
                }
                group?.memberIds?.filter { it != fromUserId } ?: emptyList()
            }
        }

        // Xác định nhãn đường đi để hiển thị trực quan trên demo
        val targetServers = targetUserIds
            .flatMap { userSessions[it].orEmpty() }
            .map { it.serverId }
            .distinct()

        val routeLabel = when {
            targetServers.isEmpty() -> "$fromServerId -> offline (queued)"
            targetServers.all { it == fromServerId } -> fromServerId
            else -> "$fromServerId -> ${targetServers.joinToString(",")}"
        }

        val chatMessage = ChatMessage(
            messageId = messageId,
            channelId = request.channelId,
            channelType = request.channelType,
            messageFrom = fromUserId,
            messageTo = request.toId,
            content = request.content,
            createdAt = now,
            routedViaServer = routeLabel
        )

        // 1. Lưu tin nhắn vào KV Store trước khi phân phối
        messageStore.saveMessage(chatMessage)
        println("[KV-STORE] Lưu msgId=#$messageId kênh=${chatMessage.channelId} ($routeLabel)")

        // 2. Gửi ACK về chính thiết bị vừa gửi
        serverNodes[fromServerId]?.deliverToLocalDevice(
            fromDeviceId,
            WsPacket.MessageAck(
                clientTempId = request.clientTempId,
                messageId = messageId,
                handledByServer = fromServerId
            )
        )

        val deliverPacket = WsPacket.DeliverMessage(chatMessage)

        // 3. Đồng bộ đa thiết bị: đẩy tin vừa gửi tới các thiết bị KHÁC của chính người gửi
        val senderOtherDevices = userSessions[fromUserId]
            .orEmpty()
            .filter { it.deviceId != fromDeviceId }
        for (loc in senderOtherDevices) {
            println("[SYNC] Đồng bộ tin #$messageId sang thiết bị khác của '$fromUserId' tại ${loc.serverId}")
            serverNodes[loc.serverId]?.deliverToLocalDevice(loc.deviceId, deliverPacket)
        }

        // 4. Đẩy tin tới từng người nhận (1-1 hoặc Group Fan-out)
        for (targetId in targetUserIds) {
            val targetSessions = userSessions[targetId].orEmpty().toList()
            if (targetSessions.isEmpty()) {
                pushNotifier.notifyOfflineUser(targetId, chatMessage)
            } else {
                for (loc in targetSessions) {
                    if (loc.serverId != fromServerId) {
                        println("[ROUTER] Chuyển tin #$messageId từ $fromServerId -> ${loc.serverId} cho '$targetId'")
                    }
                    serverNodes[loc.serverId]?.deliverToLocalDevice(loc.deviceId, deliverPacket)
                }
            }
        }

        return chatMessage
    }

    /**
     * Pub/Sub: Phát thông báo đổi trạng thái Online/Offline tới toàn bộ bạn bè đang kết nối.
     */
    suspend fun broadcastPresenceToFriends(update: UserPresence) {
        val packet = WsPacket.PresenceUpdate(update)
        val friends = DemoCatalog.users.map { it.id }.filter { it != update.userId }
        for (friendId in friends) {
            val sessions = userSessions[friendId].orEmpty().toList()
            for (loc in sessions) {
                serverNodes[loc.serverId]?.deliverToLocalDevice(loc.deviceId, packet)
            }
        }
    }

    /**
     * Trả về các channelId mà user tham gia (dùng cho API /api/sync).
     */
    fun channelsForUser(userId: String): List<String> {
        val dmChannels = DemoCatalog.users
            .filter { it.id != userId }
            .map { friend -> com.chatdemo.shared.ChannelHelper.directChannelId(userId, friend.id) }
        val groupChannels = if (userId in DemoCatalog.defaultGroup.memberIds) {
            listOf(DemoCatalog.defaultGroup.channelId)
        } else {
            emptyList()
        }
        return dmChannels + groupChannels
    }

    /**
     * Ngắt các session đã quá hạn heartbeat ra khỏi danh bạ để tránh rò rỉ kết nối.
     */
    suspend fun evictExpiredPresenceUser(userId: String) {
        val stale = userSessions.remove(userId).orEmpty()
        for (loc in stale) {
            serviceDiscovery.decrementConnections(loc.serverId)
        }
    }
}
