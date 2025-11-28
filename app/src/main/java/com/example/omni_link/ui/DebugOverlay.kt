package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.debug.DebugLogEntry
import com.example.omni_link.debug.DebugLogManager
import com.example.omni_link.debug.DebugOverlayState
import com.example.omni_link.ui.theme.*
import kotlin.math.roundToInt

// Terminal-style colors for log levels
private val LogColors =
        mapOf(
                DebugLogEntry.LogLevel.INFO to Color(0xFF00BFFF), // Deep sky blue
                DebugLogEntry.LogLevel.PROMPT to Color(0xFFFF00FF), // Magenta
                DebugLogEntry.LogLevel.RESPONSE to Color(0xFF00FF00), // Lime green
                DebugLogEntry.LogLevel.ACTION to Color(0xFFFFD700), // Gold
                DebugLogEntry.LogLevel.SUCCESS to Color(0xFF00FF00), // Green
                DebugLogEntry.LogLevel.ERROR to Color(0xFFFF0000), // Red
                DebugLogEntry.LogLevel.DEBUG to Color(0xFF808080) // Gray
        )

/** Small floating debug button to toggle the debug overlay */
@Composable
fun DebugFloatingButton(
        onClick: () -> Unit,
        hasNewLogs: Boolean = false,
        modifier: Modifier = Modifier
) {
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

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
                Surface(
                        onClick = onClick,
                        modifier = Modifier.size(40.dp),
                        color = OmniBlack.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp),
                        border =
                                androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (hasNewLogs) OmniGreen else OmniGrayMid
                                )
                ) {
                        Box(contentAlignment = Alignment.Center) {
                                Text(
                                        text = "DBG",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (hasNewLogs) OmniGreen else OmniGrayText
                                )
                        }
                }
        }
}

/** Main debug overlay panel showing real-time logs */
@Composable
fun DebugOverlayPanel(
        state: DebugOverlayState,
        onDismiss: () -> Unit,
        onClear: () -> Unit,
        onToggleExpand: () -> Unit,
        onCopyLogs: (String) -> Unit,
        modifier: Modifier = Modifier
) {
        val listState = rememberLazyListState()
        val filteredLogs =
                remember(state.logs, state.filterLevel) {
                        if (state.filterLevel == null) {
                                state.logs
                        } else {
                                state.logs.filter { it.level == state.filterLevel }
                        }
                }

        // Auto-scroll to bottom when new logs arrive
        LaunchedEffect(filteredLogs.size, state.autoScroll) {
                if (state.autoScroll && filteredLogs.isNotEmpty()) {
                        listState.animateScrollToItem(filteredLogs.size - 1)
                }
        }

        AnimatedVisibility(
                visible = state.isVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = modifier
        ) {
                Surface(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .then(
                                                if (state.isExpanded) Modifier.fillMaxHeight(0.7f)
                                                else Modifier.heightIn(max = 250.dp)
                                        ),
                        color = OmniBlack.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
                        shadowElevation = 8.dp
                ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                                // Header
                                DebugPanelHeader(
                                        logCount = filteredLogs.size,
                                        isExpanded = state.isExpanded,
                                        onDismiss = onDismiss,
                                        onClear = onClear,
                                        onToggleExpand = onToggleExpand,
                                        onCopy = {
                                                val logsText =
                                                        filteredLogs.joinToString("\n") { log ->
                                                                "[${log.formattedTime()}] [${log.level}] ${log.tag}: ${log.message}" +
                                                                        (log.details?.let {
                                                                                "\n  $it"
                                                                        }
                                                                                ?: "")
                                                        }
                                                onCopyLogs(logsText)
                                        }
                                )

                                // Green terminal line
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(2.dp)
                                                        .background(OmniGreen)
                                )

                                // Filter chips
                                FilterChipsRow(
                                        currentFilter = state.filterLevel,
                                        onFilterChange = { DebugLogManager.setFilter(it) }
                                )

                                // Logs list
                                if (filteredLogs.isEmpty()) {
                                        EmptyLogsState()
                                } else {
                                        LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxWidth().weight(1f),
                                                contentPadding = PaddingValues(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                                items(filteredLogs, key = { it.id }) { log ->
                                                        LogEntryRow(log)
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun DebugPanelHeader(
        logCount: Int,
        isExpanded: Boolean,
        onDismiss: () -> Unit,
        onClear: () -> Unit,
        onToggleExpand: () -> Unit,
        onCopy: () -> Unit
) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniGrayDark)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                // Terminal icon
                Box(
                        modifier = Modifier.size(24.dp).background(OmniGreen),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                text = ">_",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = OmniBlack
                        )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = "AI DEBUG CONSOLE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = OmniGreen,
                                letterSpacing = 2.sp
                        )
                        Text(
                                text = "$logCount ENTRIES",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OmniGrayText
                        )
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Copy button
                        IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(28.dp).background(OmniGrayMid)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy logs",
                                        tint = OmniGrayText,
                                        modifier = Modifier.size(16.dp)
                                )
                        }

                        // Clear button
                        IconButton(
                                onClick = onClear,
                                modifier = Modifier.size(28.dp).background(OmniGrayMid)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear logs",
                                        tint = OmniGrayText,
                                        modifier = Modifier.size(16.dp)
                                )
                        }

                        // Expand/collapse button
                        IconButton(
                                onClick = onToggleExpand,
                                modifier = Modifier.size(28.dp).background(OmniGrayMid)
                        ) {
                                Icon(
                                        imageVector =
                                                if (isExpanded) Icons.Default.ExpandLess
                                                else Icons.Default.ExpandMore,
                                        contentDescription =
                                                if (isExpanded) "Collapse" else "Expand",
                                        tint = OmniGrayText,
                                        modifier = Modifier.size(16.dp)
                                )
                        }

                        // Close button
                        IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(28.dp).background(OmniRed)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = OmniWhite,
                                        modifier = Modifier.size(16.dp)
                                )
                        }
                }
        }
}

@Composable
private fun FilterChipsRow(
        currentFilter: DebugLogEntry.LogLevel?,
        onFilterChange: (DebugLogEntry.LogLevel?) -> Unit
) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .background(OmniBlackSoft)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                // "All" chip
                FilterChip(
                        label = "ALL",
                        isSelected = currentFilter == null,
                        color = OmniWhite,
                        onClick = { onFilterChange(null) }
                )

                // Level chips
                DebugLogEntry.LogLevel.entries.forEach { level ->
                        FilterChip(
                                label = level.name,
                                isSelected = currentFilter == level,
                                color = LogColors[level] ?: OmniWhite,
                                onClick = { onFilterChange(level) }
                        )
                }
        }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, color: Color, onClick: () -> Unit) {
        Surface(
                onClick = onClick,
                modifier = Modifier.height(24.dp),
                color = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(2.dp),
                border =
                        androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) color else OmniGrayMid
                        )
        ) {
                Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                        Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) color else OmniGrayText
                        )
                }
        }
}

@Composable
private fun LogEntryRow(log: DebugLogEntry) {
        var isExpanded by remember { mutableStateOf(false) }
        val levelColor = LogColors[log.level] ?: OmniWhite

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniGrayDark.copy(alpha = 0.5f))
                                .border(1.dp, OmniGrayMid.copy(alpha = 0.3f))
                                .clickable(enabled = log.details != null) {
                                        isExpanded = !isExpanded
                                }
                                .padding(8.dp)
        ) {
                Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        // Timestamp
                        Text(
                                text = log.formattedTime(),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OmniGrayText
                        )

                        // Level badge
                        Box(
                                modifier =
                                        Modifier.background(levelColor.copy(alpha = 0.2f))
                                                .border(1.dp, levelColor)
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                                Text(
                                        text = log.level.name.take(3),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = levelColor
                                )
                        }

                        // Tag
                        Text(
                                text = "[${log.tag}]",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = OmniYellow
                        )

                        // Message
                        Text(
                                text = log.message,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OmniWhite,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                        )

                        // Expand indicator if details available
                        if (log.details != null) {
                                Icon(
                                        imageVector =
                                                if (isExpanded) Icons.Default.ExpandLess
                                                else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = OmniGrayText,
                                        modifier = Modifier.size(14.dp)
                                )
                        }
                }

                // Details section (expanded)
                AnimatedVisibility(visible = isExpanded && log.details != null) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(1.dp)
                                                        .background(OmniGrayMid)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = log.details ?: "",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OmniGrayText,
                                        lineHeight = 14.sp
                                )
                        }
                }
        }
}

@Composable
private fun EmptyLogsState() {
        Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
        ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                                text = ">_",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = OmniGrayMid
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = "NO LOGS YET",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OmniGrayText,
                                letterSpacing = 2.sp
                        )
                        Text(
                                text = "AI activity will appear here",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OmniGrayLight
                        )
                }
        }
}

/** Complete debug overlay that can be shown from the accessibility service */
@Composable
fun DebugOverlay(
        state: DebugOverlayState,
        onDismiss: () -> Unit,
        onClear: () -> Unit,
        onToggleExpand: () -> Unit,
        onCopyLogs: (String) -> Unit
) {
        Box(modifier = Modifier.fillMaxSize()) {
                DebugOverlayPanel(
                        state = state,
                        onDismiss = onDismiss,
                        onClear = onClear,
                        onToggleExpand = onToggleExpand,
                        onCopyLogs = onCopyLogs,
                        modifier = Modifier.align(Alignment.TopCenter)
                )
        }
}
