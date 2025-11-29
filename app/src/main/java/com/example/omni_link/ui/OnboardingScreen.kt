package com.example.omni_link.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.ai.ModelManager
import com.example.omni_link.glyph.GlyphMatrixHelper
import com.example.omni_link.glyph.GlyphType
import com.example.omni_link.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
        val title: String,
        val subtitle: String,
        val description: String,
        val glyphType: GlyphType
)

// Total pages: 4 info pages + 1 model download page = 5
private const val TOTAL_PAGES = 5
private const val MODEL_DOWNLOAD_PAGE_INDEX = 4

private val onboardingPages =
        listOf(
                OnboardingPage(
                        title = "NOMM.",
                        subtitle = "Nothing On My Mind",
                        description =
                                "Your private AI assistant that sees what you see and helps you think.",
                        glyphType = GlyphType.MOBIUS_FIGURE_8
                ),
                OnboardingPage(
                        title = "ON-DEVICE AI",
                        subtitle = "Download & Run Locally",
                        description =
                                "Download compact AI models to run entirely on your device. No internet needed after setup. Your conversations stay private.",
                        glyphType = GlyphType.DNA_HELIX
                ),
                OnboardingPage(
                        title = "SCREEN-AWARE",
                        subtitle = "Context Is Everything",
                        description =
                                "NOMM reads your screen to understand context. Ask about what you're viewing and get relevant, intelligent assistance.",
                        glyphType = GlyphType.ROTATING_CUBE
                ),
                OnboardingPage(
                        title = "GLYPH MATRIX",
                        subtitle = "Your Phone Comes Alive",
                        description =
                                "The LED matrix on your Nothing Phone displays animated patterns while NOMM is active. Choose from 8 unique animations.",
                        glyphType = GlyphType.SPIRAL_GALAXY
                )
        )

@Composable
fun OnboardingScreen(viewModel: OmniViewModel, onComplete: () -> Unit) {
        val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
        val scope = rememberCoroutineScope()

        // Model state from ViewModel
        val cactusModels by viewModel.cactusModels.collectAsState()
        val activeDownloads by viewModel.activeDownloads.collectAsState()
        val uiState by viewModel.uiState.collectAsState()

        // Check if any model is downloaded
        val hasDownloadedModel = cactusModels.any { it.isDownloaded }

        // Check if a model is loaded and ready
        val isModelLoaded = uiState.isModelReady && uiState.currentModel != null

        // Check if any download is in progress
        val isDownloading = activeDownloads.any { it.value.isDownloading }

        Box(modifier = Modifier.fillMaxSize().background(NothingBlack)) {
                Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        // Skip button - show on all pages except model download when downloading
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                val canSkip =
                                        pagerState.currentPage != MODEL_DOWNLOAD_PAGE_INDEX ||
                                                (!isDownloading &&
                                                        (hasDownloadedModel || isModelLoaded))
                                if (pagerState.currentPage < TOTAL_PAGES - 1 ||
                                                (pagerState.currentPage ==
                                                        MODEL_DOWNLOAD_PAGE_INDEX &&
                                                        (hasDownloadedModel || isModelLoaded))
                                ) {
                                        TextButton(
                                                onClick = onComplete,
                                                modifier = Modifier.align(Alignment.CenterEnd),
                                                enabled = canSkip
                                        ) {
                                                Text(
                                                        text = "SKIP",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color =
                                                                if (canSkip) NothingGray500
                                                                else NothingGray700,
                                                        letterSpacing = 2.sp
                                                )
                                        }
                                }
                        }

                        // Pager content
                        HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                userScrollEnabled =
                                        !(pagerState.currentPage == MODEL_DOWNLOAD_PAGE_INDEX &&
                                                isDownloading)
                        ) { page ->
                                if (page == MODEL_DOWNLOAD_PAGE_INDEX) {
                                        // Model download page
                                        OnboardingModelDownloadPage(
                                                cactusModels = cactusModels,
                                                activeDownloads = activeDownloads,
                                                currentModel = uiState.currentModel,
                                                isModelLoading =
                                                        uiState.statusMessage.contains("Loading"),
                                                onDownloadClick = { slug ->
                                                        viewModel.downloadCactusModel(
                                                                slug,
                                                                autoLoad = true
                                                        )
                                                },
                                                onSelectClick = { slug ->
                                                        viewModel.selectCactusModel(slug)
                                                }
                                        )
                                } else {
                                        // Regular info pages
                                        OnboardingPageContent(
                                                page = onboardingPages[page],
                                                pageIndex = page
                                        )
                                }
                        }

                        // Bottom section
                        Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                                // Page indicators
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.padding(bottom = 40.dp)
                                ) {
                                        repeat(TOTAL_PAGES) { index ->
                                                PageIndicator(
                                                        isSelected = pagerState.currentPage == index
                                                )
                                        }
                                }

                                // Action button
                                val isModelPage =
                                        pagerState.currentPage == MODEL_DOWNLOAD_PAGE_INDEX
                                val buttonEnabled =
                                        if (isModelPage) isModelLoaded && !isDownloading else true
                                val buttonText =
                                        when {
                                                isModelPage && isDownloading -> "DOWNLOADING..."
                                                isModelPage && isModelLoaded -> "GET STARTED"
                                                isModelPage && hasDownloadedModel ->
                                                        "SELECT A MODEL"
                                                isModelPage -> "DOWNLOAD A MODEL"
                                                pagerState.currentPage == TOTAL_PAGES - 1 ->
                                                        "GET STARTED"
                                                else -> "NEXT"
                                        }

                                Button(
                                        onClick = {
                                                if (isModelPage && isModelLoaded) {
                                                        onComplete()
                                                } else if (!isModelPage) {
                                                        scope.launch {
                                                                pagerState.animateScrollToPage(
                                                                        pagerState.currentPage + 1
                                                                )
                                                        }
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        enabled = buttonEnabled,
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                when {
                                                                        isModelPage &&
                                                                                isModelLoaded ->
                                                                                NothingRed
                                                                        isModelPage &&
                                                                                isDownloading ->
                                                                                NothingGray800
                                                                        isModelPage ->
                                                                                NothingGray900
                                                                        else -> NothingGray900
                                                                },
                                                        contentColor = NothingWhite,
                                                        disabledContainerColor = NothingGray900,
                                                        disabledContentColor = NothingGray600
                                                )
                                ) {
                                        if (isModelPage && isDownloading) {
                                                CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        color = NothingRed,
                                                        strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Text(
                                                text = buttonText,
                                                style = MaterialTheme.typography.labelLarge,
                                                letterSpacing = 3.sp
                                        )
                                }
                        }
                }
        }
}

/** Model download page for onboarding - shows available models and download progress */
@Composable
private fun OnboardingModelDownloadPage(
        cactusModels: List<ModelManager.CactusModelInfo>,
        activeDownloads: Map<String, ModelManager.ModelDownloadState>,
        currentModel: String?,
        isModelLoading: Boolean,
        onDownloadClick: (String) -> Unit,
        onSelectClick: (String) -> Unit
) {
        // Check if any models are already downloaded
        val hasDownloadedModels = cactusModels.any { it.isDownloaded }

        Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Text(
                        text = "05",
                        style = MaterialTheme.typography.headlineMedium,
                        color = NothingRed
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Red accent line
                Box(modifier = Modifier.width(48.dp).height(2.dp).background(NothingRed))

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        text = if (hasDownloadedModels) "SELECT MODEL" else "DOWNLOAD MODEL",
                        style = MaterialTheme.typography.displaySmall,
                        color = NothingWhite,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = "Choose Your AI",
                        style = MaterialTheme.typography.titleMedium,
                        color = NothingGray500,
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text =
                                if (hasDownloadedModels)
                                        "Tap a downloaded model to select it, or download a new one."
                                else
                                        "Select a model to download. We recommend LFM2 350M — it's fast, efficient, and works great on most devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingGray400,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Model list
                LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                        // Filter to show only non-local downloadable models, sorted by size
                        // But show downloaded models first
                        val downloadableModels =
                                cactusModels
                                        .filter { !it.isLocal }
                                        .sortedWith(
                                                compareByDescending<ModelManager.CactusModelInfo> {
                                                        it.isDownloaded
                                                }
                                                        .thenBy { it.sizeMb }
                                        )

                        items(downloadableModels, key = { it.slug }) { model ->
                                val downloadState = activeDownloads[model.slug]
                                val isDownloading = downloadState?.isDownloading == true
                                val downloadProgress = downloadState?.progress ?: 0f
                                val downloadError = downloadState?.error
                                val isSelected = currentModel == model.slug
                                val isLoading = isModelLoading && isSelected

                                OnboardingModelCard(
                                        model = model,
                                        isDownloading = isDownloading,
                                        downloadProgress = downloadProgress,
                                        downloadError = downloadError,
                                        isSelected = isSelected,
                                        isLoading = isLoading,
                                        onDownloadClick = { onDownloadClick(model.slug) },
                                        onSelectClick = { onSelectClick(model.slug) }
                                )
                        }
                }
        }
}

/** Compact model card for onboarding - minimal design with essential info */
@Composable
private fun OnboardingModelCard(
        model: ModelManager.CactusModelInfo,
        isDownloading: Boolean,
        downloadProgress: Float,
        downloadError: String?,
        isSelected: Boolean,
        isLoading: Boolean,
        onDownloadClick: () -> Unit,
        onSelectClick: () -> Unit
) {
        val isDownloaded = model.isDownloaded
        val borderColor =
                when {
                        isSelected -> NothingRed
                        isLoading -> NothingRedDim
                        isDownloaded -> NothingGray600
                        isDownloading -> NothingRedDim
                        downloadError != null -> NothingRed
                        else -> NothingGray800
                }

        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                                .then(
                                        when {
                                                isDownloaded && !isLoading ->
                                                        Modifier.clickable { onSelectClick() }
                                                !isDownloaded && !isDownloading ->
                                                        Modifier.clickable { onDownloadClick() }
                                                else -> Modifier
                                        }
                                ),
                color = if (isSelected) NothingCharcoal.copy(alpha = 0.9f) else NothingCharcoal,
                shape = RoundedCornerShape(12.dp)
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Status indicator
                                Box(
                                        modifier =
                                                Modifier.size(36.dp)
                                                        .background(
                                                                when {
                                                                        isSelected -> NothingRed
                                                                        isLoading -> NothingGray800
                                                                        isDownloaded ->
                                                                                NothingGray700
                                                                        isDownloading ->
                                                                                NothingGray800
                                                                        else -> NothingGray900
                                                                },
                                                                RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                when {
                                                                        isSelected -> NothingRed
                                                                        isDownloaded ->
                                                                                NothingGray600
                                                                        else -> NothingGray700
                                                                },
                                                                RoundedCornerShape(8.dp)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        when {
                                                isLoading -> {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                color = NothingRed,
                                                                strokeWidth = 2.dp
                                                        )
                                                }
                                                isDownloading -> {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                color = NothingRed,
                                                                strokeWidth = 2.dp
                                                        )
                                                }
                                                isSelected -> {
                                                        Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = NothingBlack,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                }
                                                isDownloaded -> {
                                                        Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Downloaded",
                                                                tint = NothingWhite,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                }
                                                else -> {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.Download,
                                                                contentDescription = "Download",
                                                                tint = NothingGray500,
                                                                modifier = Modifier.size(18.dp)
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Model info
                                Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                Text(
                                                        text = model.name.uppercase(),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 1.sp,
                                                        color = NothingWhite
                                                )
                                                when {
                                                        isSelected -> {
                                                                Surface(
                                                                        color = NothingRed,
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
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
                                                                                color =
                                                                                        NothingBlack,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        ),
                                                                                fontSize = 9.sp
                                                                        )
                                                                }
                                                        }
                                                        isLoading -> {
                                                                Surface(
                                                                        color = NothingGray700,
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                ) {
                                                                        Text(
                                                                                text = "LOADING",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color =
                                                                                        NothingGray300,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        ),
                                                                                fontSize = 9.sp
                                                                        )
                                                                }
                                                        }
                                                        isDownloaded -> {
                                                                Surface(
                                                                        color = NothingGray700,
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
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
                                                                                        NothingGray300,
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        6.dp,
                                                                                                vertical =
                                                                                                        2.dp
                                                                                        ),
                                                                                fontSize = 9.sp
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                                text = model.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NothingGray400,
                                                maxLines = 1
                                        )
                                }

                                // Size badge
                                Surface(color = NothingGray900, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                                text = "${model.sizeMb}MB",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NothingGray300,
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                        )
                                        )
                                }
                        }

                        // Download progress bar
                        if (isDownloading) {
                                LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = NothingRed,
                                        trackColor = NothingGray800,
                                )
                        }

                        // Error message
                        if (downloadError != null) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .background(
                                                                NothingRedDim.copy(alpha = 0.2f)
                                                        )
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = downloadError,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = NothingRed,
                                                modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                                onClick = onDownloadClick,
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                                Text(
                                                        text = "RETRY",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NothingRed
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, pageIndex: Int) {
        // Animation frame for glyph
        val infiniteTransition = rememberInfiniteTransition(label = "onboarding_glyph")
        val frame by
                infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 240f,
                        animationSpec =
                                infiniteRepeatable(
                                        animation =
                                                tween(durationMillis = 8000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                ),
                        label = "frame"
                )

        // Static values (floating animations removed)
        val glowScale = 1f
        val glowAlpha = 0.35f
        val haloRotation = 0f

        // Generate glyph bitmap
        val bitmap =
                remember(page.glyphType, frame.toInt()) {
                        GlyphMatrixHelper.generatePreviewBitmap(page.glyphType, frame.toInt())
                }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // Background radial glow
                Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                                brush =
                                        Brush.radialGradient(
                                                colors =
                                                        listOf(
                                                                NothingRed.copy(
                                                                        alpha =
                                                                                0.06f *
                                                                                        glowAlpha *
                                                                                        2
                                                                ),
                                                                NothingRed.copy(
                                                                        alpha =
                                                                                0.02f *
                                                                                        glowAlpha *
                                                                                        2
                                                                ),
                                                                Color.Transparent
                                                        ),
                                                center =
                                                        Offset(size.width / 2, size.height * 0.35f),
                                                radius = size.minDimension * 0.8f * glowScale
                                        ),
                                radius = size.maxDimension
                        )
                }

                Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Hero glyph container with dramatic effects
                        Box(contentAlignment = Alignment.Center) {
                                // Outer rotating energy ring
                                Box(
                                        modifier =
                                                Modifier.size((200 * glowScale).dp)
                                                        .alpha(glowAlpha * 0.4f)
                                                        .graphicsLayer(rotationZ = haloRotation)
                                                        .border(
                                                                1.dp,
                                                                Brush.sweepGradient(
                                                                        listOf(
                                                                                NothingRed.copy(
                                                                                        alpha = 0.6f
                                                                                ),
                                                                                Color.Transparent,
                                                                                NothingRed.copy(
                                                                                        alpha = 0.3f
                                                                                ),
                                                                                Color.Transparent,
                                                                                NothingRed.copy(
                                                                                        alpha = 0.6f
                                                                                )
                                                                        )
                                                                ),
                                                                RoundedCornerShape(50)
                                                        )
                                )

                                // Middle pulsing ring
                                Box(
                                        modifier =
                                                Modifier.size((180 * glowScale).dp)
                                                        .alpha(glowAlpha * 0.3f)
                                                        .border(
                                                                2.dp,
                                                                NothingRed.copy(alpha = 0.2f),
                                                                RoundedCornerShape(50)
                                                        )
                                )

                                // Glow backdrop
                                Box(
                                        modifier =
                                                Modifier.size((170 * glowScale).dp)
                                                        .background(
                                                                Brush.radialGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        NothingRed
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                glowAlpha *
                                                                                                                        0.3f
                                                                                                ),
                                                                                        NothingRed
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                glowAlpha *
                                                                                                                        0.1f
                                                                                                ),
                                                                                        Color.Transparent
                                                                                )
                                                                ),
                                                                RoundedCornerShape(50)
                                                        )
                                )

                                // Main glyph container
                                Box(
                                        modifier =
                                                Modifier.size(170.dp)
                                                        .background(
                                                                Brush.radialGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        NothingCharcoal
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.8f
                                                                                                ),
                                                                                        NothingBlack
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.9f
                                                                                                )
                                                                                )
                                                                ),
                                                                RoundedCornerShape(24.dp)
                                                        )
                                                        .border(
                                                                1.dp,
                                                                Brush.verticalGradient(
                                                                        colors =
                                                                                listOf(
                                                                                        NothingGray700,
                                                                                        NothingGray900
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                )
                                                                ),
                                                                RoundedCornerShape(24.dp)
                                                        )
                                                        .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                ) {
                                        OnboardingGlyphCanvas(
                                                bitmap = bitmap,
                                                modifier = Modifier.fillMaxSize()
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // Page number with enhanced styling
                        Box(
                                modifier =
                                        Modifier.background(
                                                        NothingRed.copy(alpha = 0.15f),
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                                Text(
                                        text = "0${pageIndex + 1}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = NothingRed,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Animated accent line
                        Box(
                                modifier =
                                        Modifier.width(60.dp)
                                                .height(3.dp)
                                                .background(
                                                        Brush.horizontalGradient(
                                                                colors =
                                                                        listOf(
                                                                                Color.Transparent,
                                                                                NothingRed,
                                                                                Color.Transparent
                                                                        )
                                                        ),
                                                        RoundedCornerShape(2.dp)
                                                )
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Title with enhanced styling
                        Text(
                                text = page.title,
                                style = MaterialTheme.typography.displaySmall,
                                color = NothingWhite,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Subtitle
                        Text(
                                text = page.subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = NothingRed.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Description with better spacing
                        Text(
                                text = page.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = NothingGray300,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp
                        )
                }
        }
}

/**
 * Canvas that renders the LED matrix simulation for onboarding. Shows the glyph animation with a
 * clean, minimal appearance.
 */
@Composable
private fun OnboardingGlyphCanvas(bitmap: Bitmap, modifier: Modifier = Modifier) {
        Canvas(modifier = modifier) {
                val matrixSize = GlyphMatrixHelper.MATRIX_SIZE
                val cellSize = size.minDimension / matrixSize
                val ledRadius = cellSize * 0.38f
                val startX = (size.width - matrixSize * cellSize) / 2
                val startY = (size.height - matrixSize * cellSize) / 2

                // Draw background grid (very dim)
                for (y in 0 until matrixSize) {
                        for (x in 0 until matrixSize) {
                                val cx = startX + x * cellSize + cellSize / 2
                                val cy = startY + y * cellSize + cellSize / 2

                                drawCircle(
                                        color = NothingGray900,
                                        radius = ledRadius * 0.6f,
                                        center = Offset(cx, cy)
                                )
                        }
                }

                // Draw lit LEDs from bitmap
                for (y in 0 until matrixSize) {
                        for (x in 0 until matrixSize) {
                                val pixel = bitmap.getPixel(x, y)
                                val brightness = (pixel shr 16) and 0xFF

                                if (brightness > 15) {
                                        val cx = startX + x * cellSize + cellSize / 2
                                        val cy = startY + y * cellSize + cellSize / 2
                                        val alpha = brightness / 255f

                                        // Glow effect for bright pixels
                                        if (brightness > 120) {
                                                drawCircle(
                                                        color =
                                                                NothingWhite.copy(
                                                                        alpha = alpha * 0.25f
                                                                ),
                                                        radius = ledRadius * 2f,
                                                        center = Offset(cx, cy)
                                                )
                                        }

                                        // Main LED - white with brightness
                                        drawCircle(
                                                color = NothingWhite.copy(alpha = alpha),
                                                radius = ledRadius,
                                                center = Offset(cx, cy)
                                        )
                                }
                        }
                }
        }
}

@Composable
private fun PageIndicator(isSelected: Boolean) {
        val width by
                animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec =
                                spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                ),
                        label = "indicator_width"
                )

        val color by
                animateColorAsState(
                        targetValue = if (isSelected) NothingRed else NothingGray800,
                        animationSpec = tween(300),
                        label = "indicator_color"
                )

        Box(modifier = Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
}
