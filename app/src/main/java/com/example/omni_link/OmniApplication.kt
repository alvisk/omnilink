package com.example.omni_link

import android.app.Application
import android.util.Log
import com.cactus.CactusContextInitializer

/**
 * Application class for NOMM (Nothing On My Mind) Initializes global dependencies including Cactus
 * SDK
 */
class OmniApplication : Application() {

    companion object {
        private const val TAG = "OmniApplication"

        // Singleton reference for accessing application context
        lateinit var instance: OmniApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Cactus SDK context (required before using any Cactus functionality)
        initializeCactus()
    }

    private fun initializeCactus() {
        try {
            CactusContextInitializer.initialize(this)
            Log.d(TAG, "Cactus SDK context initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Cactus SDK", e)
        }
    }
}
