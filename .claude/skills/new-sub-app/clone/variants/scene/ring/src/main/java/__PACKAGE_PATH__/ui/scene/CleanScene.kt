/**
 * [INPUT]: 依赖 :core:ui 的颜色 token，依赖 ui/motion 的 FxLayer / rememberSceneClock / rememberReducedMotion
 * [OUTPUT]: 对外提供 ScenePhase、CleanScene
 * [POS]: 大场景插槽（变体：ring 环形仪表）。一圈刻度 + 渐变进度环 + 环头光点，背后是呼吸的柔光，外圈是粒子层：
 *        空闲时刻度缓慢转动、光点环绕；扫描时一道彗星光弧绕圈扫过、经过的刻度被点亮、光点向中心汇聚；
 *        清理时整环加速旋转、内盘脉动、光点螺旋吸入；完成时环身闪一下、迸发光点和冲击波。
 *        接口在所有场景变体里一致：CleanScene(phase, progress, modifier, compact) { 中间内容 }，
 *        progress 在 IDLE 时是存储占用，其余阶段是进度（0..1）；compact 用于卡片里的小场景（粒子更少、不出界）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.scene

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import {{CODE_PACKAGE}}.ui.motion.FxLayer
import {{CODE_PACKAGE}}.ui.motion.FxMode
import {{CODE_PACKAGE}}.ui.motion.rememberSceneClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ScenePhase { IDLE, SCANNING, CLEANING, DONE }

private const val TICKS = 60

@Composable
fun CleanScene(
    phase: ScenePhase,
    progress: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    // 完成那一刻环身加粗再弹回
    val flash = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == ScenePhase.DONE) {
            flash.snapTo(1f)
            flash.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    // 各阶段的转速（度 / 秒），切换时平滑过渡
    val speed by animateFloatAsState(
        when (phase) {
            ScenePhase.IDLE -> 6f
            ScenePhase.SCANNING -> 40f
            ScenePhase.CLEANING -> 160f
            ScenePhase.DONE -> 10f
        },
        tween(900),
        label = "speed",
    )
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
        FxLayer(fx, Modifier.fillMaxSize(), density = if (compact) 0.35f else 1f)
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 空闲时整体轻微漂浮
                    val t = clock.floatValue
                    translationY = sin(t * 0.9f) * size.height * 0.008f
                },
        ) {
            val t = clock.floatValue
            val half = size.minDimension / 2f
            val c = center
            val stroke = half * if (compact) 0.16f else 0.11f
            val ringR = half * 0.80f
            val arcTopLeft = Offset(c.x - ringR, c.y - ringR)
            val arcSize = Size(ringR * 2, ringR * 2)
            val spin = t * speed

            // 背后呼吸的柔光
            val breathe = 1f + 0.04f * sin(t * 1.6f)
            drawCircle(
                Brush.radialGradient(listOf(primary.copy(alpha = 0.16f), Color.Transparent), c, half * breathe),
                half * breathe,
                c,
            )

            // 外圈刻度：缓慢转动，扫描时被彗星经过的刻度点亮
            if (!compact) {
                rotate(spin * 0.25f, c) {
                    for (i in 0 until TICKS) {
                        val a = i * 2 * PI.toFloat() / TICKS
                        val long = i % 5 == 0
                        val r1 = half * 0.95f
                        val r2 = half * if (long) 0.88f else 0.91f
                        var alpha = if (long) 0.35f else 0.18f
                        if (phase == ScenePhase.SCANNING) {
                            val head = ((90f + spin * 2.2f - spin * 0.25f) % 360f + 360f) % 360f
                            val deg = Math.toDegrees(a.toDouble()).toFloat()
                            val d = ((head - deg) % 360f + 360f) % 360f
                            if (d < 70f) alpha = alpha + (1f - d / 70f) * 0.6f
                        }
                        drawLine(
                            primary.copy(alpha = alpha),
                            Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                            Offset(c.x + cos(a) * r2, c.y + sin(a) * r2),
                            strokeWidth = half * 0.012f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            // 轨道 + 进度弧
            drawCircle(track, ringR, c, style = Stroke(stroke))
            val width = stroke * (1f + 0.45f * flash.value)
            val sweep = Brush.sweepGradient(listOf(secondary, primary, secondary), c)
            rotate(if (phase == ScenePhase.CLEANING) spin * 0.5f else 0f, c) {
                drawArc(sweep, -90f, 360f * shown, false, arcTopLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            }

            // 环头光点：在进度弧末端发光、轻轻脉动
            if (shown > 0.01f && phase != ScenePhase.CLEANING) {
                val headA = (-90f + 360f * shown) * PI.toFloat() / 180f
                val hp = Offset(c.x + cos(headA) * ringR, c.y + sin(headA) * ringR)
                val glow = stroke * (1.1f + 0.25f * sin(t * 3f))
                drawCircle(secondary.copy(alpha = 0.25f), glow * 1.6f, hp)
                drawCircle(Color.White.copy(alpha = 0.9f), stroke * 0.32f, hp)
            }

            // 扫描：彗星光弧绕圈，尾巴渐隐
            if (phase == ScenePhase.SCANNING) {
                rotate(spin * 2.2f, c) {
                    drawArc(
                        Brush.sweepGradient(
                            0f to Color.Transparent, 0.25f to secondary.copy(alpha = 0.85f),
                            0.2501f to Color.Transparent, 1f to Color.Transparent,
                            center = c,
                        ),
                        0f, 90f, false, arcTopLeft, arcSize, style = Stroke(stroke * 1.25f, cap = StrokeCap.Round),
                    )
                }
            }

            // 内盘：清理时脉动，完成时换成辅色
            val inner = ringR - stroke * 1.4f
            when (phase) {
                ScenePhase.CLEANING -> {
                    val p = 0.88f + 0.08f * abs(sin(t * 5f))
                    drawCircle(primary.copy(alpha = 0.10f + 0.06f * abs(sin(t * 5f))), inner * p, c)
                }
                ScenePhase.DONE -> drawCircle(secondary.copy(alpha = 0.14f + 0.1f * flash.value), inner, c)
                else -> drawCircle(primary.copy(alpha = 0.05f), inner, c)
            }
        }
        content()
    }
}
