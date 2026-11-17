/**
 * [INPUT]: 依赖 :features:cleaner 的 ScanViewModel / Cleaner，依赖 ./ScanReveal 的揭晓节奏，依赖 ui/scene 的 CleanScene，
 *          依赖 ui/motion 的 enterUp / pressScale，依赖 data/Copy，依赖 :core:ads 的 showInterstitial，依赖 :core:ui 的通用组件
 * [OUTPUT]: 对外提供 ScanScreen
 * [POS]: 扫描页（变体：stepwise 分步）：大场景扫描，三类结果逐行揭晓、总数滚动；全部揭晓后弹一次扫描完成插屏；
 *        用户点某一类进入类别页逐个勾选再删除（删除和完成页在类别页里走）。接口在所有清理流程变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.scan

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.showInterstitial
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.CleanResult
import com.appbase.feature.cleaner.state.ScanRow
import com.appbase.feature.cleaner.state.ScanViewModel
import {{CODE_PACKAGE}}.data.Copy
import {{CODE_PACKAGE}}.ui.Placements
import {{CODE_PACKAGE}}.ui.components.icon
import {{CODE_PACKAGE}}.ui.components.tint
import {{CODE_PACKAGE}}.ui.motion.enterUp
import {{CODE_PACKAGE}}.ui.motion.pressScale
import {{CODE_PACKAGE}}.ui.scene.CleanScene
import {{CODE_PACKAGE}}.ui.scene.ScenePhase

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScanScreen(onBack: () -> Unit, onOpenKind: (FileKind) -> Unit, onCleaned: (Set<FileKind>, CleanResult) -> Unit) {
    val vm: ScanViewModel = viewModel { ScanViewModel(Cleaner.scans, createSavedStateHandle()) }
    val ui by vm.ui.collectAsState()
    val reveal = rememberScanReveal(ui)
    val activity = LocalActivity.current
    // 全部揭晓后弹一次（进程恢复、从类别页返回都不再弹）
    var adShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(reveal.done) {
        if (reveal.done && !adShown) {
            adShown = true
            showInterstitial(activity, Placements.SCAN_DONE_INTERSTITIAL)
        }
    }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Scan", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            CleanScene(
                if (reveal.done) ScenePhase.DONE else ScenePhase.SCANNING,
                reveal.sceneProgress,
                Modifier.fillMaxWidth(0.66f).align(Alignment.CenterHorizontally),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatSize((ui.junkBytes * reveal.ease).toLong()), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(if (reveal.done) "ready to remove" else "checking…", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.size(16.dp))
            Text(
                if (reveal.done) "Pick a category to review and remove files" else "Looking through the folders you've shared",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                ui.rows.forEachIndexed { i, row ->
                    ResultRow(row, reveal.rowDone(i), Modifier.enterUp(i)) { onOpenKind(row.kind) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(row: ScanRow, done: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier.fillMaxWidth().then(if (done) Modifier.pressScale() else Modifier), onClick = if (done) onClick else null) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(row.kind.icon(), row.kind.tint())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Copy.title(row.kind), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    if (done) "${plural(row.count ?: 0, "file")} · ${formatSize(row.size ?: 0)}" else "Scanning…",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            RowStatus(done)
            if (done) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}
