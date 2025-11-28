package com.example.omni_link.data

/** Actions that the AI can perform on the device */
sealed class AIAction {
    /** Click on an element by its label or index */
    data class Click(
            val target: String, // Label or element description
            val index: Int? = null // Optional: direct index from screen state
    ) : AIAction()

    /** Type text into a field */
    data class Type(val target: String, val text: String, val clearFirst: Boolean = true) :
            AIAction()

    /** Scroll in a direction */
    data class Scroll(
            val direction: ScrollDirection,
            val target: String? = null // Optional: specific scrollable element
    ) : AIAction()

    /** Go back (system back button) */
    object Back : AIAction()

    /** Go home */
    object Home : AIAction()

    /** Open an app by name */
    data class OpenApp(val appName: String) : AIAction()

    /** Wait for a condition or time */
    data class Wait(val milliseconds: Long = 1000) : AIAction()

    /** Speak/respond to the user (no device action) */
    data class Respond(val message: String) : AIAction()

    /** Ask for clarification */
    data class Clarify(val question: String) : AIAction()

    /** Task complete - no more actions needed */
    data class Complete(val summary: String) : AIAction()

    // ═══════════════════════════════════════════════════════════════════════════
    // DEVICE INTENT ACTIONS - Programmatic actions using Android Intents
    // ═══════════════════════════════════════════════════════════════════════════

    /** Open calendar app, optionally at a specific date or to create an event */
    data class OpenCalendar(
            val title: String? = null, // Event title (if creating)
            val description: String? = null, // Event description
            val startTime: Long? = null, // Start time in millis (null = now)
            val endTime: Long? = null, // End time in millis
            val location: String? = null // Event location
    ) : AIAction()

    /** Open dialer with a phone number (user must press call) */
    data class DialNumber(val phoneNumber: String) : AIAction()

    /** Directly call a phone number (requires CALL_PHONE permission) */
    data class CallNumber(val phoneNumber: String) : AIAction()

    /** Compose an SMS message */
    data class SendSMS(val phoneNumber: String, val message: String) : AIAction()

    /** Open a URL in the default browser */
    data class OpenURL(val url: String) : AIAction()

    /** Search the web using the default search engine */
    data class WebSearch(val query: String) : AIAction()

    /** Set an alarm */
    data class SetAlarm(
            val hour: Int, // 0-23
            val minute: Int, // 0-59
            val message: String? = null,
            val days: List<Int>? = null // Days of week (Calendar.MONDAY, etc.)
    ) : AIAction()

    /** Set a timer */
    data class SetTimer(val seconds: Int, val message: String? = null) : AIAction()

    /** Share text content via the share sheet */
    data class ShareText(val text: String, val subject: String? = null) : AIAction()

    /** Copy text to clipboard */
    data class CopyToClipboard(val text: String, val label: String = "Copied text") : AIAction()

    /** Compose an email */
    data class SendEmail(val to: String, val subject: String? = null, val body: String? = null) :
            AIAction()

    /** Open Maps/Navigation to an address or coordinates */
    data class OpenMaps(
            val query: String? = null, // Search query (address, place name)
            val latitude: Double? = null,
            val longitude: Double? = null,
            val navigate: Boolean = false // Start navigation immediately
    ) : AIAction()

    /** Play media (music, podcast, etc.) */
    data class PlayMedia(
            val query: String, // What to play
            val artist: String? = null,
            val album: String? = null
    ) : AIAction()

    /** Take a photo or video */
    data class CaptureMedia(
            val video: Boolean = false // true for video, false for photo
    ) : AIAction()

    /** Open device settings to a specific section */
    data class OpenSettings(val section: SettingsSection = SettingsSection.MAIN) : AIAction()

    enum class ScrollDirection {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }

    enum class SettingsSection {
        MAIN,
        WIFI,
        BLUETOOTH,
        DISPLAY,
        SOUND,
        BATTERY,
        APPS,
        NOTIFICATIONS,
        LOCATION,
        SECURITY,
        ACCESSIBILITY
    }
}

/** Result of executing an action */
sealed class ActionResult {
    data class Success(val description: String) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
    data class NeedsConfirmation(val action: AIAction, val reason: String) : ActionResult()
}

/** A plan of actions to execute */
data class ActionPlan(
        val reasoning: String,
        val actions: List<AIAction>,
        val isComplete: Boolean = false
)
