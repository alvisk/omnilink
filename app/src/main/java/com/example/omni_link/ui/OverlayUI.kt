package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.ui.theme.*
import kotlin.math.roundToInt

/** Floating action button that triggers AI suggestions */
@Composable
fun OmniFloatingButton(
        onClick: () -> Unit,
        onLongClick: () -> Unit = {},
        isLoading: Boolean = false,
        modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Pulsing animation when idle
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1200, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "pulseAlpha"
            )

    // Rotation animation when loading
    val rotation by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "rotation"
            )

    // Long press threshold in milliseconds
    val longPressTimeoutMs = 500L
    // Drag threshold in pixels - if moved more than this, it's a drag not a tap
    val dragThreshold = 10f

    Box(
            modifier =
                    modifier
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .size(64.dp)
                            // Custom gesture handler for tap, long-press, and drag
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downTime = System.currentTimeMillis()
                                    val startPosition = down.position
                                    var totalDrag = androidx.compose.ui.geometry.Offset.Zero
                                    var isDragging = false
                                    var longPressTriggered = false

                                    // Wait for up or track drag
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val currentTime = System.currentTimeMillis()
                                        val elapsedTime = currentTime - downTime

                                        // Check if all pointers are up
                                        if (event.changes.all { !it.pressed }) {
                                            // Pointer released
                                            if (!isDragging && !longPressTriggered && elapsedTime < longPressTimeoutMs) {
                                                // Short tap - trigger onClick
                                                onClick()
                                            }
                                            break
                                        }

                                        // Track movement
                                        val change = event.changes.firstOrNull() ?: continue
                                        val dragAmount = change.position - change.previousPosition
                                        totalDrag += dragAmount

                                        // Check if this is a drag (moved beyond threshold)
                                        val totalDistance = kotlin.math.sqrt(
                                            totalDrag.x * totalDrag.x + totalDrag.y * totalDrag.y
                                        )
                                        if (totalDistance > dragThreshold) {
                                            isDragging = true
                                            // Apply drag offset
                                            offsetX += dragAmount.x
                                            offsetY += dragAmount.y
                                            change.consume()
                                        } else if (!isDragging && !longPressTriggered && elapsedTime >= longPressTimeoutMs) {
                                            // Long press detected (and not dragging)
                                            longPressTriggered = true
                                            onLongClick()
                                        }
                                    }
                                }
                            }
    ) {
        // Outer glow effect
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        brush =
                                                Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        OmniRed.copy(
                                                                                alpha =
                                                                                        pulseAlpha *
                                                                                                0.3f
                                                                        ),
                                                                        Color.Transparent
                                                                )
                                                )
                                )
        )

        // Main button - tap for suggestions, long-press for text selection
        Surface(
                modifier =
                        Modifier.size(56.dp)
                                .align(Alignment.Center)
                                .shadow(8.dp, RoundedCornerShape(4.dp)),
                color = OmniBlack,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, OmniRed)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = OmniRed,
                            strokeWidth = 2.dp
                    )
                } else {
                    // Blocky "O" logo
                    Box(
                            modifier =
                                    Modifier.size(28.dp)
                                            .border(3.dp, OmniRed, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                    ) { Box(modifier = Modifier.size(8.dp).background(OmniRed)) }
                }
            }
        }
    }
}

/** Text selection floating button - triggers Circle-to-Search style OCR */
@Composable
fun TextSelectionButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    // Subtle pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "text_pulse")
    val pulseAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(1500, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "textPulseAlpha"
            )

    Surface(
            onClick = onClick,
            modifier = modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp),
            color = OmniBlack.copy(alpha = pulseAlpha),
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, OmniCyan)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                    imageVector = Icons.Outlined.TextFields,
                    contentDescription = "Select text",
                    tint = OmniCyan,
                    modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Suggestions panel that displays AI-generated suggestions */
@Composable
fun SuggestionsPanel(
        state: SuggestionState,
        onSuggestionClick: (Suggestion) -> Unit,
        onDismiss: () -> Unit,
        onFocusAreaClick: () -> Unit = {},
        onClearFocusArea: () -> Unit = {},
        modifier: Modifier = Modifier
) {
    AnimatedVisibility(
            visible = state.isVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = modifier
    ) {
        Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                color = OmniBlack,
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
                shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                SuggestionsPanelHeader(
                        isLoading = state.isLoading,
                        isStreaming = state.isStreaming,
                        suggestionCount = state.suggestions.size,
                        focusRegion = state.focusRegion,
                        onDismiss = onDismiss,
                        onFocusAreaClick = onFocusAreaClick,
                        onClearFocusArea = onClearFocusArea
                )

                // Red accent line
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(OmniRed))

                // Content
                when {
                    state.isLoading -> {
                        StreamingLoadingState(
                                streamingText = state.streamingText,
                                isStreaming = state.isStreaming
                        )
                    }
                    state.error != null -> {
                        ErrorState(error = state.error)
                    }
                    state.suggestions.isEmpty() -> {
                        EmptySuggestionsState()
                    }
                    else -> {
                        SuggestionsList(
                                suggestions = state.suggestions,
                                onSuggestionClick = onSuggestionClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsPanelHeader(
        isLoading: Boolean,
        isStreaming: Boolean = false,
        suggestionCount: Int,
        focusRegion: FocusRegion? = null,
        onDismiss: () -> Unit,
        onFocusAreaClick: () -> Unit = {},
        onClearFocusArea: () -> Unit = {}
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(OmniGrayDark)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Status block
        Box(
                modifier = Modifier.size(32.dp).background(if (isLoading) OmniYellow else OmniRed),
                contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "loading")
                val alpha by
                        infiniteTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 1f,
                                animationSpec =
                                        infiniteRepeatable(
                                                animation = tween(500),
                                                repeatMode = RepeatMode.Reverse
                                        ),
                                label = "alpha"
                        )
                Text(
                        text = if (isStreaming) "⚡" else "...",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniBlack.copy(alpha = alpha),
                        fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                        text = "AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniWhite,
                        fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                        text = "SUGGESTIONS",
                        style = MaterialTheme.typography.titleSmall,
                        color = OmniWhite,
                        letterSpacing = 3.sp
                )

                // Focus area indicator
                if (focusRegion != null) {
                    FocusAreaIndicator(focusRegion = focusRegion, onClear = onClearFocusArea)
                }
            }
            Text(
                    text =
                            when {
                                isStreaming -> "AI THINKING..."
                                isLoading ->
                                        if (focusRegion != null) "ANALYZING FOCUS AREA..."
                                        else "ANALYZING SCREEN..."
                                focusRegion != null -> "$suggestionCount AVAILABLE (FOCUSED)"
                                else -> "$suggestionCount AVAILABLE"
                            },
                    style = MaterialTheme.typography.labelSmall,
                    color =
                            when {
                                isStreaming -> OmniYellow
                                focusRegion != null -> OmniYellow
                                else -> OmniGrayText
                            },
                    letterSpacing = 1.sp
            )
        }

        // Focus area button
        IconButton(
                onClick = onFocusAreaClick,
                modifier =
                        Modifier.size(32.dp)
                                .background(if (focusRegion != null) OmniYellow else OmniGrayMid)
        ) {
            Icon(
                    imageVector = Icons.Outlined.CropFree,
                    contentDescription = "Select focus area",
                    tint = if (focusRegion != null) OmniBlack else OmniWhite,
                    modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Close button
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp).background(OmniGrayMid)) {
            Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = OmniWhite,
                    modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Animated blocks
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "block$index")
                val alpha by
                        infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec =
                                        infiniteRepeatable(
                                                animation = tween(600, delayMillis = index * 150),
                                                repeatMode = RepeatMode.Reverse
                                        ),
                                label = "blockAlpha$index"
                        )
                Box(modifier = Modifier.size(16.dp).background(OmniRed.copy(alpha = alpha)))
            }
        }

        Text(
                text = "ANALYZING SCREEN CONTENT",
                style = MaterialTheme.typography.labelMedium,
                color = OmniGrayText,
                letterSpacing = 2.sp
        )
    }
}

/** Loading state that shows streaming AI output in real-time */
@Composable
private fun StreamingLoadingState(streamingText: String, isStreaming: Boolean) {
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom as text appears
    LaunchedEffect(streamingText) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Animated thinking indicator
        Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "pulse$index")
                val alpha by
                        infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec =
                                        infiniteRepeatable(
                                                animation = tween(600, delayMillis = index * 150),
                                                repeatMode = RepeatMode.Reverse
                                        ),
                                label = "pulseAlpha$index"
                        )
                Box(modifier = Modifier.size(12.dp).background(OmniYellow.copy(alpha = alpha)))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                    text = if (isStreaming) "AI THINKING" else "ANALYZING",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniYellow,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
            )
        }

        // Streaming text output - show the AI's thinking
        if (streamingText.isNotEmpty()) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .heightIn(min = 60.dp, max = 200.dp)
                                    .background(OmniGrayDark)
                                    .border(1.dp, OmniGrayMid)
                                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                    // Display streaming text with a blinking cursor
                    Row {
                        Text(
                                text = streamingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = OmniGrayLight,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 18.sp
                        )

                        // Blinking cursor
                        val cursorAlpha by
                                rememberInfiniteTransition(label = "cursor")
                                        .animateFloat(
                                                initialValue = 0f,
                                                targetValue = 1f,
                                                animationSpec =
                                                        infiniteRepeatable(
                                                                animation = tween(500),
                                                                repeatMode = RepeatMode.Reverse
                                                        ),
                                                label = "cursorAlpha"
                                        )
                        Text(
                                text = "▌",
                                style = MaterialTheme.typography.bodySmall,
                                color = OmniYellow.copy(alpha = cursorAlpha),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            // Show placeholder when no streaming text yet
            Text(
                    text = "Waiting for AI response...",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniGrayText
            )
        }
    }
}

@Composable
private fun ErrorState(error: String) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(16.dp)
                            .background(OmniGrayDark)
                            .border(2.dp, OmniRed),
            contentAlignment = Alignment.Center
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                    modifier = Modifier.size(24.dp).background(OmniRed),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = "!",
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniWhite,
                        fontWeight = FontWeight.Bold
                )
            }
            Text(
                    text = error.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniRed,
                    letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun EmptySuggestionsState() {
    Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
                modifier = Modifier.size(48.dp).border(2.dp, OmniGrayMid),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = OmniGrayText,
                    modifier = Modifier.size(24.dp)
            )
        }

        Text(
                text = "NO SUGGESTIONS",
                style = MaterialTheme.typography.labelMedium,
                color = OmniGrayText,
                letterSpacing = 2.sp
        )

        Text(
                text = "Unable to generate suggestions for this screen",
                style = MaterialTheme.typography.bodySmall,
                color = OmniGrayLight
        )
    }
}

@Composable
private fun SuggestionsList(
        suggestions: List<Suggestion>,
        onSuggestionClick: (Suggestion) -> Unit
) {
    LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions, key = { it.id }) { suggestion ->
            SuggestionCard(suggestion = suggestion, onClick = { onSuggestionClick(suggestion) })
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: Suggestion, onClick: () -> Unit) {
    Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().border(2.dp, OmniGrayMid, RoundedCornerShape(0.dp)),
            color = OmniGrayDark,
            shape = RoundedCornerShape(0.dp)
    ) {
        Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon block
            Box(
                    modifier =
                            Modifier.size(40.dp)
                                    .background(OmniRed.copy(alpha = 0.2f))
                                    .border(1.dp, OmniRed),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = getSuggestionIcon(suggestion.icon),
                        contentDescription = null,
                        tint = OmniRed,
                        modifier = Modifier.size(20.dp)
                )
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = suggestion.title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniWhite,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(
                        text = suggestion.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniGrayText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                )
            }

            // Action indicator
            Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = OmniGrayText,
                    modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun getSuggestionIcon(icon: Suggestion.SuggestionIcon): ImageVector {
    return when (icon) {
        Suggestion.SuggestionIcon.LIGHTBULB -> Icons.Outlined.Lightbulb
        Suggestion.SuggestionIcon.CLICK -> Icons.Outlined.TouchApp
        Suggestion.SuggestionIcon.TYPE -> Icons.Outlined.Keyboard
        Suggestion.SuggestionIcon.SCROLL -> Icons.Outlined.SwipeVertical
        Suggestion.SuggestionIcon.NAVIGATE -> Icons.Outlined.Navigation
        Suggestion.SuggestionIcon.APP -> Icons.Outlined.Apps
        Suggestion.SuggestionIcon.SEARCH -> Icons.Outlined.Search
        Suggestion.SuggestionIcon.SHARE -> Icons.Outlined.Share
        Suggestion.SuggestionIcon.COPY -> Icons.Outlined.ContentCopy
        Suggestion.SuggestionIcon.INFO -> Icons.Outlined.Info
        Suggestion.SuggestionIcon.CALENDAR -> Icons.Outlined.CalendarMonth
        Suggestion.SuggestionIcon.PHONE -> Icons.Outlined.Phone
        Suggestion.SuggestionIcon.SMS -> Icons.Outlined.Sms
        Suggestion.SuggestionIcon.ALARM -> Icons.Outlined.Alarm
        Suggestion.SuggestionIcon.TIMER -> Icons.Outlined.Timer
        Suggestion.SuggestionIcon.EMAIL -> Icons.Outlined.Email
        Suggestion.SuggestionIcon.MAP -> Icons.Outlined.Map
        Suggestion.SuggestionIcon.CAMERA -> Icons.Outlined.CameraAlt
        Suggestion.SuggestionIcon.VIDEO -> Icons.Outlined.Videocam
        Suggestion.SuggestionIcon.MUSIC -> Icons.Outlined.MusicNote
        Suggestion.SuggestionIcon.SETTINGS -> Icons.Outlined.Settings
        Suggestion.SuggestionIcon.WEB -> Icons.Outlined.Language
    }
}

/** Complete overlay composable that combines the floating button and suggestions panel */
@Composable
fun OmniOverlay(
        state: SuggestionState,
        onButtonClick: () -> Unit,
        onSuggestionClick: (Suggestion) -> Unit,
        onDismiss: () -> Unit,
        onFocusAreaClick: () -> Unit = {},
        onClearFocusArea: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Suggestions panel at bottom
        SuggestionsPanel(
                state = state,
                onSuggestionClick = onSuggestionClick,
                onDismiss = onDismiss,
                onFocusAreaClick = onFocusAreaClick,
                onClearFocusArea = onClearFocusArea,
                modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Floating button (positioned bottom-end by default, but draggable)
        if (!state.isVisible) {
            OmniFloatingButton(
                    onClick = onButtonClick,
                    isLoading = state.isLoading,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}
