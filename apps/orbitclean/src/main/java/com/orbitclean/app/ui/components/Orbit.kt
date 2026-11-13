/**
 * [INPUT]: 依赖 ui/theme 的颜色/渐变/OrbitText，依赖 ui/saturn 的 LocalSaturn（玻璃面板取舞台画面做背景模糊），依赖 util 的 formatSize
 * [OUTPUT]: 对外提供土星场景页面的积木：OrbitTopBar、GlassIconButton、Kicker、ValueText、heroShadow、GlassSheet、PrimaryCta、
 *           ToolTile、OrbitButton、IconTile、ProgressLine、OrbitRow、RowValue、CheckPill、OrbitNavBar、OrbitGlyph、splitSize、Gutter
 * [POS]: ui/components 的原型组件层：每个组件对应原型一条 CSS 规则，尺寸单位是 DesignFrame 下的设计单位；常规列表卡片仍在 Common.kt
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.common.formatSize
import com.appbase.core.ui.theme.AccentGradient
import com.appbase.core.ui.theme.CardBg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.ProgressGradient
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextDim
import com.appbase.core.ui.theme.TextPrimary
import com.orbitclean.app.ui.saturn.LocalSaturn
import com.orbitclean.app.ui.theme.GlassButtonBg
import com.orbitclean.app.ui.theme.NumberGradient
import com.orbitclean.app.ui.theme.OrbitText
import com.orbitclean.app.ui.theme.PrimarySoft
import com.orbitclean.app.ui.theme.SecondarySoft
import com.orbitclean.app.ui.theme.SheetBg
import com.orbitclean.app.ui.theme.SheetGlass
import com.orbitclean.app.ui.theme.TileGradient
import kotlin.math.tan

/** 原型 --g：所有土星场景页面的左右边距 */
val Gutter = 20.dp

/** "1.9 GB" → ("1.9", "GB")，数字和单位分开排版 */
fun splitSize(bytes: Long): Pair<String, String> {
    val text = formatSize(bytes)
    val i = text.lastIndexOf(' ')
    return if (i < 0) text to "" else text.substring(0, i) to text.substring(i + 1)
}

/** .hero 的 text-shadow: 0 2px 18px rgba(0,0,0,.65) */
@Composable
fun heroShadow(): Shadow {
    val px = LocalDensity.current.density
    return Shadow(Color(0xA6000000), Offset(0f, 2f * px), 18f * px)
}

/* ================================================================== *
 *  顶栏
 * ================================================================== */

/** .icon-btn：44 方块、15 圆角、半透明玻璃 */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(GlassButtonBg)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

/** .top：左返回、居中标题；[top] 是顶栏在设计单位下的 y */
@Composable
fun OrbitTopBar(title: String, top: Float, onBack: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = top.dp, start = Gutter, end = Gutter)
            .height(44.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.align(Alignment.CenterStart)) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            }
        }
        Text(
            title.uppercase(),
            style = OrbitText.Title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
    }
}

/** 品牌标 / 主按钮图标：星球 + 倾斜光环（原型里的内联 SVG） */
@Composable
fun OrbitGlyph(planet: Color, ring: Color, size: Dp, planetR: Float = 6.5f, ringRy: Float = 5f, stroke: Float = 1.8f) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val u = this.size.width / 32f
        drawCircle(planet, radius = planetR * u)
        rotate(-22f) {
            val w = 29f * u
            val h = ringRy * 2 * u
            drawOval(ring, topLeft = Offset((this.size.width - w) / 2, (this.size.height - h) / 2), size = Size(w, h), style = Stroke(stroke * u))
        }
    }
}

/* ================================================================== *
 *  主视觉文字
 * ================================================================== */

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = OrbitText.Kicker.copy(shadow = heroShadow()), modifier = modifier)
}

/** .mid + small：渐变数字，单位小一号、左边距 8 */
@Composable
fun ValueText(value: String, unit: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(value)
            if (unit.isNotEmpty()) {
                withStyle(SpanStyle(fontSize = 18.sp, letterSpacing = 3.sp)) { append(" ") }
                withStyle(SpanStyle(fontSize = OrbitText.ValueUnit.fontSize, fontWeight = OrbitText.ValueUnit.fontWeight, letterSpacing = OrbitText.ValueUnit.letterSpacing)) {
                    append(unit)
                }
            }
        },
        style = OrbitText.Value.copy(brush = NumberGradient, shadow = heroShadow()),
        modifier = modifier.offset(x = (-2).dp),
    )
}

/* ================================================================== *
 *  底部玻璃面板
 * ================================================================== */

/** backdrop-filter: blur(24px) saturate(140%) */
@RequiresApi(31)
private fun glassEffect(radiusPx: Float): androidx.compose.ui.graphics.RenderEffect {
    val saturate = ColorMatrix().apply { setSaturation(1.4f) }
    val blur = RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    return RenderEffect.createChainEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturate)), blur).asComposeRenderEffect()
}

private val SheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
private val SheetEdge = Brush.verticalGradient(0f to Color(0x1FFFFFFF), 0.08f to Color(0x1FFFFFFF), 0.1f to Color.Transparent)

/**
 * .sheet：圆角顶、模糊背后的土星（Android 12+ 且舞台在渲染时），金青高光线。
 * [contentPadding] 默认是原型的 .panel 内边距，底部由调用方加上导航栏留白。
 */
@Composable
fun GlassSheet(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalSaturn.current?.backdrop
    val blur = backdrop != null && Build.VERSION.SDK_INT >= 31
    val blurPx = with(LocalDensity.current) { 24.dp.toPx() }
    // 模糊 + 饱和度滤镜只随密度变化，不在每次重组时重建原生 RenderEffect
    val effect = remember(blurPx) { if (Build.VERSION.SDK_INT >= 31) glassEffect(blurPx) else null }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(SheetShape),
    ) {
        if (blur && backdrop != null && Build.VERSION.SDK_INT >= 31) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = effect
                        clip = true
                    }
                    .drawBehind { translate(-origin.x, -origin.y) { drawLayer(backdrop) } },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (blur) SheetGlass else SheetBg)
                .border(1.dp, SheetEdge, SheetShape)
                .drawBehind {
                    val inset = 48.dp.toPx()
                    drawLine(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Secondary.copy(alpha = 0.7f), Primary.copy(alpha = 0.75f), Color.Transparent),
                            inset,
                            size.width - inset,
                        ),
                        Offset(inset, 0.5f),
                        Offset(size.width - inset, 0.5f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/* ================================================================== *
 *  按钮
 * ================================================================== */

/** .cta-wrap + .cta：流光边框（金青锥形渐变 4s 一圈）+ 扫光（3s，前 55% 划过）+ 右侧箭头块 */
@Composable
fun PrimaryCta(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cta")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "border")
    val sheen by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "sheen")
    val border = Brush.sweepGradient(
        0f to Secondary.copy(alpha = 0f),
        70f / 360f to Secondary.copy(alpha = 0.9f),
        160f / 360f to Primary.copy(alpha = 0f),
        250f / 360f to Primary.copy(alpha = 0.9f),
        1f to Secondary.copy(alpha = 0f),
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .drawBehind { rotate(angle - 90f) { drawCircle(border, radius = size.maxDimension) } }
            .padding(2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(AccentGradient)
                .drawWithContent {
                    drawContent()
                    drawSheen(sheen)
                }
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbitGlyph(planet = OnAccent, ring = OnAccent, size = 26.dp, planetR = 5.5f, ringRy = 4.6f, stroke = 2f)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = OrbitText.Cta)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = OrbitText.CtaSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(OnAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 80 宽的斜向白光条：translateX(-140% → 560%) skewX(-20deg)，只在前 55% 时间里出现 */
private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawSheen(progress: Float) {
    if (progress > 0.55f) return
    val w = 80.dp.toPx()
    val x = -1.4f * w + (5.6f + 1.4f) * w * (progress / 0.55f)
    withTransform({
        translate(left = x)
        transform(androidx.compose.ui.graphics.Matrix().apply { values[androidx.compose.ui.graphics.Matrix.SkewX] = -tan(Math.toRadians(20.0)).toFloat() })
    }) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0f)), 0f, w),
            size = Size(w, size.height),
        )
    }
}

/** .btn / .btn.primary：56 高、19 圆角 */
@Composable
fun OrbitButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val shape = RoundedCornerShape(19.dp)
    val filled = primary && enabled
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(AccentGradient) else Modifier.background(CardBg).border(1.dp, Line, shape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = OrbitText.Button,
            color = when {
                !enabled -> TextDim
                filled -> OnAccent
                else -> TextPrimary
            },
        )
    }
}

/** .tool：80 高的工具卡片，图标块左上、文字左下 */
@Composable
fun ToolTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(80.dp)
            .clip(shape)
            .background(TileGradient)
            .border(1.dp, Line, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SecondarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Secondary, modifier = Modifier.size(17.dp))
        }
        // 末尾字母允许画进右侧内边距，不被裁掉（与浏览器里文字溢出可见的效果一致）
        Text(label, style = OrbitText.ToolLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
    }
}

/* ================================================================== *
 *  列表行
 * ================================================================== */

/** .ic.gold / .ic.teal：38 方块、12 圆角 */
@Composable
fun IconTile(icon: ImageVector, accent: Color, size: Dp = 38.dp, iconSize: Dp = 19.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent == Primary) PrimarySoft else SecondarySoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(iconSize))
    }
}

/** .bar：细进度条；[progress] 为 null 时显示来回流动的不定进度 */
@Composable
fun ProgressLine(progress: Float?, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    // 不定进度的流光只在绘制阶段读取：每帧只重画，不重组整个进度条
    val shimmer = if (progress == null) {
        val t = rememberInfiniteTransition(label = "line")
        t.animateFloat(-0.4f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    } else {
        null
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        if (progress != null) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(height)).background(ProgressGradient))
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .drawBehind {
                        val x = shimmer?.value ?: 0f
                        drawRect(ProgressGradient, topLeft = Offset(size.width * x, 0f), size = size.copy(width = size.width * 0.4f))
                    },
            )
        }
    }
}

/** .row：玻璃列表行，图标块 + 标题/副标题（可带进度条）+ 尾部内容 */
@Composable
fun OrbitRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    progress: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, accent, iconSize = if (progress == null) 18.dp else 19.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = OrbitText.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = OrbitText.RowSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null) {
                Spacer(Modifier.height(8.dp))
                progress()
            }
        }
        trailing()
    }
}

/** .row .val：最小宽 56、右对齐，多行之间的进度条一样长 */
@Composable
fun RowValue(text: String) {
    Text(text, style = OrbitText.RowValue.copy(textAlign = TextAlign.End), modifier = Modifier.widthIn(min = 56.dp))
}

/** .check */
@Composable
fun CheckPill() {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(32.dp)
            .clip(shape)
            .background(PrimarySoft)
            .border(1.dp, Primary.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Check", style = OrbitText.Check)
    }
}

/* ================================================================== *
 *  底部导航
 * ================================================================== */

class NavItem(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

/**
 * .nav：三等分、顶部分隔线；首页放在玻璃面板里（[standalone] = false），其他页单独铺实色底。
 * [bottomInset] 是系统导航栏高度（设计单位）。
 */
@Composable
fun OrbitNavBar(items: List<NavItem>, bottomInset: Float, standalone: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (standalone) Modifier.background(SheetBg) else Modifier)
            .drawBehind { drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(top = 6.dp, bottom = 12.dp + bottomInset.dp),
    ) {
        items.forEach { item ->
            val color = if (item.selected) Primary else TextDim
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = item.onClick)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(item.icon, item.label, tint = color, modifier = Modifier.size(22.dp))
                Text(item.label.uppercase(), style = OrbitText.Nav, color = color)
            }
        }
    }
}

/**
 * 横幅广告槽：广告未加载时高度为 0 且不留间距；加载出来后在下方留 [gap] 并平滑撑开，
 * 页面因此不会出现一块空白，土星舞台也会随面板高度平滑重新取景
 */
@Composable
fun BannerSlot(gap: Dp = 16.dp, banner: @Composable (Modifier) -> Unit) {
    Box(Modifier.fillMaxWidth().animateContentSize().gapBelowIfVisible(gap)) { banner(Modifier) }
}

private fun Modifier.gapBelowIfVisible(gap: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val g = if (p.height > 0) gap.roundToPx() else 0
    layout(p.width, p.height + g) { p.place(0, 0) }
}

/** 次要说明文字 */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, style: TextStyle = OrbitText.Hint) {
    Text(text, style = style, modifier = modifier)
}
