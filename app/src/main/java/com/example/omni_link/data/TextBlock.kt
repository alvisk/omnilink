package com.example.omni_link.data

import android.graphics.Rect
import com.example.omni_link.ai.TextOption

/**
 * Represents a block of text detected by OCR with its location on screen. Used for Circle-to-Search
 * style text selection.
 */
data class TextBlock(
        val text: String,
        val bounds: Rect,
        val confidence: Float = 1.0f,
        val language: String? = null
) {
    /** Check if this text block contains a point */
    fun contains(x: Float, y: Float): Boolean {
        return bounds.contains(x.toInt(), y.toInt())
    }

    /** Check if this text block intersects with a rectangle */
    fun intersects(rect: Rect): Boolean {
        return Rect.intersects(bounds, rect)
    }

    /** Get center point of this text block */
    fun centerX(): Float = bounds.centerX().toFloat()
    fun centerY(): Float = bounds.centerY().toFloat()
}

/** Represents the state of the text selection overlay */
data class TextSelectionState(
        val isActive: Boolean = false,
        val screenshotBitmap: android.graphics.Bitmap? = null,
        val textBlocks: List<TextBlock> = emptyList(),
        val selectedBlocks: Set<Int> = emptySet(), // Indices of selected blocks
        val isProcessing: Boolean = false,
        val error: String? = null,
        // For circle/lasso selection
        val selectionPath: List<Pair<Float, Float>> = emptyList(),
        val isDrawingSelection: Boolean = false,
        // AI-generated options for selected text
        val textOptions: List<TextOption> = emptyList(),
        val isGeneratingOptions: Boolean = false,
        val optionsStreamingText: String = ""
) {
    /** Get all selected text combined */
    fun getSelectedText(): String {
        return selectedBlocks.mapNotNull { textBlocks.getOrNull(it) }.joinToString(" ") { it.text }
    }

    /** Check if any text is selected */
    fun hasSelection(): Boolean = selectedBlocks.isNotEmpty()

    /** Check if we have AI-generated options */
    fun hasOptions(): Boolean = textOptions.isNotEmpty()
}
