/**
 * [INPUT]: 依赖 :core:ui 的 Palette / BrandStyle / BrandTheme / CoreTheme
 * [OUTPUT]: 对外提供 AppPalette、AppStyle、AppTypography、installAppTheme、AppTheme
 * [POS]: ui/theme 的配色与风格真相源：配色「海盐蓝 · 浅色」+ 风格「锐利科技」；所有页面只从 token 取色，不写字面量颜色
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.theme

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
    bg = Color(0xFFF6F8FB),
    textPrimary = Color(0xFF0F1B2D),
    textSoft = Color(0xFF31435B),
    textSecondary = Color(0xFF5B6B80),
    textDim = Color(0xFF8A97A8),
    primary = Color(0xFF1E6FFF),
    secondary = Color(0xFF00B3A4),
    danger = Color(0xFFE5484D),
    onAccent = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    line = Color(0x1A0F1B2D),
    dialog = Color(0xFFFFFFFF),
    accentGradient = Brush.horizontalGradient(listOf(Color(0xFF1E6FFF), Color(0xFF00B3A4))),
    progressGradient = Brush.horizontalGradient(listOf(Color(0xFF00B3A4), Color(0xFF1E6FFF))),
)

val AppStyle = BrandStyle(
    cornerScale = 0.2f,
    surface = SurfaceStyle.Outlined,
    button = ButtonStyle.Solid,
    pillButton = false,
    uppercaseButton = true,
    circleBadge = false,
)

/** 系统字体族（不打包字体文件）；要换自定义字体时把文件放进 res/font 并在这里改 */
private val AppFont: FontFamily = FontFamily.Monospace

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
private val ButtonText = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)

/** Application.onCreate 里最先调用 */
fun installAppTheme() = BrandTheme.install(AppPalette, typography = { AppTypography }, buttonText = { ButtonText }, style = AppStyle)

@Composable
fun AppTheme(content: @Composable () -> Unit) = CoreTheme(content)
