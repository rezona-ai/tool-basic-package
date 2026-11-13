/**
 * [INPUT]: 依赖 compose runtime 的快照状态与 CompositionLocal，依赖 compose graphics 的 GraphicsLayer
 * [OUTPUT]: 对外提供 SaturnDirector、LocalSaturn、SaturnStage（页面申请舞台）、Modifier.saturnTilt（手指转动视角）
 * [POS]: ui/saturn 的"导演"：土星舞台常驻在 AppNav 底层，页面只通过 SaturnStage 声明镜头；
 *        最后申请的页面说了算，页面离开时自动交还，镜头因此能在页面之间连续推移（与原型的单画布一致）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.saturn

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

/** 一个页面对舞台的申请：镜头 + 粒子 + 冲击波 */
class StageRequest internal constructor() {
    var view: SaturnView? by mutableStateOf(null)
    var particles: ParticleMode by mutableStateOf(ParticleMode.NONE)
    var shockwave: Boolean by mutableStateOf(false)
}

class SaturnDirector {
    private val requests = mutableStateListOf<StageRequest>()

    /** 当前生效的申请；为 null 时舞台不渲染（常规列表页） */
    val current: StageRequest? get() = requests.lastOrNull()

    /** 手指位置换算出的视角偏移目标（原型 tilt.tx / tilt.ty） */
    var tiltX by mutableFloatStateOf(0f)
    var tiltY by mutableFloatStateOf(0f)

    /** 舞台每帧录制的画面，玻璃面板拿它做背景模糊 */
    var backdrop: GraphicsLayer? by mutableStateOf(null)
        internal set

    internal fun push(r: StageRequest) = requests.add(r)
    internal fun remove(r: StageRequest) = requests.remove(r)
}

val LocalSaturn = staticCompositionLocalOf<SaturnDirector?> { null }

/**
 * 页面在组合期间调用：只要页面还在，舞台就按它给的镜头渲染。
 * [view] 为 null 表示布局尚未量好，舞台保持上一个镜头。
 */
@Composable
fun SaturnStage(view: SaturnView?, particles: ParticleMode = ParticleMode.NONE, shockwave: Boolean = false) {
    val director = LocalSaturn.current ?: return
    val request = remember { StageRequest() }
    SideEffect {
        if (view != null) request.view = view
        request.particles = particles
        request.shockwave = shockwave
    }
    DisposableEffect(director) {
        director.push(request)
        onDispose { director.remove(request) }
    }
}

/** 手指按住画面空白处时，视角跟着手指轻微转动；松手回正。不消费事件，按钮照常可点 */
fun Modifier.saturnTilt(director: SaturnDirector?) = if (director == null) {
    this
} else {
    pointerInput(director) {
        fun aim(p: Offset, size: IntSize) {
            director.tiltX = (p.x / size.width - 0.5f) * 0.22f
            director.tiltY = (p.y / size.height - 0.5f) * 0.16f
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            aim(down.position, size)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                aim(change.position, size)
            }
            director.tiltX = 0f
            director.tiltY = 0f
        }
    }
}
