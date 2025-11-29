package com.example.omni_link.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.omni_link.ai.ChatMessage
import com.example.omni_link.glyph.GlyphMatrixHelper
import com.example.omni_link.glyph.GlyphType
import com.example.omni_link.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: OmniViewModel = viewModel()) {
        val uiState by viewModel.uiState.collectAsState()
        val messages by viewModel.messages.collectAsState()
        val isServiceRunning by viewModel.isServiceRunning.collectAsState()
        val screenState by viewModel.screenState.collectAsState()
        val currentGlyphType by viewModel.currentGlyphType.collectAsState()

        val context = LocalContext.current
        val listState = rememberLazyListState()

        var inputText by remember { mutableStateOf("") }
        var showPermissionSheet by remember { mutableStateOf(false) }
        var showModelSettings by remember { mutableStateOf(false) }
        var showGlyphSettings by remember { mutableStateOf(false) }
        // Use StateFlow for overlay state to properly sync with service
        val isOverlayEnabled by viewModel.floatingOverlayEnabled.collectAsState()
        var isDebugEnabled by remember { mutableStateOf(viewModel.isDebugOverlayEnabled()) }

        // First launch tooltip state
        val prefs = context.getSharedPreferences("nomm_prefs", Context.MODE_PRIVATE)
        var showWidgetTooltip by remember {
                mutableStateOf(!prefs.getBoolean("widget_tooltip_shown", false))
        }

        LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                }
        }

        // Sync overlay state when service status changes
        LaunchedEffect(isServiceRunning) {
                // Re-check overlay state when service connects/disconnects
                viewModel.isFloatingOverlayEnabled()
        }

        if (showGlyphSettings) {
                GlyphSettingsScreen(
                        currentGlyphType = currentGlyphType,
                        onGlyphSelected = { viewModel.setGlyphType(it) },
                        onBackClick = { showGlyphSettings = false }
                )
                return
        }

        if (showModelSettings) {
                ModelSettingsScreen(
                        viewModel = viewModel,
                        onBackClick = { showModelSettings = false }
                )
                return
        }

        Scaffold(
                topBar = {
                        Column {
                                // Spacer for status bar
                                Spacer(
                                        modifier =
                                                Modifier.windowInsetsPadding(
                                                        WindowInsets.statusBars
                                                )
                                )
                                BlockyTopBar(
                                        uiState = uiState,
                                        isServiceRunning = isServiceRunning,
                                        isOverlayEnabled = isOverlayEnabled,
                                        onSettingsClick = { showPermissionSheet = true },
                                        onModelSettingsClick = { showModelSettings = true },
                                        onOverlayToggle = {
                                                viewModel.toggleFloatingOverlay()
                                                // Dismiss tooltip on first interaction
                                                if (showWidgetTooltip) {
                                                        showWidgetTooltip = false
                                                        prefs.edit().putBoolean("widget_tooltip_shown", true).apply()
                                                }
                                        },
                                        onCopyAllClick = {
                                                if (messages.isNotEmpty()) {
                                                        val allText =
                                                                messages.joinToString("\n\n") { msg
                                                                        ->
                                                                        "[${msg.role.name}]: ${msg.content}"
                                                                }
                                                        val clipboard =
                                                                context.getSystemService(
                                                                        Context.CLIPBOARD_SERVICE
                                                                ) as
                                                                        ClipboardManager
                                                        clipboard.setPrimaryClip(
                                                                ClipData.newPlainText(
                                                                        "Chat Messages",
                                                                        allText
                                                                )
                                                        )
                                                        Toast.makeText(
                                                                        context,
                                                                        "All messages copied!",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        },
                                        hasMessages = messages.isNotEmpty()
                                )
                        }
                },
                containerColor = OmniBlack
        ) { padding ->
                Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                                // Red accent line
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(OmniRed))

                        // Status Bar
                        AnimatedVisibility(
                                visible = uiState.currentAction != null,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                        ) { BlockyStatusBar(action = uiState.currentAction ?: "") }

                        // Permission Warning
                        if (!isServiceRunning) {
                                BlockyWarningBanner(onClick = { showPermissionSheet = true })
                        }

                        // Chat Messages
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (messages.isEmpty()) {
                                        BlockyEmptyState(
                                                isModelReady = uiState.isModelReady,
                                                isServiceRunning = isServiceRunning,
                                                currentGlyphType = currentGlyphType
                                        )
                                } else {
                                        LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                                items(messages, key = { it.id }) { message ->
                                                        BlockyChatBubble(message = message)
                                                }

                                                if (uiState.isThinking) {
                                                        item { BlockyThinkingIndicator() }
                                                }
                                        }
                                }
                        }

                                // Input Area
                                BlockyInputBar(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        onSend = {
                                                if (inputText.isNotBlank()) {
                                                        viewModel.sendMessage(inputText)
                                                        inputText = ""
                                                }
                                        },
                                        isEnabled = uiState.isModelReady && !uiState.isThinking,
                                        isThinking = uiState.isThinking
                                )
                        }

                        // First launch widget tooltip
                        if (showWidgetTooltip) {
                                WidgetTooltipOverlay(
                                        onDismiss = {
                                                showWidgetTooltip = false
                                                prefs.edit().putBoolean("widget_tooltip_shown", true).apply()
                                        }
                                )
                        }
                }
        }

        if (showPermissionSheet) {
                BlockyPermissionSheet(
                        isServiceRunning = isServiceRunning,
                        currentGlyphName = currentGlyphType.displayName,
                        isDebugEnabled = isDebugEnabled,
                        onDismiss = { showPermissionSheet = false },
                        onOpenAccessibility = {
                                context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                )
                        },
                        onOpenOverlay = {
                                context.startActivity(
                                        Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                android.net.Uri.parse(
                                                        "package:${context.packageName}"
                                                )
                                        )
                                )
                        },
                        onOpenGlyphSettings = {
                                showPermissionSheet = false
                                showGlyphSettings = true
                        },
                        onDebugToggle = {
                                viewModel.toggleDebugOverlay()
                                isDebugEnabled = viewModel.isDebugOverlayEnabled()
                        }
                )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockyTopBar(
        uiState: OmniUiState,
        isServiceRunning: Boolean,
        isOverlayEnabled: Boolean = false,
        onSettingsClick: () -> Unit,
        onModelSettingsClick: () -> Unit = {},
        onOverlayToggle: () -> Unit = {},
        onCopyAllClick: () -> Unit = {},
        hasMessages: Boolean = false
) {
        Surface(color = OmniBlack, modifier = Modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Mobius logo
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .background(
                                                        if (isServiceRunning && uiState.isModelReady
                                                        )
                                                                OmniRed
                                                        else OmniGrayDark,
                                                        RoundedCornerShape(12.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                MobiusLogo(
                                        modifier = Modifier.size(32.dp),
                                        primaryColor = OmniWhite,
                                        secondaryColor =
                                                if (isServiceRunning && uiState.isModelReady)
                                                        OmniRed
                                                else OmniGrayDark
                                )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = "NOMM.",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = OmniWhite,
                                        letterSpacing = 6.sp
                                )
                                Text(
                                        text =
                                                if (isServiceRunning)
                                                        uiState.statusMessage.uppercase()
                                                else "SERVICE OFFLINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isServiceRunning) OmniGrayText else OmniRed,
                                        letterSpacing = 2.sp
                                )
                        }

                        // Inference time
                        if (uiState.lastInferenceTimeMs > 0) {
                                Box(
                                        modifier =
                                                Modifier.border(
                                                                2.dp,
                                                                OmniRed,
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 10.dp,
                                                                vertical = 4.dp
                                                        )
                                ) {
                                        Text(
                                                text = "${uiState.lastInferenceTimeMs}MS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniRed,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Cohesive pill button group
                        Row(
                                modifier =
                                        Modifier.background(OmniGrayDark, RoundedCornerShape(12.dp))
                        ) {
                                // Overlay toggle button (left edge - rounded left)
                                IconButton(
                                        onClick = onOverlayToggle,
                                        enabled = isServiceRunning,
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                if (isOverlayEnabled) OmniRed
                                                                else if (isServiceRunning)
                                                                        OmniGrayDark
                                                                else
                                                                        OmniGrayDark.copy(
                                                                                alpha = 0.5f
                                                                        ),
                                                                RoundedCornerShape(
                                                                        topStart = 12.dp,
                                                                        bottomStart = 12.dp,
                                                                        topEnd = 0.dp,
                                                                        bottomEnd = 0.dp
                                                                )
                                                        )
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.Layers,
                                                contentDescription =
                                                        if (isOverlayEnabled) "Disable Overlay"
                                                        else "Enable Overlay",
                                                tint =
                                                        if (isServiceRunning) OmniWhite
                                                        else OmniGrayText
                                        )
                                }

                                // Divider
                                Box(
                                        modifier =
                                                Modifier.width(1.dp)
                                                        .height(40.dp)
                                                        .background(OmniBlack.copy(alpha = 0.3f))
                                )

                                // Copy All button (middle - no rounded corners)
                                IconButton(
                                        onClick = onCopyAllClick,
                                        enabled = hasMessages,
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                if (hasMessages) OmniGrayMid
                                                                else OmniGrayDark,
                                                                RoundedCornerShape(0.dp)
                                                        )
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = "Copy All Messages",
                                                tint = if (hasMessages) OmniWhite else OmniGrayText
                                        )
                                }

                                // Divider
                                Box(
                                        modifier =
                                                Modifier.width(1.dp)
                                                        .height(40.dp)
                                                        .background(OmniBlack.copy(alpha = 0.3f))
                                )

                                // Model button (middle - no rounded corners)
                                IconButton(
                                        onClick = onModelSettingsClick,
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                if (uiState.isModelReady) OmniRed
                                                                else OmniGrayDark,
                                                                RoundedCornerShape(0.dp)
                                                        )
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.Memory,
                                                contentDescription = "AI Models",
                                                tint = OmniWhite
                                        )
                                }

                                // Divider
                                Box(
                                        modifier =
                                                Modifier.width(1.dp)
                                                        .height(40.dp)
                                                        .background(OmniBlack.copy(alpha = 0.3f))
                                )

                                // Settings button (right edge - rounded right)
                                IconButton(
                                        onClick = onSettingsClick,
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                OmniGrayDark,
                                                                RoundedCornerShape(
                                                                        topStart = 0.dp,
                                                                        bottomStart = 0.dp,
                                                                        topEnd = 12.dp,
                                                                        bottomEnd = 12.dp
                                                                )
                                                        )
                                ) {
                                        Icon(
                                                imageVector = Icons.Outlined.Settings,
                                                contentDescription = "Settings",
                                                tint = OmniWhite
                                        )
                                }
                        }
                }
        }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BlockyChatBubble(message: ChatMessage) {
        val isUser = message.role == ChatMessage.Role.USER
        val context = LocalContext.current

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
                if (!isUser) {
                        // AI indicator block
                        Box(
                                modifier =
                                        Modifier.size(32.dp)
                                                .background(OmniRed, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OmniWhite,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                }

                // Message block - long press to copy
                Box(
                        modifier =
                                Modifier.widthIn(max = 280.dp)
                                        .background(
                                                if (isUser) OmniGrayDark else OmniBlackSoft,
                                                RoundedCornerShape(16.dp)
                                        )
                                        .border(
                                                2.dp,
                                                if (isUser) OmniGrayMid else OmniRed,
                                                RoundedCornerShape(16.dp)
                                        )
                                        .combinedClickable(
                                                onClick = {},
                                                onLongClick = {
                                                        val clipboard =
                                                                context.getSystemService(
                                                                        Context.CLIPBOARD_SERVICE
                                                                ) as
                                                                        ClipboardManager
                                                        clipboard.setPrimaryClip(
                                                                ClipData.newPlainText(
                                                                        "Message",
                                                                        message.content
                                                                )
                                                        )
                                                        Toast.makeText(
                                                                        context,
                                                                        "Message copied!",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        )
                ) {
                        Text(
                                text = message.content,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OmniWhite
                        )
                }

                if (isUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // User indicator block
                        Box(
                                modifier =
                                        Modifier.size(32.dp)
                                                .background(OmniGrayMid, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = "U",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OmniWhite,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                }
        }
}

@Composable
fun BlockyThinkingIndicator() {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
        ) {
        // AI block (static)
                val alpha = 1f

                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .background(
                                                OmniRed.copy(alpha = alpha),
                                                RoundedCornerShape(8.dp)
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                text = "AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniWhite,
                                fontWeight = FontWeight.Bold
                        )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Thinking block
                Box(
                        modifier =
                                Modifier.background(OmniBlackSoft, RoundedCornerShape(16.dp))
                                        .border(2.dp, OmniRed, RoundedCornerShape(16.dp))
                ) {
                        Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                        repeat(3) { index ->
                                        Box(
                                                modifier =
                                                        Modifier.size(8.dp)
                                                                .background(
                                                                        OmniRed,
                                                                        RoundedCornerShape(50)
                                                                )
                                        )
                                }
                        }
                }
        }
}

@Composable
fun BlockyInputBar(
        value: String,
        onValueChange: (String) -> Unit,
        onSend: () -> Unit,
        isEnabled: Boolean,
        isThinking: Boolean
) {
        Column {
                // Top border
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(OmniRed))

                Surface(color = OmniBlack, modifier = Modifier.fillMaxWidth()) {
                        Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                // Input field
                                Box(
                                        modifier =
                                                Modifier.weight(1f)
                                                        .background(
                                                                OmniGrayDark,
                                                                RoundedCornerShape(28.dp)
                                                        )
                                                        .border(
                                                                2.dp,
                                                                if (value.isNotEmpty()) OmniRed
                                                                else OmniGrayMid,
                                                                RoundedCornerShape(28.dp)
                                                        )
                                ) {
                                        OutlinedTextField(
                                                value = value,
                                                onValueChange = onValueChange,
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = {
                                                        Text(
                                                                text =
                                                                        if (isThinking)
                                                                                "PROCESSING..."
                                                                        else "ENTER COMMAND",
                                                                color = OmniGrayText,
                                                                letterSpacing = 2.sp,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyMedium
                                                        )
                                                },
                                                enabled = isEnabled,
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedBorderColor =
                                                                        Color.Transparent,
                                                                unfocusedBorderColor =
                                                                        Color.Transparent,
                                                                focusedContainerColor =
                                                                        Color.Transparent,
                                                                unfocusedContainerColor =
                                                                        Color.Transparent,
                                                                cursorColor = OmniRed,
                                                                focusedTextColor = OmniWhite,
                                                                unfocusedTextColor = OmniWhite
                                                        ),
                                                keyboardOptions =
                                                        KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions =
                                                        KeyboardActions(onSend = { onSend() }),
                                                maxLines = 4
                                        )
                                }

                                // Send button - rounded
                                Box(
                                        modifier =
                                                Modifier.size(56.dp)
                                                        .background(
                                                                if (isEnabled && value.isNotBlank())
                                                                        OmniRed
                                                                else OmniGrayDark,
                                                                RoundedCornerShape(50)
                                                        )
                                                        .then(
                                                                if (isEnabled && value.isNotBlank()
                                                                ) {
                                                                        Modifier
                                                                } else {
                                                                        Modifier.border(
                                                                                2.dp,
                                                                                OmniGrayMid,
                                                                                RoundedCornerShape(
                                                                                        50
                                                                                )
                                                                        )
                                                                }
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        IconButton(
                                                onClick = onSend,
                                                enabled = isEnabled && value.isNotBlank()
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.ArrowUpward,
                                                        contentDescription = "Send",
                                                        tint = OmniWhite,
                                                        modifier = Modifier.size(28.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun BlockyEmptyState(
        isModelReady: Boolean,
        isServiceRunning: Boolean,
        currentGlyphType: GlyphType = GlyphType.MOBIUS_FIGURE_8
) {
        // Animation frame counter for the glyph
        val infiniteTransition = rememberInfiniteTransition(label = "glyph_animation")
        val frame by
                infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 240f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation =
                                                tween(durationMillis = 9600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                ),
                        label = "frame"
                )

        // Entrance animations
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }

        val titleAlpha by
                animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = tween(800, delayMillis = 200, easing = EaseOutCubic),
                        label = "titleAlpha"
                )
        val subtitleAlpha by
                animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = tween(800, delayMillis = 400, easing = EaseOutCubic),
                        label = "subtitleAlpha"
                )
        val statusAlpha by
                animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0f,
                        animationSpec = tween(800, delayMillis = 600, easing = EaseOutCubic),
                        label = "statusAlpha"
                )
        val glyphScale by
                animateFloatAsState(
                        targetValue = if (isVisible) 1f else 0.5f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
                        label = "glyphScale"
                )

        // Generate bitmap for current frame
        val bitmap =
                remember(currentGlyphType, frame.toInt()) {
                        GlyphMatrixHelper.generatePreviewBitmap(currentGlyphType, frame.toInt())
                }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        // Hero Glyph Container - clean, minimal
                        Box(
                                modifier =
                                        Modifier.graphicsLayer(
                                                scaleX = glyphScale,
                                                scaleY = glyphScale
                                        ),
                                contentAlignment = Alignment.Center
                        ) {
                                // Main glyph container
                                Box(
                                        modifier =
                                                Modifier.size(180.dp)
                                                        .background(
                                                                Brush.radialGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        OmniCharcoal,
                                                                                        OmniBlackSoft
                                                                                )
                                                                ),
                                                                RoundedCornerShape(24.dp)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                Brush.verticalGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        OmniGrayMid,
                                                                                        OmniGrayDark
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                )
                                                                ),
                                                                RoundedCornerShape(24.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        // LED matrix simulation - larger and more prominent
                                        GlyphMatrixCanvas(
                                                bitmap = bitmap,
                                                modifier = Modifier.size(150.dp)
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // NOMM title - dramatic typography
                        Text(
                                text = "NOMM.",
                                style = MaterialTheme.typography.displayMedium,
                                color = OmniWhite.copy(alpha = titleAlpha),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 20.sp,
                                modifier = Modifier.alpha(titleAlpha)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tagline with animated reveal
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.alpha(subtitleAlpha)
                        ) {
                                Text(
                                        text = "NOTHING ON MY MIND",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = OmniRed,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Animated accent line
                                val lineWidth by
                                        animateFloatAsState(
                                                targetValue = if (isVisible) 1f else 0f,
                                                animationSpec =
                                                        tween(
                                                                1000,
                                                                delayMillis = 600,
                                                                easing = EaseOutCubic
                                                        ),
                                                label = "lineWidth"
                                        )
                                Box(
                                        modifier =
                                                Modifier.width((200 * lineWidth).dp)
                                                        .height(2.dp)
                                                        .background(
                                                                Brush.horizontalGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        Color.Transparent,
                                                                                        OmniRed,
                                                                                        Color.Transparent
                                                                                )
                                                                ),
                                                                RoundedCornerShape(1.dp)
                                                        )
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                        text = "Your private, on-device AI assistant",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = OmniGrayText,
                                        letterSpacing = 0.5.sp
                                )
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // Status indicators with enhanced design
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                modifier = Modifier.alpha(statusAlpha)
                        ) {
                                EnhancedStatusIndicator(
                                        label = "MODEL",
                                        isActive = isModelReady,
                                        activeColor = OmniRed
                                )
                                EnhancedStatusIndicator(
                                        label = "SERVICE",
                                        isActive = isServiceRunning,
                                        activeColor = OmniRed
                                )
                                EnhancedStatusIndicator(
                                        label = "PRIVATE",
                                        isActive = true,
                                        activeColor = NothingWhite // Monochrome - Nothing design
                                )
                        }
                }
        }
}

/** Status indicator - clean, static Nothing design */
@Composable
private fun EnhancedStatusIndicator(
        label: String,
        isActive: Boolean,
        activeColor: Color = OmniRed
) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // Static indicator dot
                Box(
                        modifier =
                                Modifier.size(12.dp)
                                        .background(
                                                if (isActive) activeColor else OmniGrayDark,
                                                RoundedCornerShape(50)
                                        )
                                        .then(
                                                if (!isActive)
                                                        Modifier.border(
                                                                1.dp,
                                                                OmniGrayMid,
                                                                RoundedCornerShape(50)
                                                        )
                                                else Modifier
                                        )
                )
                Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) NothingGray300 else OmniGrayText,
                        letterSpacing = 2.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
        }
}

// Charcoal color alias for compatibility
private val OmniCharcoal = NothingCharcoal

/**
 * Canvas that renders the LED matrix simulation from a bitmap. Simulates the actual 25x25 LED grid
 * appearance.
 */
@Composable
private fun GlyphMatrixCanvas(bitmap: Bitmap, modifier: Modifier = Modifier) {
        Canvas(modifier = modifier) {
                val matrixSize = GlyphMatrixHelper.MATRIX_SIZE
                val cellSize = size.minDimension / matrixSize
                val ledRadius = cellSize * 0.35f
                val startX = (size.width - matrixSize * cellSize) / 2
                val startY = (size.height - matrixSize * cellSize) / 2

                // Draw background grid (very dim)
                for (y in 0 until matrixSize) {
                        for (x in 0 until matrixSize) {
                                val cx = startX + x * cellSize + cellSize / 2
                                val cy = startY + y * cellSize + cellSize / 2

                                // Dim background dot to show grid
                                drawCircle(
                                        color = Color(0xFF1A1A1A),
                                        radius = ledRadius,
                                        center = Offset(cx, cy)
                                )
                        }
                }

                // Draw lit LEDs from bitmap
                for (y in 0 until matrixSize) {
                        for (x in 0 until matrixSize) {
                                val pixel = bitmap.getPixel(x, y)
                                val brightness = (pixel shr 16) and 0xFF // Red channel (grayscale)

                                if (brightness > 10) {
                                        val cx = startX + x * cellSize + cellSize / 2
                                        val cy = startY + y * cellSize + cellSize / 2

                                        // Glow effect for bright pixels
                                        if (brightness > 150) {
                                                drawCircle(
                                                        color =
                                                                Color.White.copy(
                                                                        alpha =
                                                                                brightness / 255f *
                                                                                        0.3f
                                                                ),
                                                        radius = ledRadius * 1.8f,
                                                        center = Offset(cx, cy)
                                                )
                                        }

                                        // Main LED
                                        val ledColor =
                                                Color(
                                                        red = brightness / 255f,
                                                        green = brightness / 255f,
                                                        blue = brightness / 255f,
                                                        alpha = 1f
                                                )
                                        drawCircle(
                                                color = ledColor,
                                                radius = ledRadius,
                                                center = Offset(cx, cy)
                                        )
                                }
                        }
                }
        }
}

@Composable
fun BlockyStatus(label: String, isActive: Boolean, activeColor: Color = OmniRed) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
                Box(
                        modifier =
                                Modifier.size(12.dp)
                                        .background(
                                                if (isActive) activeColor else OmniGrayDark,
                                                RoundedCornerShape(50)
                                        )
                )
                Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniGrayText,
                        letterSpacing = 1.sp
                )
        }
}

@Composable
fun BlockyStatusBar(action: String) {
        Box(modifier = Modifier.fillMaxWidth().background(OmniRedDark)) {
                Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        // Status indicator (static)
                        Box(
                                modifier =
                                        Modifier.size(8.dp)
                                                .background(
                                                        OmniWhite,
                                                        RoundedCornerShape(50)
                                                )
                        )
                        Text(
                                text = action.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniWhite,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                        )
                }
        }
}

@Composable
fun BlockyWarningBanner(onClick: () -> Unit) {
        Surface(onClick = onClick, color = OmniRed, modifier = Modifier.fillMaxWidth()) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(24.dp)
                                                .background(OmniBlack, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = "!",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OmniRed,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        Text(
                                text = "ACCESSIBILITY SERVICE REQUIRED",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniWhite,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                        )
                        Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = OmniWhite,
                                modifier = Modifier.size(20.dp)
                        )
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockyPermissionSheet(
        isServiceRunning: Boolean,
        currentGlyphName: String = "",
        isDebugEnabled: Boolean = true,
        onDismiss: () -> Unit,
        onOpenAccessibility: () -> Unit,
        onOpenOverlay: () -> Unit,
        onOpenGlyphSettings: () -> Unit = {},
        onDebugToggle: () -> Unit = {}
) {
        val context = LocalContext.current
        val hasOverlayPermission = Settings.canDrawOverlays(context)

        ModalBottomSheet(
                onDismissRequest = onDismiss,
                containerColor = OmniBlack,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // Header with red accent
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Box(
                                        modifier =
                                                Modifier.size(8.dp)
                                                        .background(OmniRed, RoundedCornerShape(50))
                                )
                                Text(
                                        text = "SETTINGS",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = OmniWhite,
                                        letterSpacing = 6.sp
                                )
                        }

                        // Permissions section header
                        Text(
                                text = "PERMISSIONS",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniGrayText,
                                letterSpacing = 2.sp
                        )

                        // Permissions
                        BlockyPermissionItem(
                                title = "ACCESSIBILITY",
                                description = "Read screen content",
                                isGranted = isServiceRunning,
                                onClick = onOpenAccessibility
                        )

                        BlockyPermissionItem(
                                title = "OVERLAY",
                                description = "Floating button access",
                                isGranted = hasOverlayPermission,
                                onClick = onOpenOverlay
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Device section header
                        Text(
                                text = "DEVICE",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniGrayText,
                                letterSpacing = 2.sp
                        )

                        // Glyph settings entry
                        BlockySettingsItem(
                                title = "GLYPH ANIMATION",
                                description =
                                        if (currentGlyphName.isNotEmpty()) currentGlyphName
                                        else "LED matrix display",
                                icon = Icons.Outlined.GridView,
                                onClick = onOpenGlyphSettings
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Developer section header
                        Text(
                                text = "DEVELOPER",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniGrayText,
                                letterSpacing = 2.sp
                        )

                        // Debug toggle
                        BlockyToggleItem(
                                title = "DEBUG OVERLAY",
                                description = "Show AI logs and prompts",
                                icon = Icons.Outlined.BugReport,
                                isEnabled = isDebugEnabled,
                                onToggle = onDebugToggle
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Privacy block
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(OmniGrayDark, RoundedCornerShape(12.dp))
                                                .border(2.dp, OmniGreen, RoundedCornerShape(12.dp))
                        ) {
                                Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(8.dp)
                                                                .background(
                                                                        OmniGreen,
                                                                        RoundedCornerShape(50)
                                                                )
                                        )
                                        Text(
                                                text = "100% PRIVATE - LOCAL PROCESSING ONLY",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OmniGreen,
                                                letterSpacing = 1.sp
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                }
        }
}

@Composable
fun BlockySettingsItem(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onClick: () -> Unit
) {
        Surface(
                onClick = onClick,
                color = OmniGrayDark,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(2.dp, OmniGrayMid, RoundedCornerShape(12.dp))
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Icon block
                        Box(
                                modifier =
                                        Modifier.size(40.dp)
                                                .background(
                                                        OmniBlackSoft,
                                                        RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                        1.dp,
                                                        OmniGrayMid,
                                                        RoundedCornerShape(10.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = OmniWhite,
                                        modifier = Modifier.size(20.dp)
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = OmniWhite,
                                        letterSpacing = 2.sp
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OmniGrayText
                                )
                        }

                        Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = OmniGrayText,
                                modifier = Modifier.size(24.dp)
                        )
                }
        }
}

@Composable
fun BlockyPermissionItem(
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
) {
        Surface(
                onClick = onClick,
                color = OmniGrayDark,
                shape = RoundedCornerShape(12.dp),
                modifier =
                        Modifier.border(
                                2.dp,
                                if (isGranted) OmniGreen else OmniRed,
                                RoundedCornerShape(12.dp)
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Status block
                        Box(
                                modifier =
                                        Modifier.size(40.dp)
                                                .background(
                                                        if (isGranted) OmniGreen else OmniRed,
                                                        RoundedCornerShape(10.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = if (isGranted) "OK" else "!",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OmniBlack,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = OmniWhite,
                                        letterSpacing = 2.sp
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OmniGrayText
                                )
                        }

                        if (!isGranted) {
                                Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = OmniGrayText,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                }
        }
}

@Composable
fun BlockyToggleItem(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isEnabled: Boolean,
        onToggle: () -> Unit
) {
        Surface(
                onClick = onToggle,
                color = OmniGrayDark,
                shape = RoundedCornerShape(12.dp),
                modifier =
                        Modifier.border(
                                2.dp,
                                if (isEnabled) OmniGreen else OmniGrayMid,
                                RoundedCornerShape(12.dp)
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Icon block
                        Box(
                                modifier =
                                        Modifier.size(40.dp)
                                                .background(
                                                        if (isEnabled) OmniGreen else OmniBlackSoft,
                                                        RoundedCornerShape(10.dp)
                                                )
                                                .border(
                                                        1.dp,
                                                        if (isEnabled) OmniGreen else OmniGrayMid,
                                                        RoundedCornerShape(10.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isEnabled) OmniBlack else OmniWhite,
                                        modifier = Modifier.size(20.dp)
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = OmniWhite,
                                        letterSpacing = 2.sp
                                )
                                Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OmniGrayText
                                )
                        }

                        // Toggle status
                        Box(
                                modifier =
                                        Modifier.background(
                                                        if (isEnabled) OmniGreen else OmniGrayMid,
                                                        RoundedCornerShape(16.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                                Text(
                                        text = if (isEnabled) "ON" else "OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isEnabled) OmniBlack else OmniWhite,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                )
                        }
                }
        }
}

/** Möbius strip / infinity logo - 3D twisted ribbon effect */
@Composable
private fun MobiusLogo(
        modifier: Modifier = Modifier,
        alpha: Float = 1f,
        primaryColor: Color = OmniRed,
        secondaryColor: Color = OmniWhite
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
                                quadraticTo(22f * scaleX, 14f * scaleY, 23f * scaleX, 17f * scaleY)
                                lineTo(23f * scaleX, 22f * scaleY)
                                quadraticTo(22f * scaleX, 18f * scaleY, 18f * scaleX, 18f * scaleY)
                                quadraticTo(12f * scaleX, 18f * scaleY, 12f * scaleX, 24f * scaleY)
                                lineTo(8f * scaleX, 24f * scaleY)
                                close()
                        }
                drawPath(highlightPath, color = secondaryColor.copy(alpha = 0.3f))
        }
}

/** First-launch tooltip overlay pointing to widget button */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetTooltipOverlay(onDismiss: () -> Unit) {
        // Animate entrance
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { isVisible = true }

        val alpha by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = tween(300),
                label = "tooltipAlpha"
        )

        // Bouncing arrow animation
        val infiniteTransition = rememberInfiniteTransition(label = "arrow_bounce")
        val arrowOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 8f,
                animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                ),
                label = "arrowBounce"
        )

        Box(
                modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .combinedClickable(
                                onClick = onDismiss,
                                onLongClick = onDismiss
                        )
        ) {
                // Tooltip positioned near top-right where widget button is
                Column(
                        modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 100.dp, end = 16.dp),
                        horizontalAlignment = Alignment.End
                ) {
                        // Arrow pointing up
                        Canvas(
                                modifier = Modifier
                                        .size(40.dp)
                                        .offset(x = 60.dp, y = (-arrowOffset).dp)
                        ) {
                                val path = Path().apply {
                                        moveTo(size.width / 2, 0f)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, size.height)
                                        close()
                                }
                                drawPath(path, color = Color(0xFFD71921))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tooltip card
                        Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = NothingCharcoal,
                                shadowElevation = 8.dp,
                                modifier = Modifier.widthIn(max = 240.dp)
                        ) {
                                Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        Text(
                                                text = "ENABLE WIDGET",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = NothingRed,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text = "Tap here to enable the floating overlay widget for quick access anywhere",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NothingGray400,
                                                textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                                text = "TAP ANYWHERE TO DISMISS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NothingGray600,
                                                letterSpacing = 1.sp
                                        )
                                }
                        }
                }
        }
}
