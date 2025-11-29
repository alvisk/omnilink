package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
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

/** Nothing-style floating action button - Dramatic, impactful design */
@Composable
fun OmniFloatingButton(
        onClick: () -> Unit,
        onLongClick: () -> Unit = {},
        isLoading: Boolean = false,
        modifier: Modifier = Modifier
) {
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        // Static values (animations removed)
        val breatheScale = 1f
        val glowAlpha = 0.5f
        val ringRotation = 0f
        val ringScale = 1f
        val ringAlpha = 0.3f

        val longPressTimeoutMs = 500L
        val dragThreshold = 10f

        Box(
                modifier =
                        modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
                contentAlignment = Alignment.Center
        ) {
                // Outer pulsing ring effect
                Box(
                        modifier =
                                Modifier.size((64 * ringScale).dp)
                                        .alpha(ringAlpha)
                                        .border(
                                                2.dp,
                                                NothingRed.copy(alpha = 0.6f),
                                                RoundedCornerShape(18.dp)
                                        )
                )

                // Glowing backdrop
                Box(
                        modifier =
                                Modifier.size((68 * breatheScale).dp)
                                        .alpha(glowAlpha * 0.4f)
                                        .background(
                                                androidx.compose.ui.graphics.Brush.radialGradient(
                                                        colors =
                                                                listOf(
                                                                        NothingRed.copy(
                                                                                alpha = 0.4f
                                                                        ),
                                                                        NothingRed.copy(
                                                                                alpha = 0.1f
                                                                        ),
                                                                        Color.Transparent
                                                                )
                                                ),
                                                RoundedCornerShape(50)
                                        )
                )

                // Rotating accent ring
                Box(
                        modifier =
                                Modifier.size(62.dp)
                                        .alpha(glowAlpha * 0.6f)
                                        .graphicsLayer(rotationZ = ringRotation)
                                        .border(
                                                1.dp,
                                                androidx.compose.ui.graphics.Brush.sweepGradient(
                                                        listOf(
                                                                NothingRed.copy(alpha = 0.8f),
                                                                Color.Transparent,
                                                                Color.Transparent,
                                                                NothingRed.copy(alpha = 0.4f),
                                                                Color.Transparent,
                                                                Color.Transparent,
                                                                NothingRed.copy(alpha = 0.8f)
                                                        )
                                                ),
                                                RoundedCornerShape(17.dp)
                                        )
                )

                // Main button
                Box(
                        modifier =
                                Modifier.size(56.dp)
                                        .graphicsLayer(scaleX = breatheScale, scaleY = breatheScale)
                                        .background(
                                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        NothingCharcoal,
                                                                        NothingBlack
                                                                )
                                                ),
                                                RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                                1.dp,
                                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        colors =
                                                                listOf(
                                                                        NothingGray700,
                                                                        NothingGray900
                                                                )
                                                ),
                                                RoundedCornerShape(16.dp)
                                        )
                                        .pointerInput(Unit) {
                                                awaitEachGesture {
                                                        val down =
                                                                awaitFirstDown(
                                                                        requireUnconsumed = false
                                                                )
                                                        val downTime = System.currentTimeMillis()
                                                        val startPosition = down.position
                                                        var totalDrag =
                                                                androidx.compose.ui.geometry.Offset
                                                                        .Zero
                                                        var isDragging = false
                                                        var longPressTriggered = false

                                                        while (true) {
                                                                val event = awaitPointerEvent()
                                                                val currentTime =
                                                                        System.currentTimeMillis()
                                                                val elapsedTime =
                                                                        currentTime - downTime

                                                                if (event.changes.all {
                                                                                !it.pressed
                                                                        }
                                                                ) {
                                                                        if (!isDragging &&
                                                                                        !longPressTriggered &&
                                                                                        elapsedTime <
                                                                                                longPressTimeoutMs
                                                                        ) {
                                                                                onClick()
                                                                        }
                                                                        break
                                                                }

                                                                val change =
                                                                        event.changes.firstOrNull()
                                                                                ?: continue
                                                                val dragAmount =
                                                                        change.position -
                                                                                change.previousPosition
                                                                totalDrag += dragAmount

                                                                val totalDistance =
                                                                        kotlin.math.sqrt(
                                                                                totalDrag.x *
                                                                                        totalDrag
                                                                                                .x +
                                                                                        totalDrag
                                                                                                .y *
                                                                                                totalDrag
                                                                                                        .y
                                                                        )
                                                                if (totalDistance > dragThreshold) {
                                                                        isDragging = true
                                                                        offsetX += dragAmount.x
                                                                        offsetY += dragAmount.y
                                                                        change.consume()
                                                                } else if (!isDragging &&
                                                                                !longPressTriggered &&
                                                                                elapsedTime >=
                                                                                        longPressTimeoutMs
                                                                ) {
                                                                        longPressTriggered = true
                                                                        onLongClick()
                                                                }
                                                        }
                                                }
                                        },
                        contentAlignment = Alignment.Center
                ) {
                        if (isLoading) {
                                // Dramatic loading indicator with glow
                                Box(contentAlignment = Alignment.Center) {
                                        // Glow behind spinner
                                        Box(
                                                modifier =
                                                        Modifier.size(28.dp)
                                                                .alpha(glowAlpha)
                                                                .background(
                                                                        NothingRed.copy(
                                                                                alpha = 0.3f
                                                                        ),
                                                                        RoundedCornerShape(50)
                                                                )
                                        )
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                color = NothingRed,
                                                strokeWidth = 2.5.dp,
                                                trackColor = NothingGray800
                                        )
                                }
                        } else {
                                // Möbius strip infinity logo with enhanced styling
                                Box(contentAlignment = Alignment.Center) {
                                        // Subtle inner glow
                                        Box(
                                                modifier =
                                                        Modifier.size(32.dp)
                                                                .alpha(glowAlpha * 0.3f)
                                                                .background(
                                                                        NothingWhite.copy(
                                                                                alpha = 0.1f
                                                                        ),
                                                                        RoundedCornerShape(50)
                                                                )
                                        )
                                        MobiusLogo(
                                                modifier = Modifier.size(30.dp),
                                                alpha = 1f,
                                                primaryColor = NothingWhite,
                                                secondaryColor = NothingRed.copy(alpha = 0.5f)
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun DotPixel(alpha: Float, color: Color, size: Int = 8) {
        Box(
                modifier =
                        Modifier.size(size.dp)
                                .alpha(alpha)
                                .background(color, RoundedCornerShape(50))
        )
}

/** Möbius strip / infinity logo - 3D twisted ribbon effect */
@Composable
private fun MobiusLogo(
        modifier: Modifier = Modifier,
        alpha: Float = 1f,
        primaryColor: Color = NothingWhite,
        secondaryColor: Color = NothingGray500
) {
        Canvas(modifier = modifier.alpha(alpha)) {
                val scaleX = size.width / 48f
                val scaleY = size.height / 48f

                // Draw the Möbius figure-8 infinity shape
                // Main infinity ribbon - front face (primary color)
                val mainPath =
                        Path().apply {
                                // Left loop
                                moveTo(8f * scaleX, 24f * scaleY)
                                quadraticTo(8f * scaleX, 14f * scaleY, 17f * scaleX, 14f * scaleY)
                                quadraticTo(24f * scaleX, 14f * scaleY, 24f * scaleX, 20f * scaleY)
                                // Right loop
                                quadraticTo(24f * scaleX, 14f * scaleY, 31f * scaleX, 14f * scaleY)
                                quadraticTo(40f * scaleX, 14f * scaleY, 40f * scaleX, 24f * scaleY)
                                quadraticTo(40f * scaleX, 34f * scaleY, 31f * scaleX, 34f * scaleY)
                                quadraticTo(24f * scaleX, 34f * scaleY, 24f * scaleX, 28f * scaleY)
                                quadraticTo(24f * scaleX, 34f * scaleY, 17f * scaleX, 34f * scaleY)
                                quadraticTo(8f * scaleX, 34f * scaleY, 8f * scaleX, 24f * scaleY)
                                close()

                                // Inner cutout (creates the ribbon effect)
                                moveTo(12f * scaleX, 24f * scaleY)
                                quadraticTo(12f * scaleX, 30f * scaleY, 18f * scaleX, 30f * scaleY)
                                quadraticTo(22f * scaleX, 30f * scaleY, 23f * scaleX, 26f * scaleY)
                                lineTo(25f * scaleX, 26f * scaleY)
                                quadraticTo(26f * scaleX, 30f * scaleY, 30f * scaleX, 30f * scaleY)
                                quadraticTo(36f * scaleX, 30f * scaleY, 36f * scaleX, 24f * scaleY)
                                quadraticTo(36f * scaleX, 18f * scaleY, 30f * scaleX, 18f * scaleY)
                                quadraticTo(26f * scaleX, 18f * scaleY, 25f * scaleX, 22f * scaleY)
                                lineTo(23f * scaleX, 22f * scaleY)
                                quadraticTo(22f * scaleX, 18f * scaleY, 18f * scaleX, 18f * scaleY)
                                quadraticTo(12f * scaleX, 18f * scaleY, 12f * scaleX, 24f * scaleY)
                                close()
                        }

                // Draw filled main shape
                drawPath(mainPath, color = primaryColor)

                // Depth shading for 3D effect (darker overlay on right loop)
                val shadePath =
                        Path().apply {
                                moveTo(25f * scaleX, 22f * scaleY)
                                quadraticTo(26f * scaleX, 18f * scaleY, 30f * scaleX, 18f * scaleY)
                                quadraticTo(36f * scaleX, 18f * scaleY, 36f * scaleX, 24f * scaleY)
                                lineTo(40f * scaleX, 24f * scaleY)
                                quadraticTo(40f * scaleX, 14f * scaleY, 31f * scaleX, 14f * scaleY)
                                quadraticTo(24f * scaleX, 14f * scaleY, 24f * scaleX, 20f * scaleY)
                                quadraticTo(24f * scaleX, 18f * scaleY, 23f * scaleX, 19f * scaleY)
                                lineTo(25f * scaleX, 22f * scaleY)
                                close()
                        }
                drawPath(shadePath, color = primaryColor.copy(alpha = 0.7f))

                // Highlight for 3D effect (lighter on left loop)
                val highlightPath =
                        Path().apply {
                                moveTo(8f * scaleX, 24f * scaleY)
                                quadraticTo(8f * scaleX, 14f * scaleY, 17f * scaleX, 14f * scaleY)
                                quadraticTo(20f * scaleX, 14f * scaleY, 22f * scaleX, 16f * scaleY)
                                lineTo(20f * scaleX, 19f * scaleY)
                                quadraticTo(19f * scaleX, 18f * scaleY, 18f * scaleX, 18f * scaleY)
                                quadraticTo(12f * scaleX, 18f * scaleY, 12f * scaleX, 24f * scaleY)
                                quadraticTo(12f * scaleX, 26f * scaleY, 13f * scaleX, 28f * scaleY)
                                lineTo(10f * scaleX, 28f * scaleY)
                                quadraticTo(8f * scaleX, 26f * scaleY, 8f * scaleX, 24f * scaleY)
                                close()
                        }
                drawPath(highlightPath, color = secondaryColor.copy(alpha = 0.3f))
        }
}

/** Text selection floating button - monochrome style */
@Composable
fun TextSelectionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
        Surface(
                onClick = onClick,
                modifier = modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = NothingCharcoal,
                border = androidx.compose.foundation.BorderStroke(1.dp, NothingGray800)
        ) {
                Box(contentAlignment = Alignment.Center) {
                        Icon(
                                imageVector = Icons.Outlined.TextFields,
                                contentDescription = "Select text",
                                tint = NothingWhite,
                                modifier = Modifier.size(20.dp)
                        )
                }
        }
}
/** Suggestions panel - Nothing-style minimalist design */
@Composable
fun SuggestionsPanel(
        state: SuggestionState,
        onSuggestionClick: (Suggestion) -> Unit,
        onDismiss: () -> Unit,
        onFocusAreaClick: () -> Unit = {},
        onClearFocusArea: () -> Unit = {},
        onFastForward: () -> Unit = {},
        modifier: Modifier = Modifier
) {
        AnimatedVisibility(
                visible = state.isVisible,
                enter =
                        slideInVertically(initialOffsetY = { it }) +
                                fadeIn(animationSpec = tween(200)),
                exit =
                        slideOutVertically(targetOffsetY = { it }) +
                                fadeOut(animationSpec = tween(150)),
                modifier = modifier
        ) {
                Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        color = NothingBlack,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                                // Minimal header
                                NothingPanelHeader(
                                        isLoading = state.isLoading,
                                        isStreaming = state.isStreaming,
                                        isCloudActive = state.isCloudInferenceActive,
                                        suggestionCount = state.suggestions.size,
                                        focusRegion = state.focusRegion,
                                        onDismiss = onDismiss,
                                        onFocusAreaClick = onFocusAreaClick,
                                        onClearFocusArea = onClearFocusArea
                                )

                                // Thin red accent line
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(NothingRed.copy(alpha = 0.6f))
                                )

                                // Content
                                when {
                                        state.isLoading -> {
                                                NothingLoadingState(
                                                        streamingText = state.streamingText,
                                                        isStreaming = state.isStreaming,
                                                        isCloudActive =
                                                                state.isCloudInferenceActive,
                                                        canUseFastForward = state.canUseFastForward,
                                                        onFastForward = onFastForward
                                                )
                                        }
                                        state.error != null -> {
                                                NothingErrorState(error = state.error)
                                        }
                                        state.suggestions.isEmpty() -> {
                                                NothingEmptyState()
                                        }
                                        else -> {
                                                NothingSuggestionsList(
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
private fun NothingPanelHeader(
        isLoading: Boolean,
        isStreaming: Boolean = false,
        isCloudActive: Boolean = false,
        suggestionCount: Int,
        focusRegion: FocusRegion? = null,
        onDismiss: () -> Unit,
        onFocusAreaClick: () -> Unit = {},
        onClearFocusArea: () -> Unit = {}
) {
        // Cloud indicator color - cyan/blue for cloud
        val NothingCloud = Color(0xFF00D4FF)

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(NothingCharcoal)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // Status indicator - minimal dot (cyan for cloud, red for local)
                Box(
                        modifier =
                                Modifier.size(8.dp)
                                        .background(
                                                when {
                                                        isCloudActive -> NothingCloud
                                                        isLoading -> NothingRed
                                                        else -> NothingGray600
                                                },
                                                RoundedCornerShape(50)
                                        )
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Text(
                                        text = "omni",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NothingWhite,
                                        fontFamily = Ndot57,
                                        letterSpacing = 2.sp
                                )

                                if (focusRegion != null) {
                                        FocusAreaIndicator(
                                                focusRegion = focusRegion,
                                                onClear = onClearFocusArea
                                        )
                                }
                        }
                        Text(
                                text =
                                        when {
                                                isCloudActive -> "⚡ fast forward"
                                                isStreaming -> "thinking"
                                                isLoading ->
                                                        if (focusRegion != null) "analyzing region"
                                                        else "analyzing"
                                                focusRegion != null ->
                                                        "$suggestionCount suggestions · focused"
                                                else -> "$suggestionCount suggestions"
                                        },
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingGray500,
                                letterSpacing = 0.5.sp
                        )
                }

                // Focus area toggle
                IconButton(onClick = onFocusAreaClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                                imageVector = Icons.Outlined.CropFree,
                                contentDescription = "Select focus area",
                                tint = if (focusRegion != null) NothingRed else NothingGray500,
                                modifier = Modifier.size(18.dp)
                        )
                }

                // Close button
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = NothingGray500,
                                modifier = Modifier.size(18.dp)
                        )
                }
        }
}

@Composable
private fun NothingLoadingState(
        streamingText: String,
        isStreaming: Boolean,
        isCloudActive: Boolean = false,
        canUseFastForward: Boolean = true,
        onFastForward: () -> Unit = {}
) {
        val scrollState = rememberScrollState()
        val NothingCloud = Color(0xFF00D4FF)

        LaunchedEffect(streamingText) { scrollState.animateScrollTo(scrollState.maxValue) }

        Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Header row with loading indicator and fast forward button
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Loading indicator - dot sequence
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                        repeat(3) { _ ->
                                        Box(
                                                modifier =
                                                        Modifier.size(4.dp)
                                                                .background(
                                                                        if (isCloudActive)
                                                                                NothingCloud
                                                                        else NothingRed,
                                                                        RoundedCornerShape(50)
                                                                )
                                        )
                                }

                                if (isCloudActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text = "⚡ cloud",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NothingCloud,
                                                fontWeight = FontWeight.Medium
                                        )
                                }
                        }

                        // Fast Forward button - show when local inference is running
                        if (canUseFastForward && !isCloudActive) {
                                Surface(
                                        onClick = onFastForward,
                                        modifier = Modifier.size(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, NothingRed)
                                ) {
                                        Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                        imageVector = Icons.Default.FastForward,
                                                        contentDescription = "Fast forward with cloud AI",
                                                        tint = NothingRed,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                }
                        }
                }

                // Streaming text output
                if (streamingText.isNotEmpty()) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .heightIn(min = 48.dp, max = 180.dp)
                                                .border(
                                                        1.dp,
                                                        NothingGray800,
                                                        RoundedCornerShape(12.dp)
                                                )
                                                .padding(12.dp)
                        ) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth().verticalScroll(scrollState)
                                ) {
                                        Row {
                                                Text(
                                                        text = streamingText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = NothingGray300,
                                                        fontFamily =
                                                                androidx.compose.ui.text.font
                                                                        .FontFamily.Monospace,
                                                        lineHeight = 18.sp
                                                )

                                                // Static cursor (animation removed)
                                                Text(
                                                        text = "_",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = NothingRed,
                                                        fontFamily =
                                                                androidx.compose.ui.text.font
                                                                        .FontFamily.Monospace
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun NothingErrorState(error: String) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Box(modifier = Modifier.size(4.dp).background(NothingRed, RoundedCornerShape(50)))
                Text(
                        text = error.lowercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingGray400,
                        letterSpacing = 0.5.sp
                )
        }
}

@Composable
private fun NothingEmptyState() {
        Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Text(
                        text = "no suggestions",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingGray500,
                        fontFamily = Ndot57
                )
                Text(
                        text = "try selecting a different area",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingGray600
                )
        }
}

@Composable
private fun NothingSuggestionsList(
        suggestions: List<Suggestion>,
        onSuggestionClick: (Suggestion) -> Unit
) {
        LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
                items(suggestions, key = { it.id }) { suggestion ->
                        NothingSuggestionCard(
                                suggestion = suggestion,
                                onClick = { onSuggestionClick(suggestion) }
                        )
                }
        }
}

@Composable
private fun NothingSuggestionCard(suggestion: Suggestion, onClick: () -> Unit) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Minimal icon indicator
                        Icon(
                                imageVector = getSuggestionIcon(suggestion.icon),
                                contentDescription = null,
                                tint = NothingGray500,
                                modifier = Modifier.size(18.dp)
                        )

                        Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                                Text(
                                        text = suggestion.title.lowercase(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NothingWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                        text = suggestion.description.lowercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingGray500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }

                        // Subtle arrow
                        Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = NothingGray700,
                                modifier = Modifier.size(16.dp)
                        )
                }
        }

        // Subtle divider
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(NothingGray900)
        )
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
                Suggestion.SuggestionIcon.MUSIC -> Icons.Outlined.MusicNote
                Suggestion.SuggestionIcon.SETTINGS -> Icons.Outlined.Settings
                Suggestion.SuggestionIcon.WEB -> Icons.Outlined.Language
        }
}

/** Complete overlay composable */
@Composable
fun OmniOverlay(
        state: SuggestionState,
        onButtonClick: () -> Unit,
        onSuggestionClick: (Suggestion) -> Unit,
        onDismiss: () -> Unit,
        onFocusAreaClick: () -> Unit = {},
        onClearFocusArea: () -> Unit = {},
        onFastForward: () -> Unit = {}
) {
        Box(modifier = Modifier.fillMaxSize()) {
                // Suggestions panel at bottom
                SuggestionsPanel(
                        state = state,
                        onSuggestionClick = onSuggestionClick,
                        onDismiss = onDismiss,
                        onFocusAreaClick = onFocusAreaClick,
                        onClearFocusArea = onClearFocusArea,
                        onFastForward = onFastForward,
                        modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Floating button
                if (!state.isVisible) {
                        OmniFloatingButton(
                                onClick = onButtonClick,
                                isLoading = state.isLoading,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        )
                }
        }
}
