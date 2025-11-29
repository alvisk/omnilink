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
// NOTHING MONOCHROME THEME - Minimalist Black & White + Red Accent
// ═══════════════════════════════════════════════════════════════════════════

// The Nothing red - the ONLY accent color
val NothingRed = Color(0xFFD71921) // Nothing's signature red
val NothingRedDim = Color(0xFF8B1016) // Darker red for subtle states
val NothingRedGlow = Color(0xFFFF2D36) // Brighter red for emphasis

// Pure monochrome scale - strict grayscale
val NothingBlack = Color(0xFF000000) // True black - primary background
val NothingDark = Color(0xFF0A0A0A) // Near-black for layering
val NothingCharcoal = Color(0xFF141414) // Card backgrounds
val NothingGray900 = Color(0xFF1C1C1C) // Elevated surfaces
val NothingGray800 = Color(0xFF262626) // Borders, dividers
val NothingGray700 = Color(0xFF333333) // Subtle separators
val NothingGray600 = Color(0xFF4D4D4D) // Disabled states
val NothingGray500 = Color(0xFF666666) // Secondary text
val NothingGray400 = Color(0xFF808080) // Placeholder text
val NothingGray300 = Color(0xFF999999) // Tertiary text
val NothingGray200 = Color(0xFFB3B3B3) // Hint text
val NothingGray100 = Color(0xFFCCCCCC) // Light accents
val NothingWhite = Color(0xFFFFFFFF) // Primary text, icons

// Semantic aliases for consistency (NOMM = Nothing On My Mind)
val NOMMRed = NothingRed
val NOMMRedBright = NothingRedGlow
val NOMMRedDark = NothingRedDim
val NOMMRedMuted = NothingRedDim
val NOMMBlack = NothingBlack
val NOMMBlackSoft = NothingDark
val NOMMGrayDark = NothingCharcoal
val NOMMGrayMid = NothingGray800
val NOMMGrayLight = NothingGray600
val NOMMGrayText = NothingGray400
val NOMMWhite = NothingWhite

// Legacy aliases for backward compatibility
@Deprecated("Use NOMMRed", ReplaceWith("NOMMRed")) val OmniRed = NothingRed
@Deprecated("Use NOMMRedBright", ReplaceWith("NOMMRedBright")) val OmniRedBright = NothingRedGlow
@Deprecated("Use NOMMRedDark", ReplaceWith("NOMMRedDark")) val OmniRedDark = NothingRedDim
@Deprecated("Use NOMMRedMuted", ReplaceWith("NOMMRedMuted")) val OmniRedMuted = NothingRedDim
@Deprecated("Use NOMMBlack", ReplaceWith("NOMMBlack")) val OmniBlack = NothingBlack
@Deprecated("Use NOMMBlackSoft", ReplaceWith("NOMMBlackSoft")) val OmniBlackSoft = NothingDark
@Deprecated("Use NOMMGrayDark", ReplaceWith("NOMMGrayDark")) val OmniGrayDark = NothingCharcoal
@Deprecated("Use NOMMGrayMid", ReplaceWith("NOMMGrayMid")) val OmniGrayMid = NothingGray800
@Deprecated("Use NOMMGrayLight", ReplaceWith("NOMMGrayLight")) val OmniGrayLight = NothingGray600
@Deprecated("Use NOMMGrayText", ReplaceWith("NOMMGrayText")) val OmniGrayText = NothingGray400
@Deprecated("Use NOMMWhite", ReplaceWith("NOMMWhite")) val OmniWhite = NothingWhite

// Deprecated colors - mapped to monochrome for compatibility
// These should be phased out in favor of red-only accents
@Deprecated("Use NothingRed for accents", ReplaceWith("NothingRed")) val OmniGreen = NothingGray300
@Deprecated("Use NothingRed for accents", ReplaceWith("NothingRed")) val OmniYellow = NothingGray200
@Deprecated("Use NothingRed for accents", ReplaceWith("NothingRed")) val OmniCyan = NothingGray300
@Deprecated("Use NothingRed for accents", ReplaceWith("NothingRed")) val OmniBlue = NothingGray400
@Deprecated("Use NothingRed for accents", ReplaceWith("NothingRed")) val OmniOrange = NothingGray300
@Deprecated("Use NothingRed for accents", ReplaceWith("NothingRed")) val OmniPurple = NothingGray400

// Message Colors - monochrome
val UserMessageBg = NothingGray900
val AssistantMessageBg = NothingCharcoal
val UserMessageBgLight = Color(0xFFE8E8E8)
val AssistantMessageBgLight = Color(0xFFF5F5F5)

private val DarkColorScheme =
        darkColorScheme(
                primary = NothingRed,
                onPrimary = NothingWhite,
                primaryContainer = NothingRedDim,
                onPrimaryContainer = NothingWhite,
                secondary = NothingGray500,
                onSecondary = NothingWhite,
                secondaryContainer = NothingGray900,
                onSecondaryContainer = NothingWhite,
                tertiary = NothingRed,
                onTertiary = NothingWhite,
                background = NothingBlack,
                onBackground = NothingWhite,
                surface = NothingBlack,
                onSurface = NothingWhite,
                surfaceVariant = NothingCharcoal,
                onSurfaceVariant = NothingGray400,
                outline = NothingGray800,
                outlineVariant = NothingGray900,
                error = NothingRed,
                onError = NothingWhite,
                errorContainer = NothingRedDim,
                onErrorContainer = NothingWhite
        )

private val LightColorScheme =
        lightColorScheme(
                primary = NothingRed,
                onPrimary = NothingWhite,
                primaryContainer = Color(0xFFFFE5E6),
                onPrimaryContainer = NothingRedDim,
                secondary = NothingGray600,
                onSecondary = NothingWhite,
                secondaryContainer = Color(0xFFE8E8E8),
                onSecondaryContainer = NothingBlack,
                tertiary = NothingRed,
                onTertiary = NothingWhite,
                background = NothingWhite,
                onBackground = NothingBlack,
                surface = NothingWhite,
                onSurface = NothingBlack,
                surfaceVariant = Color(0xFFF5F5F5),
                onSurfaceVariant = NothingGray600,
                outline = Color(0xFFD9D9D9),
                outlineVariant = Color(0xFFE8E8E8),
                error = NothingRed,
                onError = NothingWhite,
                errorContainer = Color(0xFFFFE5E6),
                onErrorContainer = NothingRedDim
        )

@Composable
fun NOMMTheme(
        darkTheme: Boolean = true,
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
                        window.statusBarColor = NothingBlack.toArgb()
                        window.navigationBarColor = NothingBlack.toArgb()
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                                false
                }
        }

        MaterialTheme(colorScheme = colorScheme, typography = NothingTypography, content = content)
}
