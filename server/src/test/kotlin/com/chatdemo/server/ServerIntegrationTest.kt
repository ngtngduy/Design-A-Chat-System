package com.chatdemo.server

import com.chatdemo.shared.ChannelHelper
import com.chatdemo.shared.ChannelType
import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.DiscoveryResponse
import com.chatdemo.shared.LoginRequest
import com.chatdemo.shared.LoginResponse
import com.chatdemo.shared.PresenceStatus
import com.chatdemo.shared.SyncResponse
import com.chatdemo.shared.WsPacket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private class FakeNode(override val serverId: String) : ChatServerNode {
        val delivered = mutableListOf<Pair<String, WsPacket>>()

        override suspend fun deliverToLocalDevice(deviceId: String, packet: WsPacket): Boolean {
            delivered.add(deviceId to packet)
            return true
        }
    }

    @Test
    fun snowflakeIdsAreUniqueAndSorted() {
        val gen = SnowflakeIdGenerator(nodeId = 1L)
        val ids = (1..200).map { gen.nextId() }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids, ids.sorted())
    }

    @Test
    fun discoveryBalancesBetweenTwoChatServers() = runBlocking {
        val cluster = ChatServerCluster()
        cluster.messageRouter.registerServerNode(FakeNode("chat-server-1"))
        cluster.messageRouter.registerServerNode(FakeNode("chat-server-2"))

        val firstPick = cluster.serviceDiscovery.pickBestServer()
        assertEquals("chat-server-1", firstPick.serverId)

        cluster.messageRouter.onClientAuthenticated("alice", "alice-dev-1", firstPick.serverId)

        val secondPick = cluster.serviceDiscovery.pickBestServer()
        assertEquals("chat-server-2", secondPick.serverId)
    }

    @Test
    fun crossServerDirectMessageAndMultiDeviceSyncWork() = runBlocking {
        val cluster = ChatServerCluster()
        val node1 = FakeNode("chat-server-1")
        val node2 = FakeNode("chat-server-2")
        cluster.messageRouter.registerServerNode(node1)
        cluster.messageRouter.registerServerNode(node2)

        // Alice nối vào server 1 (2 thiết bị), Bob nối vào server 2
        cluster.messageRouter.onClientAuthenticated("alice", "alice-phone", "chat-server-1")
        cluster.messageRouter.onClientAuthenticated("alice", "alice-desktop", "chat-server-1")
        cluster.messageRouter.onClientAuthenticated("bob", "bob-desktop", "chat-server-2")

        val dmChannel = ChannelHelper.directChannelId("alice", "bob")
        val saved = cluster.messageRouter.handleSendMessage(
            fromUserId = "alice",
            fromDeviceId = "alice-phone",
            fromServerId = "chat-server-1",
            request = WsPacket.SendMessage(
                clientTempId = "tmp-1",
                channelId = dmChannel,
                channelType = ChannelType.DIRECT,
                toId = "bob",
                content = "Chào Bob từ server 1!"
            )
        )

        assertEquals("chat-server-1 -> chat-server-2", saved.routedViaServer)

        // Alice phone nhận ACK
        assertTrue(node1.delivered.any { (dev, pkt) ->
            dev == "alice-phone" && pkt is WsPacket.MessageAck && pkt.messageId == saved.messageId
        })

        // Alice desktop nhận bản đồng bộ ngay lập tức
        assertTrue(node1.delivered.any { (dev, pkt) ->
            dev == "alice-desktop" && pkt is WsPacket.DeliverMessage && pkt.message.messageId == saved.messageId
        })

        // Bob nhận qua server 2
        assertTrue(node2.delivered.any { (dev, pkt) ->
            dev == "bob-desktop" && pkt is WsPacket.DeliverMessage && pkt.message.content == "Chào Bob từ server 1!"
        })
    }

    @Test
    fun groupFanoutDeliversToOnlineAndPushesToOffline() = runBlocking {
        val cluster = ChatServerCluster()
        val node1 = FakeNode("chat-server-1")
        val node2 = FakeNode("chat-server-2")
        cluster.messageRouter.registerServerNode(node1)
        cluster.messageRouter.registerServerNode(node2)

        // Alice & Bob online; Charlie offline
        cluster.messageRouter.onClientAuthenticated("alice", "alice-1", "chat-server-1")
        cluster.messageRouter.onClientAuthenticated("bob", "bob-1", "chat-server-2")

        val msg = cluster.messageRouter.handleSendMessage(
            fromUserId = "alice",
            fromDeviceId = "alice-1",
            fromServerId = "chat-server-1",
            request = WsPacket.SendMessage(
                clientTempId = "grp-1",
                channelId = DemoCatalog.defaultGroup.channelId,
                channelType = ChannelType.GROUP,
                toId = DemoCatalog.defaultGroup.channelId,
                content = "Họp nhóm 15h nhé!"
            )
        )

        // Bob nhận online
        assertTrue(node2.delivered.any { (dev, pkt) ->
            dev == "bob-1" && pkt is WsPacket.DeliverMessage && pkt.message.messageId == msg.messageId
        })
        // Charlie offline nên nhận Push Notification giả lập
        assertTrue(cluster.pushNotifier.recentLogs().any { it.contains("charlie") })
    }

    @Test
    fun presenceTimeoutMarksUserOfflineAndNotifiesFriends() = runBlocking {
        var fakeTime = 1_000L
        val presenceManager = PresenceManager(timeoutMs = 10_000L, timeProvider = { fakeTime })
        val cluster = ChatServerCluster(presenceManager = presenceManager)
        val node1 = FakeNode("chat-server-1")
        val node2 = FakeNode("chat-server-2")
        cluster.messageRouter.registerServerNode(node1)
        cluster.messageRouter.registerServerNode(node2)

        cluster.messageRouter.onClientAuthenticated("alice", "alice-1", "chat-server-1")
        cluster.messageRouter.onClientAuthenticated("bob", "bob-1", "chat-server-2")
        node1.delivered.clear()

        // Giả lập Bob mất mạng quá 10 giây (không gửi heartbeat, nhưng Alice vẫn gửi heartbeat)
        fakeTime = 12_000L
        cluster.messageRouter.onClientHeartbeat("alice")
        cluster.runPresenceTimeoutSweep()

        val bobPresence = cluster.presenceManager.getAllPresences().first { it.userId == "bob" }
        assertEquals(PresenceStatus.OFFLINE, bobPresence.status)
        assertTrue(node1.delivered.any { (dev, pkt) ->
            dev == "alice-1" && pkt is WsPacket.PresenceUpdate &&
                pkt.presence.userId == "bob" && pkt.presence.status == PresenceStatus.OFFLINE
        })
    }

    @Test
    fun apiEndpointsLoginDiscoveryAndSyncWork() = testApplication {
        val cluster = ChatServerCluster()
        cluster.messageRouter.registerServerNode(FakeNode("chat-server-1"))
        cluster.messageRouter.registerServerNode(FakeNode("chat-server-2"))

        application {
            configureApiServer(cluster)
        }

        val loginRes = client.post("/api/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest.serializer(), LoginRequest("alice")))
        }
        assertEquals(HttpStatusCode.OK, loginRes.status)
        val loginBody = json.decodeFromString(LoginResponse.serializer(), loginRes.bodyAsText())
        assertEquals("alice", loginBody.user.id)
        assertEquals(2, loginBody.friends.size)

        val discRes = client.get("/api/discovery")
        assertEquals(HttpStatusCode.OK, discRes.status)
        val discBody = json.decodeFromString(DiscoveryResponse.serializer(), discRes.bodyAsText())
        assertEquals("chat-server-1", discBody.recommendedServer.serverId)

        // Lưu 1 tin rồi kiểm tra /api/sync
        cluster.messageRouter.handleSendMessage(
            fromUserId = "bob",
            fromDeviceId = "bob-1",
            fromServerId = "chat-server-2",
            request = WsPacket.SendMessage(
                clientTempId = "t-1",
                channelId = ChannelHelper.directChannelId("alice", "bob"),
                channelType = ChannelType.DIRECT,
                toId = "alice",
                content = "Tin nhắn lúc Alice offline"
            )
        )

        val syncRes = client.get("/api/sync?userId=alice&afterId=0")
        assertEquals(HttpStatusCode.OK, syncRes.status)
        val syncBody = json.decodeFromString(SyncResponse.serializer(), syncRes.bodyAsText())
        assertEquals(1, syncBody.messages.size)
        assertEquals("Tin nhắn lúc Alice offline", syncBody.messages.first().content)
    }
}
