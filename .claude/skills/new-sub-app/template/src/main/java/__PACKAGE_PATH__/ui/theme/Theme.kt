/**
 * [INPUT]: 依赖 :core:ui 的 Palette / BrandStyle / BrandTheme / CoreTheme
 * [OUTPUT]: 对外提供 AppPalette、AppStyle、AppTypography、installAppTheme、AppTheme
 * [POS]: ui/theme 的配色与风格真相源：配色「{{PALETTE_NAME}}」+ 风格「{{STYLE_NAME}}」；所有页面只从 token 取色，不写字面量颜色
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.theme

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
    isDark = {{IS_DARK}},
    bg = Color({{BG}}),
    textPrimary = Color({{TEXT_PRIMARY}}),
    textSoft = Color({{TEXT_SOFT}}),
    textSecondary = Color({{TEXT_SECONDARY}}),
    textDim = Color({{TEXT_DIM}}),
    primary = Color({{PRIMARY}}),
    secondary = Color({{SECONDARY}}),
    danger = Color({{DANGER}}),
    onAccent = Color({{ON_ACCENT}}),
    card = Color({{CARD}}),
    line = Color({{LINE}}),
    dialog = Color({{DIALOG}}),
    accentGradient = Brush.horizontalGradient(listOf(Color({{GRADIENT_START}}), Color({{GRADIENT_END}}))),
    progressGradient = Brush.horizontalGradient(listOf(Color({{SECONDARY}}), Color({{PRIMARY}}))),
)

val AppStyle = BrandStyle(
    cornerScale = {{CORNER_SCALE}}f,
    surface = SurfaceStyle.{{SURFACE}},
    button = ButtonStyle.{{BUTTON}},
    pillButton = {{PILL_BUTTON}},
    uppercaseButton = {{UPPERCASE_BUTTON}},
    circleBadge = {{CIRCLE_BADGE}},
)

/** 系统字体族（不打包字体文件）；要换自定义字体时把文件放进 res/font 并在这里改 */
private val AppFont: FontFamily = FontFamily.{{FONT_FAMILY}}

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
private val ButtonText = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, letterSpacing = {{BUTTON_TRACKING}}.sp)

/** Application.onCreate 里最先调用 */
fun installAppTheme() = BrandTheme.install(AppPalette, typography = { AppTypography }, buttonText = { ButtonText }, style = AppStyle)

@Composable
fun AppTheme(content: @Composable () -> Unit) = CoreTheme(content)
