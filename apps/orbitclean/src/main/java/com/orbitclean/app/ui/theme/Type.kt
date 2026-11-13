/**
 * [INPUT]: 依赖 res/font 的 unbounded_variable / sora_variable（可变字重，已裁成拉丁字符集），依赖 ./Theme 的颜色
 * [OUTPUT]: 对外提供 Display/Body 字体族、AppTypography、OrbitText 文字样式
 * [POS]: ui/theme 的字体真相源；OrbitText 的每一档都与 HTML 原型的 CSS 一一对应（字号、字重、行高、字距），
 *        行高按 CSS 方式居中、不加字体内边距，排出来与浏览器一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.theme

import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextDim
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.core.ui.theme.TextSoft

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Display = FontFamily(
    listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { variable(com.orbitclean.app.R.font.unbounded_variable, it) },
)

val Body = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { variable(com.orbitclean.app.R.font.sora_variable, it) },
)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Body),
    displayMedium = base.displayMedium.copy(fontFamily = Body),
    displaySmall = base.displaySmall.copy(fontFamily = Body),
    headlineLarge = base.headlineLarge.copy(fontFamily = Body),
    headlineMedium = base.headlineMedium.copy(fontFamily = Body),
    headlineSmall = base.headlineSmall.copy(fontFamily = Body),
    titleLarge = base.titleLarge.copy(fontFamily = Body),
    titleMedium = base.titleMedium.copy(fontFamily = Body),
    titleSmall = base.titleSmall.copy(fontFamily = Body),
    bodyLarge = base.bodyLarge.copy(fontFamily = Body),
    bodyMedium = base.bodyMedium.copy(fontFamily = Body),
    bodySmall = base.bodySmall.copy(fontFamily = Body),
    labelLarge = base.labelLarge.copy(fontFamily = Body),
    labelMedium = base.labelMedium.copy(fontFamily = Body),
    labelSmall = base.labelSmall.copy(fontFamily = Body),
)

/* ------------------------------------------------------------------ *
 *  CSS 式排版：line-height 的空白上下均分，不加 Android 字体内边距
 * ------------------------------------------------------------------ */
private val CssLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/** px 字距 → em，原型里字距都写成像素 */
private fun tracking(px: Float, size: Float): TextUnit = (px / size).em

private fun css(
    family: FontFamily,
    weight: FontWeight,
    size: Float,
    lineHeight: Float,
    letterPx: Float = 0f,
    color: Color = Color.Unspecified,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking(letterPx, size),
    color = color,
    lineHeightStyle = CssLine,
    platformStyle = NoPadding,
)

/** 深空界面的全部文字档位；注释里是原型对应的 CSS 选择器 */
object OrbitText {
    /** .kicker */
    val Kicker = css(Body, FontWeight.SemiBold, 11f, 1.2f, 2.4f, Secondary)
    /** .big —— 首页存储占用 */
    val Hero = css(Display, FontWeight.Bold, 84f, 0.9f, -2.5f, TextPrimary)
    /** .big sup */
    val HeroUnit = css(Display, FontWeight.SemiBold, 28f, 1f, 0f, Primary)
    /** .mid —— 扫描/清理/完成的计数 */
    val Value = css(Display, FontWeight.Bold, 46f, 1f, -1.2f)
    /** .mid small */
    val ValueUnit = css(Display, FontWeight.SemiBold, 18f, 1f, 0.5f)
    /** .brand */
    val Brand = css(Display, FontWeight.Bold, 14f, 1.1f, 2f, TextPrimary)
    /** .brand small */
    val BrandSub = css(Body, FontWeight.Normal, 11f, 1.2f, 0.2f, TextSecondary)
    /** .top-title */
    val Title = css(Body, FontWeight.SemiBold, 13f, 1.2f, 2.4f, TextSoft)
    /** .cta */
    val Cta = css(Body, FontWeight.SemiBold, 15f, 1.15f, 1.6f, OnAccent)
    /** .cta-label small */
    val CtaSub = css(Body, FontWeight.Normal, 11.5f, 1.2f, 0.2f, OnAccent.copy(alpha = 0.7f))
    /** .sub */
    val Sub = css(Body, FontWeight.Normal, 13f, 1.4f, 0f, TextSecondary)
    /** .path */
    val Mono = css(FontFamily.Monospace, FontWeight.Normal, 11f, 1.4f, 0f, TextDim)
    /** .row b */
    val RowTitle = css(Body, FontWeight.SemiBold, 14f, 1.3f, 0f, TextPrimary)
    /** .row em */
    val RowSub = css(Body, FontWeight.Normal, 12f, 1.3f, 0f, TextSecondary)
    /** .row .val */
    val RowValue = css(Body, FontWeight.SemiBold, 14f, 1.2f, 0f, TextPrimary)
    /** .btn */
    val Button = css(Body, FontWeight.SemiBold, 14f, 1.2f, 1.8f)
    /** .tool .tl —— Android 上 Sora 的字形比 Chrome 略宽，字距收 0.2px 才放得下 "Redundant" */
    val ToolLabel = css(Body, FontWeight.Medium, 12f, 1.2f, -0.2f, TextSoft)
    /** .nav button */
    val Nav = css(Body, FontWeight.SemiBold, 10f, 1.2f, 1.6f)
    /** 面板里的小号说明（.store-top / 清理页进度行） */
    val Small = css(Body, FontWeight.Normal, 12.5f, 1.3f, 0f, TextSecondary)
    val Hint = css(Body, FontWeight.Normal, 12f, 1.3f, 0f, TextDim)
    /** .check */
    val Check = css(Body, FontWeight.SemiBold, 12f, 1.2f, 0f, Primary)

    /** 旧页面沿用的样式名 */
    val Caption = Sub
}
