package com.example.omni_link.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_link.glyph.GlyphMatrixHelper
import com.example.omni_link.glyph.GlyphType
import com.example.omni_link.ui.theme.*

/**
 * Glyph settings screen for selecting and previewing LED matrix animations. Shows live animated
 * previews of each glyph type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphSettingsScreen(
        currentGlyphType: GlyphType,
        onGlyphSelected: (GlyphType) -> Unit,
        onBackClick: () -> Unit
) {
        Scaffold(
                topBar = {
                        Surface(color = OmniBlack) {
                                Column {
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
                                                // Back button
                                                Box(
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                OmniGrayDark,
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        )
                                                                        .border(
                                                                                2.dp,
                                                                                OmniGrayMid,
                                                                                RoundedCornerShape(
                                                                                        10.dp
                                                                                )
                                                                        ),
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

                                                // Title
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                text = "GLYPH",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .headlineSmall,
                                                                color = OmniWhite,
                                                                letterSpacing = 6.sp
                                                        )
                                                        Text(
                                                                text = "LED MATRIX DISPLAY",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color = OmniGrayText,
                                                                letterSpacing = 2.sp
                                                        )
                                                }
                                        }

                                        // Divider
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(2.dp)
                                                                .background(OmniGrayMid)
                                        )
                                }
                        }
                },
                containerColor = OmniBlack
        ) { paddingValues ->
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                        // Info box
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(OmniGrayDark, RoundedCornerShape(12.dp))
                                                .border(
                                                        1.dp,
                                                        OmniGrayMid,
                                                        RoundedCornerShape(12.dp)
                                                )
                                                .padding(12.dp)
                        ) {
                                Column {
                                        Text(
                                                text = "SELECT ANIMATION",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = NothingRed,
                                                letterSpacing = 2.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                                text =
                                                        "Choose the animation to display on your Nothing Phone's LED matrix when the service is active.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = OmniGrayText
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Glyph grid
                        LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                        ) {
                                items(GlyphType.entries.toList()) { glyphType ->
                                        GlyphPreviewCard(
                                                glyphType = glyphType,
                                                isSelected = glyphType == currentGlyphType,
                                                onSelect = { onGlyphSelected(glyphType) }
                                        )
                                }
                        }
                }
        }
}

/** Card showing an animated preview of a glyph type. */
@Composable
private fun GlyphPreviewCard(glyphType: GlyphType, isSelected: Boolean, onSelect: () -> Unit) {
        // Animation frame counter
        val infiniteTransition = rememberInfiniteTransition(label = "glyph_animation")
        val frame by
                infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 240f, // Multiple cycles
                        animationSpec =
                                infiniteRepeatable(
                                        animation =
                                                tween(durationMillis = 9600, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                ),
                        label = "frame"
                )

        // Generate bitmap for current frame
        val bitmap =
                remember(glyphType, frame.toInt()) {
                        GlyphMatrixHelper.generatePreviewBitmap(glyphType, frame.toInt())
                }

        val borderColor = if (isSelected) NothingRed else OmniGrayMid
        val bgColor = if (isSelected) NothingRedDim.copy(alpha = 0.15f) else OmniGrayDark

        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .aspectRatio(0.85f)
                                .background(bgColor, RoundedCornerShape(16.dp))
                                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                                .clickable { onSelect() }
        ) {
                Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        // Preview area
                        Box(
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                                contentAlignment = Alignment.Center
                        ) {
                                // LED matrix simulation
                                GlyphMatrixCanvas(
                                        bitmap = bitmap,
                                        modifier = Modifier.aspectRatio(1f).fillMaxSize(0.9f)
                                )
                        }

                        // Label area
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(
                                                        if (isSelected)
                                                                NothingRedDim.copy(alpha = 0.3f)
                                                        else OmniBlackSoft
                                                )
                                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        text = glyphType.displayName,
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        color =
                                                                if (isSelected) NothingRed
                                                                else OmniWhite,
                                                        fontWeight =
                                                                if (isSelected) FontWeight.Bold
                                                                else FontWeight.Medium
                                                )
                                                Text(
                                                        text = glyphType.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = OmniGrayText,
                                                        fontSize = 10.sp
                                                )
                                        }

                                        if (isSelected) {
                                                Box(
                                                        modifier =
                                                                Modifier.size(24.dp)
                                                                        .background(
                                                                                NothingRed,
                                                                                RoundedCornerShape(
                                                                                        6.dp
                                                                                )
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = OmniWhite,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }
}

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
