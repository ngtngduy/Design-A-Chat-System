package com.chatdemo.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.chatdemo.app.ChatController
import com.chatdemo.app.ChatDemoApp

private data class DesktopWindowEntry(
    val id: Int,
    val controller: ChatController
)

/**
 * Điểm chạy phiên bản Desktop (macOS/Windows/Linux).
 * Chỉ cần chạy hàm main() 1 lần, sau đó bấm "+ Cửa sổ" ngay trong app
 * để mở thêm 2-3 cửa sổ độc lập phục vụ demo đa thiết bị & cross-server.
 */
fun main() = application {
    var nextWindowId by remember { mutableStateOf(2) }
    val windows = remember {
        mutableStateListOf(
            DesktopWindowEntry(
                id = 1,
                controller = ChatController(defaultHost = "127.0.0.1", platformLabel = "desktop")
            )
        )
    }

    val openNewWindow = {
        val id = nextWindowId++
        windows.add(
            DesktopWindowEntry(
                id = id,
                controller = ChatController(defaultHost = "127.0.0.1", platformLabel = "desktop")
            )
        )
        Unit
    }

    for (entry in windows) {
        key(entry.id) {
            Window(
                onCloseRequest = {
                    entry.controller.logout()
                    windows.remove(entry)
                    if (windows.isEmpty()) {
                        exitApplication()
                    }
                },
                title = entry.controller.windowTitle(),
                state = rememberWindowState(width = 430.dp, height = 760.dp)
            ) {
                ChatDemoApp(
                    defaultHost = "127.0.0.1",
                    platformLabel = "desktop",
                    externalController = entry.controller,
                    onOpenNewWindow = openNewWindow
                )
            }
        }
    }
}
