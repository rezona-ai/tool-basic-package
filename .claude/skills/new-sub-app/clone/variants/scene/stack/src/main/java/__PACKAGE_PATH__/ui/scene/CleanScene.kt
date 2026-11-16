/**
 * [INPUT]: 依赖 :core:ui 的颜色 token / BrandTheme
 * [OUTPUT]: 对外提供 ScenePhase、CleanScene
 * [POS]: 大场景插槽（变体：stack 分层柱）。一排竖条像存储刻度：空闲时按占用点亮，扫描时一道亮带来回扫过，
 *        清理时从右往左依次熄灭，完成时全部换成辅色。接口在所有场景变体里一致：CleanScene(phase, progress, modifier) { 中间内容 }
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.scene

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import kotlin.math.abs

enum class ScenePhase { IDLE, SCANNING, CLEANING, DONE }

private const val BARS = 11

@Composable
fun CleanScene(phase: ScenePhase, progress: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "bars")
    val loop = rememberInfiniteTransition(label = "bars-loop")
    val band by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "band")
    val primary = Primary
    val secondary = Secondary
    val track = Line
    val corner = BrandTheme.style.corner(6.dp)
    // 上半部分放中间内容（数字、文字），下半部分是竖条，互不遮挡
    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight(0.44f).align(Alignment.BottomCenter)) {
            val gap = size.width * 0.025f
            val w = (size.width - gap * (BARS - 1)) / BARS
            val r = CornerRadius(corner.toPx().coerceAtMost(w / 2))
            for (i in 0 until BARS) {
                val pos = (i + 0.5f) / BARS
                // 中间高、两边低，像一座小山
                val h = size.height * (0.45f + 0.55f * (1f - abs(pos - 0.5f) * 1.6f).coerceIn(0f, 1f))
                val x = i * (w + gap)
                val y = size.height - h
                drawRoundRect(track, Offset(x, y), Size(w, h), r)
                val lit = when (phase) {
                    ScenePhase.IDLE -> pos <= shown
                    ScenePhase.SCANNING -> abs(pos - band) < 0.14f || pos <= shown
                    ScenePhase.CLEANING -> pos <= 1f - shown
                    ScenePhase.DONE -> true
                }
                if (lit) {
                    val color = when {
                        phase == ScenePhase.DONE -> secondary
                        phase == ScenePhase.SCANNING && abs(pos - band) < 0.14f -> secondary
                        else -> primary
                    }
                    drawRoundRect(color.copy(alpha = if (phase == ScenePhase.DONE) 0.7f else 0.9f), Offset(x, y), Size(w, h), r)
                }
            }
        }
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.52f).align(Alignment.TopCenter), contentAlignment = Alignment.Center) { content() }
    }
}
