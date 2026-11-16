package com.chatdemo.shared

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChannelHelperTest {
    @Test
    fun directChannelIdIsSymmetric() {
        assertEquals("dm:alice:bob", ChannelHelper.directChannelId("alice", "bob"))
        assertEquals("dm:alice:bob", ChannelHelper.directChannelId("bob", "alice"))
    }

    @Test
    fun wsPacketRoundTripSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val packet: WsPacket = WsPacket.SendMessage(
            clientTempId = "tmp-1",
            channelId = "dm:alice:bob",
            channelType = ChannelType.DIRECT,
            toId = "bob",
            content = "Xin chào Bob!"
        )
        val encoded = json.encodeToString(WsPacket.serializer(), packet)
        val decoded = json.decodeFromString(WsPacket.serializer(), encoded)
        assertEquals(packet, decoded)
    }
}
