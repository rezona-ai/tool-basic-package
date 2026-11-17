/**
 * [INPUT]: 依赖 :features:cleaner 的 DoneViewModel / FileKind / Cleaner，依赖 ui/scene 的 CleanScene，依赖 data/Copy，
 *          依赖 :core:ads 的 AdBanner，依赖 ui/components 的 UsageBar / RecordCleanAndMaybeReview
 * [OUTPUT]: 对外提供 DoneScreen
 * [POS]: 完成页（变体：summary 汇总）：大场景 DONE + 释放的空间大数字、清理前后用量两条对比、继续清理推荐卡片、回首页，
 *        底部固定横幅；进入时记一次清理并按规则请求应用内评分。接口在所有完成页变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.done

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdBanner
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.DoneViewModel
import {{CODE_PACKAGE}}.data.Copy
import {{CODE_PACKAGE}}.ui.Placements
import {{CODE_PACKAGE}}.ui.components.RecordCleanAndMaybeReview
import {{CODE_PACKAGE}}.ui.components.UsageBar
import {{CODE_PACKAGE}}.ui.components.icon
import {{CODE_PACKAGE}}.ui.components.tint
import {{CODE_PACKAGE}}.ui.motion.animateCountUp
import {{CODE_PACKAGE}}.ui.motion.enterUp
import {{CODE_PACKAGE}}.ui.motion.pressScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import {{CODE_PACKAGE}}.ui.scene.CleanScene
import {{CODE_PACKAGE}}.ui.scene.ScenePhase

@Composable
fun DoneScreen(cleaned: Set<FileKind>, freed: Long, count: Int, onHome: () -> Unit, onOpen: (FileKind) -> Unit) {
    val vm: DoneViewModel = viewModel { DoneViewModel(Cleaner.scans, cleaned, freed) }
    val recommends by vm.recommends.collectAsState()
    RecordCleanAndMaybeReview()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            CleanScene(ScenePhase.DONE, 1f, Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 释放的空间从 0 滚到实际值
                    Text(formatSize(animateCountUp(freed.toFloat()).toLong()), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("cleared", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Text(
                "${plural(count, "file")} removed",
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
            )
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Before  ${(vm.storage.before * 100).toInt()}%", fontSize = 13.sp, color = TextSecondary)
                    UsageBar(vm.storage.before)
                    Text("Now  ${(vm.storage.after * 100).toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    // “现在”这条从清理前的长度缩到清理后，直观看到腾出的空间
                    var shrink by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { shrink = true }
                    UsageBar(if (shrink) vm.storage.after else vm.storage.before)
                }
            }
            SectionTitle("Keep going")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recommends.forEachIndexed { i, r ->
                    AppCard(Modifier.fillMaxWidth().enterUp(i + 2).pressScale(), onClick = { onOpen(r.kind) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(r.kind.icon(), r.kind.tint())
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Copy.title(r.kind), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (r.found != null) "${plural(r.found!!, "file")} · ${formatSize(r.size ?: 0)}" else Copy.blurb(r.kind),
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            GradientButton("Back to home", onClick = onHome)
        }
        AdBanner(Placements.DONE_BANNER, Modifier.fillMaxWidth())
    }
}
