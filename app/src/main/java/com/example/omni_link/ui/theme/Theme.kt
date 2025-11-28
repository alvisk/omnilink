package com.example.omni_link.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════════════════
// OMNI BLACK & RED THEME - High Contrast Blocky Design
// ═══════════════════════════════════════════════════════════════════════════

// Primary Colors
val OmniRed = Color(0xFFFF0000) // Pure red - primary accent
val OmniRedBright = Color(0xFFFF3333) // Brighter red for highlights
val OmniRedDark = Color(0xFFCC0000) // Darker red for pressed states
val OmniRedMuted = Color(0xFF990000) // Muted red for subtle elements

// Blacks & Grays - Stark contrast
val OmniBlack = Color(0xFF000000) // Pure black background
val OmniBlackSoft = Color(0xFF0D0D0D) // Slightly softer black
val OmniGrayDark = Color(0xFF1A1A1A) // Dark gray for cards
val OmniGrayMid = Color(0xFF2A2A2A) // Mid gray for borders
val OmniGrayLight = Color(0xFF444444) // Light gray for disabled
val OmniGrayText = Color(0xFF888888) // Gray for secondary text
val OmniWhite = Color(0xFFFFFFFF) // Pure white text

// Status Colors
val OmniGreen = Color(0xFF00FF00) // Terminal green for success
val OmniYellow = Color(0xFFFFFF00) // Bright yellow for warnings

// Message Colors
val UserMessageBg = OmniGrayDark
val AssistantMessageBg = OmniBlackSoft
val UserMessageBgLight = Color(0xFFE0E0E0)
val AssistantMessageBgLight = Color(0xFFF5F5F5)

private val DarkColorScheme =
        darkColorScheme(
                primary = OmniRed,
                onPrimary = OmniWhite,
                primaryContainer = OmniRedDark,
                onPrimaryContainer = OmniWhite,
                secondary = OmniGrayText,
                onSecondary = OmniWhite,
                secondaryContainer = OmniGrayDark,
                onSecondaryContainer = OmniWhite,
                tertiary = OmniRed,
                onTertiary = OmniWhite,
                background = OmniBlack,
                onBackground = OmniWhite,
                surface = OmniBlack,
                onSurface = OmniWhite,
                surfaceVariant = OmniGrayDark,
                onSurfaceVariant = OmniGrayText,
                outline = OmniGrayMid,
                outlineVariant = OmniGrayDark,
                error = OmniRed,
                onError = OmniWhite,
                errorContainer = OmniRedDark,
                onErrorContainer = OmniWhite
        )

private val LightColorScheme =
        lightColorScheme(
                primary = OmniRedDark,
                onPrimary = OmniWhite,
                primaryContainer = Color(0xFFFFE0E0),
                onPrimaryContainer = OmniRedDark,
                secondary = OmniGrayLight,
                onSecondary = OmniWhite,
                secondaryContainer = Color(0xFFE0E0E0),
                onSecondaryContainer = OmniBlack,
                tertiary = OmniRed,
                onTertiary = OmniWhite,
                background = OmniWhite,
                onBackground = OmniBlack,
                surface = OmniWhite,
                onSurface = OmniBlack,
                surfaceVariant = Color(0xFFF0F0F0),
                onSurfaceVariant = OmniGrayLight,
                outline = Color(0xFFCCCCCC),
                outlineVariant = Color(0xFFE0E0E0),
                error = OmniRed,
                onError = OmniWhite,
                errorContainer = Color(0xFFFFE0E0),
                onErrorContainer = OmniRedDark
        )

@Composable
fun OmniLinkTheme(
        darkTheme: Boolean = true, // Default to dark theme for black/red aesthetic
        dynamicColor: Boolean = false,
        content: @Composable () -> Unit
) {
    val colorScheme =
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = OmniBlack.toArgb()
            window.navigationBarColor = OmniBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = NothingTypography, content = content)
}
