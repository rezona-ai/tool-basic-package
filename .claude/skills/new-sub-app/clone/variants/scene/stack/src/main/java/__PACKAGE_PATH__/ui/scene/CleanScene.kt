/**
 * [INPUT]: 依赖 :core:ui 的颜色 token / BrandTheme，依赖 ui/motion 的 FxLayer / rememberSceneClock
 * [OUTPUT]: 对外提供 ScenePhase、CleanScene
 * [POS]: 大场景插槽（变体：stack 分层柱）。一排高低不同的竖条像存储刻度，上半部分放中间内容：
 *        空闲时竖条按占用点亮、轻轻起伏；扫描时竖条像均衡器一样跳动、一道亮带来回扫过；
 *        清理时竖条从右往左依次塌下、光点从柱顶向上飘散；完成时竖条带回弹依次长回、全部换成辅色并迸发光点。
 *        接口在所有场景变体里一致：CleanScene(phase, progress, modifier, compact) { 中间内容 }
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import {{CODE_PACKAGE}}.ui.motion.FxLayer
import {{CODE_PACKAGE}}.ui.motion.FxMode
import {{CODE_PACKAGE}}.ui.motion.rememberSceneClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

enum class ScenePhase { IDLE, SCANNING, CLEANING, DONE }

private const val BARS = 11

@Composable
fun CleanScene(
    phase: ScenePhase,
    progress: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessVeryLow), label = "bars")
    // 每根竖条自己的高度系数：完成时依次带回弹长回，进入页面时从 0 长出来
    val grow = remember { List(BARS) { Animatable(0f) } }
    LaunchedEffect(phase) {
        grow.forEachIndexed { i, a ->
            launch {
                if (phase == ScenePhase.DONE) {
                    a.snapTo(0.2f)
                    delay(i * 45L)
                }
                a.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
        }
    }
    val clock = rememberSceneClock()
    val primary = Primary
    val secondary = Secondary
    val track = Line
    val corner = BrandTheme.style.corner(6.dp)
    val fx = when (phase) {
        ScenePhase.IDLE -> FxMode.AMBIENT
        ScenePhase.SCANNING -> FxMode.INFLOW
        ScenePhase.CLEANING -> FxMode.VORTEX
        ScenePhase.DONE -> FxMode.BURST
    }
    Box(modifier.aspectRatio(1f).then(if (compact) Modifier.clipToBounds() else Modifier)) {
        FxLayer(fx, Modifier.fillMaxSize(), density = if (compact) 0.3f else 0.8f)
        Canvas(Modifier.fillMaxWidth().fillMaxHeight(0.46f).align(Alignment.BottomCenter)) {
            val t = clock.floatValue
            val gap = size.width * 0.025f
            val w = (size.width - gap * (BARS - 1)) / BARS
            val r = CornerRadius(corner.toPx().coerceAtMost(w / 2))
            val band = (sin(t * 2.4f) + 1f) / 2f
            for (i in 0 until BARS) {
                val pos = (i + 0.5f) / BARS
                // 中间高、两边低，像一座小山
                var k = 0.45f + 0.55f * (1f - abs(pos - 0.5f) * 1.6f).coerceIn(0f, 1f)
                k *= when (phase) {
                    ScenePhase.IDLE -> 1f + 0.03f * sin(t * 1.5f + i * 0.7f)
                    ScenePhase.SCANNING -> 0.7f + 0.3f * abs(sin(t * 6f + i * 1.3f))
                    ScenePhase.CLEANING -> if (pos > 1f - shown) 0.18f else 1f
                    ScenePhase.DONE -> 1f
                }
                val h = size.height * k * grow[i].value.coerceAtLeast(0.05f)
                val x = i * (w + gap)
                val y = size.height - h
                drawRoundRect(track, Offset(x, y), Size(w, h), r)
                val near = phase == ScenePhase.SCANNING && abs(pos - band) < 0.12f
                val lit = when (phase) {
                    ScenePhase.IDLE -> pos <= shown
                    ScenePhase.SCANNING -> near || pos <= shown
                    ScenePhase.CLEANING -> pos <= 1f - shown
                    ScenePhase.DONE -> true
                }
                if (lit) {
                    val color = if (phase == ScenePhase.DONE || near) secondary else primary
                    drawRoundRect(color.copy(alpha = 0.9f), Offset(x, y), Size(w, h), r)
                    // 柱顶高光
                    drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f), Offset(x, y), Size(w, w * 0.6f), r)
                }
            }
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.52f).align(Alignment.TopCenter), contentAlignment = Alignment.Center) { content() }
    }
}
