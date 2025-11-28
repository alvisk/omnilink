package com.example.omni_link.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.omni_link.ai.ChatMessage
import com.example.omni_link.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: OmniViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val screenState by viewModel.screenState.collectAsState()

    val context = LocalContext.current
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var showPermissionSheet by remember { mutableStateOf(false) }
    var showModelSettings by remember { mutableStateOf(false) }
    var isOverlayEnabled by remember { mutableStateOf(viewModel.isFloatingOverlayEnabled()) }
    var isDebugEnabled by remember { mutableStateOf(viewModel.isDebugOverlayEnabled()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showModelSettings) {
        ModelSettingsScreen(viewModel = viewModel, onBackClick = { showModelSettings = false })
        return
    }

    Scaffold(
            topBar = {
                Column {
                    // Spacer for status bar
                    Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
                    BlockyTopBar(
                            uiState = uiState,
                            isServiceRunning = isServiceRunning,
                            isOverlayEnabled = isOverlayEnabled,
                            isDebugEnabled = isDebugEnabled,
                            onSettingsClick = { showPermissionSheet = true },
                            onModelSettingsClick = { showModelSettings = true },
                            onOverlayToggle = {
                                viewModel.toggleFloatingOverlay()
                                isOverlayEnabled = viewModel.isFloatingOverlayEnabled()
                            },
                            onDebugToggle = {
                                viewModel.toggleDebugOverlay()
                                isDebugEnabled = viewModel.isDebugOverlayEnabled()
                            },
                            onCopyAllClick = {
                                if (messages.isNotEmpty()) {
                                    val allText =
                                            messages.joinToString("\n\n") { msg ->
                                                "[${msg.role.name}]: ${msg.content}"
                                            }
                                    val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                                    ClipboardManager
                                    clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Chat Messages", allText)
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
                            isServiceRunning = isServiceRunning
                    )
                } else {
                    LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message -> BlockyChatBubble(message = message) }

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
    }

    if (showPermissionSheet) {
        BlockyPermissionSheet(
                isServiceRunning = isServiceRunning,
                onDismiss = { showPermissionSheet = false },
                onOpenAccessibility = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onOpenOverlay = {
                    context.startActivity(
                            Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                            )
                    )
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
        isDebugEnabled: Boolean = false,
        onSettingsClick: () -> Unit,
        onModelSettingsClick: () -> Unit = {},
        onOverlayToggle: () -> Unit = {},
        onDebugToggle: () -> Unit = {},
        onCopyAllClick: () -> Unit = {},
        hasMessages: Boolean = false
) {
    Surface(color = OmniBlack, modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Status block
            Box(
                    modifier =
                            Modifier.size(48.dp)
                                    .background(
                                            if (isServiceRunning && uiState.isModelReady) OmniRed
                                            else OmniGrayDark
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = if (isServiceRunning && uiState.isModelReady) "ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniWhite,
                        fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "OMNI",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OmniWhite,
                        letterSpacing = 6.sp
                )
                Text(
                        text =
                                if (isServiceRunning) uiState.statusMessage.uppercase()
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
                                Modifier.border(2.dp, OmniRed, RoundedCornerShape(0.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
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

            // Overlay toggle button
            IconButton(
                    onClick = onOverlayToggle,
                    enabled = isServiceRunning,
                    modifier =
                            Modifier.size(40.dp)
                                    .background(
                                            if (isOverlayEnabled) OmniRed
                                            else if (isServiceRunning) OmniGrayDark
                                            else OmniGrayDark.copy(alpha = 0.5f)
                                    )
            ) {
                Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription =
                                if (isOverlayEnabled) "Disable Overlay" else "Enable Overlay",
                        tint = if (isServiceRunning) OmniWhite else OmniGrayText
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Debug overlay toggle button
            IconButton(
                    onClick = onDebugToggle,
                    enabled = isServiceRunning,
                    modifier =
                            Modifier.size(40.dp)
                                    .background(
                                            if (isDebugEnabled) OmniGreen
                                            else if (isServiceRunning) OmniGrayDark
                                            else OmniGrayDark.copy(alpha = 0.5f)
                                    )
            ) {
                Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription =
                                if (isDebugEnabled) "Disable Debug" else "Enable Debug",
                        tint = if (isServiceRunning) OmniWhite else OmniGrayText
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Copy All button
            IconButton(
                    onClick = onCopyAllClick,
                    enabled = hasMessages,
                    modifier =
                            Modifier.size(40.dp)
                                    .background(if (hasMessages) OmniGrayMid else OmniGrayDark)
            ) {
                Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy All Messages",
                        tint = if (hasMessages) OmniWhite else OmniGrayText
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Model button
            IconButton(
                    onClick = onModelSettingsClick,
                    modifier =
                            Modifier.size(40.dp)
                                    .background(if (uiState.isModelReady) OmniRed else OmniGrayDark)
            ) {
                Icon(
                        imageVector = Icons.Outlined.Memory,
                        contentDescription = "AI Models",
                        tint = OmniWhite
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Settings button
            IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp).background(OmniGrayDark)
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
                    modifier = Modifier.size(32.dp).background(OmniRed),
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
                                .background(if (isUser) OmniGrayDark else OmniBlackSoft)
                                .border(
                                        2.dp,
                                        if (isUser) OmniGrayMid else OmniRed,
                                        RoundedCornerShape(0.dp)
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
                    modifier = Modifier.size(32.dp).background(OmniGrayMid),
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
        // AI block with pulsing
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
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

        Box(
                modifier = Modifier.size(32.dp).background(OmniRed.copy(alpha = alpha)),
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
                        Modifier.background(OmniBlackSoft)
                                .border(2.dp, OmniRed, RoundedCornerShape(0.dp))
        ) {
            Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val dotTransition = rememberInfiniteTransition(label = "dot$index")
                    val dotAlpha by
                            dotTransition.animateFloat(
                                    initialValue = 0.3f,
                                    targetValue = 1f,
                                    animationSpec =
                                            infiniteRepeatable(
                                                    animation =
                                                            tween(400, delayMillis = index * 150),
                                                    repeatMode = RepeatMode.Reverse
                                            ),
                                    label = "dotAlpha$index"
                            )
                    Box(modifier = Modifier.size(8.dp).background(OmniRed.copy(alpha = dotAlpha)))
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
                                        .background(OmniGrayDark)
                                        .border(
                                                2.dp,
                                                if (value.isNotEmpty()) OmniRed else OmniGrayMid
                                        )
                ) {
                    OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                        text = if (isThinking) "PROCESSING..." else "ENTER COMMAND",
                                        color = OmniGrayText,
                                        letterSpacing = 2.sp,
                                        style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            enabled = isEnabled,
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            cursorColor = OmniRed,
                                            focusedTextColor = OmniWhite,
                                            unfocusedTextColor = OmniWhite
                                    ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { onSend() }),
                            maxLines = 4
                    )
                }

                // Send button - blocky
                Box(
                        modifier =
                                Modifier.size(56.dp)
                                        .background(
                                                if (isEnabled && value.isNotBlank()) OmniRed
                                                else OmniGrayDark
                                        )
                                        .then(
                                                if (isEnabled && value.isNotBlank()) {
                                                    Modifier
                                                } else {
                                                    Modifier.border(2.dp, OmniGrayMid)
                                                }
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onSend, enabled = isEnabled && value.isNotBlank()) {
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
fun BlockyEmptyState(isModelReady: Boolean, isServiceRunning: Boolean) {
    Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        // Blocky grid pattern
        Box(
                modifier =
                        Modifier.size(120.dp).drawBehind {
                            val blockSize = 20.dp.toPx()
                            val gap = 4.dp.toPx()
                            val cols = 4
                            val rows = 4
                            val startX = (size.width - (cols * blockSize + (cols - 1) * gap)) / 2
                            val startY = (size.height - (rows * blockSize + (rows - 1) * gap)) / 2

                            for (row in 0 until rows) {
                                for (col in 0 until cols) {
                                    val x = startX + col * (blockSize + gap)
                                    val y = startY + row * (blockSize + gap)
                                    val isCenter = (row == 1 || row == 2) && (col == 1 || col == 2)
                                    drawRect(
                                            color = if (isCenter) OmniRed else OmniGrayDark,
                                            topLeft = Offset(x, y),
                                            size =
                                                    androidx.compose.ui.geometry.Size(
                                                            blockSize,
                                                            blockSize
                                                    )
                                    )
                                }
                            }
                        }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
                text = "OMNI",
                style = MaterialTheme.typography.displaySmall,
                color = OmniWhite,
                letterSpacing = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.background(OmniRed).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                    text = "ON-DEVICE AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = OmniWhite,
                    letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Command suggestions - blocky style
        Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            listOf("OPEN CALCULATOR", "TAP SETTINGS", "SCAN SCREEN", "NAVIGATE APP").forEach {
                    suggestion ->
                Box(
                        modifier =
                                Modifier.border(2.dp, OmniGrayMid)
                                        .background(OmniBlack)
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelMedium,
                            color = OmniGrayText,
                            letterSpacing = 2.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Status blocks
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BlockyStatus(label = "MODEL", isActive = isModelReady)
            BlockyStatus(label = "SERVICE", isActive = isServiceRunning)
            BlockyStatus(label = "PRIVACY", isActive = true, activeColor = OmniGreen)
        }
    }
}

@Composable
fun BlockyStatus(label: String, isActive: Boolean, activeColor: Color = OmniRed) {
    Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(12.dp).background(if (isActive) activeColor else OmniGrayDark))
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
            // Pulsing block
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by
                    infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 1f,
                            animationSpec =
                                    infiniteRepeatable(
                                            animation = tween(300),
                                            repeatMode = RepeatMode.Reverse
                                    ),
                            label = "alpha"
                    )
            Box(modifier = Modifier.size(8.dp).background(OmniWhite.copy(alpha = alpha)))
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
                    modifier = Modifier.size(24.dp).background(OmniBlack),
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
        onDismiss: () -> Unit,
        onOpenAccessibility: () -> Unit,
        onOpenOverlay: () -> Unit
) {
    val context = LocalContext.current
    val hasOverlayPermission = Settings.canDrawOverlays(context)

    ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = OmniBlack,
            shape = RoundedCornerShape(0.dp)
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
                Box(modifier = Modifier.size(8.dp).background(OmniRed))
                Text(
                        text = "SETUP",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OmniWhite,
                        letterSpacing = 6.sp
                )
            }

            Text(
                    text =
                            "OMNI requires special permissions. All processing happens locally on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmniGrayText
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy block
            Box(
                    modifier =
                            Modifier.fillMaxWidth().background(OmniGrayDark).border(2.dp, OmniGreen)
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(OmniGreen))
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
fun BlockyPermissionItem(
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
) {
    Surface(
            onClick = onClick,
            color = OmniGrayDark,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.border(2.dp, if (isGranted) OmniGreen else OmniRed)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Status block
            Box(
                    modifier =
                            Modifier.size(40.dp).background(if (isGranted) OmniGreen else OmniRed),
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
