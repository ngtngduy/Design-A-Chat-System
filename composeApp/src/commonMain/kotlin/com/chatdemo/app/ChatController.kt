package com.chatdemo.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chatdemo.shared.ChannelHelper
import com.chatdemo.shared.ChannelType
import com.chatdemo.shared.ChatMessage
import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.GroupChannel
import com.chatdemo.shared.PresenceStatus
import com.chatdemo.shared.ServerNodeInfo
import com.chatdemo.shared.User
import com.chatdemo.shared.UserPresence
import com.chatdemo.shared.WsPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SIMULATED_OFFLINE
}

sealed interface ActiveConversation {
    data class Direct(val friend: User) : ActiveConversation {
        val channelIdFor: (String) -> String = { myId ->
            ChannelHelper.directChannelId(myId, friend.id)
        }
    }

    data class Group(val group: GroupChannel) : ActiveConversation
}

/**
 * Bộ điều phối trạng thái toàn bộ App Chat (dùng chung cho Android & Desktop).
 * Quản lý: Đăng nhập, Service Discovery, gửi/nhận tin real-time, đồng bộ `cur_max_message_id`,
 * và nút "Giả lập mất mạng" để demo Presence Timeout.
 */
class ChatController(
    defaultHost: String,
    platformLabel: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val networkClient = ChatNetworkClient()

    val deviceId: String = "$platformLabel-${Random.nextInt(1000, 9999)}"

    var hostInput by mutableStateOf(defaultHost)
    var currentUser by mutableStateOf<User?>(null)
        private set
    var friends by mutableStateOf<List<User>>(emptyList())
        private set
    var groups by mutableStateOf<List<GroupChannel>>(emptyList())
        private set
    var presences by mutableStateOf<Map<String, UserPresence>>(emptyMap())
        private set
    var activeConversation by mutableStateOf<ActiveConversation?>(null)
        private set

    var messagesByChannel by mutableStateOf<Map<String, List<ChatMessage>>>(emptyMap())
        private set

    /** Con trỏ đồng bộ đa thiết bị (mỗi thiết bị tự giữ riêng theo sách Alex Xu). */
    var curMaxMessageId by mutableStateOf(0L)
        private set

    var connectedServer by mutableStateOf<ServerNodeInfo?>(null)
        private set
    var allClusterServers by mutableStateOf<List<ServerNodeInfo>>(emptyList())
        private set
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set
    var heartbeatCount by mutableStateOf(0)
        private set
    var statusNotice by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isBusy by mutableStateOf(false)
        private set

    // Lưu tạm tin nhắn vừa bấm gửi trong lúc chờ Server cấp #messageId qua MessageAck
    private val pendingOutgoing = mutableMapOf<String, ChatMessage>()
    private var nextTempMessageId = -1L

    fun loginAndConnect(userId: String) {
        val host = hostInput.trim().ifBlank { "127.0.0.1" }
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val loginRes = networkClient.login(host, userId)
                currentUser = loginRes.user
                friends = loginRes.friends
                groups = loginRes.groups
                presences = loginRes.presences.associateBy { it.userId }
                messagesByChannel = emptyMap()
                curMaxMessageId = 0L
                heartbeatCount = 0

                connectAndSyncInternal(host, loginRes.user)
            } catch (e: Exception) {
                errorMessage = "Không kết nối được tới server ($host:${DemoCatalog.API_PORT}): ${e.message}"
                connectionState = ConnectionState.DISCONNECTED
            } finally {
                isBusy = false
            }
        }
    }

    fun reconnectAfterOutage() {
        val user = currentUser ?: return
        val host = hostInput.trim().ifBlank { "127.0.0.1" }
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                // Cập nhật lại trạng thái bạn bè mới nhất trước khi sync
                val loginRes = networkClient.login(host, user.id)
                presences = loginRes.presences.associateBy { it.userId }
                connectAndSyncInternal(host, user)
            } catch (e: Exception) {
                errorMessage = "Kết nối lại thất bại: ${e.message}"
                connectionState = ConnectionState.SIMULATED_OFFLINE
            } finally {
                isBusy = false
            }
        }
    }

    private suspend fun connectAndSyncInternal(host: String, user: User) {
        connectionState = ConnectionState.CONNECTING

        // Bước 1: Hỏi Service Discovery (cổng 8080) để biết nên vào Chat Server 8081 hay 8082
        val discovery = networkClient.discoverChatServer(host)
        connectedServer = discovery.recommendedServer
        allClusterServers = discovery.allServers

        // Bước 2: Mở WebSocket tới Chat Server được chỉ định
        networkClient.connectWebSocket(
            scope = scope,
            host = host,
            port = discovery.recommendedServer.port,
            userId = user.id,
            deviceId = deviceId,
            onPacket = { packet -> handleIncomingPacket(packet) },
            onHeartbeatSent = { heartbeatCount += 1 },
            onDisconnected = { reason ->
                if (connectionState == ConnectionState.CONNECTED) {
                    connectionState = ConnectionState.SIMULATED_OFFLINE
                    statusNotice = reason
                }
            }
        )
        connectionState = ConnectionState.CONNECTED
        statusNotice = "Đã nối ${discovery.recommendedServer.serverId} (:${discovery.recommendedServer.port})"

        // Bước 3: Đồng bộ các tin nhắn bị lỡ có messageId > curMaxMessageId
        val syncRes = networkClient.syncMessages(host, user.id, curMaxMessageId)
        if (syncRes.messages.isNotEmpty()) {
            mergeMessages(syncRes.messages)
            statusNotice = "Đã đồng bộ ${syncRes.messages.size} tin mới (sau #$curMaxMessageId)"
        }
        if (syncRes.newMaxMessageId > curMaxMessageId) {
            curMaxMessageId = syncRes.newMaxMessageId
        }
    }

    fun simulateNetworkOutage() {
        scope.launch {
            connectionState = ConnectionState.SIMULATED_OFFLINE
            connectedServer = null
            statusNotice = "Đã ngắt mạng & dừng Heartbeat (sau 10s server sẽ báo Offline)"
            networkClient.disconnectWebSocket()
        }
    }

    fun openDirectChat(friend: User) {
        activeConversation = ActiveConversation.Direct(friend)
    }

    fun openGroupChat(group: GroupChannel) {
        activeConversation = ActiveConversation.Group(group)
    }

    fun backToConversationList() {
        activeConversation = null
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val user = currentUser ?: return
        val conv = activeConversation ?: return

        if (connectionState != ConnectionState.CONNECTED) {
            errorMessage = "Đang mất mạng — hãy bấm 'Kết nối lại' để gửi và đồng bộ tin nhắn."
            return
        }

        val (channelId, channelType, toId) = when (conv) {
            is ActiveConversation.Direct -> Triple(
                ChannelHelper.directChannelId(user.id, conv.friend.id),
                ChannelType.DIRECT,
                conv.friend.id
            )
            is ActiveConversation.Group -> Triple(
                conv.group.channelId,
                ChannelType.GROUP,
                conv.group.channelId
            )
        }

        val tempId = "tmp-${currentTimeMillis()}-${Random.nextInt(100, 999)}"
        val optimisticId = nextTempMessageId--
        val optimisticMsg = ChatMessage(
            messageId = optimisticId,
            channelId = channelId,
            channelType = channelType,
            messageFrom = user.id,
            messageTo = toId,
            content = trimmed,
            createdAt = currentTimeMillis(),
            routedViaServer = "Đang gửi..."
        )
        pendingOutgoing[tempId] = optimisticMsg
        addOrReplaceMessage(optimisticMsg)

        scope.launch {
            try {
                networkClient.sendPacket(
                    WsPacket.SendMessage(
                        clientTempId = tempId,
                        channelId = channelId,
                        channelType = channelType,
                        toId = toId,
                        content = trimmed
                    )
                )
            } catch (e: Exception) {
                errorMessage = "Gửi tin thất bại: ${e.message}"
            }
        }
    }

    fun logout() {
        scope.launch {
            networkClient.disconnectWebSocket()
            currentUser = null
            activeConversation = null
            friends = emptyList()
            groups = emptyList()
            presences = emptyMap()
            messagesByChannel = emptyMap()
            pendingOutgoing.clear()
            curMaxMessageId = 0L
            connectedServer = null
            heartbeatCount = 0
            connectionState = ConnectionState.DISCONNECTED
            statusNotice = ""
            errorMessage = null
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun handleIncomingPacket(packet: WsPacket) {
        when (packet) {
            is WsPacket.ServerNotice -> {
                statusNotice = packet.info
            }

            is WsPacket.MessageAck -> {
                val temp = pendingOutgoing.remove(packet.clientTempId)
                if (temp != null) {
                    // Thay thế tin nhắn tạm bằng messageId thật từ ID Generator
                    removeTempMessage(temp.channelId, temp.messageId)
                    val confirmed = temp.copy(
                        messageId = packet.messageId,
                        routedViaServer = "Đã lưu bởi ${packet.handledByServer}"
                    )
                    addOrReplaceMessage(confirmed)
                }
                if (packet.messageId > curMaxMessageId) {
                    curMaxMessageId = packet.messageId
                }
            }

            is WsPacket.DeliverMessage -> {
                addOrReplaceMessage(packet.message)
                if (packet.message.messageId > curMaxMessageId) {
                    curMaxMessageId = packet.message.messageId
                }
            }

            is WsPacket.PresenceUpdate -> {
                presences = presences + (packet.presence.userId to packet.presence)
            }

            else -> Unit
        }
    }

    private fun mergeMessages(incoming: List<ChatMessage>) {
        incoming.forEach { addOrReplaceMessage(it) }
    }

    private fun removeTempMessage(channelId: String, tempMessageId: Long) {
        val current = messagesByChannel[channelId].orEmpty()
        messagesByChannel = messagesByChannel + (channelId to current.filterNot { it.messageId == tempMessageId })
    }

    private fun addOrReplaceMessage(message: ChatMessage) {
        val current = messagesByChannel[message.channelId].orEmpty()
            .filterNot { it.messageId == message.messageId }
        val updated = (current + message).sortedWith(
            compareBy<ChatMessage> { it.messageId < 0 }.thenBy { it.messageId }
        )
        messagesByChannel = messagesByChannel + (message.channelId to updated)
    }

    fun messagesForActiveConversation(): List<ChatMessage> {
        val user = currentUser ?: return emptyList()
        val conv = activeConversation ?: return emptyList()
        val channelId = when (conv) {
            is ActiveConversation.Direct -> ChannelHelper.directChannelId(user.id, conv.friend.id)
            is ActiveConversation.Group -> conv.group.channelId
        }
        return messagesByChannel[channelId].orEmpty()
    }

    fun isUserOnline(userId: String): Boolean {
        return presences[userId]?.status == PresenceStatus.ONLINE
    }

    fun windowTitle(): String {
        val user = currentUser
        val srv = connectedServer
        return when {
            user != null && srv != null -> "Chat Demo — ${user.displayName} (${srv.serverId}:${srv.port})"
            user != null -> "Chat Demo — ${user.displayName} (Offline)"
            else -> "Chat System Demo ($deviceId)"
        }
    }
}
