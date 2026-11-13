/**
 * [INPUT]: 依赖 compose 的 LocalDensity / WindowInsets
 * [OUTPUT]: 对外提供 DesignFrame、DesignMetrics、Modifier.reportTop、PlainScreen
 * [POS]: ui/components 的"原型画面"缩放层：把原型 390 宽的设计单位映射到真实屏宽（1 dp = 屏宽 / 390），
 *        土星场景页面里的尺寸、字号、坐标因此可以直接照抄原型 CSS；PlainScreen 给常规列表页铺底色和系统栏留白
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import com.appbase.core.ui.theme.Bg
import com.orbitclean.app.ui.saturn.SaturnGeom
import kotlin.math.max

/** 设计单位下的屏幕信息；原型画面的原点放在 [frameTop]，顶栏放在 [topBar] */
@Immutable
data class DesignMetrics(
    val height: Float,
    val topBar: Float,
    val navBottom: Float,
) {
    /** 原型里顶栏在 y = 22；真机要让出状态栏，整个画面随之下移 */
    val frameTop: Float get() = topBar - 22f
}

/**
 * 原型画面：内部 1.dp / 1.sp 等于原型里的 1px（按屏宽等比缩放，不跟随系统字体缩放，保证与设计稿一致）。
 */
@Composable
fun DesignFrame(content: @Composable BoxScope.(DesignMetrics) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val system = LocalDensity.current
        val design = Density(constraints.maxWidth / SaturnGeom.FRAME_W, fontScale = 1f)
        val status = WindowInsets.statusBars.getTop(system) / design.density
        val nav = WindowInsets.navigationBars.getBottom(system) / design.density
        val metrics = DesignMetrics(
            height = constraints.maxHeight / design.density,
            topBar = max(22f, status + 10f),
            navBottom = nav,
        )
        CompositionLocalProvider(LocalDensity provides design) {
            Box(Modifier.fillMaxSize()) { content(metrics) }
        }
    }
}

/**
 * 只换算密度、不铺满父布局的轻量版：用于底部导航这类嵌在别处的原型组件。
 * 参数是系统导航栏高度（设计单位）。
 */
@Composable
fun DesignDensity(content: @Composable (navBottom: Float) -> Unit) {
    val system = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val design = Density(widthPx / SaturnGeom.FRAME_W, fontScale = 1f)
    val nav = WindowInsets.navigationBars.getBottom(system) / design.density
    CompositionLocalProvider(LocalDensity provides design) { content(nav) }
}

/** 报告元素顶边在屏幕上的位置（设计单位），页面据此把土星放进原型的构图 */
@Composable
fun Modifier.reportTop(onTop: (Float) -> Unit): Modifier {
    val density = LocalDensity.current.density
    return onGloballyPositioned { onTop(it.positionInRoot().y / density) }
}

/** 常规页面：深空底色 + 状态栏留白；[bottomInset] 为 true 时还给手势/三键导航栏留白 */
@Composable
fun PlainScreen(bottomInset: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier),
        content = content,
    )
}
