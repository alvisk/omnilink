package com.example.omni_link.glyph

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.omni_link.debug.DebugLogManager
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Available glyph animation types */
enum class GlyphType(val displayName: String, val description: String) {
    MOBIUS_FIGURE_8("Möbius ∞", "Figure-8 twisted ribbon"),
    MOBIUS_RING("Möbius Ring", "Circular twisted strip"),
    PULSE_RING("Pulse Ring", "Expanding ring pulse"),
    ROTATING_CUBE("3D Cube", "Wireframe rotating cube"),
    DNA_HELIX("DNA Helix", "Double helix spiral"),
    MATRIX_RAIN("Matrix Rain", "Falling digital rain"),
    HEARTBEAT("Heartbeat", "Pulsing heart rhythm"),
    SPIRAL_GALAXY("Spiral Galaxy", "Rotating spiral arms")
}

/**
 * Helper class for controlling the Nothing Phone 3 Glyph Matrix LED display.
 *
 * Uses the official Nothing GlyphMatrix SDK to control the 25x25 LED matrix on the back of the
 * phone. Displays the NOMM logo when accessibility service is active.
 *
 * Note: This uses GlyphMatrixManager (for 25x25 matrix) NOT GlyphManager (for LED strips).
 *
 * @see <a
 * href="https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit">GlyphMatrix
 * Developer Kit</a>
 */
class GlyphMatrixHelper(private val context: Context) {

    companion object {
        private const val TAG = "GlyphMatrixHelper"

        // Glyph Matrix is 25x25 LED grid
        const val MATRIX_SIZE = 25

        // Device ID for Nothing Phone 3
        private val DEVICE_PHONE_3 = Glyph.DEVICE_23112

        /**
         * Generate a glyph bitmap for preview purposes (no SDK required). This can be called
         * statically for UI previews.
         */
        fun generatePreviewBitmap(type: GlyphType, frame: Int): Bitmap {
            return when (type) {
                GlyphType.MOBIUS_FIGURE_8 -> createMobiusFigure8Bitmap(frame)
                GlyphType.MOBIUS_RING -> createMobiusRingBitmap(frame)
                GlyphType.PULSE_RING -> createPulseRingBitmap(frame)
                GlyphType.ROTATING_CUBE -> createRotatingCubeBitmap(frame)
                GlyphType.DNA_HELIX -> createDnaHelixBitmap(frame)
                GlyphType.MATRIX_RAIN -> createMatrixRainBitmap(frame)
                GlyphType.HEARTBEAT -> createHeartbeatBitmap(frame)
                GlyphType.SPIRAL_GALAXY -> createSpiralGalaxyBitmap(frame)
            }
        }

        /** Figure-8 Möbius strip */
        private fun createMobiusFigure8Bitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val rotationAngle = (frame % 120) / 120.0 * 2 * kotlin.math.PI
            val scale = 9.0
            val stripWidth = 1.2

            val zBuffer = Array(MATRIX_SIZE) { FloatArray(MATRIX_SIZE) { Float.MAX_VALUE } }
            val brightnessBuffer = Array(MATRIX_SIZE) { IntArray(MATRIX_SIZE) { 0 } }

            val uSteps = 100
            val vSteps = 6

            for (ui in 0 until uSteps) {
                val u = ui.toDouble() / uSteps * 2 * kotlin.math.PI

                for (vi in 0..vSteps) {
                    val v = (vi.toDouble() / vSteps - 0.5) * 2 * stripWidth
                    val baseX = scale * kotlin.math.cos(u)
                    val baseY = scale * kotlin.math.sin(2 * u) / 2
                    val twistAngle = u / 2
                    val tangentX = -kotlin.math.sin(u)
                    val tangentY = kotlin.math.cos(2 * u)
                    val tangentLen = kotlin.math.sqrt(tangentX * tangentX + tangentY * tangentY)
                    val normalX = tangentY / tangentLen
                    val normalY = -tangentX / tangentLen
                    val cosTwist = kotlin.math.cos(twistAngle)
                    val sinTwist = kotlin.math.sin(twistAngle)
                    val x3d = baseX + v * normalX * cosTwist
                    val y3d = baseY + v * normalY * cosTwist
                    val z3d = v * sinTwist * 1.5
                    val cosRot = kotlin.math.cos(rotationAngle)
                    val sinRot = kotlin.math.sin(rotationAngle)
                    val xRot = x3d * cosRot - y3d * sinRot
                    val yRot = x3d * sinRot + y3d * cosRot
                    val zRot = z3d
                    val tiltAngle = 0.5
                    val cosTilt = kotlin.math.cos(tiltAngle)
                    val sinTilt = kotlin.math.sin(tiltAngle)
                    val yTilt = yRot * cosTilt - zRot * sinTilt
                    val zTilt = yRot * sinTilt + zRot * cosTilt
                    val screenX = (center + xRot).toInt()
                    val screenY = (center + yTilt).toInt()

                    if (screenX in 0 until MATRIX_SIZE && screenY in 0 until MATRIX_SIZE) {
                        val depth = zTilt.toFloat()
                        val maxDepth = scale + stripWidth * 2
                        val normalizedDepth =
                                ((zTilt + maxDepth) / (2 * maxDepth)).coerceIn(0.0, 1.0)
                        val baseBrightness = (80 + 175 * (1.0 - normalizedDepth)).toInt()
                        val edgeFactor = kotlin.math.abs(v) / stripWidth
                        val edgeBoost = (edgeFactor * 40).toInt()
                        val brightness = (baseBrightness + edgeBoost).coerceIn(50, 255)

                        if (depth < zBuffer[screenY][screenX]) {
                            zBuffer[screenY][screenX] = depth
                            brightnessBuffer[screenY][screenX] = brightness
                        }
                    }
                }
            }

            renderBuffer(canvas, brightnessBuffer)
            return bitmap
        }

        /** Circular Möbius ring */
        private fun createMobiusRingBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val rotationAngle = (frame % 120) / 120.0 * 2 * kotlin.math.PI
            val R = 8.0
            val stripWidth = 1.5

            val zBuffer = Array(MATRIX_SIZE) { FloatArray(MATRIX_SIZE) { Float.MAX_VALUE } }
            val brightnessBuffer = Array(MATRIX_SIZE) { IntArray(MATRIX_SIZE) { 0 } }

            val uSteps = 80
            val vSteps = 6

            for (ui in 0 until uSteps) {
                val u = ui.toDouble() / uSteps * 2 * kotlin.math.PI

                for (vi in 0..vSteps) {
                    val v = (vi.toDouble() / vSteps - 0.5) * 2 * stripWidth
                    val halfU = u / 2
                    val cosHalfU = kotlin.math.cos(halfU)
                    val sinHalfU = kotlin.math.sin(halfU)
                    val cosU = kotlin.math.cos(u)
                    val sinU = kotlin.math.sin(u)
                    val x3d = (R + v * cosHalfU) * cosU
                    val y3d = (R + v * cosHalfU) * sinU
                    val z3d = v * sinHalfU
                    val cosRot = kotlin.math.cos(rotationAngle)
                    val sinRot = kotlin.math.sin(rotationAngle)
                    val xRot = x3d * cosRot - z3d * sinRot
                    val zRot = x3d * sinRot + z3d * cosRot
                    val yRot = y3d
                    val tiltAngle = 0.4
                    val cosTilt = kotlin.math.cos(tiltAngle)
                    val sinTilt = kotlin.math.sin(tiltAngle)
                    val yTilt = yRot * cosTilt - zRot * sinTilt
                    val zTilt = yRot * sinTilt + zRot * cosTilt
                    val screenX = (center + xRot).toInt()
                    val screenY = (center + yTilt).toInt()

                    if (screenX in 0 until MATRIX_SIZE && screenY in 0 until MATRIX_SIZE) {
                        val depth = zTilt.toFloat()
                        val normalizedDepth =
                                ((zTilt + R + stripWidth) / (2 * (R + stripWidth))).coerceIn(
                                        0.0,
                                        1.0
                                )
                        val brightness =
                                (100 + 155 * (1.0 - normalizedDepth)).toInt().coerceIn(60, 255)

                        if (depth < zBuffer[screenY][screenX]) {
                            zBuffer[screenY][screenX] = depth
                            brightnessBuffer[screenY][screenX] = brightness
                        }
                    }
                }
            }

            renderBuffer(canvas, brightnessBuffer)
            return bitmap
        }

        /** Pulse ring animation */
        private fun createPulseRingBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val progress = (frame % 60) / 60f
            val maxRadius = 11f

            // Draw multiple rings at different phases
            for (ringOffset in 0..2) {
                val ringProgress = (progress + ringOffset * 0.33f) % 1f
                val radius = ringProgress * maxRadius
                val alpha = (255 * (1f - ringProgress)).toInt().coerceIn(0, 255)

                if (alpha > 20) {
                    val paint =
                            Paint().apply {
                                color = Color.rgb(alpha, alpha, alpha)
                                style = Paint.Style.STROKE
                                strokeWidth = 1.5f
                                isAntiAlias = false
                            }
                    canvas.drawCircle(center, center, radius, paint)
                }
            }

            // Center dot
            val centerPulse = ((kotlin.math.sin(frame * 0.15) + 1) / 2 * 100 + 155).toInt()
            val centerPaint =
                    Paint().apply {
                        color = Color.rgb(centerPulse, centerPulse, centerPulse)
                        isAntiAlias = false
                    }
            canvas.drawCircle(center, center, 2f, centerPaint)

            return bitmap
        }

        /** Rotating wireframe cube */
        private fun createRotatingCubeBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val size = 7.0
            val angleY = (frame % 120) / 120.0 * 2 * kotlin.math.PI
            val angleX = (frame % 180) / 180.0 * 2 * kotlin.math.PI

            // Cube vertices
            val vertices =
                    arrayOf(
                            doubleArrayOf(-size, -size, -size),
                            doubleArrayOf(size, -size, -size),
                            doubleArrayOf(size, size, -size),
                            doubleArrayOf(-size, size, -size),
                            doubleArrayOf(-size, -size, size),
                            doubleArrayOf(size, -size, size),
                            doubleArrayOf(size, size, size),
                            doubleArrayOf(-size, size, size)
                    )

            // Edges
            val edges =
                    arrayOf(
                            intArrayOf(0, 1),
                            intArrayOf(1, 2),
                            intArrayOf(2, 3),
                            intArrayOf(3, 0),
                            intArrayOf(4, 5),
                            intArrayOf(5, 6),
                            intArrayOf(6, 7),
                            intArrayOf(7, 4),
                            intArrayOf(0, 4),
                            intArrayOf(1, 5),
                            intArrayOf(2, 6),
                            intArrayOf(3, 7)
                    )

            // Rotate and project vertices
            val projected =
                    vertices.map { v ->
                        // Rotate around Y
                        var x = v[0] * kotlin.math.cos(angleY) - v[2] * kotlin.math.sin(angleY)
                        var z = v[0] * kotlin.math.sin(angleY) + v[2] * kotlin.math.cos(angleY)
                        var y = v[1]
                        // Rotate around X
                        val y2 = y * kotlin.math.cos(angleX) - z * kotlin.math.sin(angleX)
                        val z2 = y * kotlin.math.sin(angleX) + z * kotlin.math.cos(angleX)
                        // Project
                        floatArrayOf((center + x).toFloat(), (center + y2).toFloat(), z2.toFloat())
                    }

            val paint =
                    Paint().apply {
                        strokeWidth = 1f
                        isAntiAlias = false
                    }

            // Draw edges with depth-based brightness
            for (edge in edges) {
                val p1 = projected[edge[0]]
                val p2 = projected[edge[1]]
                val avgDepth = (p1[2] + p2[2]) / 2
                val brightness = (180 + avgDepth * 5).toInt().coerceIn(80, 255)
                paint.color = Color.rgb(brightness, brightness, brightness)
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paint)
            }

            return bitmap
        }

        /** DNA double helix */
        private fun createDnaHelixBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val offset = (frame % 60) / 60f * 2 * kotlin.math.PI

            val paint = Paint().apply { isAntiAlias = false }

            for (i in 0 until MATRIX_SIZE) {
                val y = i.toFloat()
                val phase = (i / MATRIX_SIZE.toFloat()) * 4 * kotlin.math.PI + offset

                // Two strands
                val x1 = center + 6 * kotlin.math.sin(phase).toFloat()
                val x2 = center + 6 * kotlin.math.sin(phase + kotlin.math.PI).toFloat()

                // Depth-based brightness
                val depth1 = kotlin.math.cos(phase)
                val depth2 = kotlin.math.cos(phase + kotlin.math.PI)
                val brightness1 = (150 + 100 * depth1).toInt().coerceIn(50, 255)
                val brightness2 = (150 + 100 * depth2).toInt().coerceIn(50, 255)

                paint.color = Color.rgb(brightness1, brightness1, brightness1)
                canvas.drawPoint(x1, y, paint)

                paint.color = Color.rgb(brightness2, brightness2, brightness2)
                canvas.drawPoint(x2, y, paint)

                // Cross-links every few pixels
                if (i % 4 == 0) {
                    val linkBrightness = ((brightness1 + brightness2) / 2 * 0.6).toInt()
                    paint.color = Color.rgb(linkBrightness, linkBrightness, linkBrightness)
                    canvas.drawLine(x1, y, x2, y, paint)
                }
            }

            return bitmap
        }

        /** Matrix rain effect */
        private fun createMatrixRainBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val paint = Paint().apply { isAntiAlias = false }

            // Use frame as seed for pseudo-random but deterministic columns
            val columnSpeeds = IntArray(MATRIX_SIZE) { col -> (col * 7 + 3) % 5 + 2 }
            val columnOffsets = IntArray(MATRIX_SIZE) { col -> (col * 13 + 7) % MATRIX_SIZE }

            for (col in 0 until MATRIX_SIZE) {
                val speed = columnSpeeds[col]
                val baseOffset = columnOffsets[col]
                val dropHead = (frame * speed / 3 + baseOffset) % (MATRIX_SIZE + 10)

                for (row in 0 until MATRIX_SIZE) {
                    val distFromHead = dropHead - row
                    if (distFromHead in 0..8) {
                        val brightness = (255 - distFromHead * 28).coerceIn(40, 255)
                        paint.color = Color.rgb(brightness, brightness, brightness)
                        canvas.drawPoint(col.toFloat(), row.toFloat(), paint)
                    }
                }
            }

            return bitmap
        }

        /** Heartbeat pulse */
        private fun createHeartbeatBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val cycleFrame = frame % 60

            // Heartbeat timing: quick double pulse then pause
            val scale =
                    when {
                        cycleFrame < 5 -> 1f + cycleFrame * 0.06f // First beat up
                        cycleFrame < 10 -> 1.3f - (cycleFrame - 5) * 0.06f // First beat down
                        cycleFrame < 15 -> 1f + (cycleFrame - 10) * 0.04f // Second beat up
                        cycleFrame < 20 -> 1.2f - (cycleFrame - 15) * 0.04f // Second beat down
                        else -> 1f // Rest
                    }

            val brightness = (180 * scale).toInt().coerceIn(100, 255)
            val paint =
                    Paint().apply {
                        color = Color.rgb(brightness, brightness, brightness)
                        isAntiAlias = false
                    }

            // Draw heart shape
            val heartSize = 5f * scale
            for (t in 0 until 360 step 5) {
                val rad = t * kotlin.math.PI / 180
                // Heart parametric equations
                val x = 16 * kotlin.math.sin(rad).pow(3.0)
                val y =
                        -(13 * kotlin.math.cos(rad) -
                                5 * kotlin.math.cos(2 * rad) -
                                2 * kotlin.math.cos(3 * rad) -
                                kotlin.math.cos(4 * rad))

                val px = (center + x * heartSize / 16).toFloat()
                val py = (center + y * heartSize / 16 - 1).toFloat()

                if (px >= 0f && px < MATRIX_SIZE && py >= 0f && py < MATRIX_SIZE) {
                    canvas.drawPoint(px, py, paint)
                }
            }

            return bitmap
        }

        /** Spiral galaxy */
        private fun createSpiralGalaxyBitmap(frame: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val center = MATRIX_SIZE / 2f
            val rotation = (frame % 180) / 180.0 * 2 * kotlin.math.PI

            val paint = Paint().apply { isAntiAlias = false }

            // Draw spiral arms
            for (arm in 0..1) {
                val armOffset = arm * kotlin.math.PI

                for (i in 0 until 80) {
                    val t = i / 80.0 * 3 * kotlin.math.PI
                    val r = 1 + t * 3
                    val angle = t + rotation + armOffset

                    val x = center + (r * kotlin.math.cos(angle)).toFloat()
                    val y = center + (r * kotlin.math.sin(angle)).toFloat()

                    val brightness = (255 - i * 2.5).toInt().coerceIn(40, 255)
                    paint.color = Color.rgb(brightness, brightness, brightness)

                    if (x >= 0f && x < MATRIX_SIZE && y >= 0f && y < MATRIX_SIZE) {
                        canvas.drawPoint(x, y, paint)
                    }
                }
            }

            // Bright center
            paint.color = Color.rgb(255, 255, 255)
            canvas.drawCircle(center, center, 2f, paint)

            return bitmap
        }

        /** Helper to render brightness buffer to canvas */
        private fun renderBuffer(canvas: Canvas, brightnessBuffer: Array<IntArray>) {
            for (y in 0 until MATRIX_SIZE) {
                for (x in 0 until MATRIX_SIZE) {
                    val brightness = brightnessBuffer[y][x]
                    if (brightness > 0) {
                        val paint =
                                Paint().apply {
                                    color = Color.rgb(brightness, brightness, brightness)
                                    isAntiAlias = false
                                }
                        canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
                    }
                }
            }
        }
    }

    // Current glyph type
    private var currentGlyphType: GlyphType = GlyphType.MOBIUS_FIGURE_8

    // GlyphMatrix Manager from Nothing SDK (NOT GlyphManager which is for LED strips)
    private var glyphMatrixManager: GlyphMatrixManager? = null

    // State tracking
    private var isInitialized = false
    private var isServiceConnected = false
    private var isDisplaying = false
    private var animationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    // Store the callback to invoke when service actually connects
    private var pendingCallback: GlyphCallback? = null

    // GlyphMatrix callback for SDK initialization
    private val glyphCallback =
            object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    Log.d(TAG, "GlyphMatrix service connected: $componentName")
                    DebugLogManager.info(TAG, "Glyph Connected", "Service: $componentName")

                    // Register for Phone 3 (25x25 matrix)
                    glyphMatrixManager?.register(DEVICE_PHONE_3)
                    isServiceConnected = true

                    // NOW invoke the success callback - service is actually ready
                    pendingCallback?.onSuccess()
                    pendingCallback = null
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    Log.d(TAG, "GlyphMatrix service disconnected: $componentName")
                    DebugLogManager.info(TAG, "Glyph Disconnected", "Service: $componentName")
                    isServiceConnected = false
                }
            }

    /** Callback interface for Glyph operations */
    interface GlyphCallback {
        fun onSuccess()
        fun onFailure(error: String)
    }

    /** Initialize the GlyphMatrix SDK. Call this when accessibility service starts. */
    fun initialize(callback: GlyphCallback? = null) {
        Log.d(TAG, "Initializing GlyphMatrix SDK...")
        DebugLogManager.info(TAG, "Glyph Init", "Connecting to Nothing GlyphMatrix service")

        // Store callback to invoke when service actually connects
        pendingCallback = callback

        try {
            glyphMatrixManager =
                    GlyphMatrixManager.getInstance(context.applicationContext).also { manager ->
                        manager.init(glyphCallback)
                    }
            isInitialized = true
            Log.d(TAG, "GlyphMatrix SDK init called, waiting for service connection...")
            // Don't call callback here - wait for onServiceConnected
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GlyphMatrix SDK", e)
            DebugLogManager.error(TAG, "Glyph Init Failed", e.message ?: "Unknown error")
            pendingCallback = null
            callback?.onFailure(e.message ?: "Unknown error")
        }
    }

    /** Set the current glyph type to display. */
    fun setGlyphType(type: GlyphType) {
        currentGlyphType = type
        Log.d(TAG, "Glyph type set to: ${type.displayName}")
        DebugLogManager.info(TAG, "Glyph Type", "Changed to ${type.displayName}")
    }

    /** Get the current glyph type. */
    fun getGlyphType(): GlyphType = currentGlyphType

    /** Display a static glyph on the Glyph Matrix LEDs. */
    fun displayLogo() {
        if (!isInitialized || glyphMatrixManager == null) {
            Log.w(TAG, "GlyphMatrix not initialized")
            return
        }

        if (!isServiceConnected) {
            Log.w(TAG, "GlyphMatrix service not connected")
            return
        }

        if (isDisplaying) {
            Log.d(TAG, "Already displaying")
            return
        }

        isDisplaying = true
        Log.d(TAG, "Displaying ${currentGlyphType.displayName} on GlyphMatrix")
        DebugLogManager.info(TAG, "Glyph Display", "Showing ${currentGlyphType.displayName}")

        try {
            val bitmap = generatePreviewBitmap(currentGlyphType, 0)
            val logoObject =
                    GlyphMatrixObject.Builder()
                            .setImageSource(bitmap)
                            .setPosition(0, 0)
                            .setScale(100)
                            .setOrientation(0)
                            .setBrightness(255)
                            .build()
            val matrixFrame = GlyphMatrixFrame.Builder().addTop(logoObject).build(context)
            glyphMatrixManager?.setAppMatrixFrame(matrixFrame.render())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display glyph", e)
            DebugLogManager.error(TAG, "Glyph Display Failed", e.message ?: "Unknown error")
            isDisplaying = false
        }
    }

    /** Display an animated glyph. */
    fun displayBreathingLogo() {
        if (!isInitialized || glyphMatrixManager == null) {
            Log.w(TAG, "GlyphMatrix not initialized")
            return
        }

        if (!isServiceConnected) {
            Log.w(TAG, "GlyphMatrix service not connected")
            return
        }

        // Cancel any existing animation
        animationJob?.cancel()

        isDisplaying = true
        Log.d(TAG, "Starting ${currentGlyphType.displayName} animation on GlyphMatrix")
        DebugLogManager.info(TAG, "Glyph Animation", "Animating ${currentGlyphType.displayName}")

        animationJob =
                scope.launch {
                    var frame = 0

                    while (isActive && isDisplaying) {
                        try {
                            val bitmap = generatePreviewBitmap(currentGlyphType, frame)
                            val logoObject =
                                    GlyphMatrixObject.Builder()
                                            .setImageSource(bitmap)
                                            .setPosition(0, 0)
                                            .setScale(100)
                                            .setOrientation(0)
                                            .setBrightness(255)
                                            .build()
                            val matrixFrame =
                                    GlyphMatrixFrame.Builder().addTop(logoObject).build(context)
                            glyphMatrixManager?.setAppMatrixFrame(matrixFrame.render())
                        } catch (e: Exception) {
                            Log.e(TAG, "Animation frame failed", e)
                        }

                        frame++
                        delay(40) // ~25 FPS for smooth animation
                    }
                }
    }

    /**
     * Flash the Glyph Matrix once to provide feedback (e.g., Essential Button press). Briefly shows
     * full brightness then turns off.
     */
    suspend fun flashOnce() {
        Log.d(TAG, "Flashing GlyphMatrix once")

        if (!isReady()) {
            Log.w(TAG, "GlyphMatrix not ready for flash")
            return
        }

        try {
            // Create a full-brightness white frame
            val flashBitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flashBitmap)
            canvas.drawColor(Color.WHITE) // Full brightness

            val flashObject =
                    GlyphMatrixObject.Builder()
                            .setImageSource(flashBitmap)
                            .setPosition(0, 0)
                            .setScale(100)
                            .setOrientation(0)
                            .setBrightness(255) // Maximum brightness
                            .build()

            val matrixFrame = GlyphMatrixFrame.Builder().addTop(flashObject).build(context)
            glyphMatrixManager?.setAppMatrixFrame(matrixFrame.render())

            // Keep the flash visible
            delay(150)

            // Turn off
            glyphMatrixManager?.closeAppMatrix()
            Log.d(TAG, "Flash complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flash GlyphMatrix", e)
        }
    }

    /** Clear the Glyph Matrix display and turn off all LEDs. */
    fun clearDisplay() {
        Log.d(TAG, "Clearing GlyphMatrix display")

        // Stop any animation
        animationJob?.cancel()
        animationJob = null
        isDisplaying = false

        try {
            // closeAppMatrix() stops app-based matrix display
            glyphMatrixManager?.closeAppMatrix()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear display", e)
        }
    }

    /** Clean up resources and disconnect from the GlyphMatrix service. */
    fun cleanup() {
        Log.d(TAG, "Cleaning up GlyphMatrix Helper")
        clearDisplay()

        try {
            glyphMatrixManager?.unInit()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }

        glyphMatrixManager = null
        pendingCallback = null
        isInitialized = false
        isServiceConnected = false
    }

    /** Check if the GlyphMatrix is currently displaying. */
    fun isDisplaying(): Boolean = isDisplaying

    /** Check if the SDK is initialized and service connected. */
    fun isReady(): Boolean = isInitialized && isServiceConnected
}
