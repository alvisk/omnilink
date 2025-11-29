package com.example.omni_link.data

/** Represents a contextual AI-generated suggestion based on screen content */
data class Suggestion(
        val id: String,
        val title: String,
        val description: String,
        val action: AIAction?,
        val icon: SuggestionIcon = SuggestionIcon.LIGHTBULB,
        val priority: Int = 0 // Higher = more relevant
) {
    enum class SuggestionIcon {
        LIGHTBULB, // General suggestion
        CLICK, // Click action
        TYPE, // Type action
        SCROLL, // Scroll action
        NAVIGATE, // Navigation
        APP, // Open app
        SEARCH, // Search action
        SHARE, // Share content
        COPY, // Copy text
        INFO, // Information
        // Device Intent Icons
        CALENDAR, // Calendar/events
        PHONE, // Call/dial
        SMS, // Text message
        ALARM, // Alarm/timer
        TIMER, // Timer
        EMAIL, // Email
        MAP, // Maps/navigation
        MUSIC, // Play media
        SETTINGS, // Settings
        WEB // URL/browser
    }
}

/** State for the suggestion overlay */
data class SuggestionState(
        val isVisible: Boolean = false,
        val isLoading: Boolean = false,
        val suggestions: List<Suggestion> = emptyList(),
        val error: String? = null,
        val lastScreenContext: String? = null,
        val lastScreenState: ScreenState? = null, // Store actual screen state for fast forward
        val streamingText: String = "", // Real-time AI thinking output
        val isStreaming: Boolean = false,
        // Focus area feature
        val focusRegion: FocusRegion? = null,
        val focusAreaSelectionState: FocusAreaSelectionState = FocusAreaSelectionState(),
        val isFocusAreaModeEnabled: Boolean = false,
        // Cloud inference feature (fast forward)
        val isCloudInferenceActive: Boolean = false,
        val canUseFastForward: Boolean = true // Fast forward uses OpenRouter cloud AI
)
