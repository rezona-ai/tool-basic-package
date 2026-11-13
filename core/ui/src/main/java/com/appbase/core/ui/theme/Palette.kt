/**
 * [INPUT]: 依赖 compose material3 的 MaterialTheme / darkColorScheme / lightColorScheme
 * [OUTPUT]: 对外提供 Palette、BrandStyle（圆角、卡片、按钮、图标底座的风格参数）、BrandTheme（品牌在 Application.onCreate 里注册）、CoreTheme，以及公共颜色 token（Bg、Primary、Secondary…）
 * [POS]: core/ui 的主题骨架：公共代码只认这里的 token，不含任何品牌色值；具体颜色、字体由品牌提供（见 app 的 ui/theme）。
 *        token 是读取 [BrandTheme.palette] 的属性，非 Composable 场景（Canvas、广告遮罩）也能直接用
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

/** 一个品牌的全部公共颜色。[primary] 是可操作的强调色（按钮、勾选、开关），[secondary] 是辅助强调色 */
class Palette(
    val isDark: Boolean,
    val bg: Color,
    val textPrimary: Color,
    val textSoft: Color,
    val textSecondary: Color,
    val textDim: Color,
    val primary: Color,
    val secondary: Color,
    /** 只用于不可撤销的警告（删除） */
    val danger: Color,
    /** 强调色 / 渐变按钮上的文字与图标 */
    val onAccent: Color,
    val card: Color,
    val line: Color,
    val dialog: Color,
    /** 主按钮背景 */
    val accentGradient: Brush,
    /** 进度条、用量条 */
    val progressGradient: Brush,
)

/** 卡片、列表行这类面的画法 */
enum class SurfaceStyle {
    /** 浅色填充 + 细描边（卡片）/ 只有填充（列表行） */
    Tinted,

    /** 只有描边，没有填充 */
    Outlined,

    /** 填充 + 阴影，没有描边 */
    Elevated,

    /** 只有填充 */
    Flat,
}

/** 主按钮（GradientButton）的画法 */
enum class ButtonStyle {
    /** Palette.accentGradient 渐变底 */
    Gradient,

    /** Palette.primary 纯色底 */
    Solid,

    /** Palette.primary 描边，文字同色 */
    Outlined,
}

/**
 * 公共组件的风格参数；默认值就是 Orbit Clean 现在的样子。
 * 新品牌靠它和 Palette、字体一起做出自己的视觉，组件代码不用改
 */
class BrandStyle(
    /** 圆角倍率：0 直角，0.5 小圆角，1 默认（卡片 18dp / 列表行 16dp / 图标底座 12dp / 缩略图 10dp），1.5 大圆角 */
    val cornerScale: Float = 1f,
    val surface: SurfaceStyle = SurfaceStyle.Tinted,
    val button: ButtonStyle = ButtonStyle.Gradient,
    /** 主按钮是否是胶囊形（圆角 = 高度一半）；否则用卡片圆角 */
    val pillButton: Boolean = true,
    /** 主按钮文字是否全大写 */
    val uppercaseButton: Boolean = true,
    /** 图标底座是圆形还是圆角方块 */
    val circleBadge: Boolean = false,
) {
    /** 按倍率缩放的圆角 */
    fun corner(base: Dp): Dp = base * cornerScale
}

/** 品牌主题注册处：Application.onCreate 里最先调用 [install]，之后所有 token 都可用 */
object BrandTheme {
    lateinit var palette: Palette
        private set
    lateinit var typography: Typography
        private set

    /** 公共按钮（GradientButton）的文字样式；颜色由按钮自己指定 */
    lateinit var buttonText: TextStyle
        private set

    var style: BrandStyle = BrandStyle()
        private set

    /** 字体样式可能依赖颜色 token，所以以函数传入，在颜色就绪后再创建 */
    fun install(palette: Palette, typography: () -> Typography, buttonText: () -> TextStyle, style: BrandStyle = BrandStyle()) {
        this.palette = palette
        this.style = style
        this.typography = typography()
        this.buttonText = buttonText()
    }
}

val Bg: Color get() = BrandTheme.palette.bg
val TextPrimary: Color get() = BrandTheme.palette.textPrimary
val TextSoft: Color get() = BrandTheme.palette.textSoft
val TextSecondary: Color get() = BrandTheme.palette.textSecondary
val TextDim: Color get() = BrandTheme.palette.textDim
val Primary: Color get() = BrandTheme.palette.primary
val Secondary: Color get() = BrandTheme.palette.secondary
val Danger: Color get() = BrandTheme.palette.danger
val OnAccent: Color get() = BrandTheme.palette.onAccent
val CardBg: Color get() = BrandTheme.palette.card
val Line: Color get() = BrandTheme.palette.line
val DialogBg: Color get() = BrandTheme.palette.dialog
val AccentGradient: Brush get() = BrandTheme.palette.accentGradient
val ProgressGradient: Brush get() = BrandTheme.palette.progressGradient

@Composable
fun CoreTheme(content: @Composable () -> Unit) {
    val p = BrandTheme.palette
    val colors = if (p.isDark) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onAccent, secondary = p.secondary, onSecondary = p.onAccent,
            background = p.bg, onBackground = p.textPrimary, surface = p.bg, onSurface = p.textPrimary,
            onSurfaceVariant = p.textSecondary, surfaceContainerHigh = p.dialog, surfaceContainerHighest = p.dialog,
            outline = p.line, outlineVariant = p.line, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onAccent, secondary = p.secondary, onSecondary = p.onAccent,
            background = p.bg, onBackground = p.textPrimary, surface = p.bg, onSurface = p.textPrimary,
            onSurfaceVariant = p.textSecondary, surfaceContainerHigh = p.dialog, surfaceContainerHighest = p.dialog,
            outline = p.line, outlineVariant = p.line, error = p.danger,
        )
    }
    MaterialTheme(colorScheme = colors, typography = BrandTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides p.textPrimary, content = content)
    }
}
