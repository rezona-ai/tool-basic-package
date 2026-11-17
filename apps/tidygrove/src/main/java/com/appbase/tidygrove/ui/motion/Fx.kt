/**
 * [INPUT]: 依赖 :core:ui 的颜色 token（Primary / Secondary），依赖 ./Motion 的 rememberReducedMotion
 * [OUTPUT]: 对外提供 FxMode、FxLayer（帧驱动的粒子层 + 冲击波）
 * [POS]: 大场景的特效层：所有场景变体都把它垫在自己的图形下面或上面，按阶段切换模式——
 *        AMBIENT 空闲时细碎光点缓慢环绕、INFLOW 扫描时光点带拖尾汇向中心、VORTEX 清理时大量光点螺旋吸入、
 *        BURST 完成时从中心迸发并扩散两道冲击波。用屏幕坐标（以场景中心为原点、半径为单位），
 *        不依赖任何母包的几何。系统“移除动画”开启时不生成粒子
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class FxMode {
    NONE,
    /** 空闲：光点在场景外圈缓慢环绕，淡入淡出 */
    AMBIENT,
    /** 扫描：光点从外侧带拖尾汇向中心 */
    INFLOW,
    /** 清理：大量光点快速螺旋吸入 */
    VORTEX,
    /** 完成：进入时迸发一次 + 两道冲击波，之后回到 AMBIENT */
    BURST,
}

private const val TAU = (2 * PI).toFloat()

private class Mote(
    var a: Float,
    var r: Float,
    val va: Float,
    val vr: Float,
    val size: Float,
    val color: Int,
    val inward: Boolean,
    val ttl: Float,
) {
    var age = 0f
    var life = 1f
    var pa = a
    var pr = r
}

private class Wave(var age: Float, val delay: Float)

internal class FxField(private val capacity: Int) {
    private val motes = ArrayList<Mote>(capacity)
    private val waves = ArrayList<Wave>(2)
    private var pending = 0f

    fun seedAmbient(n: Int) = repeat(n) { if (motes.size < capacity) motes += ambient().also { it.age = Random.nextFloat() * it.ttl } }

    fun burst(n: Int) {
        repeat(n) {
            if (motes.size < capacity) {
                val a = Random.nextFloat() * TAU
                motes += Mote(a, 0.25f, (Random.nextFloat() - 0.5f) * 0.6f, 0.9f + Random.nextFloat() * 1.6f, 1f + Random.nextFloat() * 1.6f, Random.nextInt(4), false, 1.6f)
            }
        }
        waves.clear()
        waves += Wave(0f, 0f)
        waves += Wave(0f, 0.28f)
    }

    fun step(dt: Float, mode: FxMode) {
        val rate = when (mode) {
            FxMode.NONE -> 0f
            FxMode.AMBIENT, FxMode.BURST -> 7f
            FxMode.INFLOW -> 26f
            FxMode.VORTEX -> 90f
        }
        pending += rate * dt
        while (pending >= 1f && motes.size < capacity) {
            motes += when (mode) {
                FxMode.INFLOW -> inflow(fast = false)
                FxMode.VORTEX -> inflow(fast = true)
                else -> ambient()
            }
            pending -= 1f
        }
        if (pending >= 1f) pending = 0f
        val it = motes.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.pa = m.a
            m.pr = m.r
            m.age += dt
            if (m.inward) {
                // 越靠近中心转得越快、落得越快，形成漩涡
                m.r -= m.vr * dt * (0.6f + 0.5f / m.r.coerceAtLeast(0.15f))
                m.a += m.va * dt / m.r.coerceAtLeast(0.2f)
                m.life = if (m.r < 0.3f) (m.r / 0.3f) else (m.age / 0.25f).coerceAtMost(1f)
            } else if (m.ttl > 2f) {
                // 环绕光点：正弦淡入淡出
                m.a += m.va * dt
                m.life = sin(PI.toFloat() * (m.age / m.ttl).coerceIn(0f, 1f)) * 0.9f
            } else {
                // 迸发：向外飞出并逐渐减速、变淡
                m.r += m.vr * dt * (1f - m.age / m.ttl).coerceAtLeast(0.15f)
                m.a += m.va * dt
                m.life = 1f - m.age / m.ttl
            }
            if (m.life <= 0f || m.r < 0.05f || m.r > 2.2f || m.age > m.ttl) it.remove()
        }
        waves.forEach { w -> w.age += dt }
        waves.removeAll { it.age - it.delay > WAVE_S }
    }

    fun draw(scope: DrawScope, colors: List<Color>) = with(scope) {
        val c = center
        val unit = size.minDimension / 2f
        waves.forEach { w ->
            val t = ((w.age - w.delay) / WAVE_S).coerceIn(0f, 1f)
            if (w.age >= w.delay) {
                val ease = 1f - (1f - t) * (1f - t)
                drawCircle(colors[0].copy(alpha = (1f - t) * 0.45f), unit * (0.35f + ease * 0.95f), c, style = Stroke(unit * 0.035f * (1f - t) + 1f))
            }
        }
        motes.forEach { m ->
            val to = Offset(c.x + cos(m.a) * m.r * unit, c.y + sin(m.a) * m.r * unit)
            val alpha = m.life.coerceIn(0f, 1f)
            val color = colors[m.color]
            if (m.ttl <= 2f || m.inward) {
                val from = Offset(c.x + cos(m.pa) * m.pr * unit, c.y + sin(m.pa) * m.pr * unit)
                // 拖尾：按本帧位移拉长 3 倍，速度感更明显
                val tail = Offset(to.x + (from.x - to.x) * 3f, to.y + (from.y - to.y) * 3f)
                drawLine(color.copy(alpha = alpha * 0.45f), tail, to, strokeWidth = m.size * unit * 0.012f, cap = StrokeCap.Round)
            }
            drawCircle(color.copy(alpha = alpha), m.size * unit * 0.011f, to)
        }
    }

    private fun ambient() = Mote(
        a = Random.nextFloat() * TAU,
        r = 0.78f + Random.nextFloat() * 0.42f,
        va = (0.12f + Random.nextFloat() * 0.25f) * if (Random.nextInt(5) == 0) -1f else 1f,
        vr = 0f,
        size = 0.7f + Random.nextFloat() * 1.1f,
        color = Random.nextInt(4),
        inward = false,
        ttl = 4f + Random.nextFloat() * 4f,
    )

    private fun inflow(fast: Boolean) = Mote(
        a = Random.nextFloat() * TAU,
        r = 1.15f + Random.nextFloat() * 0.6f,
        va = (if (fast) 1.2f else 0.6f) + Random.nextFloat() * 0.7f,
        vr = (if (fast) 1.3f else 0.45f) + Random.nextFloat() * (if (fast) 0.9f else 0.4f),
        size = 0.9f + Random.nextFloat() * 1.4f,
        color = Random.nextInt(4),
        inward = true,
        ttl = 8f,
    )

    private companion object {
        const val WAVE_S = 1.2f
    }
}

/**
 * 场景的粒子层，铺满父布局（场景区域）。每帧推进一次，只在绘制阶段读时间，不触发重组。
 * [mode] 切到 BURST 时迸发一次；[density] 按场景大小缩放粒子数量（小场景传 0.4 左右）
 */
@Composable
fun FxLayer(mode: FxMode, modifier: Modifier = Modifier, density: Float = 1f) {
    val reduced = rememberReducedMotion()
    if (reduced || mode == FxMode.NONE) return
    val field = remember { FxField((160 * density).toInt().coerceAtLeast(24)).also { it.seedAmbient((22 * density).toInt()) } }
    val current by rememberUpdatedState(mode)
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mode) { if (mode == FxMode.BURST) field.burst((90 * density).toInt().coerceAtLeast(20)) }
    LaunchedEffect(field) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                field.step(dt, current)
                frame = now
            }
        }
    }
    val primary = Primary
    val secondary = Secondary
    val colors = remember(primary, secondary) {
        listOf(primary, secondary, lerp(primary, Color.White, 0.45f), lerp(secondary, Color.White, 0.4f))
    }
    Canvas(modifier) {
        frame // 只在绘制阶段读，每帧重绘
        field.draw(this, colors)
    }
}
