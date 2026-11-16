package com.chatdemo.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.chatdemo.app.ChatDemoApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Chat System Demo (Desktop)",
        state = rememberWindowState(width = 420.dp, height = 740.dp)
    ) {
        ChatDemoApp(defaultHost = "127.0.0.1")
    }
}
