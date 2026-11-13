/**
 * [INPUT]: 依赖 ./SaturnCamera 的投影，依赖 core/ui/theme 的 Bg/Primary/Secondary，依赖 compose DrawScope
 * [OUTPUT]: 对外提供 DrawScope.drawSaturnFallback（Canvas 版土星）
 * [POS]: ui/saturn 的低档位渲染器：API < 33 或低内存设备用渐变 + 路径画土星，构图与 SaturnShader 保持一致
 *        光环按"后半圈 → 星球 → 前半圈"三层绘制，模拟遮挡关系；路径对象逐帧复用，不产生分配
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.saturn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import kotlin.math.PI
import kotlin.math.sin

/** 一条环带：内外半径（星球半径为单位）与不透明度 */
private class Band(val inner: Float, val outer: Float, val alpha: Float, val color: Color)

private val RING_LIGHT = Color(0xFFE6D3AE)
private val RING_DARK = Color(0xFFA79A86)

private val BANDS = listOf(
    Band(1.24f, 1.52f, 0.22f, RING_DARK),
    Band(1.53f, 1.94f, 0.85f, RING_LIGHT),
    Band(2.03f, 2.20f, 0.66f, RING_LIGHT),
    Band(2.22f, 2.26f, 0.55f, RING_LIGHT),
    Band(2.32f, 2.34f, 0.40f, RING_LIGHT),
)

/** 星球表面的云带：纵向位置（-1..1）、厚度、颜色 */
private val CLOUD_BANDS = listOf(
    Triple(-0.62f, 0.16f, Color(0x33503A20)),
    Triple(-0.30f, 0.10f, Color(0x2A5A4024)),
    Triple(0.02f, 0.14f, Color(0x40FFF3DC)),
    Triple(0.28f, 0.12f, Color(0x305A4024)),
    Triple(0.55f, 0.18f, Color(0x28503A20)),
)

private const val STEPS = 72

/**
 * 每帧要画十几条路径；复用同一批 Path（rewind 只清点不释放内存），低档机上不再每帧分配、析构原生对象。
 * 录制进显示列表时路径是按值拷贝的，下一帧 rewind 不会影响已提交的画面。只在主线程绘制中使用。
 */
private object PathPool {
    private val paths = ArrayList<Path>()
    private var next = 0

    fun reset() {
        next = 0
    }

    fun obtain(): Path {
        if (next == paths.size) paths += Path()
        return paths[next++].apply { rewind() }
    }
}

/**
 * 画环平面上半径 [r] 的一段弧，只保留 [front] 一侧（前半圈 sin(a) > 0）。
 * 返回的 Path 在侧别切换处断开，不会连出穿过星球的线。
 */
private fun arcPath(cam: SaturnCamera, r: Float, from: Float, to: Float, front: Boolean): Path {
    val path = PathPool.obtain()
    var open = false
    for (i in 0..STEPS) {
        val a = from + (to - from) * i / STEPS
        if ((sin(a) > 0f) != front) {
            open = false
            continue
        }
        val p = cam.polar(r, a)
        if (open) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
        open = true
    }
    return path
}

/** 半个环带（填充的半圆环） */
private fun bandPath(cam: SaturnCamera, band: Band, front: Boolean): Path {
    val start = if (front) 0f else PI.toFloat()
    val span = PI.toFloat()
    return PathPool.obtain().apply {
        for (i in 0..STEPS) {
            val p = cam.polar(band.outer, start + span * i / STEPS)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        for (i in STEPS downTo 0) {
            val p = cam.polar(band.inner, start + span * i / STEPS)
            lineTo(p.x, p.y)
        }
        close()
    }
}

private fun DrawScope.drawRings(cam: SaturnCamera, front: Boolean) {
    BANDS.forEach { drawPath(bandPath(cam, it, front), it.color.copy(alpha = it.alpha)) }
}

private fun DrawScope.drawPlanet(cam: SaturnCamera, t: Float) {
    val c = Offset(cam.cx, cam.cy)
    val r = cam.radius
    val light = c + Offset(-0.38f * r, -0.36f * r)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFF6E8C6), Color(0xFFD7B57A), Color(0xFF8A6438), Color(0xFF241608)),
            center = light,
            radius = r * 1.9f,
        ),
        radius = r,
        center = c,
    )
    val disc = PathPool.obtain().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
    clipPath(disc) {
        rotate(Math.toDegrees(cam.roll.toDouble()).toFloat(), pivot = c) {
            CLOUD_BANDS.forEachIndexed { i, (y, h, color) ->
                // 云带上下轻微起伏
                val wobble = sin(t * 0.3f + i * 1.7f) * 0.012f
                drawRect(color, topLeft = Offset(c.x - r, c.y + (y + wobble - h / 2) * r), size = androidx.compose.ui.geometry.Size(2 * r, h * r))
            }
            // 风暴：随自转从左向右横穿星球表面，转到背面时隐去
            val phase = t * 0.11f
            val facing = kotlin.math.cos(phase)
            if (facing > 0f) {
                val w = 0.36f * r * facing
                drawOval(
                    Color(0x66FFF3DC),
                    topLeft = Offset(c.x + sin(phase) * r * 0.85f - w / 2, c.y - 0.38f * r),
                    size = androidx.compose.ui.geometry.Size(w, 0.15f * r),
                )
            }
        }
        // 晨昏线：光源对侧压暗
        drawCircle(
            Brush.radialGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.78f),
                center = light,
                radius = r * 2.1f,
            ),
            radius = r,
            center = c,
        )
    }
    drawCircle(Color(0x33FFD9A0), radius = r * 1.06f, center = c, style = Stroke(r * 0.05f))
}

private fun DrawScope.drawOrbit(cam: SaturnCamera, usage: Float, front: Boolean) {
    val r = SaturnGeom.ORBIT_R
    val start = SaturnGeom.ORBIT_START
    val strokeW = cam.radius * 0.018f
    drawPath(arcPath(cam, r, 0f, SaturnGeom.TAU, front), Primary.copy(alpha = 0.14f), style = Stroke(strokeW))
    drawPath(
        arcPath(cam, r, start, start + usage * SaturnGeom.TAU, front),
        Primary,
        style = Stroke(strokeW * 1.6f, cap = StrokeCap.Round),
    )
    val moonA = start + usage * SaturnGeom.TAU
    if ((sin(moonA) > 0f) == front) {
        val m = cam.polar(r, moonA)
        drawCircle(Primary.copy(alpha = 0.35f), radius = cam.radius * 0.09f, center = m)
        drawCircle(Color(0xFFE8FFF8), radius = cam.radius * 0.035f, center = m)
    }
}

private fun DrawScope.drawEffects(cam: SaturnCamera, scan: Float, sweep: Float, shock: Float, front: Boolean) {
    if (scan > 0.01f) {
        drawPath(
            arcPath(cam, 1.9f, sweep - 0.7f, sweep, front),
            Primary.copy(alpha = 0.55f * scan),
            style = Stroke(cam.radius * 0.5f),
        )
    }
    if (shock > 0f) {
        val fade = 1f - ((shock - 1f) / 3.4f).coerceIn(0f, 1f)
        drawPath(arcPath(cam, shock, 0f, SaturnGeom.TAU, front), Primary.copy(alpha = fade), style = Stroke(cam.radius * 0.04f))
    }
}

/** Canvas 版土星：背景 → 后半圈环/轨道 → 星球 → 前半圈环/轨道 */
internal fun DrawScope.drawSaturnFallback(
    cam: SaturnCamera,
    t: Float,
    usage: Float,
    scan: Float,
    sweep: Float,
    shock: Float,
) {
    PathPool.reset()
    drawRect(Bg)
    drawCircle(
        Brush.radialGradient(listOf(Color(0x2E0C3A40), Color.Transparent), center = Offset(size.width * 0.8f, size.height * 0.15f), radius = size.width),
        radius = size.width,
        center = Offset(size.width * 0.8f, size.height * 0.15f),
    )
    listOf(false, true).forEach { front ->
        if (front) drawPlanet(cam, t)
        drawRings(cam, front)
        drawOrbit(cam, usage, front)
        drawEffects(cam, scan, sweep, shock, front)
    }
}
