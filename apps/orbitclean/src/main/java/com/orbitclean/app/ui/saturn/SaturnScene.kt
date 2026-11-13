/**
 * [INPUT]: 依赖 ./SaturnDirector 的当前申请与手势偏移，依赖 ./SaturnCamera、./SaturnShader、./SaturnFallback、./Particles
 * [OUTPUT]: 对外提供 SaturnBackdrop（AppNav 底层的常驻土星舞台）
 * [POS]: ui/saturn 的渲染入口：按设备档位选择着色器或 Canvas 降级版，把镜头平滑推到当前页面要求的位置并叠加漂浮摇摆，
 *        驱动粒子、画上下暗角，并把整帧录进离屏 GraphicsLayer（只渲染一次，屏幕与玻璃面板模糊共用这张纹理）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.saturn

import android.app.ActivityManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.appbase.core.ui.theme.Bg
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 冲击波周期（秒）；扫描时光束角速度 2.6，平时 0.6（弧度/秒），与原型一致 */
private const val SHOCK_PERIOD = 2.6f
private const val SWEEP_FAST = 2.6f
private const val SWEEP_IDLE = 0.6f

/** 镜头和效果参数的平滑过渡（像素）；首次拿到目标时直接就位，避免从 0 飞进来 */
private class Rig {
    val cx = Animatable(0f)
    val cy = Animatable(0f)
    val radius = Animatable(0f)
    val scan = Animatable(0f)
    val pulse = Animatable(0f)
    val usage = Animatable(0f)
    var placed by mutableStateOf(false)

    suspend fun moveTo(v: SaturnView, px: Float) = coroutineScope {
        val pairs = listOf(cx to v.cx * px, cy to v.cy * px, radius to v.radius * px, scan to v.scan, pulse to v.pulse, usage to v.usage)
        pairs.forEach { (anim, value) ->
            if (placed) launch { anim.animateTo(value, tween(1100, easing = FastOutSlowInEasing)) } else anim.snapTo(value)
        }
        placed = true
    }
}

/** 原型 .shade：顶部 14% 与底部 48% 的暗角，压住星空让文字更清楚 */
private val Shade = Brush.verticalGradient(
    0f to Color(0x8C02040A),
    0.14f to Color.Transparent,
    0.52f to Color.Transparent,
    1f to Color(0x8C02040A),
)

/**
 * 常驻舞台，铺满 [modifier] 给的区域；当前没有页面申请时不渲染任何东西。
 * 设计单位按 屏宽 / 390 换算成像素。
 */
@Composable
fun SaturnBackdrop(director: SaturnDirector, modifier: Modifier = Modifier) {
    val request = director.current ?: return
    val context = LocalContext.current
    val highTier = remember {
        Build.VERSION.SDK_INT >= 33 && !context.getSystemService(ActivityManager::class.java).isLowRamDevice
    }
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val rig = remember { Rig() }
    val field = remember { ParticleField(if (highTier) 260 else 60) }
    val time = remember { mutableFloatStateOf(0f) }
    val sweep = remember { mutableFloatStateOf(0f) }
    val tilt = remember { floatArrayOf(0f, 0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val mode by rememberUpdatedState(if (reducedMotion) ParticleMode.NONE else request.particles)
    val scanning by rememberUpdatedState((request.view?.scan ?: 0f) >= 1f)

    val view = request.view
    LaunchedEffect(view, widthPx) {
        if (view != null && widthPx > 0f) rig.moveTo(view, widthPx / SaturnGeom.FRAME_W)
    }
    LaunchedEffect(mode) {
        when (mode) {
            ParticleMode.BURST -> field.burst(if (highTier) 140 else 40)
            ParticleMode.ORBIT -> field.seedOrbit(if (highTier) 110 else 30)
            else -> Unit
        }
    }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                time.floatValue = (now - start) / 1e9f * if (reducedMotion) 0.15f else 1f
                sweep.floatValue = (sweep.floatValue + dt * if (scanning) SWEEP_FAST else SWEEP_IDLE) % SaturnGeom.TAU
                val k = 1f - exp(-dt * 2.6f)
                tilt[0] += (director.tiltX - tilt[0]) * k
                tilt[1] += (director.tiltY - tilt[1]) * k
                field.step(dt, mode)
            }
        }
    }

    // AGSL 在运行时编译；个别 GPU 驱动编译失败时退回 Canvas 版，不让首页崩溃
    val shader = remember {
        if (highTier && Build.VERSION.SDK_INT >= 33) {
            runCatching { SaturnShader() }.onFailure { Log.w("SaturnScene", "shader unavailable", it) }.getOrNull()
        } else {
            null
        }
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it.shader) } }
    // 舞台画进一张离屏纹理：屏幕和玻璃面板的模糊都直接采样这张纹理，
    // 否则面板下方那块区域每帧要把着色器和粒子再完整跑一遍
    val layer = rememberGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen }
    DisposableEffect(layer) {
        director.backdrop = layer
        onDispose { if (director.backdrop === layer) director.backdrop = null }
    }

    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
    ) {
        val t = time.floatValue
        if (!rig.placed) {
            drawRect(Bg)
            return@Canvas
        }
        val cam = floating(rig, t, size.width / SaturnGeom.FRAME_W, tilt)
        val shock = if (request.shockwave && !reducedMotion) 1.05f + (t % SHOCK_PERIOD) / SHOCK_PERIOD * 3.4f else 0f
        val uSweep = -sweep.floatValue
        if (shader != null && brush != null && Build.VERSION.SDK_INT >= 33) {
            shader.update(size.width, size.height, cam, t, rig.usage.value, rig.scan.value, uSweep, shock, rig.pulse.value)
            drawRect(brush)
        } else {
            drawSaturnFallback(cam, t, rig.usage.value, rig.scan.value, uSweep, shock)
        }
        field.draw(this, cam, size.width / SaturnGeom.FRAME_W)
        drawShade()
    }
}

private fun DrawScope.drawShade() = drawRect(Shade)

/**
 * 静止时也在动：整颗星球上下漂浮（±7 设计单位、约 11 秒一个周期）、左右轻晃、微微呼吸，
 * 光环倾角与滚转缓慢摇摆；几个周期互不整除，画面不会出现明显的重复节奏。
 */
private fun floating(rig: Rig, t: Float, px: Float, tilt: FloatArray) = SaturnCamera(
    cx = rig.cx.value + (sin(t * 0.37f) * 3f + sin(t * 0.13f) * 2f) * px,
    cy = rig.cy.value + (sin(t * 0.57f) * 7f + sin(t * 0.23f) * 2f) * px,
    radius = rig.radius.value * (1f + 0.008f * sin(t * 0.45f)),
    open = SaturnGeom.OPEN - tilt[1] + sin(t * 0.21f) * 0.035f + sin(t * 0.09f) * 0.015f,
    roll = SaturnGeom.ROLL + tilt[0] + sin(t * 0.17f) * 0.03f,
)
