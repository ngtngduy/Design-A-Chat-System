package com.chatdemo.server

import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.ServerNodeInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thành phần: Service Discovery (tương ứng Apache Zookeeper trong sách Alex Xu).
 *
 * Nhiệm vụ: Khi client đăng nhập xong và cần mở WebSocket tới một Chat Server,
 * Service Discovery sẽ chọn ra Chat Server tối ưu nhất (ở đây là server có ít
 * kết nối đang hoạt động nhất) để cân bằng tải giữa 2 cổng 8081 và 8082.
 */
interface ServiceDiscovery {
    fun pickBestServer(): ServerNodeInfo
    fun listServers(): List<ServerNodeInfo>
    fun incrementConnections(serverId: String)
    fun decrementConnections(serverId: String)
}

class InMemoryServiceDiscovery(
    initialNodes: List<Pair<String, Int>> = listOf(
        "chat-server-1" to DemoCatalog.CHAT_SERVER_1_PORT,
        "chat-server-2" to DemoCatalog.CHAT_SERVER_2_PORT
    )
) : ServiceDiscovery {

    private data class NodeState(
        val serverId: String,
        val port: Int,
        val connections: AtomicInteger = AtomicInteger(0)
    )

    private val nodes = ConcurrentHashMap<String, NodeState>().apply {
        initialNodes.forEach { (id, port) ->
            put(id, NodeState(serverId = id, port = port))
        }
    }

    override fun pickBestServer(): ServerNodeInfo {
        val best = nodes.values
            .sortedWith(compareBy<NodeState> { it.connections.get() }.thenBy { it.port })
            .first()
        return ServerNodeInfo(
            serverId = best.serverId,
            port = best.port,
            activeConnections = best.connections.get()
        )
    }

    override fun listServers(): List<ServerNodeInfo> {
        return nodes.values
            .sortedBy { it.port }
            .map { ServerNodeInfo(it.serverId, it.port, it.connections.get()) }
    }

    override fun incrementConnections(serverId: String) {
        nodes[serverId]?.connections?.incrementAndGet()
    }

    override fun decrementConnections(serverId: String) {
        nodes[serverId]?.connections?.updateAndGet { current ->
            if (current > 0) current - 1 else 0
        }
    }
}
