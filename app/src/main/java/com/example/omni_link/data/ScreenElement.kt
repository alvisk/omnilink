package com.example.omni_link.data

import android.graphics.Rect

/** Represents a UI element captured from the screen */
data class ScreenElement(
        val id: String?,
        val text: String?,
        val contentDescription: String?,
        val className: String,
        val bounds: Rect,
        val isClickable: Boolean,
        val isEditable: Boolean,
        val isScrollable: Boolean,
        val isCheckable: Boolean,
        val isChecked: Boolean,
        val children: List<ScreenElement> = emptyList()
) {
    /** Returns the best label for this element (text > contentDescription > id) */
    fun getLabel(): String? = text ?: contentDescription ?: id

    /** Convert to a concise string for LLM context */
    fun toPromptString(): String {
        val label = getLabel() ?: return ""
        val type = className.substringAfterLast(".")
        val actions = mutableListOf<String>()
        if (isClickable) actions.add("clickable")
        if (isEditable) actions.add("editable")
        if (isScrollable) actions.add("scrollable")
        if (isCheckable) actions.add(if (isChecked) "checked" else "unchecked")

        return buildString {
            append("[$type] \"$label\"")
            if (actions.isNotEmpty()) {
                append(" (${actions.joinToString(", ")})")
            }
        }
    }
}

/** Represents the entire screen state at a point in time */
data class ScreenState(
        val packageName: String,
        val activityName: String?,
        val timestamp: Long,
        val elements: List<ScreenElement>
) {
    /** Convert screen state to a prompt-friendly format for the LLM */
    fun toPromptContext(): String {
        val interactiveElements =
                flattenElements()
                        .filter { it.isClickable || it.isEditable || it.getLabel() != null }
                        .mapIndexed { index, element -> "  $index: ${element.toPromptString()}" }
                        .filter { it.isNotBlank() }
                        .take(25) // Reduced from 50 for faster inference

        return buildString {
            appendLine("Current App: $packageName")
            activityName?.let { appendLine("Screen: ${it.substringAfterLast(".")}") }
            appendLine("UI Elements:")
            interactiveElements.forEach { appendLine(it) }
        }
    }

    /** Flatten the element tree into a list */
    fun flattenElements(): List<ScreenElement> {
        val result = mutableListOf<ScreenElement>()
        fun traverse(element: ScreenElement) {
            result.add(element)
            element.children.forEach { traverse(it) }
        }
        elements.forEach { traverse(it) }
        return result
    }

    /** Find an element by its label (text or content description) */
    fun findByLabel(label: String): ScreenElement? {
        return flattenElements().find {
            it.text?.contains(label, ignoreCase = true) == true ||
                    it.contentDescription?.contains(label, ignoreCase = true) == true
        }
    }

    /** Find element by index (as shown in prompt context) */
    fun findByIndex(index: Int): ScreenElement? {
        val interactiveElements =
                flattenElements().filter {
                    it.isClickable || it.isEditable || it.getLabel() != null
                }
        return interactiveElements.getOrNull(index)
    }
}
