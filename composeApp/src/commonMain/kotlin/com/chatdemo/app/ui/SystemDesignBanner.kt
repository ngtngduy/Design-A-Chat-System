package com.chatdemo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatdemo.app.ChatController
import com.chatdemo.app.ConnectionState

/**
 * Thanh quan sát kiến trúc System Design ngay trên giao diện App:
 * Hiển thị Chat Server đang nối (8081/8082), `cur_max_message_id`, nhịp Heartbeat
 * và nút "Giả lập mất mạng" để demo Presence Timeout + Sync.
 */
@Composable
fun SystemDesignBanner(controller: ChatController) {
    val state = controller.connectionState
    val (dotColor, statusText, bgColor) = when (state) {
        ConnectionState.CONNECTED -> Triple(Color(0xFF16A34A), "ONLINE", Color(0xFFECFDF5))
        ConnectionState.CONNECTING -> Triple(Color(0xFFD97706), "ĐANG NỐI...", Color(0xFFFFFBEB))
        ConnectionState.SIMULATED_OFFLINE -> Triple(Color(0xFFDC2626), "MẤT MẠNG", Color(0xFFFEF2F2))
        ConnectionState.DISCONNECTED -> Triple(Color(0xFF64748B), "CHƯA KẾT NỐI", Color(0xFFF1F5F9))
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = dotColor
                    )
                    Spacer(Modifier.width(8.dp))
                    val serverLabel = controller.connectedServer?.let { "${it.serverId} (:${it.port})" } ?: "Chưa nối server"
                    Text(
                        text = serverLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B)
                    )
                }

                if (state == ConnectionState.CONNECTED) {
                    FilledTonalButton(
                        onClick = { controller.simulateNetworkOutage() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFFEE2E2),
                            contentColor = Color(0xFFB91C1C)
                        ),
                        modifier = Modifier
                    ) {
                        Text("Giả lập mất mạng", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (state == ConnectionState.SIMULATED_OFFLINE) {
                    FilledTonalButton(
                        onClick = { controller.reconnectAfterOutage() },
                        enabled = !controller.isBusy,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFFDCFCE7),
                            contentColor = Color(0xFF15803D)
                        )
                    ) {
                        Text("Kết nối lại", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "cur_max_id: #${controller.curMaxMessageId}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "Heartbeat: ♥ ${controller.heartbeatCount}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
            }

            if (controller.statusNotice.isNotBlank()) {
                Text(
                    text = controller.statusNotice,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = Color(0xFF334155)
                )
            }
        }
    }
}
