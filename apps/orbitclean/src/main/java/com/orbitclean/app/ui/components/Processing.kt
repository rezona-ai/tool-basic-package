/**
 * [INPUT]: 依赖 ui/saturn 的 SaturnStage/SaturnShots，依赖 ./Design 的 DesignFrame，依赖 ./Orbit 的积木，依赖 core/ads 的 AdPolicy/awaitInterstitialReady/showInterstitial
 * [OUTPUT]: 对外提供 SaturnProgressView、ProgressScene、ProcessingView、ProcessingGate、waitForAd
 * [POS]: ui/components 的"处理中"场景：功能页入场（扫描光束）与删除文件（光环脉动 + 螺旋粒子）共用一套土星进度页，
 *        ProcessingGate 负责"动画 → 插屏 → 内容"的广告节奏
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.components

import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appbase.core.ads.AdPolicy
import com.appbase.core.ads.awaitInterstitialReady
import com.appbase.core.ads.showInterstitial
import com.orbitclean.app.ui.saturn.LocalSaturn
import com.orbitclean.app.ui.saturn.ParticleMode
import com.orbitclean.app.ui.saturn.SaturnShots
import com.orbitclean.app.ui.saturn.SaturnStage
import com.orbitclean.app.ui.saturn.saturnTilt
import com.orbitclean.app.ui.theme.OrbitText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 土星进度页的两种气氛：扫描（光束扫过光环、碎片飘入）/ 清理（光环脉动、碎片螺旋坠入） */
enum class ProgressScene { SCAN, CLEAN }

/**
 * 原型的清理页（state = clean）：土星放大居中，下方计数，底部小面板放进度条。
 * 扫描气氛用于功能页入场的处理中动画。
 * @param value/unit 大号渐变数字，如 "72" + "%" 或 "1.2" + "GB"
 */
@Composable
fun SaturnProgressView(
    title: String,
    kicker: String,
    value: String,
    unit: String,
    caption: String,
    progress: Float,
    scene: ProgressScene,
    label: String,
    footer: String? = null,
    onBack: (() -> Unit)? = null,
) {
    val clean = scene == ProgressScene.CLEAN
    DesignFrame { m ->
        var sheetTop by remember { mutableStateOf<Float?>(null) }
        SaturnStage(
            view = sheetTop?.let {
                val shot = if (clean) SaturnShots.clean() else SaturnShots.scan(done = false)
                shot.placed(m.frameTop, it - m.frameTop, SaturnShots.CLEAN_SHEET_TOP)
            },
            particles = if (clean) ParticleMode.SPIRAL else ParticleMode.INFLOW,
        )
        Box(Modifier.fillMaxSize().saturnTilt(LocalSaturn.current)) {
            OrbitTopBar(title, m.topBar, onBack)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Kicker(kicker)
                    Spacer(Modifier.height(10.dp))
                    ValueText(value, unit)
                    Spacer(Modifier.height(12.dp))
                    Text(caption, style = OrbitText.Sub.copy(shadow = heroShadow()))
                }
                Spacer(Modifier.height(75.dp))
                GlassSheet(
                    Modifier.reportTop { sheetTop = it },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 24.dp + m.navBottom.dp),
                ) {
                    ProgressLine(progress, height = 6.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Hint(label)
                        Hint("${(progress * 100).toInt()}%")
                    }
                    if (footer != null) Hint(footer, Modifier.fillMaxWidth(), OrbitText.Hint.copy(textAlign = TextAlign.Center))
                }
            }
        }
    }
}

/** 功能页入场的处理中动画：百分比 + 状态文案 */
@Composable
fun ProcessingView(title: String, status: String, progress: Float, onBack: (() -> Unit)? = null) {
    SaturnProgressView(
        title = title,
        kicker = "Scanning",
        value = "${(progress * 100).toInt()}",
        unit = "%",
        caption = status,
        progress = progress,
        scene = ProgressScene.SCAN,
        label = title,
        onBack = onBack,
    )
}

/**
 * 功能页的“处理中动画 → 插屏 → 结果”流程：
 * 1. 动画至少播放 [AdPolicy.PROCESSING_MIN_MS]，且等到 [ready]（数据扫描完成）；
 * 2. 不在冷却期时继续转，等插屏加载好，总时长不超过 [AdPolicy.AD_WAIT_MAX_MS]；
 * 3. 弹插屏，关闭后显示 [content]。
 * [enabled] 为 false 时直接显示内容；已显示过内容后（如从下一页返回）不会再次播放。
 */
@Composable
fun ProcessingGate(
    enabled: Boolean,
    title: String,
    status: String,
    ready: Boolean,
    placement: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(!enabled) }
    if (revealed) {
        content()
        return
    }
    val activity = LocalActivity.current
    val progress = remember { Animatable(0f) }
    val currentReady by rememberUpdatedState(ready)
    LaunchedEffect(Unit) {
        val start = SystemClock.elapsedRealtime()
        progress.animateTo(0.9f, tween(AdPolicy.PROCESSING_MIN_MS.toInt(), easing = FastOutSlowInEasing))
        snapshotFlow { currentReady }.first { it }
        waitForAd(progress, AdPolicy.AD_WAIT_MAX_MS - (SystemClock.elapsedRealtime() - start))
        showInterstitial(activity, placement) { revealed = true }
    }
    ProcessingView(title, status, progress.value, onBack)
}

/** 等插屏加载期间进度缓慢爬到 99%，加载好（或超时、冷却期）后补到 100% */
suspend fun waitForAd(progress: Animatable<Float, AnimationVector1D>, timeoutMs: Long) {
    coroutineScope {
        val crawl = launch {
            progress.animateTo(0.99f, tween(timeoutMs.coerceAtLeast(1).toInt(), easing = LinearEasing))
        }
        awaitInterstitialReady(timeoutMs)
        crawl.cancel()
    }
    progress.animateTo(1f, tween(200))
}
