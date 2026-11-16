package com.chatdemo.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.chatdemo.app.ui.ChatRoomScreen
import com.chatdemo.app.ui.ConversationListScreen
import com.chatdemo.app.ui.LoginScreen

/**
 * Điểm vào UI Compose Multiplatform dùng chung cho cả Android và Desktop.
 */
@Composable
fun ChatDemoApp(
    defaultHost: String,
    platformLabel: String,
    externalController: ChatController? = null,
    onOpenNewWindow: (() -> Unit)? = null
) {
    val controller = externalController ?: remember(defaultHost, platformLabel) {
        ChatController(defaultHost = defaultHost, platformLabel = platformLabel)
    }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                controller.currentUser == null -> LoginScreen(controller, onOpenNewWindow)
                controller.activeConversation == null -> ConversationListScreen(controller, onOpenNewWindow)
                else -> ChatRoomScreen(controller)
            }
        }
    }
}
