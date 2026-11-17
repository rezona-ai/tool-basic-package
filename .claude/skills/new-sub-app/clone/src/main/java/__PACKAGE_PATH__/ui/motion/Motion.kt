/**
 * [INPUT]: 依赖 compose animation / foundation，依赖 :core:ui 的颜色 token
 * [OUTPUT]: 对外提供 rememberReducedMotion、animateCountUp、Modifier.enterUp（错开入场）、Modifier.pressScale（按下缩放）、
 *           AnimatedCheck（对勾描线）、Motion（时长常量与导航转场）
 * [POS]: 复刻包的动效工具箱：页面入场、数字滚动、按压反馈、完成对勾、页面转场都从这里取，保证所有页面节奏一致。
 *        系统“移除动画”开启时一律直接显示终态
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay

object Motion {
    /** 首页用量、扫描结果等数字从 0 滚到目标值的时长 */
    const val COUNT_MS = 1100
    /** 列表 / 宫格每一项入场错开的间隔 */
    const val STAGGER_MS = 55L
    /** 扫描结果的展示节奏：几行结果错开出现，总时长 */
    const val SCAN_REVEAL_MS = 2200

    /** 页面前进：新页从右侧轻推进入、旧页淡出；返回反过来 */
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 8 }
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(200)) + slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 16 }
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(260)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 16 }
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(200)) + slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 8 }
    }
    /** 完成页：从中间放大淡入，像“揭晓结果” */
    val reveal: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(360)) + scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 0.92f)
    }
}

/**
 * 场景时钟（秒）：每帧更新一次。只在 Canvas 绘制阶段读取 `.floatValue`，不触发重组。
 * 系统关掉动画时停在 0，场景显示静态终态
 */
@Composable
fun rememberSceneClock(): androidx.compose.runtime.MutableFloatState {
    val reduced = rememberReducedMotion()
    val clock = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            val start = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                androidx.compose.runtime.withFrameNanos { now -> clock.floatValue = (now - start) / 1e9f }
            }
        }
    }
    return clock
}

/** 系统设置里关掉了动画（开发者选项或无障碍“移除动画”） */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * 数字滚动：第一次显示时从 0 滚到 [target]（缓出），之后目标变化时平滑过渡。
 * [play] 为 false 时直接显示目标值（例如从子页面返回、不想重播）
 */
@Composable
fun animateCountUp(target: Float, play: Boolean = true, durationMs: Int = Motion.COUNT_MS): Float {
    val reduced = rememberReducedMotion()
    val anim = remember { Animatable(if (play && !reduced) 0f else target) }
    LaunchedEffect(target) {
        if (reduced) anim.snapTo(target) else anim.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/** 错开入场：从下方 18dp 处上浮并淡入，第 [index] 项延迟 index × STAGGER_MS */
fun Modifier.enterUp(index: Int, enabled: Boolean = true): Modifier = composed {
    val reduced = rememberReducedMotion()
    if (!enabled || reduced) return@composed this
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.STAGGER_MS)
        t.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        alpha = t.value.coerceIn(0f, 1f)
        translationY = (1f - t.value) * 18.dp.toPx()
    }
}

/** 按下时缩到 0.96、松手弹回；不消费事件，原来的点击照常生效 */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(PointerEventPass.Initial)
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** 对勾描线：从左到右画出来（[delayMs] 后开始），完成页和结果行用 */
@Composable
fun AnimatedCheck(color: Color, modifier: Modifier = Modifier, delayMs: Long = 0L, strokeFraction: Float = 0.11f) {
    val reduced = rememberReducedMotion()
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        t.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    val path = remember { Path() }
    val part = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val w = size.minDimension
        path.reset()
        path.moveTo(w * 0.22f, w * 0.53f)
        path.lineTo(w * 0.42f, w * 0.72f)
        path.lineTo(w * 0.78f, w * 0.32f)
        measure.setPath(path, false)
        part.reset()
        measure.getSegment(0f, measure.length * t.value, part, true)
        drawPath(part, color, style = Stroke(w * strokeFraction, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (t.value < 1f && t.value > 0f) {
            val tip = measure.getPosition(measure.length * t.value)
            drawCircle(color.copy(alpha = 0.35f), w * strokeFraction, Offset(tip.x, tip.y))
        }
    }
}
