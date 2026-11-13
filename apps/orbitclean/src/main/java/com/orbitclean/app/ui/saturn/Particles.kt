/**
 * [INPUT]: 依赖 ./SaturnCamera 的极坐标投影，依赖 core/ui/theme 的 Secondary/Primary
 * [OUTPUT]: 对外提供 ParticleMode、ParticleField（step 推进 + draw 绘制）
 * [POS]: ui/saturn 的粒子层：所有粒子都在环平面里运动（首页星尘环绕 / 扫描吸入 / 清理螺旋 / 完成迸发），由 SaturnScene 每帧驱动
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.saturn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ParticleMode {
    NONE,
    /** 扫描：碎片从外侧缓慢飘向星球 */
    INFLOW,
    /** 清理：大量碎片螺旋坠入 */
    SPIRAL,
    /** 完成：碎片从星球向外迸发 */
    BURST,
    /** 首页：细小的星尘沿开普勒轨道缓慢环绕，淡入淡出 */
    ORBIT,
}

private val COLORS = listOf(Primary, Color(0xFFA9EBD6), Secondary, Color(0xFFF6E7C8), Color(0xFFC9A66B))

private enum class Kind { IN, OUT, ORBIT }

/** 粒子都用环平面极坐标描述：半径 r（星球半径为单位）、角度 a */
private class Particle(
    var a: Float,
    var r: Float,
    val va: Float,
    val vr: Float,
    val size: Float,
    val color: Color,
    val kind: Kind,
    /** 环绕星尘的寿命（秒），其他粒子不用 */
    val ttl: Float = 0f,
) {
    var life = 1f
    var age = 0f
    var prevA = a
    var prevR = r
}

internal class ParticleField(private val capacity: Int) {
    private val parts = ArrayList<Particle>(capacity)
    private var pending = 0f

    /** 立即补一批外向粒子（进入完成页时的迸发） */
    fun burst(n: Int) = repeat(n) { if (parts.size < capacity) parts += spawn(Kind.OUT) }

    /** 进入首页时先铺一层年龄随机的星尘，不从空白开始 */
    fun seedOrbit(n: Int) = repeat(n) {
        if (parts.size < capacity) parts += spawn(Kind.ORBIT).also { it.age = Random.nextFloat() * it.ttl }
    }

    fun step(dt: Float, mode: ParticleMode) {
        pending += RATE[mode.ordinal] * dt
        val kind = when (mode) {
            ParticleMode.BURST -> Kind.OUT
            ParticleMode.ORBIT -> Kind.ORBIT
            else -> Kind.IN
        }
        while (pending >= 1f && parts.size < capacity) {
            parts += spawn(kind, fast = mode == ParticleMode.SPIRAL)
            pending -= 1f
        }
        if (pending >= 1f) pending = 0f
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.prevA = p.a
            p.prevR = p.r
            when (p.kind) {
                Kind.OUT -> {
                    p.r += p.vr * dt
                    p.a += p.va * dt
                    p.life -= dt * 0.55f
                }
                Kind.IN -> {
                    p.r -= p.vr * dt * (0.5f + 1.6f / p.r)
                    p.a += p.va * dt * (2.6f / p.r)
                    if (p.r < 1.3f) p.life -= dt * 4f
                }
                Kind.ORBIT -> {
                    p.a += p.va * dt
                    p.r += p.vr * dt
                    p.age += dt
                    // 正弦淡入淡出，峰值 0.95
                    p.life = if (p.age >= p.ttl) 0f else sin(PI.toFloat() * p.age / p.ttl) * 0.95f
                }
            }
            if (p.life <= 0f || p.r > 5f) it.remove()
        }
    }

    /** [px] = 每个设计单位对应的像素；尺寸公式与原型一致：圆点半径 = s · k · 7，拖尾线宽 = s · 0.8 */
    fun draw(scope: DrawScope, cam: SaturnCamera, px: Float) = with(scope) {
        val center = Offset(cam.cx, cam.cy)
        parts.forEach { p ->
            val from = cam.polar(p.prevR, p.prevA)
            val to = cam.polar(p.r, p.a)
            val z = p.r * sin(p.a)
            // 在星球背后、且投影落在星球圆盘里：被遮住
            val hidden = cam.depth(z) < 0f && (to - center).getDistance() < cam.radius * 0.97f
            val alpha = p.life.coerceIn(0f, 1f) * if (hidden) 0.08f else 1f
            val dot = p.size * cam.radius * cam.scale(z) * 7f / 844f
            if (p.kind != Kind.ORBIT) {
                drawLine(p.color.copy(alpha = alpha * 0.55f), from, to, strokeWidth = p.size * 0.8f * px, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            }
            drawCircle(p.color.copy(alpha = alpha), radius = dot, center = to, blendMode = BlendMode.Plus)
        }
    }

    private fun spawn(kind: Kind, fast: Boolean = false): Particle {
        val color = COLORS[Random.nextInt(COLORS.size)]
        val a = Random.nextFloat() * SaturnGeom.TAU
        return when (kind) {
            Kind.OUT -> Particle(a, 1.1f, (Random.nextFloat() - 0.5f) * 0.5f, 1.2f + Random.nextFloat() * 2.4f, 1f + Random.nextFloat() * 2f, color, kind)
            Kind.IN -> {
                val vr = (if (fast) 1.1f else 0.55f) + Random.nextFloat() * 0.8f
                Particle(a, 2.8f + Random.nextFloat() * 1.6f, 0.5f + Random.nextFloat() * 0.7f, vr, 1.2f + Random.nextFloat() * 2.2f, color, kind)
            }
            Kind.ORBIT -> {
                // 三条轨道带：内圈贴着星球、中圈在光环外缘、外圈最远；角速度 ∝ r^-2.5，
                // 屏幕上的线速度内圈约是外圈的 4 倍，快慢一眼可辨
                val lane = ORBIT_LANES[Random.nextInt(ORBIT_LANES.size)]
                val r = lane.first + Random.nextFloat() * (lane.second - lane.first)
                Particle(
                    a = a,
                    r = r,
                    va = 1.9f / (r * r * sqrt(r)),
                    vr = (Random.nextFloat() - 0.5f) * 0.02f,
                    size = 0.8f + Random.nextFloat() * 1.4f,
                    color = color,
                    kind = kind,
                    ttl = 5f + Random.nextFloat() * 5f,
                )
            }
        }
    }

    private companion object {
        /** 每秒生成数量，按 ParticleMode 顺序 */
        val RATE = floatArrayOf(0f, 16f, 110f, 10f, 16f)

        /** 环绕星尘的三条轨道带（星球半径为单位） */
        val ORBIT_LANES = listOf(1.3f to 1.55f, 2.3f to 2.75f, 3.0f to 3.7f)
    }
}
