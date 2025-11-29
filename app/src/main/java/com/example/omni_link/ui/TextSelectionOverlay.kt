package com.example.omni_link.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.ai.TextOption
import com.example.omni_link.data.TextBlock
import com.example.omni_link.data.TextSelectionState
import com.example.omni_link.ui.theme.*

/**
 * Circle-to-Search style text selection overlay.
 * Shows a screenshot with highlightable text regions detected by OCR.
 *
 * User can:
 * - Tap on text to select individual words
 * - Draw a circle/lasso to select multiple text blocks
 * - Long-press to start multi-selection mode
 */
@Composable
fun TextSelectionOverlay(
    state: TextSelectionState,
    onTextBlockTapped: (Int) -> Unit,
    onTextBlocksSelected: (List<Int>) -> Unit,
    onSelectionPathUpdate: (List<Pair<Float, Float>>) -> Unit,
    onSelectionComplete: () -> Unit,
    onCopyText: () -> Unit,
    onSearchText: () -> Unit,
    onShareText: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animation for selection highlights
    val infiniteTransition = rememberInfiniteTransition(label = "selection_pulse")
    val highlightAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "highlightAlpha"
    )

    // Track current drawing path
    var currentPath by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }

    // Get status bar height to offset text block coordinates
    // The screenshot includes the status bar, but the overlay starts below it
    val statusBarHeight = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize()) {
        // Background screenshot
        state.screenshotBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Screen capture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Semi-transparent overlay for better text visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.15f))
        )

        // Canvas for drawing text highlights and selection path
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(statusBarHeight) {
                    val yOffset = statusBarHeight.toPx()
                    detectTapGestures(
                        onTap = { offset ->
                            // Find text block at tap location (add offset back for comparison)
                            val index = state.textBlocks.indexOfFirst { block ->
                                block.contains(offset.x, offset.y + yOffset)
                            }
                            if (index >= 0) {
                                onTextBlockTapped(index)
                            }
                        }
                    )
                }
                .pointerInput(statusBarHeight) {
                    val yOffset = statusBarHeight.toPx()
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDrawing = true
                            currentPath = listOf(offset.x to (offset.y + yOffset))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPath = currentPath + (change.position.x to (change.position.y + yOffset))
                            onSelectionPathUpdate(currentPath)
                        },
                        onDragEnd = {
                            isDrawing = false
                            if (currentPath.size >= 3) {
                                // Find all text blocks within the drawn path
                                val selectedIndices = findTextBlocksInPath(state.textBlocks, currentPath)
                                if (selectedIndices.isNotEmpty()) {
                                    onTextBlocksSelected(selectedIndices)
                                }
                            }
                            currentPath = emptyList()
                            onSelectionComplete()
                        }
                    )
                }
        ) {
            // Calculate Y offset to align text blocks with the visible overlay
            // Screenshot includes status bar, but overlay is below it
            val yOffset = statusBarHeight.toPx()

            // Draw highlight boxes for all detected text
            state.textBlocks.forEachIndexed { index, block ->
                val isSelected = state.selectedBlocks.contains(index)
                val bounds = block.bounds

                // Subtract yOffset since overlay starts below status bar
                val adjustedTop = bounds.top.toFloat() - yOffset

                // Subtle highlight for all text blocks
                drawRect(
                    color = if (isSelected) {
                        OmniYellow.copy(alpha = highlightAlpha)
                    } else {
                        OmniCyan.copy(alpha = 0.1f)
                    },
                    topLeft = Offset(bounds.left.toFloat(), adjustedTop),
                    size = Size(bounds.width().toFloat(), bounds.height().toFloat())
                )

                // Border for selected blocks
                if (isSelected) {
                    drawRect(
                        color = OmniYellow,
                        topLeft = Offset(bounds.left.toFloat(), adjustedTop),
                        size = Size(bounds.width().toFloat(), bounds.height().toFloat()),
                        style = Stroke(width = 3f)
                    )
                }
            }

            // Draw the selection path (circle/lasso)
            // Path coordinates include offset, so subtract it for drawing
            if (currentPath.size >= 2) {
                val path = Path().apply {
                    moveTo(currentPath.first().first, currentPath.first().second - yOffset)
                    for (i in 1 until currentPath.size) {
                        lineTo(currentPath[i].first, currentPath[i].second - yOffset)
                    }
                }

                // Selection path stroke
                drawPath(
                    path = path,
                    color = OmniYellow,
                    style = Stroke(width = 4f)
                )

                // Selection path fill (semi-transparent)
                if (currentPath.size >= 3) {
                    val closedPath = Path().apply {
                        moveTo(currentPath.first().first, currentPath.first().second - yOffset)
                        for (i in 1 until currentPath.size) {
                            lineTo(currentPath[i].first, currentPath[i].second - yOffset)
                        }
                        close()
                    }
                    drawPath(
                        path = closedPath,
                        color = OmniYellow.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // Top instruction bar
        TopInstructionBar(
            isProcessing = state.isProcessing,
            textBlockCount = state.textBlocks.size,
            isDrawing = isDrawing,
            onDismiss = onDismiss
        )

        // Bottom action bar (shown when text is selected)
        AnimatedVisibility(
            visible = state.hasSelection(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SelectedTextActionBar(
                selectedText = state.getSelectedText(),
                onCopy = onCopyText,
                onSearch = onSearchText,
                onShare = onShareText,
                onClear = { onTextBlocksSelected(emptyList()) }
            )
        }

        // Processing indicator
        if (state.isProcessing) {
            ProcessingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Error display
        state.error?.let { error ->
            ErrorBanner(
                error = error,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun TopInstructionBar(
    isProcessing: Boolean,
    textBlockCount: Int,
    isDrawing: Boolean,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        color = OmniBlack.copy(alpha = 0.95f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isDrawing) OmniYellow else OmniCyan,
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDrawing) Icons.Outlined.Gesture else Icons.Outlined.TextFields,
                    contentDescription = null,
                    tint = OmniBlack,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isProcessing -> "SCANNING TEXT..."
                        isDrawing -> "CIRCLE TO SELECT"
                        else -> "TEXT SELECTION"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = OmniWhite,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        isProcessing -> "Detecting text on screen"
                        isDrawing -> "Release to select text"
                        textBlockCount > 0 -> "Found $textBlockCount text elements • Tap or circle to select"
                        else -> "No text detected on screen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniGrayText
                )
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .background(OmniGrayMid, RoundedCornerShape(4.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = OmniWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedTextActionBar(
    selectedText: String,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = OmniBlack,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Selected text preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = OmniGrayDark,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = selectedText.take(150) + if (selectedText.length > 150) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniWhite,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy",
                    color = OmniCyan,
                    onClick = onCopy
                )
                ActionButton(
                    icon = Icons.Outlined.Search,
                    label = "Search",
                    color = OmniYellow,
                    onClick = onSearch
                )
                ActionButton(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    color = OmniGreen,
                    onClick = onShare
                )
                ActionButton(
                    icon = Icons.Outlined.Close,
                    label = "Clear",
                    color = OmniRed,
                    onClick = onClear
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ProcessingIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = OmniBlack.copy(alpha = 0.9f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = OmniCyan,
                strokeWidth = 3.dp
            )
            Column {
                Text(
                    text = "SCANNING",
                    style = MaterialTheme.typography.labelMedium,
                    color = OmniWhite,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Detecting text...",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniGrayText
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(32.dp),
        color = OmniBlack.copy(alpha = 0.95f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, OmniRed)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(OmniRed, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.labelLarge,
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

/**
 * Find text blocks whose center points are inside the drawn path.
 */
private fun findTextBlocksInPath(blocks: List<TextBlock>, path: List<Pair<Float, Float>>): List<Int> {
    if (path.size < 3) return emptyList()

    return blocks.mapIndexedNotNull { index, block ->
        val centerX = block.centerX()
        val centerY = block.centerY()
        if (isPointInPolygon(centerX, centerY, path)) index else null
    }
}

/**
 * Ray casting algorithm to check if a point is inside a polygon.
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
