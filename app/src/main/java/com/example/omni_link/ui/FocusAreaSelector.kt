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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.data.FocusAreaSelectionState
import com.example.omni_link.data.FocusRegion
import com.example.omni_link.ui.theme.*

/** Nothing-style Focus Area Selector Minimalist, monochrome with red accent for selection */
@Suppress("UNUSED_PARAMETER")
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
        // Static values (animations removed)
        val pulseAlpha = 0.35f
        val dashOffset = 0f

        Box(modifier = modifier.fillMaxSize()) {
                // Dark overlay
                Box(modifier = Modifier.fillMaxSize().background(NothingBlack.copy(alpha = 0.5f)))

                // Touch detection canvas
                Canvas(
                        modifier =
                                Modifier.fillMaxSize().pointerInput(Unit) {
                                        detectDragGestures(
                                                onDragStart = { offset ->
                                                        onSelectionStart(offset.x, offset.y)
                                                },
                                                onDrag = { change, _ ->
                                                        change.consume()
                                                        onSelectionUpdate(
                                                                change.position.x,
                                                                change.position.y
                                                        )
                                                },
                                                onDragEnd = { onSelectionEnd() }
                                        )
                                }
                ) {
                        if (selectionState.isSelecting) {
                                val left = minOf(selectionState.startX, selectionState.currentX)
                                val top = minOf(selectionState.startY, selectionState.currentY)
                                val right = maxOf(selectionState.startX, selectionState.currentX)
                                val bottom = maxOf(selectionState.startY, selectionState.currentY)

                                // Selection fill - subtle red tint
                                drawRect(
                                        color = NothingRed.copy(alpha = pulseAlpha * 0.3f),
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top)
                                )

                                // Dashed border
                                drawRect(
                                        color = NothingRed.copy(alpha = 0.8f),
                                        topLeft = Offset(left, top),
                                        size = Size(right - left, bottom - top),
                                        style =
                                                Stroke(
                                                        width = 2f,
                                                        pathEffect =
                                                                PathEffect.dashPathEffect(
                                                                        floatArrayOf(10f, 8f),
                                                                        dashOffset
                                                                )
                                                )
                                )

                                // Corner dots - Nothing style
                                val dotSize = 6f
                                listOf(
                                                Offset(left, top),
                                                Offset(right - dotSize, top),
                                                Offset(left, bottom - dotSize),
                                                Offset(right - dotSize, bottom - dotSize)
                                        )
                                        .forEach { corner ->
                                                drawRect(
                                                        color = NothingRed,
                                                        topLeft = corner,
                                                        size = Size(dotSize, dotSize)
                                                )
                                        }
                        }
                }

                // Minimal instruction hint
                Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                        color = NothingBlack.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(24.dp)
                ) {
                        Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                // Status dot
                                Box(
                                        modifier =
                                                Modifier.size(6.dp)
                                                        .background(
                                                                if (selectionState.isSelecting)
                                                                        NothingRed
                                                                else NothingGray500,
                                                                RoundedCornerShape(50)
                                                        )
                                )

                                Text(
                                        text =
                                                if (selectionState.isSelecting) "release to analyze"
                                                else "drag to select area",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingWhite,
                                        fontFamily = Ndot57
                                )

                                // Cancel button
                                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel",
                                                tint = NothingGray500,
                                                modifier = Modifier.size(16.dp)
                                        )
                                }
                        }
                }
        }
}

/** Minimal focus area indicator */
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
                        Row(
                                modifier = Modifier.height(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                // Red dot indicator
                                Box(
                                        modifier =
                                                Modifier.size(4.dp)
                                                        .background(
                                                                NothingRed,
                                                                RoundedCornerShape(50)
                                                        )
                                )

                                Text(
                                        text = "focused",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingGray400,
                                        letterSpacing = 0.5.sp
                                )

                                // Clear button - very minimal
                                IconButton(onClick = onClear, modifier = Modifier.size(16.dp)) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear focus",
                                                tint = NothingGray500,
                                                modifier = Modifier.size(10.dp)
                                        )
                                }
                        }
                }
        }
}
