package com.example.omni_link.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.omni_link.data.AIAction
import com.example.omni_link.data.ActionResult
import com.example.omni_link.data.ScreenElement
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.ui.OmniFloatingButton
import com.example.omni_link.ui.OmniOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Enhanced Accessibility Service for Omni-Link AI Assistant Captures screen content and executes
 * AI-directed actions
 */
class OmniAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "OmniService"
        private const val DEBOUNCE_DELAY_MS = 300L

        // Singleton instance for communication with UI
        var instance: OmniAccessibilityService? = null
            private set

        // Observable screen state
        private val _screenState = MutableStateFlow<ScreenState?>(null)
        val screenState: StateFlow<ScreenState?> = _screenState

        // Service status
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        // Suggestion state for overlay
        private val _suggestionState = MutableStateFlow(SuggestionState())
        val suggestionState: StateFlow<SuggestionState> = _suggestionState

        // Callbacks for overlay interactions
        var onSuggestionButtonClicked: (() -> Unit)? = null
        var onSuggestionClicked: ((Suggestion) -> Unit)? = null
        var onDismissSuggestions: (() -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var floatingButtonView: FrameLayout? = null

    // Debounce job for screen capture - cancel previous job when new event arrives
    private var screenCaptureJob: Job? = null

    // Track if floating overlay is enabled
    private var isFloatingOverlayEnabled = false

    // Lifecycle management for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        Log.d(TAG, "Omni Accessibility Service connected")

        // Initial screen capture
        captureScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        instance = null
        _isRunning.value = false
        removeOverlay()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        removeOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Capture screen on significant UI changes
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Proper debouncing - cancel previous job and start new one
                screenCaptureJob?.cancel()
                screenCaptureJob =
                        serviceScope.launch {
                            delay(DEBOUNCE_DELAY_MS)
                            captureScreen()
                        }
            }
        }
    }

    /** Capture the current screen state */
    fun captureScreen(): ScreenState? {
        val root = rootInActiveWindow ?: return null

        val packageName = root.packageName?.toString() ?: "unknown"
        val activityName =
                try {
                    val event = AccessibilityEvent.obtain()
                    event.className?.toString()
                } catch (e: Exception) {
                    null
                }

        val elements = mutableListOf<ScreenElement>()
        extractElements(root, elements)

        val state =
                ScreenState(
                        packageName = packageName,
                        activityName = activityName,
                        timestamp = System.currentTimeMillis(),
                        elements = elements
                )

        _screenState.value = state
        Log.d(TAG, "Captured ${state.flattenElements().size} elements from $packageName")

        return state
    }

    /** Extract UI elements recursively */
    private fun extractElements(node: AccessibilityNodeInfo?, list: MutableList<ScreenElement>) {
        if (node == null) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val children = mutableListOf<ScreenElement>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractElements(child, children)
                child.recycle()
            }
        }

        val element =
                ScreenElement(
                        id = node.viewIdResourceName?.substringAfterLast("/"),
                        text = node.text?.toString(),
                        contentDescription = node.contentDescription?.toString(),
                        className = node.className?.toString() ?: "Unknown",
                        bounds = bounds,
                        isClickable = node.isClickable,
                        isEditable = node.isEditable,
                        isScrollable = node.isScrollable,
                        isCheckable = node.isCheckable,
                        isChecked = node.isChecked,
                        children = children
                )

        // Only add meaningful elements
        if (element.text != null ||
                        element.contentDescription != null ||
                        element.isClickable ||
                        element.isEditable ||
                        children.isNotEmpty()
        ) {
            list.add(element)
        }
    }

    /** Execute an AI action on the device */
    suspend fun executeAction(action: AIAction): ActionResult {
        Log.d(TAG, "Executing action: $action")

        return when (action) {
            is AIAction.Click -> executeClick(action)
            is AIAction.Type -> executeType(action)
            is AIAction.Scroll -> executeScroll(action)
            is AIAction.Back -> executeBack()
            is AIAction.Home -> executeHome()
            is AIAction.OpenApp -> executeOpenApp(action)
            is AIAction.Wait -> {
                delay(action.milliseconds)
                ActionResult.Success("Waited ${action.milliseconds}ms")
            }
            is AIAction.Respond -> ActionResult.Success(action.message)
            is AIAction.Clarify -> ActionResult.Success(action.question)
            is AIAction.Complete -> ActionResult.Success(action.summary)
        }
    }

    /** Click on an element by label or index */
    private suspend fun executeClick(action: AIAction.Click): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult.Failure("Cannot access screen")
        val state = captureScreen() ?: return ActionResult.Failure("Cannot capture screen")

        // Find by index if provided
        if (action.index != null) {
            val element = state.findByIndex(action.index)
            if (element != null) {
                return clickOnBounds(element.bounds)
            }
        }

        // Find by label
        val nodes = root.findAccessibilityNodeInfosByText(action.target)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (node.isClickable) {
                    val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    return if (success) {
                        ActionResult.Success("Clicked on '${action.target}'")
                    } else {
                        ActionResult.Failure("Click action failed")
                    }
                }

                // Try parent if node itself isn't clickable
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        parent.recycle()
                        node.recycle()
                        return if (success) {
                            ActionResult.Success("Clicked on '${action.target}'")
                        } else {
                            ActionResult.Failure("Click action failed")
                        }
                    }
                    val temp = parent
                    parent = parent.parent
                    temp.recycle()
                }
                node.recycle()
            }
        }

        // Fallback: find element in our captured state and use gesture
        val element = state.findByLabel(action.target)
        if (element != null) {
            return clickOnBounds(element.bounds)
        }

        return ActionResult.Failure("Could not find '${action.target}'")
    }

    /** Click using gesture on specific bounds */
    private fun clickOnBounds(bounds: Rect): ActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return ActionResult.Failure("Gesture clicks require Android N+")
        }

        val centerX = bounds.centerX().toFloat()
        val centerY = bounds.centerY().toFloat()

        val path = Path()
        path.moveTo(centerX, centerY)

        val gesture =
                GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                        .build()

        val success = dispatchGesture(gesture, null, null)
        return if (success) {
            ActionResult.Success("Clicked at ($centerX, $centerY)")
        } else {
            ActionResult.Failure("Gesture click failed")
        }
    }

    /** Type text into a field */
    private suspend fun executeType(action: AIAction.Type): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult.Failure("Cannot access screen")

        // Find editable field
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        findEditableNodes(root, editableNodes)

        // Try to find by target label first
        var targetNode: AccessibilityNodeInfo? = null
        if (action.target.isNotBlank()) {
            val labelNodes = root.findAccessibilityNodeInfosByText(action.target)
            if (!labelNodes.isNullOrEmpty()) {
                for (node in labelNodes) {
                    if (node.isEditable) {
                        targetNode = node
                        break
                    }
                }
            }
        }

        // Fallback to first editable field
        if (targetNode == null && editableNodes.isNotEmpty()) {
            targetNode = editableNodes.first()
        }

        if (targetNode == null) {
            return ActionResult.Failure("No text field found")
        }

        // Focus the field
        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Clear if requested
        if (action.clearFirst) {
            val args = Bundle()
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Int.MAX_VALUE)
            targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
        }

        // Set text
        val textArgs = Bundle()
        textArgs.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                action.text
        )
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArgs)

        targetNode.recycle()

        return if (success) {
            ActionResult.Success("Typed '${action.text}'")
        } else {
            ActionResult.Failure("Failed to type text")
        }
    }

    private fun findEditableNodes(
            node: AccessibilityNodeInfo?,
            list: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return
        if (node.isEditable) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            findEditableNodes(node.getChild(i), list)
        }
    }

    /** Scroll in a direction */
    private fun executeScroll(action: AIAction.Scroll): ActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // Fallback to accessibility scroll action
            val root = rootInActiveWindow ?: return ActionResult.Failure("Cannot access screen")
            val scrollAction =
                    when (action.direction) {
                        AIAction.ScrollDirection.UP, AIAction.ScrollDirection.LEFT ->
                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        AIAction.ScrollDirection.DOWN, AIAction.ScrollDirection.RIGHT ->
                                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    }
            return if (root.performAction(scrollAction)) {
                ActionResult.Success("Scrolled ${action.direction}")
            } else {
                ActionResult.Failure("Scroll failed")
            }
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val (startX, startY, endX, endY) =
                when (action.direction) {
                    AIAction.ScrollDirection.DOWN ->
                            listOf(
                                    screenWidth / 2,
                                    screenHeight * 0.7f,
                                    screenWidth / 2,
                                    screenHeight * 0.3f
                            )
                    AIAction.ScrollDirection.UP ->
                            listOf(
                                    screenWidth / 2,
                                    screenHeight * 0.3f,
                                    screenWidth / 2,
                                    screenHeight * 0.7f
                            )
                    AIAction.ScrollDirection.LEFT ->
                            listOf(
                                    screenWidth * 0.7f,
                                    screenHeight / 2,
                                    screenWidth * 0.3f,
                                    screenHeight / 2
                            )
                    AIAction.ScrollDirection.RIGHT ->
                            listOf(
                                    screenWidth * 0.3f,
                                    screenHeight / 2,
                                    screenWidth * 0.7f,
                                    screenHeight / 2
                            )
                }

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val gesture =
                GestureDescription.Builder()
                        .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                        .build()

        return if (dispatchGesture(gesture, null, null)) {
            ActionResult.Success("Scrolled ${action.direction}")
        } else {
            ActionResult.Failure("Scroll gesture failed")
        }
    }

    /** Press back button */
    private fun executeBack(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_BACK)) {
            ActionResult.Success("Pressed back")
        } else {
            ActionResult.Failure("Back action failed")
        }
    }

    /** Go to home screen */
    private fun executeHome(): ActionResult {
        return if (performGlobalAction(GLOBAL_ACTION_HOME)) {
            ActionResult.Success("Went to home screen")
        } else {
            ActionResult.Failure("Home action failed")
        }
    }

    /** Open an app by name */
    /** Open an app by name - searches flexibly by label */
    private fun executeOpenApp(action: AIAction.OpenApp): ActionResult {
        val appName = action.appName.trim()

        // Common package name mappings
        val knownPackages =
                mapOf(
                        "settings" to "com.android.settings",
                        "chrome" to "com.android.chrome",
                        "browser" to "com.android.chrome",
                        "messages" to "com.google.android.apps.messaging",
                        "sms" to "com.google.android.apps.messaging",
                        "phone" to "com.google.android.dialer",
                        "dialer" to "com.google.android.dialer",
                        "contacts" to "com.google.android.contacts",
                        "camera" to "com.android.camera2",
                        "photos" to "com.google.android.apps.photos",
                        "gallery" to "com.google.android.apps.photos",
                        "clock" to "com.google.android.deskclock",
                        "alarm" to "com.google.android.deskclock",
                        "calendar" to "com.google.android.calendar",
                        "gmail" to "com.google.android.gm",
                        "email" to "com.google.android.gm",
                        "maps" to "com.google.android.apps.maps",
                        "youtube" to "com.google.android.youtube",
                        "play store" to "com.android.vending",
                        "files" to "com.google.android.documentsui",
                        "calculator" to "com.google.android.calculator"
                )

        // Try known package first
        val knownPackage = knownPackages[appName.lowercase()]
        if (knownPackage != null) {
            val intent = packageManager.getLaunchIntentForPackage(knownPackage)
            if (intent != null) {
                return try {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    ActionResult.Success("Opened $appName")
                } catch (e: Exception) {
                    ActionResult.Failure("Failed to open $appName: ${e.message}")
                }
            }
        }

        // Try direct package name
        val directIntent = packageManager.getLaunchIntentForPackage(appName)
        if (directIntent != null) {
            return try {
                startActivity(directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionResult.Success("Opened $appName")
            } catch (e: Exception) {
                ActionResult.Failure("Failed to open $appName: ${e.message}")
            }
        }

        // Search all launcher apps by label
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = packageManager.queryIntentActivities(launcherIntent, 0)

        // Find best match
        val matchingApp =
                activities.find { resolveInfo ->
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    label.equals(appName, ignoreCase = true) ||
                            label.contains(appName, ignoreCase = true) ||
                            appName.contains(label, ignoreCase = true)
                }

        if (matchingApp != null) {
            val intent =
                    Intent().apply {
                        setClassName(
                                matchingApp.activityInfo.packageName,
                                matchingApp.activityInfo.name
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            return try {
                startActivity(intent)
                val label = matchingApp.loadLabel(packageManager)
                ActionResult.Success("Opened $label")
            } catch (e: Exception) {
                ActionResult.Failure("Failed to open app: ${e.message}")
            }
        }

        // App not found - suggest alternatives
        val availableApps =
                activities
                        .map { it.loadLabel(packageManager).toString() }
                        .filter { it.isNotBlank() }
                        .take(10)
                        .joinToString(", ")

        return ActionResult.Failure(
                "App '$appName' not found. Available apps include: $availableApps"
        )
    }

    /** Add an overlay view (for floating UI) */
    fun addOverlay(composeContent: @Composable () -> Unit) {
        removeOverlay()

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 16
        params.y = 100

        val container = FrameLayout(this)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val composeView = ComposeView(this).apply { setContent(composeContent) }
        container.addView(composeView)

        overlayView = container
        windowManager?.addView(container, params)
    }

    /** Remove overlay view */
    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
        overlayView = null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FLOATING BUTTON OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Show the floating suggestion button overlay */
    fun showFloatingButton() {
        if (floatingButtonView != null) return
        isFloatingOverlayEnabled = true

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = 24
        params.y = 200

        val container = FrameLayout(this)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val composeView =
                ComposeView(this).apply {
                    setContent {
                        val suggestionState = _suggestionState.collectAsState()

                        OmniFloatingButton(
                                onClick = {
                                    Log.d(TAG, "Floating button clicked")
                                    onSuggestionButtonClicked?.invoke()
                                },
                                isLoading = suggestionState.value.isLoading
                        )
                    }
                }
        container.addView(composeView)

        floatingButtonView = container
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Floating button overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding floating button overlay", e)
            floatingButtonView = null
        }
    }

    /** Remove the floating button overlay */
    fun hideFloatingButton() {
        isFloatingOverlayEnabled = false
        floatingButtonView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Floating button overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button overlay", e)
            }
        }
        floatingButtonView = null
    }

    /** Toggle floating button visibility */
    fun toggleFloatingButton() {
        if (floatingButtonView != null) {
            hideFloatingButton()
        } else {
            showFloatingButton()
        }
    }

    /** Update suggestion state (called from ViewModel) */
    fun updateSuggestionState(state: SuggestionState) {
        val wasVisible = _suggestionState.value.isVisible
        _suggestionState.value = state

        // Show/hide suggestions overlay based on visibility state
        if (state.isVisible && !wasVisible) {
            Log.d(TAG, "Showing suggestions overlay (isVisible changed to true)")
            showSuggestionsOverlay()
        } else if (!state.isVisible && wasVisible) {
            Log.d(TAG, "Hiding suggestions overlay (isVisible changed to false)")
            hideSuggestionsOverlay()
        }
    }

    /** Check if floating overlay is currently enabled */
    fun isFloatingOverlayEnabled(): Boolean = isFloatingOverlayEnabled

    /** Show full overlay with suggestions panel */
    fun showSuggestionsOverlay() {
        // Hide floating button while showing full overlay
        floatingButtonView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button for overlay", e)
            }
        }
        floatingButtonView = null

        removeOverlay()

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.BOTTOM

        val container = FrameLayout(this)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val composeView =
                ComposeView(this).apply {
                    setContent {
                        val suggestionState = _suggestionState.collectAsState()

                        OmniOverlay(
                                state = suggestionState.value,
                                onButtonClick = {
                                    Log.d(TAG, "Overlay button clicked")
                                    onSuggestionButtonClicked?.invoke()
                                },
                                onSuggestionClick = { suggestion ->
                                    Log.d(TAG, "Suggestion clicked: ${suggestion.title}")
                                    onSuggestionClicked?.invoke(suggestion)
                                },
                                onDismiss = {
                                    Log.d(TAG, "Suggestions dismissed")
                                    onDismissSuggestions?.invoke()
                                }
                        )
                    }
                }
        container.addView(composeView)

        overlayView = container
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Suggestions overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding suggestions overlay", e)
            overlayView = null
        }
    }

    /** Hide the suggestions overlay and show floating button if enabled */
    fun hideSuggestionsOverlay() {
        removeOverlay()
        if (isFloatingOverlayEnabled) {
            showFloatingButton()
        }
    }
}
