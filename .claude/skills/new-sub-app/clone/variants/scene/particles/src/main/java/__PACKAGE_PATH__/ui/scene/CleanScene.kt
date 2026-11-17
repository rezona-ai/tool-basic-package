/**
 * [INPUT]: 依赖 :core:ui 的颜色 token，依赖 ui/motion 的 FxLayer / rememberSceneClock
 * [OUTPUT]: 对外提供 ScenePhase、CleanScene
 * [POS]: 大场景插槽（变体：particles 光核粒子云）。中心一颗呼吸的光核，外面一圈细进度线，周围是密集的粒子层：
 *        空闲时光点环绕漂移；扫描时一道雷达扇面转动、光点被吸向光核；清理时光核收缩变亮、光点螺旋坠入；
 *        完成时光核膨胀、光点迸发、冲击波扩散。接口在所有场景变体里一致：CleanScene(phase, progress, modifier, compact) { 中间内容 }
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.scene

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import {{CODE_PACKAGE}}.ui.motion.FxLayer
import {{CODE_PACKAGE}}.ui.motion.FxMode
import {{CODE_PACKAGE}}.ui.motion.rememberSceneClock
import kotlin.math.sin

enum class ScenePhase { IDLE, SCANNING, CLEANING, DONE }

@Composable
fun CleanScene(
    phase: ScenePhase,
    progress: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessVeryLow), label = "dots")
    // 光核大小：清理时收缩，完成时膨胀回弹
    val core by animateFloatAsState(
        when (phase) {
            ScenePhase.CLEANING -> 0.30f
            ScenePhase.DONE -> 0.52f
            else -> 0.42f
        },
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "core",
    )
    val radarAlpha by animateFloatAsState(if (phase == ScenePhase.SCANNING) 1f else 0f, tween(500), label = "radar")
    val clock = rememberSceneClock()
    val primary = Primary
    val secondary = Secondary
    val track = Line
    val fx = when (phase) {
        ScenePhase.IDLE -> FxMode.AMBIENT
        ScenePhase.SCANNING -> FxMode.INFLOW
        ScenePhase.CLEANING -> FxMode.VORTEX
        ScenePhase.DONE -> FxMode.BURST
    }
    Box(modifier.aspectRatio(1f).then(if (compact) Modifier.clipToBounds() else Modifier), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.floatValue
            val half = size.minDimension / 2f
            val c = center
            // 雷达扇面
            if (radarAlpha > 0f) {
                rotate(t * 150f, c) {
                    drawArc(
                        Brush.sweepGradient(0f to Color.Transparent, 0.16f to secondary.copy(alpha = 0.35f * radarAlpha), 0.1601f to Color.Transparent, 1f to Color.Transparent, center = c),
                        0f, 58f, true, Offset(c.x - half, c.y - half), Size(half * 2, half * 2),
                    )
                }
            }
            // 光核：呼吸 + 清理时闪烁
            val pulse = 1f + 0.05f * sin(t * if (phase == ScenePhase.CLEANING) 9f else 1.8f)
            val r = half * core * pulse
            drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.32f), primary.copy(alpha = 0.10f), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
            drawCircle(Brush.radialGradient(listOf(secondary.copy(alpha = if (phase == ScenePhase.DONE) 0.55f else 0.30f), primary.copy(alpha = 0.18f)), c, r), r, c)
            // 细进度线
            val lineR = half * 0.66f
            drawCircle(track, lineR, c, style = Stroke(half * 0.018f))
            drawArc(primary, -90f, 360f * shown, false, Offset(c.x - lineR, c.y - lineR), Size(lineR * 2, lineR * 2), style = Stroke(half * 0.035f, cap = StrokeCap.Round))
        }
        FxLayer(fx, Modifier.fillMaxSize(), density = if (compact) 0.45f else 1.4f)
        content()
    }
}
