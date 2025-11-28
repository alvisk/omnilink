package com.example.omni_link.data

/**
 * Actions that the AI can perform on the device
 */
sealed class AIAction {
    /**
     * Click on an element by its label or index
     */
    data class Click(
        val target: String, // Label or element description
        val index: Int? = null // Optional: direct index from screen state
    ) : AIAction()

    /**
     * Type text into a field
     */
    data class Type(
        val target: String,
        val text: String,
        val clearFirst: Boolean = true
    ) : AIAction()

    /**
     * Scroll in a direction
     */
    data class Scroll(
        val direction: ScrollDirection,
        val target: String? = null // Optional: specific scrollable element
    ) : AIAction()

    /**
     * Go back (system back button)
     */
    object Back : AIAction()

    /**
     * Go home
     */
    object Home : AIAction()

    /**
     * Open an app by name
     */
    data class OpenApp(val appName: String) : AIAction()

    /**
     * Wait for a condition or time
     */
    data class Wait(val milliseconds: Long = 1000) : AIAction()

    /**
     * Speak/respond to the user (no device action)
     */
    data class Respond(val message: String) : AIAction()

    /**
     * Ask for clarification
     */
    data class Clarify(val question: String) : AIAction()

    /**
     * Task complete - no more actions needed
     */
    data class Complete(val summary: String) : AIAction()

    enum class ScrollDirection {
        UP, DOWN, LEFT, RIGHT
    }
}

/**
 * Result of executing an action
 */
sealed class ActionResult {
    data class Success(val description: String) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
    data class NeedsConfirmation(val action: AIAction, val reason: String) : ActionResult()
}

/**
 * A plan of actions to execute
 */
data class ActionPlan(
    val reasoning: String,
    val actions: List<AIAction>,
    val isComplete: Boolean = false
)








