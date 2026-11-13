/**
 * [INPUT]: 依赖 compose ui 的 Offset
 * [OUTPUT]: 对外提供 SaturnCamera（环平面 → 屏幕投影）、SaturnView（镜头目标，设计单位）、SaturnShots（原型四个镜头）、SaturnGeom 常量
 * [POS]: ui/saturn 的几何真相源；着色器、Canvas 降级版、粒子三处共用同一套旋转与透视，粒子才能贴着光环走
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.saturn

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* ------------------------------------------------------------------ *
 *  坐标约定（与 SaturnShader 完全一致）
 *  - 世界系：相机在 (0, 0, D) 看向 -z，y 朝上；单位 = 土星赤道半径
 *  - 本体系：local = Rx(open) · Rz(roll) · world，光环在 y = 0 平面
 *  - 屏幕：screen = center + (wx, -wy) · radius · D / (D - wz)
 * ------------------------------------------------------------------ */
object SaturnGeom {
    const val D = 8f
    const val OPEN = -0.42f
    const val ROLL = -0.38f
    /** 外圈"用量轨道"的半径，卫星停在当前用量的位置 */
    const val ORBIT_R = 2.48f
    /** 用量轨道的起点角（本体系 xz 平面），与着色器的 -1.9 对应 */
    const val ORBIT_START = -1.9f
    const val TAU = 6.2831855f
    /** 原型画面宽度：所有设计单位都以它为基准按屏宽缩放 */
    const val FRAME_W = 390f
    /** 原型里 zoom = 1 时星球的像素半径：844 · 1.09 / 8 */
    const val PLANET_R = 114.995f
}

@Immutable
data class SaturnCamera(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val open: Float = SaturnGeom.OPEN,
    val roll: Float = SaturnGeom.ROLL,
) {
    private val so = sin(open)
    private val co = cos(open)
    private val sr = sin(roll)
    private val cr = cos(roll)

    /** 本体系环平面上的点 (x, 0, z) 在世界系里的深度；> 0 表示在星球前面 */
    fun depth(z: Float): Float = z * co

    /** 透视缩放：近大远小 */
    fun scale(z: Float): Float = SaturnGeom.D / (SaturnGeom.D - depth(z))

    /** 本体系环平面上的点 (x, 0, z) → 屏幕像素 */
    fun project(x: Float, z: Float): Offset {
        val y1 = z * so
        val wx = x * cr + y1 * sr
        val wy = -x * sr + y1 * cr
        val k = radius * scale(z)
        return Offset(cx + wx * k, cy - wy * k)
    }

    /** 环平面上极坐标 (r, a) 的投影，粒子和降级版画环都用它 */
    fun polar(r: Float, a: Float): Offset = project(r * cos(a), r * sin(a))
}

/** 一个页面想要的镜头与效果，坐标是原型 390 宽画面里的设计单位 */
@Immutable
data class SaturnView(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    /** 雷达扫描光束强度 0..1 */
    val scan: Float = 0f,
    /** 光环脉动强度 0..1（清理中） */
    val pulse: Float = 0f,
    /** 用量轨道 0..1 */
    val usage: Float = 0f,
) {
    /**
     * 把原型镜头放进真实屏幕：[top] 是原型画面原点在屏幕上的位置；
     * 可用空间比原型少时星球按比例上移并缩小，不压住下方文字；空间更宽裕时（比如横幅没加载、面板变矮）
     * 星球随之下移并自然放大，最多 1.1 倍。半径按 √k 变化，比位置变化更温和。
     */
    fun placed(top: Float, available: Float, protoAvailable: Float): SaturnView {
        val k = (available / protoAvailable).coerceIn(0.6f, 1.21f)
        return copy(cy = top + cy * k, radius = radius * sqrt(k))
    }
}

/** 原型四个状态的镜头（VIEW 表），以及各自"底部面板顶边"在原型里的位置 */
object SaturnShots {
    fun home(usage: Float) = SaturnView(250f, 212f, SaturnGeom.PLANET_R * 0.98f, usage = usage)
    fun scan(done: Boolean) = SaturnView(195f, 262f, SaturnGeom.PLANET_R * 0.92f, scan = if (done) 0.35f else 1f)
    fun clean() = SaturnView(195f, 300f, SaturnGeom.PLANET_R * 1.22f, pulse = 1f)
    fun done(usage: Float) = SaturnView(195f, 148f, SaturnGeom.PLANET_R * 0.72f, usage = usage)

    const val HOME_SHEET_TOP = 494f
    const val SCAN_SHEET_TOP = 554f
    const val CLEAN_SHEET_TOP = 694f
    const val DONE_SHEET_TOP = 382f
}
