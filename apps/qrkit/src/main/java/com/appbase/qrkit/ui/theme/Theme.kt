/**
 * [INPUT]: 依赖 :core:ui 的 Palette / BrandStyle / BrandTheme / CoreTheme
 * [OUTPUT]: 对外提供 AppPalette、AppStyle、AppTypography、installAppTheme、AppTheme
 * [POS]: ui/theme 的配色与风格真相源：配色「薰衣草 · 浅色」+ 风格「扁平 Material」；所有页面只从 token 取色，不写字面量颜色
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.appbase.core.ui.theme.BrandStyle
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.ButtonStyle
import com.appbase.core.ui.theme.CoreTheme
import com.appbase.core.ui.theme.Palette
import com.appbase.core.ui.theme.SurfaceStyle

val AppPalette = Palette(
    isDark = false,
    bg = Color(0xFFF7F6FD),
    textPrimary = Color(0xFF1C1A33),
    textSoft = Color(0xFF3B3860),
    textSecondary = Color(0xFF66638A),
    textDim = Color(0xFF9896B5),
    primary = Color(0xFF6C5CE7),
    secondary = Color(0xFFFF7EB6),
    danger = Color(0xFFE5484D),
    onAccent = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    line = Color(0x1A1C1A33),
    dialog = Color(0xFFFFFFFF),
    accentGradient = Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), Color(0xFFA78BFA))),
    progressGradient = Brush.horizontalGradient(listOf(Color(0xFFFF7EB6), Color(0xFF6C5CE7))),
)

val AppStyle = BrandStyle(
    cornerScale = 0.8f,
    surface = SurfaceStyle.Flat,
    button = ButtonStyle.Solid,
    pillButton = false,
    uppercaseButton = false,
    circleBadge = true,
)

/** 系统字体族（不打包字体文件）；要换自定义字体时把文件放进 res/font 并在这里改 */
private val AppFont: FontFamily = FontFamily.Default

val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.f() = copy(fontFamily = AppFont)
    Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

/** 主按钮文字 */
private val ButtonText = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)

/** Application.onCreate 里最先调用 */
fun installAppTheme() = BrandTheme.install(AppPalette, typography = { AppTypography }, buttonText = { ButtonText }, style = AppStyle)

@Composable
fun AppTheme(content: @Composable () -> Unit) = CoreTheme(content)
