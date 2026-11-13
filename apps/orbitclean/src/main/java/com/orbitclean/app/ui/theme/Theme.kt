/**
 * [INPUT]: 依赖 core/ui/theme 的 Palette / BrandTheme / CoreTheme 与公共 token，依赖 ./Type.kt 的 AppTypography / OrbitText
 * [OUTPUT]: 对外提供 OrbitPalette、installOrbitTheme、AppTheme，以及 Orbit Clean 原型独有的 token（玻璃面板、微光渐变、强调色淡底）
 * [POS]: ui/theme 的 Orbit Clean 配色真相源：深空单主题，土星金（Secondary）+ 品牌青（Primary）两个强调色；
 *        公共颜色经 BrandTheme 注册给 core，所有页面只从 token 取色，不写字面量颜色
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.CoreTheme
import com.appbase.core.ui.theme.Palette
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary

private val SaturnGold = Color(0xFFE9CF9C)
private val BrandTeal = Color(0xFF56E1C2)

/** 深空底 + 三档冷灰文字；土星金（暖）+ 品牌青（冷，可操作的部分）；红色只用于不可撤销的警告 */
val OrbitPalette = Palette(
    isDark = true,
    bg = Color(0xFF04060B),
    textPrimary = Color(0xFFEEF2F7),
    textSoft = Color(0xFFC7D0DC),
    textSecondary = Color(0xFF98A3B4),
    textDim = Color(0xFF66728A),
    primary = BrandTeal,
    secondary = SaturnGold,
    danger = Color(0xFFFF7A7A),
    onAccent = Color(0xFF08151A),
    card = Color(0x0DFFFFFF),
    line = Color(0x1AFFFFFF),
    dialog = Color(0xFF121824),
    // 100deg 的金→薄荷→青，停点与原型 --cta 一致
    accentGradient = Brush.horizontalGradient(0f to Color(0xFFF2DDB0), 0.58f to Color(0xFFA9EBD6), 1f to BrandTeal),
    progressGradient = Brush.horizontalGradient(listOf(SaturnGold, BrandTeal)),
)

/** Application.onCreate 里最先调用：注册 Orbit Clean 的颜色和字体 */
fun installOrbitTheme() = BrandTheme.install(OrbitPalette, typography = { AppTypography }, buttonText = { OrbitText.Button })

/* ------------------------------------------------------------------ *
 *  Orbit Clean 原型独有的面与渐变（公共代码不使用）
 * ------------------------------------------------------------------ */
/** 玻璃按钮（.icon-btn）比卡片略亮一档 */
val GlassButtonBg = Color(0x0FFFFFFF)
/** 底部面板：有背景模糊时用半透明（.sheet），无模糊时用实色（.fx-low） */
val SheetGlass = Color(0x940C1018)
val SheetBg = Color(0xFF0B1220)
val SecondarySoft = Color(0x29E9CF9C)
val PrimarySoft = Color(0x2456E1C2)

/** 工具卡片 160deg 的微光渐变 */
val TileGradient = Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x05FFFFFF)))
val NumberGradient: Brush get() = Brush.horizontalGradient(0f to Color(0xFFFFF3DC), 0.42f to Secondary, 1f to Primary)

@Composable
fun AppTheme(content: @Composable () -> Unit) = CoreTheme(content)
