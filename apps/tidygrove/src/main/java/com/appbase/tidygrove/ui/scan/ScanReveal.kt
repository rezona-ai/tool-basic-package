/**
 * [INPUT]: 依赖 :features:cleaner 的 ScanUi / ScanRow，依赖 ui/motion 的 Motion / AnimatedCheck / rememberReducedMotion，依赖 :core:ui 的组件
 * [OUTPUT]: 对外提供 rememberScanReveal、ScanReveal、RowStatus（扫描结果行右侧的转圈 → 对勾）
 * [POS]: 两种清理流程变体共用的扫描“演出”：真实扫描很快，直接跳出结果会显得突兀，所以结果按行错开揭晓
 *        （第 i 行在演出进度走到 (i+1)/n 后才显示）、总数缓出滚动。演出只在第一次进入时播放，
 *        从类别页返回不重播；它不和广告挂钩（不为等插屏加载而延长）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.feature.cleaner.state.ScanUi
import com.appbase.tidygrove.ui.motion.AnimatedCheck
import com.appbase.tidygrove.ui.motion.Motion
import com.appbase.tidygrove.ui.motion.rememberReducedMotion

/** 演出状态：[progress] 0..1；[rowDone] 某一行是否可以揭晓；[done] 真实结果到齐且演出播完 */
class ScanReveal(private val ui: ScanUi, val progress: Float) {
    fun rowDone(index: Int): Boolean = ui.rows[index].done && progress >= (index + 1f) / ui.rows.size
    val done: Boolean get() = ui.done && progress >= 1f

    /** 演出进度（缓出）乘到总数上，数字从 0 滚到真实值 */
    val ease: Float get() = 1f - (1f - progress) * (1f - progress) * (1f - progress)

    /** 场景进度：真实进度和演出进度取小 */
    val sceneProgress: Float get() = minOf(ui.progress, progress)
}

@Composable
fun rememberScanReveal(ui: ScanUi): ScanReveal {
    val reduced = rememberReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    val show = remember { Animatable(if (played || reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        show.animateTo(1f, tween(Motion.SCAN_REVEAL_MS, easing = LinearEasing))
        played = true
    }
    return ScanReveal(ui, show.value)
}

/** 结果行右侧：扫描中转圈，揭晓时换成描出来的对勾 */
@Composable
fun RowStatus(done: Boolean, modifier: Modifier = Modifier) {
    AnimatedContent(
        done,
        modifier,
        transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 0.6f)) togetherWith fadeOut(tween(150)) },
        label = "row-status",
    ) { ok ->
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (ok) AnimatedCheck(Secondary, Modifier.size(24.dp)) else CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}
