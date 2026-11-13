/**
 * [INPUT]: 依赖 ./ScanViewModel 的 ScanUi，依赖 ui/saturn 的 SaturnStage/SaturnShots，依赖 ui/components 的 DesignFrame 与原型积木，依赖 ads 的插屏
 * [OUTPUT]: 对外提供 JunkScanScreen
 * [POS]: ui/scan 的一键扫描页，逐项对应原型 state = scan：土星居中（cx 195 / cy 262）、雷达光束扫过光环、碎片全程飘入；扫描演出固定 4.2 秒；
 *        下方滚动累计可清理空间，面板里逐类给出结果；扫描完成时弹一次插屏，点某一类进入对应文件列表
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.scan

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdPlacement
import com.appbase.core.ads.showInterstitial
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.color
import com.appbase.core.ui.icon
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.Placements
import com.orbitclean.app.ui.components.DesignFrame
import com.orbitclean.app.ui.components.GlassSheet
import com.orbitclean.app.ui.components.Gutter
import com.orbitclean.app.ui.components.Kicker
import com.orbitclean.app.ui.components.OrbitButton
import com.orbitclean.app.ui.components.OrbitRow
import com.orbitclean.app.ui.components.OrbitTopBar
import com.orbitclean.app.ui.components.ProgressLine
import com.orbitclean.app.ui.components.RowValue
import com.orbitclean.app.ui.components.ValueText
import com.orbitclean.app.ui.components.color
import com.orbitclean.app.ui.components.icon
import com.orbitclean.app.ui.components.reportTop
import com.orbitclean.app.ui.components.splitSize
import com.orbitclean.app.ui.saturn.LocalSaturn
import com.orbitclean.app.ui.saturn.ParticleMode
import com.orbitclean.app.ui.saturn.SaturnShots
import com.orbitclean.app.ui.saturn.SaturnStage
import com.orbitclean.app.ui.saturn.saturnTilt
import com.orbitclean.app.ui.theme.OrbitText

@Composable
fun JunkScanScreen(onBack: () -> Unit, onOpen: (FileKind) -> Unit) {
    val vm: ScanViewModel = viewModel { ScanViewModel(App.instance.scans, createSavedStateHandle()) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    // 原型的扫描演出固定 4.2 秒：计数缓出滚动、三行进度错开推进；真实结果到了且演出播完才算完成。
    // 从文件列表返回时不再重播
    var played by rememberSaveable { mutableStateOf(false) }
    val show = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        show.animateTo(1f, tween(SCAN_SHOW_MS, easing = LinearEasing))
        played = true
    }
    val p = show.value
    val rows = ui.rows.mapIndexed { i, row -> ShownRow(row, (p * (1.6f - 0.3f * i)).coerceAtMost(1f)) }
    val done = ui.done && p >= 1f

    val activity = LocalActivity.current
    var adShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (done && !adShown) {
            adShown = true
            showInterstitial(activity, Placements.SCAN_DONE_INTERSTITIAL)
        }
    }
    val ease = 1f - (1f - p) * (1f - p) * (1f - p)

    DesignFrame { m ->
        var sheetTop by remember { mutableStateOf<Float?>(null) }
        SaturnStage(
            view = sheetTop?.let { SaturnShots.scan(done).placed(m.frameTop, it - m.frameTop, SaturnShots.SCAN_SHEET_TOP) },
            // 原型 state = scan 全程每秒 16 个碎片飘入，完成后也不停
            particles = ParticleMode.INFLOW,
        )
        Box(Modifier.fillMaxSize().saturnTilt(LocalSaturn.current)) {
            OrbitTopBar("Smart Scan", m.topBar, onBack)
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Column(Modifier.padding(horizontal = Gutter)) {
                    Kicker(if (done) "Can be freed" else "Scanning storage")
                    Spacer(Modifier.height(10.dp))
                    val (value, unit) = splitSize((ui.junkBytes * ease).toLong())
                    ValueText(value, unit)
                    Spacer(Modifier.height(12.dp))
                    Text(statusLine(rows, done, ui.itemCount), style = OrbitText.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(22.dp))
                GlassSheet(
                    Modifier.reportTop { sheetTop = it },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp + m.navBottom.dp),
                ) {
                    rows.forEach { ScanResultRow(it, onOpen) }
                    // 原型：扫描中是禁用的描边按钮，完成后变成渐变主按钮；中途退出走左上角返回
                    OrbitButton(if (done) "Done" else "Scanning…", onBack, primary = done, enabled = done)
                }
            }
        }
    }
}

private const val SCAN_SHOW_MS = 4200

/** 一行的展示状态：演出进度 [shown] 走满且真实结果已到，才显示数量和大小 */
private class ShownRow(val row: ScanRow, val shown: Float) {
    val done: Boolean get() = row.done && shown >= 1f
}

/** 扫描中指出正在找哪一类；完成后给出总数 */
private fun statusLine(rows: List<ShownRow>, done: Boolean, itemCount: Int): String {
    val pending = rows.firstOrNull { !it.done }
    return if (done || pending == null) {
        "${plural(itemCount, "item")} in ${rows.size} categories"
    } else {
        "Looking for ${pending.row.kind.title.lowercase()}…"
    }
}

/** .row：扫描中显示百分比与进度，完成后显示数量与大小（与原型 setRow 一致） */
@Composable
private fun ScanResultRow(item: ShownRow, onOpen: (FileKind) -> Unit) {
    val row = item.row
    OrbitRow(
        icon = row.kind.icon(),
        accent = row.kind.color(),
        title = row.kind.title,
        subtitle = if (item.done) plural(row.count ?: 0, "item") else "Scanning… ${(item.shown * 100).toInt()}%",
        progress = { ProgressLine(if (item.shown < 1f || row.done) item.shown else null) },
        onClick = if (item.done) ({ onOpen(row.kind) }) else null,
    ) {
        if (item.done && row.size != null) RowValue(formatSize(row.size))
    }
}
