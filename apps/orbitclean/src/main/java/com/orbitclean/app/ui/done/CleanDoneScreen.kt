/**
 * [INPUT]: 依赖 ./CleanDoneViewModel 的 storage/recommends，依赖 ui/saturn 的 SaturnStage/SaturnShots，依赖 ui/components 的 DesignFrame 与原型积木，依赖 ads 的 AdBanner
 * [OUTPUT]: 对外提供 CleanDoneScreen
 * [POS]: ui/done 的清理完成页，逐项对应原型 state = done：土星缩小上移（cy 148）、冲击波沿光环扩散、碎片迸发；
 *        面板里依次是清理前后用量、KEEP CLEANING 两条推荐、Banner、回首页；进入后按 ./ReviewPrompt 的规则请求一次 Play 应用内评分
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.done

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdBanner
import com.appbase.core.ads.AdPlacement
import com.appbase.core.common.ReviewPrompt
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.color
import com.appbase.core.ui.icon
import com.appbase.core.ui.theme.CardBg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.ProgressGradient
import com.appbase.core.ui.theme.Secondary
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.Placements
import com.orbitclean.app.ui.components.BannerSlot
import com.orbitclean.app.ui.components.CheckPill
import com.orbitclean.app.ui.components.DesignFrame
import com.orbitclean.app.ui.components.GlassSheet
import com.orbitclean.app.ui.components.Gutter
import com.orbitclean.app.ui.components.Kicker
import com.orbitclean.app.ui.components.OrbitButton
import com.orbitclean.app.ui.components.OrbitRow
import com.orbitclean.app.ui.components.OrbitTopBar
import com.orbitclean.app.ui.components.ValueText
import com.orbitclean.app.ui.components.color
import com.orbitclean.app.ui.components.heroShadow
import com.orbitclean.app.ui.components.icon
import com.orbitclean.app.ui.components.reportTop
import com.orbitclean.app.ui.components.splitSize
import com.orbitclean.app.ui.saturn.LocalSaturn
import com.orbitclean.app.ui.saturn.ParticleMode
import com.orbitclean.app.ui.saturn.SaturnShots
import com.orbitclean.app.ui.saturn.SaturnStage
import com.orbitclean.app.ui.saturn.saturnTilt
import com.orbitclean.app.ui.theme.OrbitText
import kotlinx.coroutines.delay

@Composable
fun CleanDoneScreen(
    kind: FileKind,
    freed: Long,
    count: Int,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpen: (FileKind) -> Unit,
) {
    val vm: CleanDoneViewModel = viewModel(key = "$kind/$freed") { CleanDoneViewModel(App.instance.scans, kind, freed) }
    val recommends by vm.recommends.collectAsStateWithLifecycle()

    // 应用内评分：每个完成页只处理一次（重组、旋转、从推荐页返回都不重复计数）；离开页面会取消等待
    val activity = LocalActivity.current
    var reviewHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (reviewHandled || count <= 0 || activity == null) return@LaunchedEffect
        reviewHandled = true
        val cleans = ReviewPrompt.recordClean(activity)
        delay(ReviewPrompt.DELAY_MS)
        ReviewPrompt.maybeAsk(activity, cleans)
    }

    DesignFrame { m ->
        var sheetTop by remember { mutableStateOf<Float?>(null) }
        SaturnStage(
            view = sheetTop?.let { SaturnShots.done(vm.storage.after).placed(m.frameTop, it - m.frameTop, SaturnShots.DONE_SHEET_TOP) },
            particles = ParticleMode.BURST,
            shockwave = true,
        )
        Box(Modifier.fillMaxSize().saturnTilt(LocalSaturn.current)) {
            OrbitTopBar("Result", m.topBar, onBack)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Kicker("All clean")
                    Spacer(Modifier.height(10.dp))
                    val (value, unit) = splitSize(freed)
                    ValueText(value, unit)
                    Spacer(Modifier.height(12.dp))
                    Text("freed · ${plural(count, "item")} removed", style = OrbitText.Sub.copy(shadow = heroShadow()))
                }
                Spacer(Modifier.height(21.dp))
                GlassSheet(
                    Modifier.reportTop { sheetTop = it },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp + m.navBottom.dp),
                ) {
                    StorageCard(vm.storage)
                    // .h3 的 margin-bottom: -6px：标题与第一条推荐间距 10，推荐之间仍是 16
                    Column {
                        Kicker("Keep cleaning")
                        recommends.take(2).forEachIndexed { i, rec ->
                            Spacer(Modifier.height(if (i == 0) 10.dp else 16.dp))
                            OrbitRow(
                                icon = rec.kind.icon(),
                                accent = rec.kind.color(),
                                title = rec.kind.title,
                                subtitle = if (rec.found != null) "${plural(rec.found, "item")} · ${formatSize(rec.size ?: 0)} found" else rec.kind.subtitle,
                                padding = PaddingValues(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                onClick = { onOpen(rec.kind) },
                            ) { CheckPill() }
                        }
                    }
                    Column {
                        BannerSlot { AdBanner(Placements.CLEAN_DONE_BANNER, it) }
                        OrbitButton("Back to Home", onHome, primary = false)
                    }
                }
            }
        }
    }
}

/** .store：清理前后用量对比；淡金底条是清理前，金青渐变是现在 */
@Composable
private fun StorageCard(delta: StorageDelta) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Line, shape)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text("Storage", style = OrbitText.Small)
            Text(
                buildAnnotatedString {
                    append("${(delta.before * 100).toInt()}% → ")
                    withStyle(SpanStyle(color = Primary, fontWeight = FontWeight.SemiBold)) { append("${(delta.after * 100).toInt()}%") }
                },
                style = OrbitText.Small,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Line),
        ) {
            Box(Modifier.fillMaxWidth(delta.before).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Secondary.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(delta.after).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(ProgressGradient))
        }
    }
}
