package com.example.omni_link

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.omni_link.ui.ChatScreen
import com.example.omni_link.ui.OmniViewModel
import com.example.omni_link.ui.OnboardingScreen
import com.example.omni_link.ui.theme.NOMMTheme

/**
 * Main Activity for NOMM (Nothing On My Mind) AI Assistant
 *
 * This is the entry point of the application. It hosts the Compose UI for the chat interface and
 * manages the app lifecycle.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "nomm_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        // DEBUG: Set to true to always show onboarding
        private const val FORCE_SHOW_ONBOARDING = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Reset onboarding if force flag is set
        if (FORCE_SHOW_ONBOARDING) {
            prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, false).apply()
            Log.d(TAG, "Force onboarding: cleared completion flag")
        }

        val shouldShowOnboarding = !prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        Log.d(TAG, "onCreate: shouldShowOnboarding = $shouldShowOnboarding")

        setContent {
            NOMMTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Create shared ViewModel for onboarding and chat
                    val omniViewModel: OmniViewModel = viewModel()
                    var showOnboarding by remember { mutableStateOf(shouldShowOnboarding) }

                    Log.d(TAG, "Compose: showOnboarding = $showOnboarding")

                    if (showOnboarding) {
                        OnboardingScreen(
                                viewModel = omniViewModel,
                                onComplete = {
                                    Log.d(TAG, "Onboarding completed")
                                    prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                                    showOnboarding = false
                                }
                        )
                    } else {
                        ChatScreen(viewModel = omniViewModel)
                    }
                }
            }
        }
    }
}
