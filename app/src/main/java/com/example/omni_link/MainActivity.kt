package com.example.omni_link

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.omni_link.ui.ChatScreen
import com.example.omni_link.ui.theme.OmniLinkTheme

/**
 * Main Activity for Omni-Link AI Assistant
 *
 * This is the entry point of the application. It hosts the Compose UI for the chat interface and
 * manages the app lifecycle.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { OmniLinkTheme { Surface(modifier = Modifier.fillMaxSize()) { ChatScreen() } } }
    }
}
