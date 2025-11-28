package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.ai.ModelManager
import com.example.omni_link.ui.theme.*

/** Model settings with blocky black & red theme */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(viewModel: OmniViewModel, onBackClick: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val cactusModels by viewModel.cactusModels.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    // Count active downloads for the header
    val activeDownloadCount = activeDownloads.count { it.value.isDownloading }

    Scaffold(
            topBar = {
                Surface(color = OmniBlack) {
                    Column {
                        // Spacer for status bar
                        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.size(40.dp).background(OmniGrayDark)
                            ) {
                                Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = OmniWhite
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(OmniRed))
                                Text(
                                        text = "AI MODELS",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = OmniWhite,
                                        letterSpacing = 4.sp
                                )
                                // Show active download count badge
                                if (activeDownloadCount > 0) {
                                    Box(
                                            modifier =
                                                    Modifier.background(OmniRed)
                                                            .padding(
                                                                    horizontal = 8.dp,
                                                                    vertical = 4.dp
                                                            )
                                    ) {
                                        Text(
                                                text = "$activeDownloadCount↓",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniWhite,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        // Red accent line
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(OmniRed))
                    }
                }
            },
            containerColor = OmniBlack
    ) { padding ->
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Status
            item {
                BlockyModelStatus(
                        currentModel = uiState.currentModel,
                        isReady = uiState.isModelReady,
                        statusMessage = uiState.statusMessage
                )
            }

            // Storage Info
            item { BlockyStorageInfo(viewModel = viewModel) }

            // Active Downloads Summary (when multiple downloads)
            if (activeDownloadCount > 0) {
                item {
                    BlockyDownloadQueue(
                            activeDownloads = activeDownloads,
                            cactusModels = cactusModels
                    )
                }
            }

            // Section Header
            item {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(OmniRed))
                    Text(
                            text = "AVAILABLE MODELS",
                            style = MaterialTheme.typography.labelMedium,
                            color = OmniGrayText,
                            letterSpacing = 2.sp
                    )
                }
            }

            // Cactus Models
            items(cactusModels, key = { it.slug }) { model ->
                val modelId = model.slug
                val isDownloaded = model.isDownloaded
                val isCurrentModel = uiState.currentModel == modelId

                // Get per-model download state
                val modelDownloadState = activeDownloads[modelId]
                val isDownloading = modelDownloadState?.isDownloading == true
                val downloadError = modelDownloadState?.error

                BlockyModelCard(
                        name = model.name,
                        sizeMb = model.sizeMb,
                        description = model.description,
                        isDownloaded = isDownloaded,
                        isCurrentModel = isCurrentModel,
                        isDownloading = isDownloading,
                        downloadProgress = modelDownloadState?.progress ?: 0f,
                        bytesDownloaded = modelDownloadState?.bytesDownloaded ?: 0L,
                        totalBytes = modelDownloadState?.totalBytes ?: 0L,
                        isLocal = model.isLocal,
                        downloadError = downloadError,
                        onDownloadClick = {
                            // Start download - autoLoad only if no model is ready
                            viewModel.downloadCactusModel(modelId, autoLoad = !uiState.isModelReady)
                        },
                        onSelectClick = { viewModel.selectCactusModel(modelId) },
                        onErrorDismiss = { viewModel.clearModelDownloadState(modelId) }
                )
            }

            // Legacy Models
            if (ModelManager.AVAILABLE_MODELS.isNotEmpty()) {
                item {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(OmniGrayMid))
                        Text(
                                text = "LEGACY MODELS",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniGrayText,
                                letterSpacing = 2.sp
                        )
                    }
                }

                items(ModelManager.AVAILABLE_MODELS.entries.toList()) { (modelId, modelInfo) ->
                    val isDownloaded = viewModel.modelManager.isModelDownloaded(modelId)
                    val isCurrentModel = uiState.currentModel == modelId
                    // Legacy models use the global download state
                    val isDownloading = downloadState is ModelManager.DownloadState.Downloading

                    BlockyLegacyModelCard(
                            modelInfo = modelInfo,
                            isDownloaded = isDownloaded,
                            isCurrentModel = isCurrentModel,
                            isDownloading = isDownloading,
                            downloadProgress =
                                    if (isDownloading) {
                                        (downloadState as? ModelManager.DownloadState.Downloading)
                                                ?.progress
                                                ?: 0f
                                    } else 0f,
                            onDownloadClick = { viewModel.downloadModel(modelId) },
                            onDeleteClick = { showDeleteDialog = modelId },
                            onSelectClick = {
                                viewModel.modelManager.getModelPath(modelId)?.let {
                                    viewModel.downloadModel(modelId)
                                }
                            }
                    )
                }
            }

            // Info Footer
            item { BlockyInfoFooter() }
        }
    }

    // Delete Dialog
    showDeleteDialog?.let { modelId ->
        AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                shape = RoundedCornerShape(0.dp),
                containerColor = OmniGrayDark,
                icon = {
                    Box(
                            modifier = Modifier.size(32.dp).background(OmniRed),
                            contentAlignment = Alignment.Center
                    ) {
                        Text(
                                text = "!",
                                style = MaterialTheme.typography.titleMedium,
                                color = OmniWhite,
                                fontWeight = FontWeight.Bold
                        )
                    }
                },
                title = {
                    Text(
                            text = "DELETE MODEL",
                            style = MaterialTheme.typography.titleMedium,
                            color = OmniWhite,
                            letterSpacing = 2.sp
                    )
                },
                text = {
                    Text(
                            text = "Remove downloaded model file? You can re-download anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmniGrayText
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                viewModel.modelManager.deleteModel(modelId)
                                showDeleteDialog = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = OmniRed)
                    ) { Text("DELETE", letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(
                            onClick = { showDeleteDialog = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = OmniGrayText)
                    ) { Text("CANCEL", letterSpacing = 2.sp) }
                }
        )
    }
}

@Composable
fun BlockyModelStatus(currentModel: String?, isReady: Boolean, statusMessage: String) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(if (isReady) OmniRedDark else OmniGrayDark)
                            .border(2.dp, if (isReady) OmniRed else OmniGrayMid)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status block
            Box(
                    modifier =
                            Modifier.size(56.dp).background(if (isReady) OmniRed else OmniGrayMid),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = if (isReady) "ON" else "OFF",
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniWhite,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = if (isReady) "AI READY" else "NO MODEL",
                        style = MaterialTheme.typography.titleMedium,
                        color = OmniWhite,
                        letterSpacing = 2.sp
                )
                Text(
                        text = (currentModel ?: statusMessage).uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isReady) OmniWhite.copy(alpha = 0.7f) else OmniGrayText
                )
            }

            if (isReady) {
                Box(
                        modifier = Modifier.size(32.dp).background(OmniGreen),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = OmniBlack,
                            modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BlockyStorageInfo(viewModel: OmniViewModel) {
    val downloadedCount = viewModel.modelManager.getCactusDownloadedCount()
    val totalSize = viewModel.modelManager.getCactusDownloadedSizeBytes()

    Box(modifier = Modifier.fillMaxWidth().background(OmniGrayDark).border(2.dp, OmniGrayMid)) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BlockyStat(value = "$downloadedCount", label = "MODELS")
            Box(modifier = Modifier.width(2.dp).height(40.dp).background(OmniGrayMid))
            BlockyStat(value = formatBytes(totalSize), label = "STORAGE")
            Box(modifier = Modifier.width(2.dp).height(40.dp).background(OmniGrayMid))
            BlockyStat(value = "LOCAL", label = "MODE")
        }
    }
}

@Composable
fun BlockyStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = OmniWhite,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = OmniGrayText,
                letterSpacing = 1.sp
        )
    }
}

/** Download queue summary showing all active downloads */
@Composable
fun BlockyDownloadQueue(
        activeDownloads: Map<String, ModelManager.ModelDownloadState>,
        cactusModels: List<ModelManager.CactusModelInfo>
) {
    val downloading = activeDownloads.filter { it.value.isDownloading }
    if (downloading.isEmpty()) return

    Box(modifier = Modifier.fillMaxWidth().background(OmniBlackSoft).border(2.dp, OmniYellow)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(OmniYellow))
                Text(
                        text =
                                "DOWNLOADING ${downloading.size} MODEL${if (downloading.size > 1) "S" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniYellow,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            downloading.forEach { (slug, state) ->
                val modelName = cactusModels.find { it.slug == slug }?.name ?: slug

                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini progress indicator
                    CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = OmniYellow,
                            trackColor = OmniGrayMid
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = modelName.uppercase(),
                                style = MaterialTheme.typography.bodySmall,
                                color = OmniWhite,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                        )

                        // Progress bar
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth().height(4.dp).background(OmniGrayMid)
                        ) {
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth(state.progress.coerceIn(0f, 1f))
                                                    .height(4.dp)
                                                    .background(OmniYellow)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                            text =
                                    if (state.progress > 0) "${(state.progress * 100).toInt()}%"
                                    else "...",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniYellow,
                            fontWeight = FontWeight.Bold
                    )
                }
            }

            // Total progress
            val totalProgress =
                    if (downloading.isNotEmpty()) {
                        downloading.values.map { it.progress }.average().toFloat()
                    } else 0f

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                        text = "TOTAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniGrayText,
                        letterSpacing = 1.sp
                )
                Text(
                        text = "${(totalProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniYellow,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun BlockyModelCard(
        name: String,
        sizeMb: Int,
        description: String,
        isDownloaded: Boolean,
        isCurrentModel: Boolean,
        isDownloading: Boolean,
        downloadProgress: Float = 0f,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = 0L,
        isLocal: Boolean = false,
        downloadError: String? = null,
        onDownloadClick: () -> Unit,
        onSelectClick: () -> Unit,
        onErrorDismiss: () -> Unit = {}
) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(if (isCurrentModel) OmniRedDark else OmniGrayDark)
                            .border(
                                    2.dp,
                                    when {
                                        isCurrentModel -> OmniRed
                                        isDownloading -> OmniYellow
                                        downloadError != null -> OmniRed.copy(alpha = 0.6f)
                                        isDownloaded -> OmniGreen
                                        else -> OmniGrayMid
                                    }
                            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Box(
                        modifier =
                                Modifier.size(40.dp)
                                        .background(
                                                when {
                                                    isCurrentModel -> OmniRed
                                                    isDownloading -> OmniYellow
                                                    isLocal -> OmniGreen
                                                    isDownloaded -> OmniGreen
                                                    else -> OmniGrayMid
                                                }
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    when {
                        isDownloading -> {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = OmniBlack,
                                    trackColor = OmniYellow.copy(alpha = 0.3f)
                            )
                        }
                        isLocal -> {
                            Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "Local Server",
                                    tint = OmniBlack,
                                    modifier = Modifier.size(20.dp)
                            )
                        }
                        else -> {
                            Text(
                                    text =
                                            when {
                                                isCurrentModel -> "▶"
                                                isDownloaded -> "✓"
                                                else -> "○"
                                            },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OmniBlack,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                text = name.uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = OmniWhite,
                                letterSpacing = 1.sp
                        )
                        when {
                            isCurrentModel -> {
                                Box(
                                        modifier =
                                                Modifier.background(OmniRed)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OmniWhite,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                    )
                                }
                            }
                            isDownloading -> {
                                Box(
                                        modifier =
                                                Modifier.background(OmniYellow)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                            text = "DOWNLOADING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OmniBlack,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                    )
                                }
                            }
                            isDownloaded && !isCurrentModel -> {
                                Box(
                                        modifier =
                                                Modifier.background(OmniGreen.copy(alpha = 0.8f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                            text = "READY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OmniBlack,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                    Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniGrayText
                    )
                    Text(
                            text =
                                    if (isLocal)
                                            "${formatBytes(sizeMb.toLong() * 1_000_000L)} • LOCAL INFERENCE"
                                    else formatBytes(sizeMb.toLong() * 1_000_000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLocal) OmniGreen else OmniGrayText,
                            letterSpacing = if (isLocal) 1.sp else 0.sp
                    )
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        isDownloading -> {
                            // Show progress indicator with percentage
                            Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                            ) {
                                if (downloadProgress > 0f) {
                                    CircularProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier.size(36.dp),
                                            strokeWidth = 3.dp,
                                            color = OmniYellow,
                                            trackColor = OmniGrayMid
                                    )
                                    Text(
                                            text = "${(downloadProgress * 100).toInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OmniWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                    )
                                } else {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            strokeWidth = 3.dp,
                                            color = OmniYellow,
                                            trackColor = OmniGrayMid
                                    )
                                }
                            }
                        }
                        isDownloaded && !isCurrentModel -> {
                            // SWITCH button for downloaded but not active models
                            Box(
                                    modifier = Modifier.height(40.dp).background(OmniGreen),
                                    contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                        onClick = onSelectClick,
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor = OmniBlack
                                                )
                                ) {
                                    Text(
                                            text = "SWITCH",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                        isCurrentModel -> {
                            // Active indicator
                            Box(
                                    modifier = Modifier.size(40.dp).background(OmniRed),
                                    contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = OmniWhite
                                )
                            }
                        }
                        else -> {
                            // Download button
                            Box(
                                    modifier = Modifier.size(40.dp).background(OmniRed),
                                    contentAlignment = Alignment.Center
                            ) {
                                IconButton(onClick = onDownloadClick) {
                                    Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = OmniWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Download Progress Bar
            AnimatedVisibility(visible = isDownloading) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(OmniGrayMid)) {
                        if (downloadProgress > 0f) {
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth(downloadProgress)
                                                    .height(8.dp)
                                                    .background(OmniYellow)
                            )
                        } else {
                            // Indeterminate shimmer effect
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth(0.3f)
                                                    .height(8.dp)
                                                    .background(OmniYellow.copy(alpha = 0.5f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                                text =
                                        if (bytesDownloaded > 0 && totalBytes > 0) {
                                            "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}"
                                        } else {
                                            "CONNECTING..."
                                        },
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniGrayText,
                                letterSpacing = 1.sp
                        )
                        Text(
                                text =
                                        if (downloadProgress > 0f) {
                                            "${(downloadProgress * 100).toInt()}%"
                                        } else "...",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniYellow,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Error Message
            AnimatedVisibility(visible = downloadError != null) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                            modifier =
                                    Modifier.fillMaxWidth().background(OmniRedDark).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = OmniRed,
                                modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = downloadError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = OmniWhite,
                                modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onErrorDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = OmniGrayText,
                                    modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlockyLegacyModelCard(
        modelInfo: ModelManager.ModelInfo,
        isDownloaded: Boolean,
        isCurrentModel: Boolean,
        isDownloading: Boolean,
        downloadProgress: Float,
        onDownloadClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onSelectClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().background(OmniGrayDark).border(2.dp, OmniGrayMid)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                        modifier =
                                Modifier.size(40.dp)
                                        .background(
                                                if (isDownloaded) OmniGrayLight else OmniGrayMid
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = if (isDownloaded) "✓" else "○",
                            style = MaterialTheme.typography.labelMedium,
                            color = OmniBlack
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = modelInfo.name.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = OmniWhite,
                            letterSpacing = 1.sp
                    )
                    Text(
                            text = modelInfo.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniGrayText
                    )
                    Text(
                            text = formatBytes(modelInfo.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniGrayText
                    )
                }

                if (isDownloading) {
                    CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = OmniRed,
                            trackColor = OmniGrayMid
                    )
                } else if (isDownloaded) {
                    Box(
                            modifier = Modifier.size(40.dp).background(OmniRed),
                            contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = OmniWhite
                            )
                        }
                    }
                } else {
                    Box(
                            modifier = Modifier.size(40.dp).background(OmniGrayMid),
                            contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = onDownloadClick) {
                            Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = OmniWhite
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isDownloading) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(8.dp).background(OmniGrayMid)) {
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth(downloadProgress)
                                                .height(8.dp)
                                                .background(OmniRed)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = "DOWNLOADING... ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = OmniRed,
                            letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BlockyInfoFooter() {
    Box(modifier = Modifier.fillMaxWidth().background(OmniBlack).border(2.dp, OmniGrayMid)) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(8.dp).background(OmniGrayText))
            Column {
                Text(
                        text = "ON-DEVICE AI",
                        style = MaterialTheme.typography.titleSmall,
                        color = OmniGrayText,
                        letterSpacing = 2.sp
                )
                Text(
                        text =
                                "Models run locally via Cactus SDK. Smaller = faster, larger = smarter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniGrayText
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
