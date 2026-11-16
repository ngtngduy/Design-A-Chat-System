package com.chatdemo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chatdemo.app.ChatDemoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Trên Android Emulator, 10.0.2.2 trỏ về localhost của máy Mac đang chạy server.
        // Nếu chạy trên điện thoại thật cùng Wi-Fi, người dùng có thể gõ IP LAN ngay ở màn hình Login.
        setContent {
            ChatDemoApp(
                defaultHost = "10.0.2.2",
                platformLabel = "android"
            )
        }
    }
}
