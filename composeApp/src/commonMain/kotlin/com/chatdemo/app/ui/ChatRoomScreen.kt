package com.chatdemo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatdemo.app.ActiveConversation
import com.chatdemo.app.ChatController
import com.chatdemo.app.ConnectionState
import com.chatdemo.shared.ChatMessage

@Composable
fun ChatRoomScreen(controller: ChatController) {
    val me = controller.currentUser ?: return
    val conv = controller.activeConversation ?: return
    val messages = controller.messagesForActiveConversation()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Tự động cuộn xuống tin nhắn mới nhất
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val (title, subtitle, isFriendOnline) = when (conv) {
        is ActiveConversation.Direct -> {
            val online = controller.isUserOnline(conv.friend.id)
            Triple(
                conv.friend.displayName,
                if (online) "Đang Online" else "Đang Offline",
                online
            )
        }
        is ActiveConversation.Group -> Triple(
            conv.group.name,
            "Nhóm (${conv.group.memberIds.joinToString(", ")})",
            null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .imePadding()
    ) {
        // Thanh tiêu đề phòng chat
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { controller.backToConversationList() }) {
                    Text("← Quay lại", fontSize = 14.sp)
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFriendOnline != null) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isFriendOnline) Color(0xFF16A34A) else Color(0xFF94A3B8))
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
                }
            }
        }

        // Thanh quan sát System Design ngay trong phòng chat
        SystemDesignBanner(controller)

        controller.errorMessage?.let { err ->
            Surface(
                color = Color(0xFFFEF2F2),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(err, color = Color(0xFFB91C1C), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { controller.clearError() }) { Text("Đóng", fontSize = 11.sp) }
                }
            }
        }

        // Danh sách tin nhắn
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có tin nhắn nào. Hãy gửi tin đầu tiên!",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            items(messages, key = { it.messageId }) { msg ->
                MessageBubbleItem(
                    message = msg,
                    isMine = msg.messageFrom == me.id,
                    showSender = conv is ActiveConversation.Group && msg.messageFrom != me.id
                )
            }
        }

        // Thanh nhập tin nhắn
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canSend = controller.connectionState == ConnectionState.CONNECTED
                val sendAction = {
                    if (inputText.isNotBlank()) {
                        controller.sendMessage(inputText)
                        inputText = ""
                    }
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (canSend) "Nhập tin nhắn..." else "Đang mất mạng — bấm Kết nối lại ở trên",
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendAction() }),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = sendAction,
                    enabled = inputText.isNotBlank()
                ) {
                    Text("Gửi")
                }
            }
        }
    }
}

@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isMine: Boolean,
    showSender: Boolean
) {
    val alignment = if (isMine) Alignment.End else Alignment.Start
    val bubbleColor = if (isMine) Color(0xFF2563EB) else Color.White
    val textColor = if (isMine) Color.White else Color(0xFF0F172A)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (showSender) {
            Text(
                text = message.messageFrom,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF475569),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMine) 14.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 14.dp
            ),
            shadowElevation = if (isMine) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        val idLabel = if (message.messageId < 0) "pending" else "#${message.messageId.toString().takeLast(6)}"
        val routeLabel = if (message.routedViaServer.isNotBlank()) " • ${message.routedViaServer}" else ""
        Text(
            text = "$idLabel$routeLabel",
            fontSize = 10.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}
