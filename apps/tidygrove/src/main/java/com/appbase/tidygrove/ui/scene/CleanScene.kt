/**
 * [INPUT]: 依赖 :core:ui 的颜色 token
 * [OUTPUT]: 对外提供 ScenePhase、CleanScene
 * [POS]: 大场景插槽（变体：ring 环形进度）。首页、扫描、清理中、完成页都用它；接口在所有场景变体里一致：
 *        CleanScene(phase, progress, modifier) { 中间内容 }，progress 在 IDLE 时是存储占用，其余阶段是进度（0..1）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.scene

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary

enum class ScenePhase { IDLE, SCANNING, CLEANING, DONE }

@Composable
fun CleanScene(phase: ScenePhase, progress: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    val loop = rememberInfiniteTransition(label = "ring-loop")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "spin")
    val pulse by loop.animateFloat(0.92f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val primary = Primary
    val secondary = Secondary
    val track = Line
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.07f
            val inset = stroke / 2 + size.minDimension * 0.04f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = Brush.sweepGradient(listOf(secondary, primary, secondary))
            drawArc(sweep, -90f, 360f * shown, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            when (phase) {
                ScenePhase.SCANNING -> rotate(spin) {
                    drawArc(primary.copy(alpha = 0.55f), -90f, 40f, false, topLeft, arcSize, style = Stroke(stroke * 0.5f, cap = StrokeCap.Round))
                }
                ScenePhase.CLEANING -> {
                    val r = (size.minDimension / 2 - inset - stroke * 1.6f) * pulse
                    drawCircle(primary.copy(alpha = 0.10f), r)
                }
                ScenePhase.DONE -> drawCircle(secondary.copy(alpha = 0.12f), size.minDimension / 2 - inset - stroke * 1.6f)
                ScenePhase.IDLE -> Unit
            }
        }
        content()
    }
}
