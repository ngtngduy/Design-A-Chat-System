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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatdemo.app.ChatController
import com.chatdemo.shared.ChannelHelper

@Composable
fun ConversationListScreen(
    controller: ChatController,
    onOpenNewWindow: (() -> Unit)? = null
) {
    val me = controller.currentUser ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Thanh tiêu đề tài khoản hiện tại
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(me.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(me.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(me.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Thiết bị: ${controller.deviceId}", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
                Row {
                    if (onOpenNewWindow != null) {
                        TextButton(onClick = onOpenNewWindow) {
                            Text("+ Cửa sổ", fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { controller.logout() }) {
                        Text("Đổi user")
                    }
                }
            }
        }

        SystemDesignBanner(controller)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Chat 1-1 (Bạn bè)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            items(controller.friends, key = { it.id }) { friend ->
                val isOnline = controller.isUserOnline(friend.id)
                val channelId = ChannelHelper.directChannelId(me.id, friend.id)
                val lastMsg = controller.messagesByChannel[channelId]?.lastOrNull()

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controller.openDirectChat(friend) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(friend.avatarColorHex)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(friend.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            // Chấm trạng thái Online/Offline (Presence)
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF16A34A) else Color(0xFF94A3B8))
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(friend.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    text = if (isOnline) "Online" else "Offline",
                                    fontSize = 11.sp,
                                    color = if (isOnline) Color(0xFF16A34A) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = lastMsg?.let { "${it.messageFrom}: ${it.content}" } ?: "Chạm để nhắn tin 1-1",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Chat Nhóm (Group ≤ 100)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                )
            }

            items(controller.groups, key = { it.channelId }) { group ->
                val lastMsg = controller.messagesByChannel[group.channelId]?.lastOrNull()
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { controller.openGroupChat(group) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7C3AED)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("G", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = lastMsg?.let { "${it.messageFrom}: ${it.content}" }
                                    ?: "Thành viên: ${group.memberIds.joinToString(", ")}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
