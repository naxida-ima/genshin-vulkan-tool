package com.example.genshinvulkan.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 纳西妲主题 · 智慧与自然 ──

// 主色 — 深翠绿
val Primary = Color(0xFF2E7D32)
val PrimaryVariant = Color(0xFF1B5E20)
val PrimaryLight = Color(0xFF4CAF50)
val PrimaryMuted = Color(0xFF81C784)

// 强调色 — 金色（神之眼）
val Accent = Color(0xFFFFB300)
val AccentLight = Color(0xFFFFD54F)

// 表面色
val SurfaceDark = Color(0xFF1C2833)
val SurfaceMedium = Color(0xFFF5F9F5)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceCard = Color(0xFFFAFDFA)

// 背景渐变端点
val BgGradientStart = Color(0xFFE8F5E9)
val BgGradientEnd = Color(0xFFC8E6C9)
val BgGradientDarkStart = Color(0xFF0D1512)
val BgGradientDarkEnd = Color(0xFF1A2A22)

// 文本
val TextPrimary = Color(0xFF1C2833)
val TextSecondary = Color(0xFF5D6D7E)
val TextOnDark = Color(0xFFE8F5E9)

// 状态色
val StatusOn = Color(0xFF2E7D32)
val StatusOff = Color(0xFF95A5A6)
val SuccessGreen = Color(0xFF27AE60)
val WarningOrange = Color(0xFFE67E22)
val DangerRed = Color(0xFFE74C3C)

// Vulkan 橙红色
val VulkanRed = Color(0xFFC62828)
val VulkanRedLight = Color(0xFFEF5350)
val OpenGLBlue = Color(0xFF1565C0)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = PrimaryVariant,
    secondary = PrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = PrimaryVariant,
    tertiary = Accent,
    onTertiary = Color(0xFF3E2723),
    background = Color(0xFFF1F8E9),
    onBackground = TextPrimary,
    surface = SurfaceLight,
    surfaceTint = PrimaryMuted,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMedium,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = Color.White,
    outline = PrimaryMuted.copy(alpha = 0.5f)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFF0D2600),
    primaryContainer = PrimaryVariant,
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = PrimaryMuted,
    onSecondary = Color(0xFF0D2600),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFE8F5E9),
    tertiary = AccentLight,
    onTertiary = Color(0xFF3E2723),
    background = Color(0xFF0D1512),
    onBackground = TextOnDark,
    surface = Color(0xFF171F1B),
    surfaceTint = PrimaryMuted,
    onSurface = TextOnDark,
    surfaceVariant = Color(0xFF26322A),
    onSurfaceVariant = Color(0xFFA0B0A4),
    error = DangerRed,
    onError = Color(0xFF2D0600),
    outline = PrimaryMuted.copy(alpha = 0.4f)
)

@Composable
fun GenshinVulkanTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
