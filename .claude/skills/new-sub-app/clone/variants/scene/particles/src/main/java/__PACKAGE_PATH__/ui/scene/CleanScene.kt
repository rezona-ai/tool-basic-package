/**
 * [INPUT]: 依赖 :core:ui 的颜色 token
 * [OUTPUT]: 对外提供 ScenePhase、CleanScene
 * [POS]: 大场景插槽（变体：particles 粒子云）。一圈粒子绕中心漂移：扫描时加速，清理时按进度向中心收拢，完成时向外散开。
 *        接口在所有场景变体里一致：CleanScene(phase, progress, modifier) { 中间内容 }
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.scene

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ScenePhase { IDLE, SCANNING, CLEANING, DONE }

private class Dot(val angle: Float, val radius: Float, val speed: Float, val size: Float, val accent: Boolean)

@Composable
fun CleanScene(phase: ScenePhase, progress: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val dots = remember {
        val rnd = Random(7)
        List(54) { Dot(rnd.nextFloat() * 360f, 0.55f + rnd.nextFloat() * 0.42f, 0.4f + rnd.nextFloat(), 0.008f + rnd.nextFloat() * 0.014f, rnd.nextInt(3) == 0) }
    }
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "dots")
    val loop = rememberInfiniteTransition(label = "dots-loop")
    val period = if (phase == ScenePhase.SCANNING) 6_000 else 18_000
    val turn by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "turn")
    val spread by animateFloatAsState(
        when (phase) {
            ScenePhase.CLEANING -> 1f - 0.6f * shown
            ScenePhase.DONE -> 1.08f
            else -> 1f
        },
        tween(700),
        label = "spread",
    )
    val primary = Primary
    val secondary = Secondary
    val track = Line
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.minDimension / 2
            val c = center
            // 内圈细线：IDLE 时表示占用，其余阶段表示进度
            drawCircle(track, half * 0.46f, style = Stroke(half * 0.02f))
            drawArc(
                primary, -90f, 360f * shown, false,
                Offset(c.x - half * 0.46f, c.y - half * 0.46f),
                androidx.compose.ui.geometry.Size(half * 0.92f, half * 0.92f),
                style = Stroke(half * 0.035f),
            )
            dots.forEach { d ->
                val a = ((d.angle + turn * d.speed) % 360f) * PI.toFloat() / 180f
                val r = half * d.radius * spread
                val alpha = if (phase == ScenePhase.DONE) 0.35f else 0.85f
                drawCircle(
                    (if (d.accent) secondary else primary).copy(alpha = alpha),
                    half * d.size * 2,
                    Offset(c.x + cos(a) * r, c.y + sin(a) * r),
                )
            }
        }
        content()
    }
}
