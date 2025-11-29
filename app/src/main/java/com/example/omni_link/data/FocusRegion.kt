package com.example.omni_link.data

import android.graphics.Rect

/**
 * Represents a focus region on the screen that the AI should prioritize. When set, the AI will
 * focus its attention on elements within this region.
 */
data class FocusRegion(
        val bounds: Rect,
        val label: String? = null, // Optional user-provided label for context
        val createdAt: Long = System.currentTimeMillis()
) {
    /** Check if a point is within the focus region */
    fun contains(x: Int, y: Int): Boolean = bounds.contains(x, y)

    /** Check if an element's bounds intersect with the focus region */
    fun intersects(elementBounds: Rect): Boolean = Rect.intersects(bounds, elementBounds)

    /** Check if an element is fully contained within the focus region */
    fun containsElement(elementBounds: Rect): Boolean = bounds.contains(elementBounds)

    /** Get the percentage of overlap between an element and the focus region */
    fun overlapPercentage(elementBounds: Rect): Float {
        if (!intersects(elementBounds)) return 0f

        val intersection = Rect()
        intersection.setIntersect(bounds, elementBounds)

        val intersectionArea = intersection.width() * intersection.height()
        val elementArea = elementBounds.width() * elementBounds.height()

        return if (elementArea > 0) {
            (intersectionArea.toFloat() / elementArea.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    /** Convert to a human-readable description for LLM context */
    fun toPromptContext(): String {
        return buildString {
            append("🎯 FOCUS AREA: ")
            if (label != null) {
                append("\"$label\" - ")
            }
            append(
                    "Region at (${bounds.left}, ${bounds.top}) to (${bounds.right}, ${bounds.bottom})"
            )
            append(" [${bounds.width()}x${bounds.height()} pixels]")
        }
    }

    companion object {
        /** Create a focus region from touch coordinates */
        fun fromTouchCoordinates(
                startX: Float,
                startY: Float,
                endX: Float,
                endY: Float,
                label: String? = null
        ): FocusRegion {
            val left = minOf(startX, endX).toInt()
            val top = minOf(startY, endY).toInt()
            val right = maxOf(startX, endX).toInt()
            val bottom = maxOf(startY, endY).toInt()

            return FocusRegion(bounds = Rect(left, top, right, bottom), label = label)
        }

        /** Minimum size for a valid focus region */
        const val MIN_SIZE = 50

        /** Check if a proposed region is large enough to be valid */
        fun isValidSize(width: Int, height: Int): Boolean {
            return width >= MIN_SIZE && height >= MIN_SIZE
        }
    }
}

/** State for the focus area selection UI */
data class FocusAreaSelectionState(
        val isSelecting: Boolean = false,
        val startX: Float = 0f,
        val startY: Float = 0f,
        val currentX: Float = 0f,
        val currentY: Float = 0f,
        val currentRegion: FocusRegion? = null,
        val showLabelDialog: Boolean = false
) {
    val hasSelection: Boolean
        get() = currentRegion != null

    val selectionBounds: Rect?
        get() =
                if (isSelecting && startX != currentX && startY != currentY) {
                    Rect(
                            minOf(startX, currentX).toInt(),
                            minOf(startY, currentY).toInt(),
                            maxOf(startX, currentX).toInt(),
                            maxOf(startY, currentY).toInt()
                    )
                } else null
}


