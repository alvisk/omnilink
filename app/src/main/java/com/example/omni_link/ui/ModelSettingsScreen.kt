package com.example.omni_link.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.omni_link.ai.OpenRouterProvider
import com.example.omni_link.ui.theme.*

/** Model settings with n0thing blocky UI theme */
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
                                        Spacer(
                                                modifier =
                                                        Modifier.windowInsetsPadding(
                                                                WindowInsets.statusBars
                                                        )
                                        )
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 12.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                // Back button - blocky
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(OmniGrayDark)
                                                                        .border(2.dp, OmniGrayMid),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        IconButton(
                                                                onClick = onBackClick,
                                                                modifier = Modifier.size(40.dp)
                                                        ) {
                                                                Icon(
                                                                        Icons.AutoMirrored.Filled
                                                                                .ArrowBack,
                                                                        contentDescription = "Back",
                                                                        tint = OmniWhite
                                                                )
                                                        }
                                                }

                                                Spacer(modifier = Modifier.width(16.dp))

                                                // Title block
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                text = "MODELS",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .headlineSmall,
                                                                color = OmniWhite,
                                                                letterSpacing = 6.sp
                                                        )
                                                        Text(
                                                                text = "ON-DEVICE AI",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color = OmniGrayText,
                                                                letterSpacing = 2.sp
                                                        )
                                                }

                                                // Show active download count badge
                                                if (activeDownloadCount > 0) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.border(
                                                                                        2.dp,
                                                                                        OmniRed,
                                                                                        RoundedCornerShape(
                                                                                                16.dp
                                                                                        )
                                                                                )
                                                                                .padding(
                                                                                        horizontal =
                                                                                                10.dp,
                                                                                        vertical =
                                                                                                6.dp
                                                                                )
                                                        ) {
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically,
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                6.dp
                                                                                        )
                                                                ) {
                                                                        // Download indicator (static)
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                        8.dp
                                                                                                )
                                                                                                .background(
                                                                                                        OmniRed,
                                                                                                        RoundedCornerShape(
                                                                                                                50
                                                                                                        )
                                                                                                )
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        "$activeDownloadCount",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelMedium,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color = OmniRed
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                        // Red accent line
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(2.dp)
                                                                .background(OmniRed)
                                        )
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

                        // Cloud Provider Section
                        item { BlockyCloudProviderSection(viewModel = viewModel) }

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
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                ) {
                                        Box(modifier = Modifier.size(6.dp).background(OmniRed))
                                        Text(
                                                text = "AVAILABLE",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OmniGrayText,
                                                letterSpacing = 3.sp
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
                                                // Start download - autoLoad only if no model is
                                                // ready
                                                viewModel.downloadCactusModel(
                                                        modelId,
                                                        autoLoad = !uiState.isModelReady
                                                )
                                        },
                                        onSelectClick = { viewModel.selectCactusModel(modelId) },
                                        onErrorDismiss = {
                                                viewModel.clearModelDownloadState(modelId)
                                        }
                                )
                        }

                        // Legacy Models
                        if (ModelManager.AVAILABLE_MODELS.isNotEmpty()) {
                                item {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier =
                                                        Modifier.padding(top = 8.dp, bottom = 4.dp)
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(6.dp)
                                                                        .background(
                                                                                OmniGrayText,
                                                                                RoundedCornerShape(
                                                                                        50
                                                                                )
                                                                        )
                                                )
                                                Text(
                                                        text = "LEGACY",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        color = OmniGrayText,
                                                        letterSpacing = 3.sp
                                                )
                                        }
                                }

                                items(ModelManager.AVAILABLE_MODELS.entries.toList()) {
                                        (modelId, modelInfo) ->
                                        val isDownloaded =
                                                viewModel.modelManager.isModelDownloaded(modelId)
                                        val isCurrentModel = uiState.currentModel == modelId
                                        // Legacy models use the global download state
                                        val isDownloading =
                                                downloadState is
                                                        ModelManager.DownloadState.Downloading

                                        BlockyLegacyModelCard(
                                                modelInfo = modelInfo,
                                                isDownloaded = isDownloaded,
                                                isCurrentModel = isCurrentModel,
                                                isDownloading = isDownloading,
                                                downloadProgress =
                                                        if (isDownloading) {
                                                                (downloadState as?
                                                                                ModelManager.DownloadState.Downloading)
                                                                        ?.progress
                                                                        ?: 0f
                                                        } else 0f,
                                                onDownloadClick = {
                                                        viewModel.downloadModel(modelId)
                                                },
                                                onDeleteClick = { showDeleteDialog = modelId },
                                                onSelectClick = {
                                                        viewModel.modelManager.getModelPath(modelId)
                                                                ?.let {
                                                                        viewModel.downloadModel(
                                                                                modelId
                                                                        )
                                                                }
                                                }
                                        )
                                }
                        }

                        // Info Footer
                        item { BlockyInfoFooter() }
                }
        }

        // Delete Dialog - rounded style
        showDeleteDialog?.let { modelId ->
                AlertDialog(
                        onDismissRequest = { showDeleteDialog = null },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = OmniBlack,
                        modifier = Modifier.border(2.dp, OmniRed, RoundedCornerShape(20.dp)),
                        icon = {
                                Box(
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .background(
                                                                OmniRed,
                                                                RoundedCornerShape(12.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = "!",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = OmniBlack,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        },
                        title = {
                                Text(
                                        text = "DELETE MODEL",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = OmniWhite
                                )
                        },
                        text = {
                                Text(
                                        text =
                                                "Remove this downloaded model file? You can re-download it anytime.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OmniGrayText
                                )
                        },
                        confirmButton = {
                                Surface(
                                        onClick = {
                                                viewModel.modelManager.deleteModel(modelId)
                                                showDeleteDialog = null
                                        },
                                        color = OmniRed,
                                        shape = RoundedCornerShape(12.dp)
                                ) {
                                        Text(
                                                text = "DELETE",
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                color = OmniBlack
                                        )
                                }
                        },
                        dismissButton = {
                                Surface(
                                        onClick = { showDeleteDialog = null },
                                        color = OmniGrayDark,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier =
                                                Modifier.border(
                                                        2.dp,
                                                        OmniGrayMid,
                                                        RoundedCornerShape(12.dp)
                                                )
                                ) {
                                        Text(
                                                text = "CANCEL",
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                color = OmniWhite
                                        )
                                }
                        }
                )
        }
}

@Composable
fun BlockyModelStatus(currentModel: String?, isReady: Boolean, statusMessage: String) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniBlackSoft, RoundedCornerShape(16.dp))
                                .border(
                                        2.dp,
                                        if (isReady) OmniRed else OmniGrayMid,
                                        RoundedCornerShape(16.dp)
                                )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                        // Status block
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .background(
                                                        if (isReady) OmniRed else OmniGrayDark,
                                                        RoundedCornerShape(12.dp)
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                Text(
                                        text = if (isReady) "ON" else "OFF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isReady) OmniBlack else OmniGrayText,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = if (isReady) "AI READY" else "NO MODEL",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = OmniWhite
                                )
                                Text(
                                        text = (currentModel ?: statusMessage).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        letterSpacing = 1.sp,
                                        color = OmniGrayText
                                )
                        }

                        if (isReady) {
                                Box(
                                        modifier =
                                                Modifier.border(
                                                                2.dp,
                                                                OmniRed,
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 10.dp,
                                                                vertical = 6.dp
                                                        )
                                ) {
                                        Text(
                                                text = "ACTIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                color = OmniRed
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

        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniGrayDark, RoundedCornerShape(16.dp))
                                .border(2.dp, OmniGrayMid, RoundedCornerShape(16.dp))
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                        BlockyStat(value = "$downloadedCount", label = "MODELS")
                        Box(modifier = Modifier.width(2.dp).height(36.dp).background(OmniGrayMid))
                        BlockyStat(value = formatBytes(totalSize), label = "STORAGE")
                        Box(modifier = Modifier.width(2.dp).height(36.dp).background(OmniGrayMid))
                        BlockyStat(value = "LOCAL", label = "MODE", highlight = true)
                }
        }
}

/** Cloud Provider Section for Fast Forward AI - OpenRouter Only */
@Composable
fun BlockyCloudProviderSection(viewModel: OmniViewModel) {
        val currentOpenRouterModel by viewModel.currentOpenRouterModel.collectAsState()
        val isOpenRouterAvailable = viewModel.isOpenRouterAvailable()

        var showModelDropdown by remember { mutableStateOf(false) }

        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                // Section Header
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                ) {
                        Box(modifier = Modifier.size(6.dp).background(OmniRed))
                        Text(
                                text = "CLOUD AI",
                                style = MaterialTheme.typography.labelMedium,
                                color = OmniGrayText,
                                letterSpacing = 3.sp
                        )
                        Text(
                                text = "(FAST FORWARD)",
                                style = MaterialTheme.typography.labelSmall,
                                color = OmniGrayMid
                        )
                }

                // OpenRouter Model Selection Card
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(OmniGrayDark, RoundedCornerShape(16.dp))
                                        .border(
                                                2.dp,
                                                if (isOpenRouterAvailable) OmniRed else OmniGrayMid,
                                                RoundedCornerShape(16.dp)
                                        )
                ) {
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                // OpenRouter header
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = "🌐",
                                                style = MaterialTheme.typography.headlineMedium
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = "OPENROUTER",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 2.sp,
                                                        color = OmniWhite
                                                )
                                                Text(
                                                        text =
                                                                if (isOpenRouterAvailable)
                                                                        "Multi-model cloud AI"
                                                                else "API key required",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = OmniGrayText
                                                )
                                        }
                                        if (isOpenRouterAvailable) {
                                                Box(
                                                        modifier =
                                                                Modifier.background(
                                                                                OmniRed,
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                        )
                                                                        .padding(
                                                                                horizontal = 8.dp,
                                                                                vertical = 4.dp
                                                                        )
                                                ) {
                                                        Text(
                                                                text = "ACTIVE",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OmniBlack
                                                        )
                                                }
                                        }
                                }

                                if (isOpenRouterAvailable) {
                                        // Divider
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(1.dp)
                                                                .background(OmniGrayMid)
                                        )

                                        Text(
                                                text = "SELECT MODEL",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniGrayText,
                                                letterSpacing = 2.sp
                                        )

                                        // Model dropdown
                                        Box {
                                                Surface(
                                                        onClick = {
                                                                showModelDropdown =
                                                                        !showModelDropdown
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = OmniBlackSoft,
                                                        border =
                                                                androidx.compose.foundation
                                                                        .BorderStroke(
                                                                                2.dp,
                                                                                OmniGrayMid
                                                                        )
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(12.dp),
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween,
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Column {
                                                                        Text(
                                                                                text =
                                                                                        currentOpenRouterModel
                                                                                                .displayName,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodyMedium,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color = OmniWhite
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        currentOpenRouterModel
                                                                                                .description,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                color = OmniGrayText
                                                                        )
                                                                }
                                                                Icon(
                                                                        imageVector =
                                                                                if (showModelDropdown
                                                                                )
                                                                                        Icons.Default
                                                                                                .KeyboardArrowUp
                                                                                else
                                                                                        Icons.Default
                                                                                                .KeyboardArrowDown,
                                                                        contentDescription =
                                                                                "Select",
                                                                        tint = OmniGrayText
                                                                )
                                                        }
                                                }

                                                DropdownMenu(
                                                        expanded = showModelDropdown,
                                                        onDismissRequest = {
                                                                showModelDropdown = false
                                                        },
                                                        modifier =
                                                                Modifier.background(OmniBlackSoft)
                                                                        .border(2.dp, OmniGrayMid)
                                                ) {
                                                        // Group: Fast Models
                                                        Text(
                                                                text = "⚡ FAST",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color = OmniRed,
                                                                letterSpacing = 2.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        )
                                                        listOf(
                                                                        OpenRouterProvider.Model
                                                                                .GEMINI_25_FLASH,
                                                                        OpenRouterProvider.Model
                                                                                .GPT4O_MINI,
                                                                        OpenRouterProvider.Model
                                                                                .GEMINI_20_FLASH,
                                                                        OpenRouterProvider.Model
                                                                                .CLAUDE_HAIKU
                                                                )
                                                                .forEach { model ->
                                                                        DropdownMenuItem(
                                                                                text = {
                                                                                        Column {
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.displayName,
                                                                                                        color =
                                                                                                                if (model ==
                                                                                                                                currentOpenRouterModel
                                                                                                                )
                                                                                                                        OmniRed
                                                                                                                else
                                                                                                                        OmniWhite
                                                                                                )
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.description,
                                                                                                        style =
                                                                                                                MaterialTheme
                                                                                                                        .typography
                                                                                                                        .labelSmall,
                                                                                                        color =
                                                                                                                OmniGrayText
                                                                                                )
                                                                                        }
                                                                                },
                                                                                onClick = {
                                                                                        viewModel
                                                                                                .setOpenRouterModel(
                                                                                                        model
                                                                                                )
                                                                                        showModelDropdown =
                                                                                                false
                                                                                }
                                                                        )
                                                                }

                                                        Divider(color = OmniGrayMid)

                                                        // Group: Premium Models
                                                        Text(
                                                                text = "🚀 PREMIUM",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color = OmniRed,
                                                                letterSpacing = 2.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        )
                                                        listOf(
                                                                        OpenRouterProvider.Model
                                                                                .GPT4O,
                                                                        OpenRouterProvider.Model
                                                                                .CLAUDE_SONNET,
                                                                        OpenRouterProvider.Model
                                                                                .GEMINI_PRO
                                                                )
                                                                .forEach { model ->
                                                                        DropdownMenuItem(
                                                                                text = {
                                                                                        Column {
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.displayName,
                                                                                                        color =
                                                                                                                if (model ==
                                                                                                                                currentOpenRouterModel
                                                                                                                )
                                                                                                                        OmniRed
                                                                                                                else
                                                                                                                        OmniWhite
                                                                                                )
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.description,
                                                                                                        style =
                                                                                                                MaterialTheme
                                                                                                                        .typography
                                                                                                                        .labelSmall,
                                                                                                        color =
                                                                                                                OmniGrayText
                                                                                                )
                                                                                        }
                                                                                },
                                                                                onClick = {
                                                                                        viewModel
                                                                                                .setOpenRouterModel(
                                                                                                        model
                                                                                                )
                                                                                        showModelDropdown =
                                                                                                false
                                                                                }
                                                                        )
                                                                }

                                                        Divider(color = OmniGrayMid)

                                                        // Group: Open Source
                                                        Text(
                                                                text = "🌟 OPEN SOURCE",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color = OmniRed,
                                                                letterSpacing = 2.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        )
                                                        listOf(
                                                                        OpenRouterProvider.Model
                                                                                .LLAMA_70B,
                                                                        OpenRouterProvider.Model
                                                                                .QWEN_72B,
                                                                        OpenRouterProvider.Model
                                                                                .DEEPSEEK
                                                                )
                                                                .forEach { model ->
                                                                        DropdownMenuItem(
                                                                                text = {
                                                                                        Column {
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.displayName,
                                                                                                        color =
                                                                                                                if (model ==
                                                                                                                                currentOpenRouterModel
                                                                                                                )
                                                                                                                        OmniRed
                                                                                                                else
                                                                                                                        OmniWhite
                                                                                                )
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.description,
                                                                                                        style =
                                                                                                                MaterialTheme
                                                                                                                        .typography
                                                                                                                        .labelSmall,
                                                                                                        color =
                                                                                                                OmniGrayText
                                                                                                )
                                                                                        }
                                                                                },
                                                                                onClick = {
                                                                                        viewModel
                                                                                                .setOpenRouterModel(
                                                                                                        model
                                                                                                )
                                                                                        showModelDropdown =
                                                                                                false
                                                                                }
                                                                        )
                                                                }

                                                        Divider(color = OmniGrayMid)

                                                        // Group: Free Tier
                                                        Text(
                                                                text = "🆓 FREE TIER",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color = OmniRed,
                                                                letterSpacing = 2.sp,
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        )
                                                        listOf(
                                                                        OpenRouterProvider.Model
                                                                                .LLAMA_8B_FREE,
                                                                        OpenRouterProvider.Model
                                                                                .GEMMA_7B_FREE
                                                                )
                                                                .forEach { model ->
                                                                        DropdownMenuItem(
                                                                                text = {
                                                                                        Column {
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.displayName,
                                                                                                        color =
                                                                                                                if (model ==
                                                                                                                                currentOpenRouterModel
                                                                                                                )
                                                                                                                        OmniRed
                                                                                                                else
                                                                                                                        OmniWhite
                                                                                                )
                                                                                                Text(
                                                                                                        text =
                                                                                                                model.description,
                                                                                                        style =
                                                                                                                MaterialTheme
                                                                                                                        .typography
                                                                                                                        .labelSmall,
                                                                                                        color =
                                                                                                                OmniGrayText
                                                                                                )
                                                                                        }
                                                                                },
                                                                                onClick = {
                                                                                        viewModel
                                                                                                .setOpenRouterModel(
                                                                                                        model
                                                                                                )
                                                                                        showModelDropdown =
                                                                                                false
                                                                                }
                                                                        )
                                                                }
                                                }
                                        }

                                        // Info text
                                        Text(
                                                text =
                                                        "🌐 ${currentOpenRouterModel.displayName}: ${currentOpenRouterModel.description}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniGrayText
                                        )
                                }
                        }
                }
        }
}

@Composable
fun BlockyStat(value: String, label: String, highlight: Boolean = false) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                        text = value.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highlight) OmniRed else OmniWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                        color = OmniGrayText
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

        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniBlackSoft, RoundedCornerShape(16.dp))
                                .border(2.dp, OmniRed, RoundedCornerShape(16.dp))
        ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                // Status indicator (static)
                                Box(
                                        modifier =
                                                Modifier.size(8.dp)
                                                        .background(
                                                                OmniRed,
                                                                RoundedCornerShape(50)
                                                        )
                                )
                                Text(
                                        text =
                                                "DOWNLOADING ${downloading.size} MODEL${if (downloading.size > 1) "S" else ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = OmniRed
                                )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        downloading.forEach { (slug, state) ->
                                val modelName = cactusModels.find { it.slug == slug }?.name ?: slug

                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Progress block
                                        Box(
                                                modifier =
                                                        Modifier.size(24.dp)
                                                                .background(
                                                                        OmniGrayDark,
                                                                        RoundedCornerShape(6.dp)
                                                                )
                                                                .border(
                                                                        1.dp,
                                                                        OmniRed,
                                                                        RoundedCornerShape(6.dp)
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text = "${(state.progress * 100).toInt()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = OmniRed,
                                                        fontSize = 9.sp
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = modelName.uppercase(),
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp,
                                                        color = OmniWhite
                                                )

                                                // Progress bar - rounded
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .height(4.dp)
                                                                        .background(
                                                                                OmniGrayMid,
                                                                                RoundedCornerShape(
                                                                                        2.dp
                                                                                )
                                                                        )
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxWidth(
                                                                                        state.progress
                                                                                                .coerceIn(
                                                                                                        0f,
                                                                                                        1f
                                                                                                )
                                                                                )
                                                                                .height(4.dp)
                                                                                .background(
                                                                                        OmniRed,
                                                                                        RoundedCornerShape(
                                                                                                2.dp
                                                                                        )
                                                                                )
                                                        )
                                                }
                                        }
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
                                        letterSpacing = 2.sp,
                                        color = OmniGrayText
                                )
                                Text(
                                        text = "${(totalProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = OmniRed
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
        val borderColor =
                when {
                        isCurrentModel -> OmniRed
                        isDownloading -> OmniRed
                        downloadError != null -> OmniRed
                        isDownloaded -> OmniGrayMid
                        else -> OmniGrayMid
                }

        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniGrayDark, RoundedCornerShape(16.dp))
                                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                // Status block
                                Box(
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                when {
                                                                        isCurrentModel -> OmniRed
                                                                        isDownloading -> OmniGrayMid
                                                                        isLocal || isDownloaded ->
                                                                                OmniGrayMid
                                                                        else -> OmniBlackSoft
                                                                },
                                                                RoundedCornerShape(10.dp)
                                                        )
                                                        .then(
                                                                if (!isCurrentModel)
                                                                        Modifier.border(
                                                                                1.dp,
                                                                                OmniGrayMid,
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        )
                                                                else Modifier
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        when {
                                                isDownloading -> {
                                                        Text(
                                                                text = "...",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OmniRed
                                                        )
                                                }
                                                isLocal -> {
                                                        Text(
                                                                text = "LAN",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OmniWhite,
                                                                fontSize = 9.sp
                                                        )
                                                }
                                                isCurrentModel -> {
                                                        Text(
                                                                text = "ON",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OmniBlack
                                                        )
                                                }
                                                isDownloaded -> {
                                                        Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Downloaded",
                                                                tint = OmniWhite,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                }
                                                else -> {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(8.dp)
                                                                                .background(
                                                                                        OmniGrayMid,
                                                                                        RoundedCornerShape(
                                                                                                50
                                                                                        )
                                                                                )
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
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp,
                                                        color = OmniWhite
                                                )
                                                when {
                                                        isCurrentModel -> {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.background(
                                                                                                OmniRed,
                                                                                                RoundedCornerShape(
                                                                                                        8.dp
                                                                                                )
                                                                                        )
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                text = "ACTIVE",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color = OmniBlack,
                                                                                fontSize = 9.sp
                                                                        )
                                                                }
                                                        }
                                                        isDownloading -> {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.border(
                                                                                                1.dp,
                                                                                                OmniRed,
                                                                                                RoundedCornerShape(
                                                                                                        8.dp
                                                                                                )
                                                                                        )
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                text = "DL",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color = OmniRed,
                                                                                fontSize = 9.sp
                                                                        )
                                                                }
                                                        }
                                                        isDownloaded && !isCurrentModel -> {
                                                                Box(
                                                                        modifier =
                                                                                Modifier.border(
                                                                                                1.dp,
                                                                                                OmniGrayMid,
                                                                                                RoundedCornerShape(
                                                                                                        8.dp
                                                                                                )
                                                                                        )
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                text = "READY",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color =
                                                                                        OmniGrayText,
                                                                                fontSize = 9.sp
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OmniGrayText,
                                                lineHeight = 16.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        text =
                                                                formatBytes(
                                                                        sizeMb.toLong() * 1_000_000L
                                                                ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = OmniGrayText
                                                )
                                                if (isLocal) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(4.dp)
                                                                                .background(
                                                                                        OmniGrayMid,
                                                                                        RoundedCornerShape(
                                                                                                50
                                                                                        )
                                                                                )
                                                        )
                                                        Text(
                                                                text = "LOCAL",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 1.sp,
                                                                color = OmniRed
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Action Buttons - blocky
                                when {
                                        isDownloading -> {
                                                // Show progress indicator with percentage
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                OmniBlackSoft,
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        )
                                                                        .border(
                                                                                2.dp,
                                                                                OmniRed,
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Text(
                                                                text =
                                                                        if (downloadProgress > 0f)
                                                                                "${(downloadProgress * 100).toInt()}"
                                                                        else "...",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = OmniRed,
                                                                fontSize = 10.sp
                                                        )
                                                }
                                        }
                                        isDownloaded && !isCurrentModel -> {
                                                // USE button for downloaded but not active models
                                                Surface(
                                                        onClick = onSelectClick,
                                                        color = OmniRed,
                                                        shape = RoundedCornerShape(12.dp)
                                                ) {
                                                        Text(
                                                                text = "USE",
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 16.dp,
                                                                                vertical = 10.dp
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 2.sp,
                                                                color = OmniBlack
                                                        )
                                                }
                                        }
                                        isCurrentModel -> {
                                                // Active indicator
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                OmniRed,
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Active",
                                                                tint = OmniBlack,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                }
                                        }
                                        else -> {
                                                // Download button
                                                Surface(
                                                        onClick = onDownloadClick,
                                                        color = OmniBlackSoft,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier =
                                                                Modifier.border(
                                                                        2.dp,
                                                                        OmniGrayMid,
                                                                        RoundedCornerShape(12.dp)
                                                                )
                                                ) {
                                                        Row(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 12.dp,
                                                                                vertical = 10.dp
                                                                        ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.spacedBy(6.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default
                                                                                        .Download,
                                                                        contentDescription =
                                                                                "Download",
                                                                        tint = OmniWhite,
                                                                        modifier =
                                                                                Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                        text = "GET",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        letterSpacing = 2.sp,
                                                                        color = OmniWhite
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        // Download Progress Bar
                        AnimatedVisibility(visible = isDownloading) {
                                Column {
                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Progress bar - rounded
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(6.dp)
                                                                .background(
                                                                        OmniGrayMid,
                                                                        RoundedCornerShape(3.dp)
                                                                )
                                        ) {
                                                if (downloadProgress > 0f) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxWidth(
                                                                                        downloadProgress
                                                                                )
                                                                                .height(6.dp)
                                                                                .background(
                                                                                        OmniRed,
                                                                                        RoundedCornerShape(
                                                                                                3.dp
                                                                                        )
                                                                                )
                                                        )
                                                } else {
                                                        // Static indeterminate state (animation removed)
                                                        Box(
                                                                modifier =
                                                                        Modifier.fillMaxWidth(0.3f)
                                                                                .height(6.dp)
                                                                                .background(
                                                                                        OmniRed.copy(
                                                                                                alpha =
                                                                                                        0.5f
                                                                                        ),
                                                                                        RoundedCornerShape(
                                                                                                3.dp
                                                                                        )
                                                                                )
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
                                                                if (bytesDownloaded > 0 &&
                                                                                totalBytes > 0
                                                                ) {
                                                                        "${formatBytes(bytesDownloaded)} / ${formatBytes(totalBytes)}"
                                                                } else {
                                                                        "CONNECTING..."
                                                                },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        letterSpacing = 1.sp,
                                                        color = OmniGrayText
                                                )
                                                Text(
                                                        text =
                                                                if (downloadProgress > 0f)
                                                                        "${(downloadProgress * 100).toInt()}%"
                                                                else "...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = OmniRed
                                                )
                                        }
                                }
                        }

                        // Error Message
                        AnimatedVisibility(visible = downloadError != null) {
                                Column {
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .background(
                                                                        OmniBlackSoft,
                                                                        RoundedCornerShape(12.dp)
                                                                )
                                                                .border(
                                                                        2.dp,
                                                                        OmniRed,
                                                                        RoundedCornerShape(12.dp)
                                                                )
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(12.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.size(24.dp)
                                                                                .background(
                                                                                        OmniRed,
                                                                                        RoundedCornerShape(
                                                                                                6.dp
                                                                                        )
                                                                                ),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Text(
                                                                        text = "!",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        color = OmniBlack
                                                                )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                text =
                                                                        (downloadError ?: "")
                                                                                .uppercase(),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                letterSpacing = 1.sp,
                                                                color = OmniWhite,
                                                                modifier = Modifier.weight(1f)
                                                        )
                                                        IconButton(
                                                                onClick = onErrorDismiss,
                                                                modifier = Modifier.size(24.dp)
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.Default.Close,
                                                                        contentDescription =
                                                                                "Dismiss",
                                                                        tint = OmniGrayText,
                                                                        modifier =
                                                                                Modifier.size(16.dp)
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun BlockyLegacyModelCard(
        modelInfo: ModelManager.ModelInfo,
        isDownloaded: Boolean,
        isCurrentModel: Boolean, // Reserved for future use
        isDownloading: Boolean,
        downloadProgress: Float,
        onDownloadClick: () -> Unit,
        onDeleteClick: () -> Unit,
        onSelectClick: () -> Unit // Reserved for future use
) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniBlackSoft, RoundedCornerShape(16.dp))
                                .border(2.dp, OmniGrayMid, RoundedCornerShape(16.dp))
        ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Box(
                                        modifier =
                                                Modifier.size(40.dp)
                                                        .background(
                                                                OmniGrayDark,
                                                                RoundedCornerShape(10.dp)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                OmniGrayMid,
                                                                RoundedCornerShape(10.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        if (isDownloaded) {
                                                Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = OmniGrayText,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                        } else {
                                                Box(
                                                        modifier =
                                                                Modifier.size(8.dp)
                                                                        .background(
                                                                                OmniGrayMid,
                                                                                RoundedCornerShape(
                                                                                        50
                                                                                )
                                                                        )
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = modelInfo.name.uppercase(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = OmniGrayText
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                text = modelInfo.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OmniGrayText,
                                                lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                text = formatBytes(modelInfo.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OmniGrayText
                                        )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                if (isDownloading) {
                                        Box(
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .background(
                                                                        OmniBlackSoft,
                                                                        RoundedCornerShape(10.dp)
                                                                )
                                                                .border(
                                                                        2.dp,
                                                                        OmniGrayMid,
                                                                        RoundedCornerShape(10.dp)
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text =
                                                                "${(downloadProgress * 100).toInt()}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = OmniGrayText,
                                                        fontSize = 10.sp
                                                )
                                        }
                                } else if (isDownloaded) {
                                        IconButton(
                                                onClick = onDeleteClick,
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .background(
                                                                        OmniBlackSoft,
                                                                        RoundedCornerShape(10.dp)
                                                                )
                                                                .border(
                                                                        2.dp,
                                                                        OmniRed,
                                                                        RoundedCornerShape(10.dp)
                                                                )
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Delete",
                                                        tint = OmniRed
                                                )
                                        }
                                } else {
                                        Surface(
                                                onClick = onDownloadClick,
                                                color = OmniBlackSoft,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier =
                                                        Modifier.border(
                                                                2.dp,
                                                                OmniGrayMid,
                                                                RoundedCornerShape(12.dp)
                                                        )
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(6.dp)
                                                ) {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.Download,
                                                                contentDescription = "Download",
                                                                tint = OmniGrayText,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                                text = "GET",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 2.sp,
                                                                color = OmniGrayText
                                                        )
                                                }
                                        }
                                }
                        }

                        AnimatedVisibility(visible = isDownloading) {
                                Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(6.dp)
                                                                .background(
                                                                        OmniGrayMid,
                                                                        RoundedCornerShape(3.dp)
                                                                )
                                        ) {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth(
                                                                                downloadProgress
                                                                        )
                                                                        .height(6.dp)
                                                                        .background(
                                                                                OmniGrayText,
                                                                                RoundedCornerShape(
                                                                                        3.dp
                                                                                )
                                                                        )
                                                )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                                text =
                                                        "DOWNLOADING... ${(downloadProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                letterSpacing = 1.sp,
                                                color = OmniGrayText
                                        )
                                }
                        }
                }
        }
}

@Composable
fun BlockyInfoFooter() {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .background(OmniBlackSoft, RoundedCornerShape(16.dp))
                                .border(2.dp, OmniGrayMid, RoundedCornerShape(16.dp))
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                ) {
                        Box(
                                modifier =
                                        Modifier.size(6.dp)
                                                .background(OmniRed, RoundedCornerShape(50))
                        )
                        Column {
                                Text(
                                        text = "ON-DEVICE AI",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        color = OmniGrayText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text =
                                                "Models run locally via Cactus SDK. Smaller models are faster, larger ones are smarter.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OmniGrayText,
                                        lineHeight = 16.sp
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
