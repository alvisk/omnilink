package com.example.omni_link.ui

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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.omni_link.ai.TextOption
import com.example.omni_link.data.TextBlock
import com.example.omni_link.data.TextSelectionState
import com.example.omni_link.ui.theme.*

/** Nothing-style text selection overlay. Monochrome design with red accent for selections. */
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
        onGenerateOptions: () -> Unit = {},
        onOptionSelected: (TextOption) -> Unit = {},
        onFastForward: () -> Unit = {},
        modifier: Modifier = Modifier
) {
        // Fluid entrance animation state
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }

        // Smooth spring animation for scale
        val scale by
                animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0.92f,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow
                                ),
                        label = "scale"
                )

        // Fade animation
        val alpha by
                animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "alpha"
                )

        // Blur effect that clears as it enters
        val blurRadius by
                animateFloatAsState(
                        targetValue = if (isVisible) 0f else 16f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                        label = "blur"
                )

        // Static highlight (animations removed)
        val highlightAlpha = 0.25f

        var currentPath by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
        var isDrawing by remember { mutableStateOf(false) }

        val statusBarHeight = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

        Box(
                modifier =
                        modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                        }
        ) {
                // Background screenshot with fluid animation
                state.screenshotBitmap?.let { bitmap ->
                        Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Screen capture",
                                modifier =
                                        Modifier.fillMaxSize()
                                                .then(
                                                        if (blurRadius > 0.5f)
                                                                Modifier.blur(blurRadius.dp)
                                                        else Modifier
                                                ),
                                contentScale = ContentScale.FillBounds
                        )
                }

                // Dark overlay for contrast - animates in
                Box(
                        modifier =
                                Modifier.fillMaxSize()
                                        .background(NothingBlack.copy(alpha = 0.4f * alpha))
                )

                // Canvas for text highlights and selection
                Canvas(
                        modifier =
                                Modifier.fillMaxSize()
                                        .pointerInput(statusBarHeight, state.screenshotBitmap) {
                                                val yOffset = statusBarHeight.toPx()
                                                // Calculate scale factors from bitmap to screen
                                                // coordinates
                                                val bitmap = state.screenshotBitmap
                                                val scaleX =
                                                        if (bitmap != null)
                                                                size.width.toFloat() / bitmap.width
                                                        else 1f
                                                val scaleY =
                                                        if (bitmap != null)
                                                                size.height.toFloat() /
                                                                        bitmap.height
                                                        else 1f

                                                detectTapGestures(
                                                        onTap = { offset ->
                                                                // Convert screen coordinates to
                                                                // bitmap coordinates for hit
                                                                // testing
                                                                val bitmapX = offset.x / scaleX
                                                                val bitmapY =
                                                                        (offset.y + yOffset) /
                                                                                scaleY

                                                                val index =
                                                                        state.textBlocks
                                                                                .indexOfFirst {
                                                                                        block ->
                                                                                        block.contains(
                                                                                                bitmapX,
                                                                                                bitmapY
                                                                                        )
                                                                                }
                                                                if (index >= 0) {
                                                                        onTextBlockTapped(index)
                                                                }
                                                        }
                                                )
                                        }
                                        .pointerInput(statusBarHeight, state.screenshotBitmap) {
                                                val yOffset = statusBarHeight.toPx()
                                                // Calculate scale factors from bitmap to screen
                                                // coordinates
                                                val bitmap = state.screenshotBitmap
                                                val scaleX =
                                                        if (bitmap != null)
                                                                size.width.toFloat() / bitmap.width
                                                        else 1f
                                                val scaleY =
                                                        if (bitmap != null)
                                                                size.height.toFloat() /
                                                                        bitmap.height
                                                        else 1f

                                                detectDragGestures(
                                                        onDragStart = { offset ->
                                                                isDrawing = true
                                                                // Store path in bitmap coordinates
                                                                // for hit testing
                                                                val bitmapX = offset.x / scaleX
                                                                val bitmapY =
                                                                        (offset.y + yOffset) /
                                                                                scaleY
                                                                currentPath =
                                                                        listOf(bitmapX to bitmapY)
                                                        },
                                                        onDrag = { change, _ ->
                                                                change.consume()
                                                                val bitmapX =
                                                                        change.position.x / scaleX
                                                                val bitmapY =
                                                                        (change.position.y +
                                                                                yOffset) / scaleY
                                                                currentPath =
                                                                        currentPath +
                                                                                (bitmapX to bitmapY)
                                                                onSelectionPathUpdate(currentPath)
                                                        },
                                                        onDragEnd = {
                                                                isDrawing = false
                                                                if (currentPath.size >= 3) {
                                                                        val selectedIndices =
                                                                                findTextBlocksInPath(
                                                                                        state.textBlocks,
                                                                                        currentPath
                                                                                )
                                                                        if (selectedIndices
                                                                                        .isNotEmpty()
                                                                        ) {
                                                                                onTextBlocksSelected(
                                                                                        selectedIndices
                                                                                )
                                                                        }
                                                                }
                                                                currentPath = emptyList()
                                                                onSelectionComplete()
                                                        }
                                                )
                                        }
                ) {
                        val yOffset = statusBarHeight.toPx()

                        // Calculate scale factors from bitmap to screen coordinates
                        val bitmap = state.screenshotBitmap
                        val scaleX = if (bitmap != null) size.width / bitmap.width else 1f
                        val scaleY = if (bitmap != null) size.height / bitmap.height else 1f

                        // Draw text block highlights
                        state.textBlocks.forEachIndexed { index, block ->
                                val isSelected = state.selectedBlocks.contains(index)
                                val bounds = block.bounds

                                // Scale bitmap coordinates to screen coordinates
                                val scaledLeft = bounds.left * scaleX
                                val scaledTop = bounds.top * scaleY - yOffset
                                val scaledWidth = bounds.width() * scaleX
                                val scaledHeight = bounds.height() * scaleY

                                // Subtle detection indicator for all blocks
                                drawRect(
                                        color =
                                                if (isSelected) {
                                                        NothingRed.copy(alpha = highlightAlpha)
                                                } else {
                                                        NothingWhite.copy(alpha = 0.05f)
                                                },
                                        topLeft = Offset(scaledLeft, scaledTop),
                                        size = Size(scaledWidth, scaledHeight)
                                )

                                // Selection border
                                if (isSelected) {
                                        drawRect(
                                                color = NothingRed.copy(alpha = 0.8f),
                                                topLeft = Offset(scaledLeft, scaledTop),
                                                size = Size(scaledWidth, scaledHeight),
                                                style = Stroke(width = 2f)
                                        )
                                }
                        }

                        // Draw selection path (path is in bitmap coordinates, need to scale to
                        // screen)
                        if (currentPath.size >= 2) {
                                val path =
                                        Path().apply {
                                                val firstX = currentPath.first().first * scaleX
                                                val firstY =
                                                        currentPath.first().second * scaleY -
                                                                yOffset
                                                moveTo(firstX, firstY)
                                                for (i in 1 until currentPath.size) {
                                                        val x = currentPath[i].first * scaleX
                                                        val y =
                                                                currentPath[i].second * scaleY -
                                                                        yOffset
                                                        lineTo(x, y)
                                                }
                                        }

                                // Path stroke
                                drawPath(
                                        path = path,
                                        color = NothingRed.copy(alpha = 0.8f),
                                        style = Stroke(width = 2f)
                                )

                                // Path fill
                                if (currentPath.size >= 3) {
                                        val closedPath =
                                                Path().apply {
                                                        val firstX =
                                                                currentPath.first().first * scaleX
                                                        val firstY =
                                                                currentPath.first().second *
                                                                        scaleY - yOffset
                                                        moveTo(firstX, firstY)
                                                        for (i in 1 until currentPath.size) {
                                                                val x =
                                                                        currentPath[i].first *
                                                                                scaleX
                                                                val y =
                                                                        currentPath[i].second *
                                                                                scaleY - yOffset
                                                                lineTo(x, y)
                                                        }
                                                        close()
                                                }
                                        drawPath(
                                                path = closedPath,
                                                color = NothingRed.copy(alpha = 0.1f)
                                        )
                                }
                        }
                }

                // Top instruction bar
                NothingTopBar(
                        isProcessing = state.isProcessing,
                        textBlockCount = state.textBlocks.size,
                        isDrawing = isDrawing,
                        onDismiss = onDismiss
                )

                // Bottom action bar
                AnimatedVisibility(
                        visible =
                                state.hasSelection() ||
                                        state.textOptions.isNotEmpty() ||
                                        state.isGeneratingOptions,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                        NothingActionBar(
                                selectedText = state.getSelectedText(),
                                textOptions = state.textOptions,
                                isGeneratingOptions = state.isGeneratingOptions,
                                streamingText = state.optionsStreamingText,
                                onCopy = onCopyText,
                                onSearch = onSearchText,
                                onShare = onShareText,
                                onClear = { onTextBlocksSelected(emptyList()) },
                                onGenerateOptions = onGenerateOptions,
                                onOptionSelected = onOptionSelected,
                                onFastForward = onFastForward
                        )
                }

                // Processing indicator
                if (state.isProcessing) {
                        NothingProcessingIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Error display
                state.error?.let { error ->
                        NothingErrorBanner(
                                error = error,
                                modifier = Modifier.align(Alignment.Center)
                        )
                }
        }
}

@Composable
private fun NothingTopBar(
        isProcessing: Boolean,
        textBlockCount: Int,
        isDrawing: Boolean,
        onDismiss: () -> Unit
) {
        Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
                color = NothingBlack.copy(alpha = 0.95f),
                shape = RoundedCornerShape(20.dp)
        ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        // Status dot
                        Box(
                                modifier =
                                        Modifier.size(6.dp)
                                                .background(
                                                        if (isDrawing) NothingRed
                                                        else NothingGray500,
                                                        RoundedCornerShape(50)
                                                )
                        )

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text =
                                                when {
                                                        isProcessing -> "scanning"
                                                        isDrawing -> "selecting"
                                                        else -> "text selection"
                                                },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NothingWhite,
                                        fontFamily = Ndot57
                                )
                                Text(
                                        text =
                                                when {
                                                        isProcessing -> "detecting text on screen"
                                                        isDrawing -> "release to select"
                                                        textBlockCount > 0 ->
                                                                "$textBlockCount text elements · tap or circle to select"
                                                        else -> "no text detected"
                                                },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingGray500
                                )
                        }

                        // Close button
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = NothingGray500,
                                        modifier = Modifier.size(18.dp)
                                )
                        }
                }
        }
}

@Composable
private fun NothingActionBar(
        selectedText: String,
        textOptions: List<TextOption>,
        isGeneratingOptions: Boolean,
        streamingText: String,
        onCopy: () -> Unit,
        onSearch: () -> Unit,
        onShare: () -> Unit,
        onClear: () -> Unit,
        onGenerateOptions: () -> Unit,
        onOptionSelected: (TextOption) -> Unit,
        onFastForward: () -> Unit = {}
) {
        Surface(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = NothingBlack,
                shape = RoundedCornerShape(20.dp)
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        // Selected text preview
                        if (selectedText.isNotBlank()) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .border(
                                                                1.dp,
                                                                NothingGray800,
                                                                RoundedCornerShape(12.dp)
                                                        )
                                                        .padding(12.dp)
                                ) {
                                        Text(
                                                text =
                                                        selectedText.take(120) +
                                                                if (selectedText.length > 120) "…"
                                                                else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NothingGray300,
                                                maxLines = 2
                                        )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // AI Options Section
                        if (isGeneratingOptions ||
                                        textOptions.isNotEmpty() ||
                                        streamingText.isNotBlank()
                        ) {
                                NothingAIOptions(
                                        options = textOptions,
                                        isLoading = isGeneratingOptions,
                                        streamingText = streamingText,
                                        onOptionSelected = onOptionSelected,
                                        onFastForward = onFastForward
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Action buttons - horizontal row
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                if (selectedText.isNotBlank()) {
                                        NothingActionButton(
                                                icon = Icons.Outlined.ContentCopy,
                                                label = "copy",
                                                onClick = onCopy,
                                                modifier = Modifier.weight(1f)
                                        )
                                        NothingActionButton(
                                                icon = Icons.Outlined.Search,
                                                label = "search",
                                                onClick = onSearch,
                                                modifier = Modifier.weight(1f)
                                        )
                                        NothingActionButton(
                                                icon = Icons.Outlined.Share,
                                                label = "share",
                                                onClick = onShare,
                                                modifier = Modifier.weight(1f)
                                        )
                                }
                                if (selectedText.isNotBlank() &&
                                                textOptions.isEmpty() &&
                                                !isGeneratingOptions
                                ) {
                                        NothingActionButton(
                                                icon = Icons.Outlined.AutoAwesome,
                                                label = "ai",
                                                onClick = onGenerateOptions,
                                                modifier = Modifier.weight(1f),
                                                isAccent = true
                                        )
                                }
                                if (selectedText.isNotBlank()) {
                                        NothingActionButton(
                                                icon = Icons.Outlined.Close,
                                                label = "clear",
                                                onClick = onClear,
                                                modifier = Modifier.weight(1f)
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun NothingAIOptions(
        options: List<TextOption>,
        isLoading: Boolean,
        streamingText: String,
        onOptionSelected: (TextOption) -> Unit,
        onFastForward: () -> Unit = {}
) {
        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // Header with fast forward button
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Box(modifier = Modifier.size(4.dp).background(NothingRed))
                                Text(
                                        text = "ai suggestions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingGray400,
                                        fontFamily = Ndot57
                                )
                                if (isLoading) {
                                        // Static loading dots (animations removed)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                repeat(3) { _ ->
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(3.dp)
                                                                                .background(
                                                                                        NothingRed,
                                                                                        RoundedCornerShape(50)
                                                                                )
                                                        )
                                                }
                                        }
                                }
                        }

                        // Fast forward button - show when loading
                        if (isLoading) {
                                Surface(
                                        onClick = onFastForward,
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = androidx.compose.ui.graphics.Color.Transparent,
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, NothingRed)
                                ) {
                                        Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                        imageVector = Icons.Default.FastForward,
                                                        contentDescription = "Fast forward with cloud AI",
                                                        tint = NothingRed,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                        }
                                }
                        }
                }

                // Streaming text
                if (isLoading && streamingText.isNotBlank()) {
                        Text(
                                text = streamingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingGray500,
                                modifier = Modifier.padding(start = 12.dp),
                                maxLines = 2
                        )
                }

                // Options
                if (options.isNotEmpty()) {
                        options.chunked(2).forEach { rowOptions ->
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        rowOptions.forEach { option ->
                                                NothingOptionChip(
                                                        option = option,
                                                        onClick = { onOptionSelected(option) },
                                                        modifier = Modifier.weight(1f)
                                                )
                                        }
                                        if (rowOptions.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun NothingOptionChip(
        option: TextOption,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        val iconVector =
                when (option.icon) {
                        TextOption.TextOptionIcon.SEARCH -> Icons.Outlined.Search
                        TextOption.TextOptionIcon.COPY -> Icons.Outlined.ContentCopy
                        TextOption.TextOptionIcon.SHARE -> Icons.Outlined.Share
                        TextOption.TextOptionIcon.PHONE -> Icons.Outlined.Phone
                        TextOption.TextOptionIcon.SMS -> Icons.Outlined.Sms
                        TextOption.TextOptionIcon.EMAIL -> Icons.Outlined.Email
                        TextOption.TextOptionIcon.MAP -> Icons.Outlined.Map
                        TextOption.TextOptionIcon.CALENDAR -> Icons.Outlined.CalendarToday
                        TextOption.TextOptionIcon.WEB -> Icons.Outlined.Language
                        TextOption.TextOptionIcon.TRANSLATE -> Icons.Outlined.Translate
                        @Suppress("DEPRECATION") TextOption.TextOptionIcon.DEFINE ->
                                Icons.Outlined.MenuBook
                        TextOption.TextOptionIcon.INFO -> Icons.Outlined.Info
                }

        Surface(
                onClick = onClick,
                modifier = modifier,
                color = NothingCharcoal,
                shape = RoundedCornerShape(12.dp)
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = NothingGray400,
                                modifier = Modifier.size(16.dp)
                        )
                        Text(
                                text = option.title.lowercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
        }
}

@Composable
private fun NothingActionButton(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        isAccent: Boolean = false
) {
        Surface(
                onClick = onClick,
                modifier = modifier,
                color = if (isAccent) NothingRed.copy(alpha = 0.15f) else NothingCharcoal,
                shape = RoundedCornerShape(12.dp)
        ) {
                Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isAccent) NothingRed else NothingGray400,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAccent) NothingRed else NothingGray500
                        )
                }
        }
}

@Composable
private fun NothingProcessingIndicator(modifier: Modifier = Modifier) {
        Surface(
                modifier = modifier,
                color = NothingBlack.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp)
        ) {
                Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // Static loading dots (animations removed)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(3) { _ ->
                                        Box(
                                                modifier =
                                                        Modifier.size(6.dp)
                                                                .background(
                                                                        NothingRed,
                                                                        RoundedCornerShape(50)
                                                                )
                                        )
                                }
                        }

                        Column {
                                Text(
                                        text = "scanning",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NothingWhite,
                                        fontFamily = Ndot57
                                )
                                Text(
                                        text = "detecting text",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NothingGray500
                                )
                        }
                }
        }
}

@Composable
private fun NothingErrorBanner(error: String, modifier: Modifier = Modifier) {
        Surface(
                modifier = modifier.padding(32.dp),
                color = NothingBlack.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border =
                        androidx.compose.foundation.BorderStroke(
                                1.dp,
                                NothingRed.copy(alpha = 0.5f)
                        )
        ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(4.dp)
                                                .background(NothingRed, RoundedCornerShape(50))
                        )
                        Text(
                                text = error.lowercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingGray400
                        )
                }
        }
}

/** Find text blocks whose center points are inside the drawn path. */
private fun findTextBlocksInPath(
        blocks: List<TextBlock>,
        path: List<Pair<Float, Float>>
): List<Int> {
        if (path.size < 3) return emptyList()

        return blocks.mapIndexedNotNull { index, block ->
                val centerX = block.centerX()
                val centerY = block.centerY()
                if (isPointInPolygon(centerX, centerY, path)) index else null
        }
}

/** Ray casting algorithm to check if a point is inside a polygon. */
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
