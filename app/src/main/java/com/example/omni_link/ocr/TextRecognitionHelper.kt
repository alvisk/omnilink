package com.example.omni_link.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.example.omni_link.data.TextBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class for text recognition using ML Kit.
 * Provides Circle-to-Search style OCR functionality.
 */
class TextRecognitionHelper {

    companion object {
        private const val TAG = "TextRecognition"

        // Singleton recognizer for efficiency
        private val recognizer: TextRecognizer by lazy {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    /**
     * Process a bitmap image and extract text blocks with their locations.
     *
     * @param bitmap The screenshot to process
     * @return List of TextBlock objects with text and bounding boxes
     */
    suspend fun recognizeText(bitmap: Bitmap): List<TextBlock> {
        Log.d(TAG, "Starting OCR on bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
        
        // First try with original image
        val originalResults = processImage(bitmap)
        Log.d(TAG, "Original image OCR found ${originalResults.size} text blocks")
        
        if (originalResults.isNotEmpty()) {
            return originalResults
        }
        
        // If no text found, try with inverted colors (helps with light text on dark backgrounds)
        Log.d(TAG, "No text found, trying with inverted colors...")
        val invertedBitmap = invertBitmap(bitmap)
        val invertedResults = processImage(invertedBitmap)
        Log.d(TAG, "Inverted image OCR found ${invertedResults.size} text blocks")
        invertedBitmap.recycle()
        
        if (invertedResults.isNotEmpty()) {
            return invertedResults
        }
        
        // Try with high contrast version
        Log.d(TAG, "No text found, trying with high contrast...")
        val contrastBitmap = increaseContrast(bitmap, 1.5f)
        val contrastResults = processImage(contrastBitmap)
        Log.d(TAG, "High contrast OCR found ${contrastResults.size} text blocks")
        contrastBitmap.recycle()
        
        return contrastResults
    }
    
    private suspend fun processImage(bitmap: Bitmap): List<TextBlock> = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "ML Kit returned ${visionText.textBlocks.size} blocks, full text: '${visionText.text.take(100)}'")
                    val textBlocks = mutableListOf<TextBlock>()

                    // Process each text block from ML Kit
                    for (block in visionText.textBlocks) {
                        Log.d(TAG, "Block: '${block.text.take(50)}' bounds: ${block.boundingBox}")
                        // Get block-level text (paragraphs)
                        block.boundingBox?.let { blockBounds ->
                            // Also process line-level for finer granularity
                            for (line in block.lines) {
                                line.boundingBox?.let { lineBounds ->
                                    // Process element-level (words) for finest selection
                                    for (element in line.elements) {
                                        element.boundingBox?.let { elementBounds ->
                                            textBlocks.add(
                                                TextBlock(
                                                    text = element.text,
                                                    bounds = elementBounds,
                                                    confidence = element.confidence ?: 1.0f,
                                                    language = block.recognizedLanguage
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Recognized ${textBlocks.size} text elements")
                    continuation.resume(textBlocks)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Invert bitmap colors - helps with light text on dark backgrounds
     */
    private fun invertBitmap(bitmap: Bitmap): Bitmap {
        val invertedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(invertedBitmap)
        val paint = Paint()
        
        // Color matrix to invert colors
        val colorMatrix = ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return invertedBitmap
    }
    
    /**
     * Increase bitmap contrast - helps with low contrast text
     */
    private fun increaseContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val contrastBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(contrastBitmap)
        val paint = Paint()
        
        val scale = contrast
        val translate = (-.5f * scale + .5f) * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return contrastBitmap
    }

    /**
     * Find text blocks that intersect with a given selection rectangle.
     */
    fun findBlocksInRect(blocks: List<TextBlock>, selectionRect: Rect): List<Int> {
        return blocks.mapIndexedNotNull { index, block ->
            if (block.intersects(selectionRect)) index else null
        }
    }

    /**
     * Find text blocks that intersect with a freeform path (circle/lasso selection).
     * Uses a simplified approach by checking if the block center is inside the path.
     */
    fun findBlocksInPath(blocks: List<TextBlock>, path: List<Pair<Float, Float>>): List<Int> {
        if (path.size < 3) return emptyList()

        return blocks.mapIndexedNotNull { index, block ->
            val centerX = block.centerX()
            val centerY = block.centerY()
            if (isPointInPolygon(centerX, centerY, path)) index else null
        }
    }

    /**
     * Find text block at a specific point (for tap selection).
     */
    fun findBlockAtPoint(blocks: List<TextBlock>, x: Float, y: Float): Int? {
        return blocks.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }
    }

    /**
     * Check if a point is inside a polygon using ray casting algorithm.
     */
    private fun isPointInPolygon(x: Float, y: Float, polygon: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].first
            val yi = polygon[i].second
            val xj = polygon[j].first
            val yj = polygon[j].second

            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    /**
     * Get the bounding rect of a path.
     */
    fun getPathBounds(path: List<Pair<Float, Float>>): Rect {
        if (path.isEmpty()) return Rect()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for ((x, y) in path) {
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
        }

        return Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
    }
}
