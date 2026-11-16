package com.chatdemo.server

/**
 * Gom toàn bộ các thành phần kiến trúc backend vào một cụm thống nhất để dễ khởi chạy
 * ở [Application.kt] và dễ kiểm thử tích hợp trong [ServerIntegrationTest].
 */
class ChatServerCluster(
    val idGenerator: IdGenerator = SnowflakeIdGenerator(),
    val messageStore: MessageStore = InMemoryKvMessageStore(),
    val serviceDiscovery: ServiceDiscovery = InMemoryServiceDiscovery(),
    val presenceManager: PresenceManager = PresenceManager(),
    val pushNotifier: PushNotifier = ConsolePushNotifier()
) {
    val messageRouter: MessageRouter = MessageRouter(
        idGenerator = idGenerator,
        messageStore = messageStore,
        serviceDiscovery = serviceDiscovery,
        presenceManager = presenceManager,
        pushNotifier = pushNotifier
    )

    /**
     * Kiểm tra định kỳ các kết nối quá hạn Heartbeat và phát Pub/Sub tới bạn bè.
     */
    suspend fun runPresenceTimeoutSweep() {
        val expiredUsers = presenceManager.checkTimeouts()
        for (update in expiredUsers) {
            messageRouter.evictExpiredPresenceUser(update.userId)
            messageRouter.broadcastPresenceToFriends(update)
        }
    }
}
