package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.data.FocusAreaSelectionState
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.ui.theme.*

/**
 * Lightweight Focus Area Selector
 * - Tap or drag to select an area
 * - Auto-dismisses and triggers AI analysis on release
 * - No confirmation dialogs - direct, immediate interaction
 */
@Composable
fun FocusAreaSelectorOverlay(
        selectionState: FocusAreaSelectionState,
        currentRegion: FocusRegion?,
        onSelectionStart: (Float, Float) -> Unit,
        onSelectionUpdate: (Float, Float) -> Unit,
        onSelectionEnd: () -> Unit,
        onClearFocus: () -> Unit,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "focus_pulse")
    val pulseAlpha by
            infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0.8f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(500, easing = EaseInOutCubic),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "pulseAlpha"
            )

    // Animated dash offset for selection border
    val dashOffset by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 24f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(400, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "dashOffset"
            )

    Box(modifier = modifier.fillMaxSize()) {
        // Light semi-transparent overlay so user can see content underneath
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))

        // Touch detection canvas for drawing selection
        Canvas(
                modifier =
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectDragGestures(
                                    onDragStart = { offset ->
                                        onSelectionStart(offset.x, offset.y)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        onSelectionUpdate(change.position.x, change.position.y)
                                    },
                                    onDragEnd = { onSelectionEnd() }
                            )
                        }
        ) {
            // Draw the current selection being made
            if (selectionState.isSelecting) {
                val left = minOf(selectionState.startX, selectionState.currentX)
                val top = minOf(selectionState.startY, selectionState.currentY)
                val right = maxOf(selectionState.startX, selectionState.currentX)
                val bottom = maxOf(selectionState.startY, selectionState.currentY)

                // Selection rectangle fill - pulsing yellow highlight
                drawRect(
                        color = OmniYellow.copy(alpha = pulseAlpha * 0.35f),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top)
                )

                // Animated dashed border
                drawRect(
                        color = OmniYellow,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style =
                                Stroke(
                                        width = 4f,
                                        pathEffect =
                                                PathEffect.dashPathEffect(
                                                        floatArrayOf(14f, 10f),
                                                        dashOffset
                                                )
                                )
                )

                // Corner handles for visual feedback
                val handleSize = 18f
                listOf(
                                Offset(left, top),
                                Offset(right - handleSize, top),
                                Offset(left, bottom - handleSize),
                                Offset(right - handleSize, bottom - handleSize)
                        )
                        .forEach { corner ->
                            drawRect(
                                    color = OmniYellow,
                                    topLeft = corner,
                                    size = Size(handleSize, handleSize)
                            )
                        }
            }
        }

        // Minimal instruction hint at top - no buttons blocking the view
        Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = OmniBlack.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = OmniYellow,
                        modifier = Modifier.size(20.dp)
                )
                Text(
                        text =
                                if (selectionState.isSelecting) "RELEASE TO ANALYZE"
                                else "TAP & DRAG TO SELECT",
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniWhite,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Cancel button
                IconButton(
                        onClick = onDismiss,
                        modifier =
                                Modifier.size(30.dp)
                                        .background(OmniGrayMid, RoundedCornerShape(4.dp))
                ) {
                    Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = OmniWhite,
                            modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Compact focus area indicator shown in the suggestions panel header */
@Composable
fun FocusAreaIndicator(
        focusRegion: FocusRegion?,
        onClear: () -> Unit,
        modifier: Modifier = Modifier
) {
    AnimatedVisibility(
            visible = focusRegion != null,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
            modifier = modifier
    ) {
        focusRegion?.let {
            Surface(
                    modifier = Modifier.height(28.dp),
                    color = OmniYellow.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OmniYellow)
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            imageVector = Icons.Outlined.CropFree,
                            contentDescription = null,
                            tint = OmniYellow,
                            modifier = Modifier.size(14.dp)
                    )
                    Text(
                            text = "FOCUSED",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniYellow,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                    )

                    // Clear button
                    IconButton(onClick = onClear, modifier = Modifier.size(18.dp)) {
                        Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear focus area",
                                tint = OmniYellow,
                                modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

