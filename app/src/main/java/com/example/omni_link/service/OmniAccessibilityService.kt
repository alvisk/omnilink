package com.example.omni_link.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.omni_link.ai.TextOption
import com.example.omni_link.data.AIAction
import com.example.omni_link.data.ActionResult
import com.example.omni_link.data.ScreenElement
import com.example.omni_link.data.ScreenState
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.data.TextSelectionState
import com.example.omni_link.debug.DebugLogManager
import com.example.omni_link.ocr.TextRecognitionHelper
import com.example.omni_link.ui.DebugFloatingButton
import com.example.omni_link.ui.DebugOverlayPanel
import com.example.omni_link.ui.FocusAreaSelectorOverlay
import com.example.omni_link.ui.OmniFloatingButton
import com.example.omni_link.ui.OmniOverlay
import com.example.omni_link.ui.TextSelectionButton
import com.example.omni_link.ui.TextSelectionOverlay
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

        // Focus area callbacks
        var onFocusAreaSelectionStart: ((Float, Float) -> Unit)? = null
        var onFocusAreaSelectionUpdate: ((Float, Float) -> Unit)? = null
        var onFocusAreaSelectionEnd: (() -> Unit)? = null
        var onFocusAreaClear: (() -> Unit)? = null
        var onFocusAreaConfirm: (() -> Unit)? = null

        // Text selection state for Circle-to-Search
        private val _textSelectionState = MutableStateFlow(TextSelectionState())
        val textSelectionState: StateFlow<TextSelectionState> = _textSelectionState

        // Text selection callbacks
        var onTextBlockTapped: ((Int) -> Unit)? = null
        var onTextBlocksSelected: ((List<Int>) -> Unit)? = null
        var onTextSelectionCopy: (() -> Unit)? = null
        var onTextSelectionSearch: (() -> Unit)? = null
        var onTextSelectionShare: (() -> Unit)? = null
        var onTextSelectionDismiss: (() -> Unit)? = null

        // Text option callbacks (Circle-to-Search AI options)
        var onGenerateTextOptions: (() -> Unit)? = null
        var onTextOptionSelected: ((TextOption) -> Unit)? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var floatingButtonView: FrameLayout? = null
    private var debugButtonView: FrameLayout? = null
    private var debugOverlayView: FrameLayout? = null
    private var focusAreaSelectorView: FrameLayout? = null
    private var textSelectionOverlayView: FrameLayout? = null

    // OCR helper for text recognition
    private val textRecognitionHelper = TextRecognitionHelper()

    // Debounce job for screen capture - cancel previous job when new event arrives
    private var screenCaptureJob: Job? = null

    // Track if floating overlay is enabled
    private var isFloatingOverlayEnabled = false

    // Track if debug overlay is enabled
    private var isDebugOverlayEnabled = false

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

        // Skip events from our own app to prevent feedback loops with overlays
        val eventPackage = event.packageName?.toString() ?: ""
        if (eventPackage.contains("omni_link")) {
            return
        }

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
        val elementCount = state.flattenElements().size
        Log.d(TAG, "Captured $elementCount elements from $packageName")

        // Skip debug logging for our own app to prevent feedback loop
        // (debug overlay updates trigger accessibility events which would cause infinite loop)
        if (!packageName.contains("omni_link")) {
            DebugLogManager.debug(
                    TAG,
                    "Screen captured: $packageName",
                    "Elements: $elementCount\n" + "Activity: ${activityName ?: "unknown"}"
            )
        }

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

        DebugLogManager.action(TAG, "Executing: ${action.javaClass.simpleName}", action.toString())

        val result =
                when (action) {
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
                    // Device Intent Actions
                    is AIAction.OpenCalendar -> executeOpenCalendar(action)
                    is AIAction.DialNumber -> executeDialNumber(action)
                    is AIAction.CallNumber -> executeCallNumber(action)
                    is AIAction.SendSMS -> executeSendSMS(action)
                    is AIAction.OpenURL -> executeOpenURL(action)
                    is AIAction.WebSearch -> executeWebSearch(action)
                    is AIAction.SetAlarm -> executeSetAlarm(action)
                    is AIAction.SetTimer -> executeSetTimer(action)
                    is AIAction.ShareText -> executeShareText(action)
                    is AIAction.CopyToClipboard -> executeCopyToClipboard(action)
                    is AIAction.SendEmail -> executeSendEmail(action)
                    is AIAction.OpenMaps -> executeOpenMaps(action)
                    is AIAction.PlayMedia -> executePlayMedia(action)
                    is AIAction.CaptureMedia -> executeCaptureMedia(action)
                    is AIAction.OpenSettings -> executeOpenSettings(action)
                }

        // Log result
        when (result) {
            is ActionResult.Success ->
                    DebugLogManager.success(TAG, "Action succeeded", result.description)
            is ActionResult.Failure -> DebugLogManager.error(TAG, "Action failed", result.reason)
            is ActionResult.NeedsConfirmation ->
                    DebugLogManager.info(TAG, "Action needs confirmation", result.reason)
        }

        return result
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

    // ═══════════════════════════════════════════════════════════════════════════
    // DEVICE INTENT ACTION IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Open calendar, optionally creating an event */
    private fun executeOpenCalendar(action: AIAction.OpenCalendar): ActionResult {
        return try {
            val intent =
                    if (action.title != null) {
                        // Create a new calendar event
                        Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, action.title)
                            action.description?.let {
                                putExtra(CalendarContract.Events.DESCRIPTION, it)
                            }
                            action.location?.let {
                                putExtra(CalendarContract.Events.EVENT_LOCATION, it)
                            }
                            action.startTime?.let {
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
                            }
                            action.endTime?.let {
                                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it)
                            }
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        // Just open calendar
                        Intent(Intent.ACTION_VIEW).apply {
                            data =
                                    CalendarContract.CONTENT_URI
                                            .buildUpon()
                                            .appendPath("time")
                                            .appendPath(
                                                    (action.startTime ?: System.currentTimeMillis())
                                                            .toString()
                                            )
                                            .build()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
            startActivity(intent)
            if (action.title != null) {
                ActionResult.Success("Creating calendar event: ${action.title}")
            } else {
                ActionResult.Success("Opened calendar")
            }
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open calendar: ${e.message}")
        }
    }

    /** Open the dialer with a phone number (user must tap call) */
    private fun executeDialNumber(action: AIAction.DialNumber): ActionResult {
        return try {
            val intent =
                    Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${action.phoneNumber}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Opened dialer with ${action.phoneNumber}")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open dialer: ${e.message}")
        }
    }

    /** Directly call a phone number (requires CALL_PHONE permission) */
    private fun executeCallNumber(action: AIAction.CallNumber): ActionResult {
        return try {
            val intent =
                    Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:${action.phoneNumber}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Calling ${action.phoneNumber}")
        } catch (e: SecurityException) {
            // Fallback to dial if CALL_PHONE permission not granted
            executeDialNumber(AIAction.DialNumber(action.phoneNumber))
        } catch (e: Exception) {
            ActionResult.Failure("Failed to make call: ${e.message}")
        }
    }

    /** Compose an SMS message */
    private fun executeSendSMS(action: AIAction.SendSMS): ActionResult {
        return try {
            val intent =
                    Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${action.phoneNumber}")
                        putExtra("sms_body", action.message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Composing SMS to ${action.phoneNumber}")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to compose SMS: ${e.message}")
        }
    }

    /** Open a URL in the browser */
    private fun executeOpenURL(action: AIAction.OpenURL): ActionResult {
        return try {
            var url = action.url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Opening $url")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open URL: ${e.message}")
        }
    }

    /** Search the web */
    private fun executeWebSearch(action: AIAction.WebSearch): ActionResult {
        return try {
            val intent =
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, action.query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Searching for: ${action.query}")
        } catch (e: Exception) {
            // Fallback to browser with Google search
            try {
                val searchUrl = "https://www.google.com/search?q=${Uri.encode(action.query)}"
                executeOpenURL(AIAction.OpenURL(searchUrl))
            } catch (e2: Exception) {
                ActionResult.Failure("Failed to search: ${e.message}")
            }
        }
    }

    /** Set an alarm */
    private fun executeSetAlarm(action: AIAction.SetAlarm): ActionResult {
        return try {
            val intent =
                    Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        action.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                        action.days?.let { putExtra(AlarmClock.EXTRA_DAYS, ArrayList(it)) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            val timeStr = String.format("%02d:%02d", action.hour, action.minute)
            ActionResult.Success("Setting alarm for $timeStr")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to set alarm: ${e.message}")
        }
    }

    /** Set a timer */
    private fun executeSetTimer(action: AIAction.SetTimer): ActionResult {
        return try {
            val intent =
                    Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        action.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            val minutes = action.seconds / 60
            val secs = action.seconds % 60
            ActionResult.Success("Setting timer for ${minutes}m ${secs}s")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to set timer: ${e.message}")
        }
    }

    /** Share text via share sheet */
    private fun executeShareText(action: AIAction.ShareText): ActionResult {
        return try {
            val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, action.text)
                        action.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    }
            val chooser =
                    Intent.createChooser(intent, "Share via").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(chooser)
            ActionResult.Success("Opening share dialog")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to share: ${e.message}")
        }
    }

    /** Copy text to clipboard */
    private fun executeCopyToClipboard(action: AIAction.CopyToClipboard): ActionResult {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(action.label, action.text)
            clipboard.setPrimaryClip(clip)
            ActionResult.Success(
                    "Copied to clipboard: ${action.text.take(50)}${if (action.text.length > 50) "..." else ""}"
            )
        } catch (e: Exception) {
            ActionResult.Failure("Failed to copy to clipboard: ${e.message}")
        }
    }

    /** Compose an email */
    private fun executeSendEmail(action: AIAction.SendEmail): ActionResult {
        return try {
            val intent =
                    Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${action.to}")
                        action.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                        action.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Composing email to ${action.to}")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to compose email: ${e.message}")
        }
    }

    /** Open maps or navigate to a location */
    private fun executeOpenMaps(action: AIAction.OpenMaps): ActionResult {
        return try {
            val uri =
                    when {
                        action.navigate && action.query != null -> {
                            Uri.parse("google.navigation:q=${Uri.encode(action.query)}")
                        }
                        action.navigate && action.latitude != null && action.longitude != null -> {
                            Uri.parse("google.navigation:q=${action.latitude},${action.longitude}")
                        }
                        action.query != null -> {
                            Uri.parse("geo:0,0?q=${Uri.encode(action.query)}")
                        }
                        action.latitude != null && action.longitude != null -> {
                            Uri.parse("geo:${action.latitude},${action.longitude}")
                        }
                        else -> {
                            Uri.parse("geo:0,0")
                        }
                    }
            val intent =
                    Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            // Try Google Maps first, fall back to any maps app
            try {
                startActivity(intent)
            } catch (e: Exception) {
                intent.setPackage(null)
                startActivity(intent)
            }
            if (action.navigate) {
                ActionResult.Success(
                        "Starting navigation to ${action.query ?: "${action.latitude},${action.longitude}"}"
                )
            } else {
                ActionResult.Success(
                        "Opening maps: ${action.query ?: "${action.latitude},${action.longitude}"}"
                )
            }
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open maps: ${e.message}")
        }
    }

    /** Play media */
    private fun executePlayMedia(action: AIAction.PlayMedia): ActionResult {
        return try {
            val intent =
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(
                                MediaStore.EXTRA_MEDIA_FOCUS,
                                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
                        )
                        putExtra(SearchManager.QUERY, action.query)
                        action.artist?.let { putExtra(MediaStore.EXTRA_MEDIA_ARTIST, it) }
                        action.album?.let { putExtra(MediaStore.EXTRA_MEDIA_ALBUM, it) }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
            ActionResult.Success("Playing: ${action.query}")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to play media: ${e.message}")
        }
    }

    /** Capture photo or video */
    private fun executeCaptureMedia(action: AIAction.CaptureMedia): ActionResult {
        return try {
            val intent =
                    if (action.video) {
                        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    } else {
                        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            ActionResult.Success(if (action.video) "Opening video camera" else "Opening camera")
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open camera: ${e.message}")
        }
    }

    /** Open settings to a specific section */
    private fun executeOpenSettings(action: AIAction.OpenSettings): ActionResult {
        return try {
            val settingsAction =
                    when (action.section) {
                        AIAction.SettingsSection.MAIN -> Settings.ACTION_SETTINGS
                        AIAction.SettingsSection.WIFI -> Settings.ACTION_WIFI_SETTINGS
                        AIAction.SettingsSection.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
                        AIAction.SettingsSection.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
                        AIAction.SettingsSection.SOUND -> Settings.ACTION_SOUND_SETTINGS
                        AIAction.SettingsSection.BATTERY -> Intent.ACTION_POWER_USAGE_SUMMARY
                        AIAction.SettingsSection.APPS -> Settings.ACTION_APPLICATION_SETTINGS
                        AIAction.SettingsSection.NOTIFICATIONS ->
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        AIAction.SettingsSection.LOCATION ->
                                Settings.ACTION_LOCATION_SOURCE_SETTINGS
                        AIAction.SettingsSection.SECURITY -> Settings.ACTION_SECURITY_SETTINGS
                        AIAction.SettingsSection.ACCESSIBILITY ->
                                Settings.ACTION_ACCESSIBILITY_SETTINGS
                    }
            val intent = Intent(settingsAction).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
            ActionResult.Success(
                    "Opened ${action.section.name.lowercase().replace("_", " ")} settings"
            )
        } catch (e: Exception) {
            ActionResult.Failure("Failed to open settings: ${e.message}")
        }
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

                        androidx.compose.foundation.layout.Column(
                                verticalArrangement =
                                        androidx.compose.foundation.layout.Arrangement.spacedBy(
                                                8.dp
                                        ),
                                horizontalAlignment =
                                        androidx.compose.ui.Alignment.CenterHorizontally
                        ) {
                            // Text selection button (smaller, cyan)
                            TextSelectionButton(
                                    onClick = {
                                        Log.d(TAG, "Text selection button clicked")
                                        DebugLogManager.info(
                                                TAG,
                                                "Text Selection Mode",
                                                "Capturing screen for OCR..."
                                        )
                                        showTextSelectionOverlay()
                                    }
                            )

                            // Main AI suggestions button (larger, red)
                            OmniFloatingButton(
                                    onClick = {
                                        Log.d(TAG, "Floating button clicked")
                                        onSuggestionButtonClicked?.invoke()
                                    },
                                    isLoading = suggestionState.value.isLoading
                            )
                        }
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
                                },
                                onFocusAreaClick = {
                                    Log.d(TAG, "Focus area button clicked")
                                    // Hide suggestions overlay and show focus area selector
                                    hideSuggestionsOverlay()
                                    showFocusAreaSelector()
                                },
                                onClearFocusArea = {
                                    Log.d(TAG, "Clear focus area clicked")
                                    onFocusAreaClear?.invoke()
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

    // ═══════════════════════════════════════════════════════════════════════════
    // DEBUG OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Show the debug floating button */
    fun showDebugButton() {
        if (debugButtonView != null) return
        isDebugOverlayEnabled = true

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 24
        params.y = 100

        val container = FrameLayout(this)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val composeView =
                ComposeView(this).apply {
                    setContent {
                        val debugState = DebugLogManager.state.collectAsState()
                        val hasNewLogs = debugState.value.logs.isNotEmpty()

                        DebugFloatingButton(
                                onClick = {
                                    Log.d(TAG, "Debug button clicked")
                                    DebugLogManager.toggleOverlay()
                                    if (DebugLogManager.state.value.isVisible) {
                                        showDebugOverlay()
                                    } else {
                                        hideDebugOverlay()
                                    }
                                },
                                hasNewLogs = hasNewLogs
                        )
                    }
                }
        container.addView(composeView)

        debugButtonView = container
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Debug button overlay added")
            DebugLogManager.info("OmniService", "Debug overlay enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding debug button overlay", e)
            debugButtonView = null
        }
    }

    /** Hide the debug floating button */
    fun hideDebugButton() {
        isDebugOverlayEnabled = false
        debugButtonView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Debug button overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing debug button overlay", e)
            }
        }
        debugButtonView = null
        hideDebugOverlay()
    }

    /** Toggle debug button visibility */
    fun toggleDebugButton() {
        if (debugButtonView != null) {
            hideDebugButton()
        } else {
            showDebugButton()
        }
    }

    /** Show the debug overlay panel */
    fun showDebugOverlay() {
        hideDebugOverlayOnly()

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP

        val container = FrameLayout(this)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val clipboardManager =
                getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager

        val composeView =
                ComposeView(this).apply {
                    setContent {
                        val debugState = DebugLogManager.state.collectAsState()

                        DebugOverlayPanel(
                                state = debugState.value,
                                onDismiss = {
                                    DebugLogManager.hideOverlay()
                                    hideDebugOverlay()
                                },
                                onClear = { DebugLogManager.clearLogs() },
                                onToggleExpand = { DebugLogManager.toggleExpanded() },
                                onCopyLogs = { logsText: String ->
                                    val clip =
                                            android.content.ClipData.newPlainText(
                                                    "Debug Logs",
                                                    logsText
                                            )
                                    clipboardManager.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(
                                                    this@OmniAccessibilityService,
                                                    "Logs copied to clipboard",
                                                    android.widget.Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                        )
                    }
                }
        container.addView(composeView)

        debugOverlayView = container
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Debug overlay panel added")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding debug overlay panel", e)
            debugOverlayView = null
        }
    }

    /** Hide only the debug overlay panel (not the button) */
    private fun hideDebugOverlayOnly() {
        debugOverlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing debug overlay panel", e)
            }
        }
        debugOverlayView = null
    }

    /** Hide the debug overlay panel */
    fun hideDebugOverlay() {
        hideDebugOverlayOnly()
    }

    /** Check if debug overlay is enabled */
    fun isDebugOverlayEnabled(): Boolean = isDebugOverlayEnabled

    // ═══════════════════════════════════════════════════════════════════════════
    // FOCUS AREA SELECTOR OVERLAY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Show the focus area selector overlay */
    fun showFocusAreaSelector() {
        hideFocusAreaSelector() // Remove existing if any

        // Temporarily hide the floating button while selecting
        floatingButtonView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding floating button for focus selector", e)
            }
        }
        floatingButtonView = null

        val params =
                WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP or Gravity.START

        val container = FrameLayout(this)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        val composeView =
                ComposeView(this).apply {
                    setContent {
                        val suggestionState = _suggestionState.collectAsState()

                        FocusAreaSelectorOverlay(
                                selectionState = suggestionState.value.focusAreaSelectionState,
                                currentRegion = suggestionState.value.focusRegion,
                                onSelectionStart = { x, y ->
                                    Log.d(TAG, "Focus area selection started at ($x, $y)")
                                    onFocusAreaSelectionStart?.invoke(x, y)
                                },
                                onSelectionUpdate = { x, y ->
                                    onFocusAreaSelectionUpdate?.invoke(x, y)
                                },
                                onSelectionEnd = {
                                    Log.d(TAG, "Focus area selection ended")
                                    onFocusAreaSelectionEnd?.invoke()
                                },
                                onClearFocus = {
                                    Log.d(TAG, "Focus area cleared")
                                    onFocusAreaClear?.invoke()
                                },
                                onDismiss = {
                                    Log.d(TAG, "Focus area selector dismissed")
                                    onFocusAreaConfirm?.invoke()
                                }
                        )
                    }
                }
        container.addView(composeView)

        focusAreaSelectorView = container
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Focus area selector overlay added")
            DebugLogManager.info(
                    TAG,
                    "Focus area selector shown",
                    "Draw a rectangle to select area of interest"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adding focus area selector overlay", e)
            focusAreaSelectorView = null
        }
    }

    /** Hide the focus area selector overlay */
    fun hideFocusAreaSelector() {
        focusAreaSelectorView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Focus area selector overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing focus area selector overlay", e)
            }
        }
        focusAreaSelectorView = null

        // Restore floating button if it was enabled
        if (isFloatingOverlayEnabled) {
            showFloatingButton()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEXT SELECTION OVERLAY (Circle-to-Search) METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Take a screenshot and show the text selection overlay with OCR. This is the entry point for
     * Circle-to-Search functionality.
     */
    fun showTextSelectionOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            captureScreenshotAndShowOverlay()
        } else {
            Log.w(TAG, "Text selection requires Android 11 (API 30) or higher")
            DebugLogManager.error(TAG, "Text selection unavailable", "Requires Android 11+")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun captureScreenshotAndShowOverlay() {
        // Update state to processing
        _textSelectionState.value = TextSelectionState(isActive = true, isProcessing = true)

        // Hide floating button while capturing
        floatingButtonView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating button for screenshot", e)
            }
        }
        floatingButtonView = null

        // Delay to ensure floating button is fully removed and screen is settled
        serviceScope.launch {
            delay(250)

            takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val bitmap =
                                        Bitmap.wrapHardwareBuffer(
                                                screenshot.hardwareBuffer,
                                                screenshot.colorSpace
                                        )

                                if (bitmap != null) {
                                    Log.d(
                                            TAG,
                                            "Screenshot captured: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}"
                                    )

                                    // Convert to software bitmap for ML Kit processing
                                    val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

                                    if (softwareBitmap != null) {
                                        Log.d(
                                                TAG,
                                                "Software bitmap created: ${softwareBitmap.width}x${softwareBitmap.height}"
                                        )
                                        processScreenshotWithOCR(softwareBitmap)
                                    } else {
                                        Log.e(
                                                TAG,
                                                "Failed to convert hardware bitmap to software bitmap"
                                        )
                                        // Try alternative approach - create a new bitmap and draw
                                        val altBitmap =
                                                Bitmap.createBitmap(
                                                        bitmap.width,
                                                        bitmap.height,
                                                        Bitmap.Config.ARGB_8888
                                                )
                                        val canvas = android.graphics.Canvas(altBitmap)
                                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                                        Log.d(TAG, "Created alternative bitmap via canvas")
                                        processScreenshotWithOCR(altBitmap)
                                    }
                                    bitmap.recycle()
                                } else {
                                    Log.e(
                                            TAG,
                                            "Failed to create bitmap from screenshot hardware buffer"
                                    )
                                    _textSelectionState.value =
                                            _textSelectionState.value.copy(
                                                    isProcessing = false,
                                                    error = "Failed to capture screenshot"
                                            )
                                    restoreFloatingButton()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing screenshot", e)
                                _textSelectionState.value =
                                        _textSelectionState.value.copy(
                                                isProcessing = false,
                                                error = "Screenshot processing error: ${e.message}"
                                        )
                                restoreFloatingButton()
                            } finally {
                                screenshot.hardwareBuffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "Screenshot failed with error code: $errorCode")
                            _textSelectionState.value =
                                    _textSelectionState.value.copy(
                                            isProcessing = false,
                                            error = "Screenshot failed (code: $errorCode)"
                                    )
                            restoreFloatingButton()
                        }
                    }
            )
        }
    }

    private fun processScreenshotWithOCR(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                Log.d(
                        TAG,
                        "Processing screenshot with OCR: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}"
                )
                DebugLogManager.info(
                        TAG,
                        "OCR Processing",
                        "Analyzing ${bitmap.width}x${bitmap.height} screenshot"
                )

                // Check if bitmap is valid
                if (bitmap.isRecycled) {
                    Log.e(TAG, "Bitmap is recycled!")
                    throw IllegalStateException("Bitmap is recycled")
                }

                val textBlocks = textRecognitionHelper.recognizeText(bitmap)

                Log.d(TAG, "OCR found ${textBlocks.size} text blocks")
                if (textBlocks.isNotEmpty()) {
                    textBlocks.take(5).forEach { block ->
                        Log.d(TAG, "  Text: '${block.text}' at ${block.bounds}")
                    }
                }
                DebugLogManager.success(
                        TAG,
                        "OCR Complete",
                        "Found ${textBlocks.size} text elements"
                )

                // Update state with results
                _textSelectionState.value =
                        TextSelectionState(
                                isActive = true,
                                screenshotBitmap = bitmap,
                                textBlocks = textBlocks,
                                isProcessing = false
                        )

                // Show the text selection overlay
                showTextSelectionOverlayUI()
            } catch (e: Exception) {
                Log.e(TAG, "OCR processing failed", e)
                DebugLogManager.error(TAG, "OCR Failed", e.message ?: "Unknown error")

                _textSelectionState.value =
                        _textSelectionState.value.copy(
                                isProcessing = false,
                                error = "Text detection failed: ${e.message}"
                        )
                restoreFloatingButton()
            }
        }
    }

    private fun showTextSelectionOverlayUI() {
        // Remove existing view without resetting state (state already has the text blocks)
        textSelectionOverlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Text selection overlay removed for refresh")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing text selection overlay", e)
            }
        }
        textSelectionOverlayView = null

        val params =
                WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                PixelFormat.TRANSLUCENT
                        )
                        .apply { gravity = Gravity.TOP or Gravity.START }

        val container =
                FrameLayout(this).apply {
                    setViewTreeLifecycleOwner(this@OmniAccessibilityService)
                    setViewTreeSavedStateRegistryOwner(this@OmniAccessibilityService)
                }

        val composeView =
                ComposeView(this).apply {
                    setContent {
                        val state = _textSelectionState.collectAsState()

                        TextSelectionOverlay(
                                state = state.value,
                                onTextBlockTapped = { index ->
                                    // Toggle selection of tapped block
                                    val currentSelected = _textSelectionState.value.selectedBlocks
                                    val newSelected =
                                            if (currentSelected.contains(index)) {
                                                currentSelected - index
                                            } else {
                                                currentSelected + index
                                            }
                                    _textSelectionState.value =
                                            _textSelectionState.value.copy(
                                                    selectedBlocks = newSelected,
                                                    // Clear previous options when selection changes
                                                    textOptions = emptyList(),
                                                    optionsStreamingText = ""
                                            )
                                    onTextBlockTapped?.invoke(index)
                                },
                                onTextBlocksSelected = { indices ->
                                    _textSelectionState.value =
                                            _textSelectionState.value.copy(
                                                    selectedBlocks = indices.toSet(),
                                                    // Clear previous options when selection changes
                                                    textOptions = emptyList(),
                                                    optionsStreamingText = ""
                                            )
                                    onTextBlocksSelected?.invoke(indices)
                                },
                                onSelectionPathUpdate = { path ->
                                    _textSelectionState.value =
                                            _textSelectionState.value.copy(
                                                    selectionPath = path,
                                                    isDrawingSelection = true
                                            )
                                },
                                onSelectionComplete = {
                                    _textSelectionState.value =
                                            _textSelectionState.value.copy(
                                                    selectionPath = emptyList(),
                                                    isDrawingSelection = false
                                            )
                                },
                                onCopyText = {
                                    val selectedText = _textSelectionState.value.getSelectedText()
                                    if (selectedText.isNotEmpty()) {
                                        copyToClipboard(selectedText)
                                        DebugLogManager.success(
                                                TAG,
                                                "Text Copied",
                                                selectedText.take(50) + "..."
                                        )
                                    }
                                    onTextSelectionCopy?.invoke()
                                },
                                onSearchText = {
                                    val selectedText = _textSelectionState.value.getSelectedText()
                                    if (selectedText.isNotEmpty()) {
                                        searchText(selectedText)
                                        DebugLogManager.info(
                                                TAG,
                                                "Searching",
                                                selectedText.take(50)
                                        )
                                    }
                                    onTextSelectionSearch?.invoke()
                                    hideTextSelectionOverlay()
                                },
                                onShareText = {
                                    val selectedText = _textSelectionState.value.getSelectedText()
                                    if (selectedText.isNotEmpty()) {
                                        shareText(selectedText)
                                    }
                                    onTextSelectionShare?.invoke()
                                    hideTextSelectionOverlay()
                                },
                                onDismiss = {
                                    onTextSelectionDismiss?.invoke()
                                    hideTextSelectionOverlay()
                                }
                        )
                    }
                }
        container.addView(composeView)

        textSelectionOverlayView = container
        try {
            windowManager?.addView(container, params)
            Log.d(TAG, "Text selection overlay added")
            DebugLogManager.info(TAG, "Text Selection Active", "Tap or circle text to select")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding text selection overlay", e)
            textSelectionOverlayView = null
        }
    }

    /** Hide the text selection overlay */
    fun hideTextSelectionOverlay() {
        textSelectionOverlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "Text selection overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing text selection overlay", e)
            }
        }
        textSelectionOverlayView = null

        // Recycle the bitmap if present
        _textSelectionState.value.screenshotBitmap?.recycle()

        // Reset state
        _textSelectionState.value = TextSelectionState()

        // Restore floating button
        restoreFloatingButton()
    }

    private fun restoreFloatingButton() {
        if (isFloatingOverlayEnabled && floatingButtonView == null) {
            showFloatingButton()
        }
    }

    /** Copy text to clipboard */
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Selected Text", text)
        clipboard.setPrimaryClip(clip)
    }

    /** Search for text using the default search app */
    private fun searchText(query: String) {
        try {
            val intent =
                    Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open search", e)
            // Fallback to browser
            try {
                val browserIntent =
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                startActivity(browserIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open browser for search", e2)
            }
        }
    }

    /** Share text using the system share sheet */
    private fun shareText(text: String) {
        try {
            val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            startActivity(
                    Intent.createChooser(intent, "Share text").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share text", e)
        }
    }

    /** Update text selection state (called from ViewModel) */
    fun updateTextSelectionState(state: TextSelectionState) {
        _textSelectionState.value = state
    }

    /** Check if text selection overlay is active */
    fun isTextSelectionActive(): Boolean = _textSelectionState.value.isActive
}
