package com.example.omni_link.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.omni_link.R

// Nothing Font Families
// NDot55 - The iconic dot-matrix display font (for headlines/display)
val Ndot55 = FontFamily(Font(R.font.ndot55_regular, FontWeight.Normal))

// NDot57 - Slightly different variant (for titles/subtitles)
val Ndot57 = FontFamily(Font(R.font.ndot57_regular, FontWeight.Normal))

// NType82 - Clean geometric sans for body text
val NType82 =
        FontFamily(
                Font(R.font.ntype82_regular, FontWeight.Normal),
                Font(R.font.ntype82_headline, FontWeight.Bold)
        )

// Nothing Typography - Minimalist, geometric, high contrast
val NothingTypography =
        Typography(
                // Display styles - NDot55 for maximum impact
                displayLarge =
                        TextStyle(
                                fontFamily = Ndot55,
                                fontWeight = FontWeight.Normal,
                                fontSize = 57.sp,
                                lineHeight = 64.sp,
                                letterSpacing = (-0.25).sp
                        ),
                displayMedium =
                        TextStyle(
                                fontFamily = Ndot55,
                                fontWeight = FontWeight.Normal,
                                fontSize = 45.sp,
                                lineHeight = 52.sp,
                                letterSpacing = 0.sp
                        ),
                displaySmall =
                        TextStyle(
                                fontFamily = Ndot55,
                                fontWeight = FontWeight.Normal,
                                fontSize = 36.sp,
                                lineHeight = 44.sp,
                                letterSpacing = 0.sp
                        ),

                // Headline styles - NDot57 for section headers
                headlineLarge =
                        TextStyle(
                                fontFamily = Ndot57,
                                fontWeight = FontWeight.Normal,
                                fontSize = 32.sp,
                                lineHeight = 40.sp,
                                letterSpacing = 0.sp
                        ),
                headlineMedium =
                        TextStyle(
                                fontFamily = Ndot57,
                                fontWeight = FontWeight.Normal,
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                letterSpacing = 0.sp
                        ),
                headlineSmall =
                        TextStyle(
                                fontFamily = Ndot57,
                                fontWeight = FontWeight.Normal,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                letterSpacing = 0.sp
                        ),

                // Title styles - NType82 for clean readability
                titleLarge =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                letterSpacing = 0.sp
                        ),
                titleMedium =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.15.sp
                        ),
                titleSmall =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.1.sp
                        ),

                // Body styles - NType82 for comfortable reading
                bodyLarge =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.5.sp
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.25.sp
                        ),
                bodySmall =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.4.sp
                        ),

                // Label styles - NType82 for UI elements
                labelLarge =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.1.sp
                        ),
                labelMedium =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.5.sp
                        ),
                labelSmall =
                        TextStyle(
                                fontFamily = NType82,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.5.sp
                        )
        )
