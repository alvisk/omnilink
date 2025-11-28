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
        INFO // Information
    }
}

/** State for the suggestion overlay */
data class SuggestionState(
        val isVisible: Boolean = false,
        val isLoading: Boolean = false,
        val suggestions: List<Suggestion> = emptyList(),
        val error: String? = null,
        val lastScreenContext: String? = null
)
