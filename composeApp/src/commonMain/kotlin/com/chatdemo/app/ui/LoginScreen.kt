package com.chatdemo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatdemo.app.ChatController
import com.chatdemo.shared.DemoCatalog

@Composable
fun LoginScreen(
    controller: ChatController,
    onOpenNewWindow: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Design A Chat System",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            if (onOpenNewWindow != null) {
                TextButton(onClick = onOpenNewWindow) {
                    Text("+ Cửa sổ", fontSize = 12.sp)
                }
            }
        }
        Text(
            text = "Minh họa Chương 12 (Alex Xu) • Thiết bị: ${controller.deviceId}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF475569)
        )

        // Ô chỉnh địa chỉ Server Host để hỗ trợ cả Desktop (127.0.0.1), Emulator (10.0.2.2) và ĐT thật (IP LAN)
        OutlinedTextField(
            value = controller.hostInput,
            onValueChange = { controller.hostInput = it },
            label = { Text("Địa chỉ Server (Host)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { controller.hostInput = "127.0.0.1" },
                label = { Text("Desktop: 127.0.0.1", fontSize = 12.sp) }
            )
            AssistChip(
                onClick = { controller.hostInput = "10.0.2.2" },
                label = { Text("Emulator: 10.0.2.2", fontSize = 12.sp) }
            )
        }

        controller.errorMessage?.let { err ->
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = err,
                    color = Color(0xFFB91C1C),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Text(
            text = "Chọn tài khoản để đăng nhập:",
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1E293B)
        )

        DemoCatalog.users.forEach { user ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !controller.isBusy) {
                        controller.loginAndConnect(user.id)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(user.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "ID: @${user.id} • Bấm để qua Service Discovery",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        if (controller.isBusy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Đang gọi Login & Service Discovery...", fontSize = 13.sp)
            }
        }

        Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Mẹo demo đa thiết bị: Mở thêm 1 cửa sổ Desktop nữa và đăng nhập cùng tài khoản Alice — mọi tin nhắn sẽ tự động đồng bộ qua cur_max_message_id.",
                fontSize = 12.sp,
                color = Color(0xFF1E40AF),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
