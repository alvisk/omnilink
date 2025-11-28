package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.omni_link.data.Suggestion
import com.example.omni_link.data.SuggestionState
import com.example.omni_link.ui.theme.*
import kotlin.math.roundToInt

/** Floating action button that triggers AI suggestions */
@Composable
fun OmniFloatingButton(
        onClick: () -> Unit,
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

    Box(
            modifier =
                    modifier
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
    ) {
        // Outer glow effect
        Box(
                modifier =
                        Modifier.size(64.dp)
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

        // Main button
        Surface(
                onClick = onClick,
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

/** Suggestions panel that displays AI-generated suggestions */
@Composable
fun SuggestionsPanel(
        state: SuggestionState,
        onSuggestionClick: (Suggestion) -> Unit,
        onDismiss: () -> Unit,
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
                        suggestionCount = state.suggestions.size,
                        onDismiss = onDismiss
                )

                // Red accent line
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(OmniRed))

                // Content
                when {
                    state.isLoading -> {
                        LoadingState()
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
        suggestionCount: Int,
        onDismiss: () -> Unit
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
                        text = "...",
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
            Text(
                    text = "SUGGESTIONS",
                    style = MaterialTheme.typography.titleSmall,
                    color = OmniWhite,
                    letterSpacing = 3.sp
            )
            Text(
                    text = if (isLoading) "ANALYZING SCREEN..." else "$suggestionCount AVAILABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniGrayText,
                    letterSpacing = 1.sp
            )
        }

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
        items(suggestions) { suggestion ->
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
    }
}

/** Complete overlay composable that combines the floating button and suggestions panel */
@Composable
fun OmniOverlay(
        state: SuggestionState,
        onButtonClick: () -> Unit,
        onSuggestionClick: (Suggestion) -> Unit,
        onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Suggestions panel at bottom
        SuggestionsPanel(
                state = state,
                onSuggestionClick = onSuggestionClick,
                onDismiss = onDismiss,
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
