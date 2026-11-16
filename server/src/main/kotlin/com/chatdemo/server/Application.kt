package com.chatdemo.server

import com.chatdemo.shared.DemoCatalog
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch

/**
 * Điểm khởi chạy cụm Backend mô phỏng kiến trúc Chương 12 (Alex Xu):
 * - Cổng 8080: API Server + Service Discovery (vai Zookeeper)
 * - Cổng 8081: Chat Server 1 (WebSocket)
 * - Cổng 8082: Chat Server 2 (WebSocket)
 */
fun main() {
    val cluster = ChatServerCluster()
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val apiServer = embeddedServer(Netty, port = DemoCatalog.API_PORT, host = "0.0.0.0") {
        configureApiServer(cluster)
    }
    val chatServer1 = embeddedServer(Netty, port = DemoCatalog.CHAT_SERVER_1_PORT, host = "0.0.0.0") {
        configureChatWsServer("chat-server-1", DemoCatalog.CHAT_SERVER_1_PORT, cluster)
    }
    val chatServer2 = embeddedServer(Netty, port = DemoCatalog.CHAT_SERVER_2_PORT, host = "0.0.0.0") {
        configureChatWsServer("chat-server-2", DemoCatalog.CHAT_SERVER_2_PORT, cluster)
    }

    apiServer.start(wait = false)
    chatServer1.start(wait = false)
    chatServer2.start(wait = false)

    // Vòng lặp kiểm tra Heartbeat timeout mỗi 2 giây
    val heartbeatWatcher = backgroundScope.launch {
        while (isActive) {
            delay(2_000L)
            cluster.runPresenceTimeoutSweep()
        }
    }

    println("============================================================")
    println("  DESIGN A CHAT SYSTEM - BACKEND CLUSTER ĐÃ KHỞI ĐỘNG")
    println("  [1] API & Service Discovery : http://0.0.0.0:${DemoCatalog.API_PORT}")
    println("  [2] Chat Server 1 (WS)      : ws://0.0.0.0:${DemoCatalog.CHAT_SERVER_1_PORT}/ws")
    println("  [3] Chat Server 2 (WS)      : ws://0.0.0.0:${DemoCatalog.CHAT_SERVER_2_PORT}/ws")
    println("  Heartbeat: mỗi ${DemoCatalog.HEARTBEAT_INTERVAL_MS / 1000}s | Timeout: ${DemoCatalog.HEARTBEAT_TIMEOUT_MS / 1000}s")
    println("============================================================")

    val shutdownLatch = CountDownLatch(1)
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Đang tắt cụm Chat Server...")
        heartbeatWatcher.cancel()
        apiServer.stop(500, 1000)
        chatServer1.stop(500, 1000)
        chatServer2.stop(500, 1000)
        shutdownLatch.countDown()
    })

    shutdownLatch.await()
}
