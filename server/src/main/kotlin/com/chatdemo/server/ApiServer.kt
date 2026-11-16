package com.chatdemo.server

import com.chatdemo.shared.DemoCatalog
import com.chatdemo.shared.DiscoveryResponse
import com.chatdemo.shared.LoginRequest
import com.chatdemo.shared.LoginResponse
import com.chatdemo.shared.SyncResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/**
 * Thành phần: Stateless API Server + Service Discovery (Cổng 8080).
 *
 * Dùng giao thức HTTP REST cho các tác vụ không cần giữ kết nối liên tục:
 * - POST /api/login: xác thực giả lập, trả về danh sách bạn bè, nhóm và trạng thái online ban đầu
 * - GET /api/discovery: đóng vai Zookeeper chỉ định Chat Server (8081 hoặc 8082) cho client
 * - GET /api/sync: đồng bộ đa thiết bị & kéo tin nhắn bị lỡ dựa vào `afterId` (cur_max_message_id)
 * - GET /api/messages/{channelId}: lấy lịch sử tin nhắn của một kênh
 */
fun Application.configureApiServer(cluster: ChatServerCluster) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    routing {
        get("/api/health") {
            call.respond(
                mapOf(
                    "status" to "ok",
                    "servers" to cluster.serviceDiscovery.listServers().toString()
                )
            )
        }

        post("/api/login") {
            val request = call.receive<LoginRequest>()
            val user = DemoCatalog.users.find { it.id == request.userId }
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                return@post
            }

            val friends = DemoCatalog.users.filter { it.id != user.id }
            val groups = if (user.id in DemoCatalog.defaultGroup.memberIds) {
                listOf(DemoCatalog.defaultGroup)
            } else {
                emptyList()
            }
            val presences = cluster.presenceManager.getAllPresences()

            println("[API-8080] Login thành công cho '${user.displayName}' (${user.id})")
            call.respond(
                LoginResponse(
                    user = user,
                    friends = friends,
                    groups = groups,
                    presences = presences
                )
            )
        }

        get("/api/discovery") {
            val best = cluster.serviceDiscovery.pickBestServer()
            val all = cluster.serviceDiscovery.listServers()
            println("[DISCOVERY-8080] Đề xuất ${best.serverId} (cổng ${best.port}, ${best.activeConnections} kết nối)")
            call.respond(
                DiscoveryResponse(
                    recommendedServer = best,
                    allServers = all
                )
            )
        }

        get("/api/sync") {
            val userId = call.request.queryParameters["userId"]
            val afterId = call.request.queryParameters["afterId"]?.toLongOrNull() ?: 0L
            if (userId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing userId"))
                return@get
            }

            val channels = cluster.messageRouter.channelsForUser(userId)
            val missedMessages = cluster.messageStore.getMessagesAfter(channels, afterId)
            val newMax = maxOf(afterId, missedMessages.maxOfOrNull { it.messageId } ?: afterId)

            if (missedMessages.isNotEmpty()) {
                println("[SYNC-8080] Đồng bộ ${missedMessages.size} tin nhắn cho '$userId' sau id=$afterId")
            }

            call.respond(
                SyncResponse(
                    userId = userId,
                    requestedAfterId = afterId,
                    newMaxMessageId = newMax,
                    messages = missedMessages
                )
            )
        }

        get("/api/messages/{channelId}") {
            val channelId = call.parameters["channelId"].orEmpty()
            val messages = cluster.messageStore.getChannelMessages(channelId)
            call.respond(messages)
        }
    }
}
