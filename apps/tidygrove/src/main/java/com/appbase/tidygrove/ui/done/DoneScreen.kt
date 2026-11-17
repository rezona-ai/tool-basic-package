/**
 * [INPUT]: 依赖 :features:cleaner 的 DoneViewModel / FileKind / Cleaner，依赖 ui/scene 的 CleanScene，依赖 data/Copy，
 *          依赖 :core:ads 的 AdBanner，依赖 ui/components 的 RecordCleanAndMaybeReview
 * [OUTPUT]: 对外提供 DoneScreen
 * [POS]: 完成页（变体：checklist 清单）：顶部一行小场景 + 标题，下面是一张逐条打勾的结果清单（清了哪些类别、删了几个文件、
 *        释放多少、现在占用多少），推荐用紧凑列表，主按钮回首页，底部固定横幅；进入时记一次清理并按规则请求评分。
 *        接口在所有完成页变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.done

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.DoneViewModel
import com.appbase.tidygrove.data.Copy
import com.appbase.tidygrove.ui.Placements
import com.appbase.tidygrove.ui.components.RecordCleanAndMaybeReview
import com.appbase.tidygrove.ui.components.icon
import com.appbase.tidygrove.ui.components.tint
import com.appbase.tidygrove.ui.motion.AnimatedCheck
import com.appbase.tidygrove.ui.motion.Motion
import com.appbase.tidygrove.ui.motion.animateCountUp
import com.appbase.tidygrove.ui.motion.enterUp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.appbase.tidygrove.ui.scene.CleanScene
import com.appbase.tidygrove.ui.scene.ScenePhase

@Composable
fun DoneScreen(cleaned: Set<FileKind>, freed: Long, count: Int, onHome: () -> Unit, onOpen: (FileKind) -> Unit) {
    val vm: DoneViewModel = viewModel { DoneViewModel(Cleaner.scans, cleaned, freed) }
    val recommends by vm.recommends.collectAsState()
    RecordCleanAndMaybeReview()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CleanScene(ScenePhase.DONE, 1f, Modifier.width(72.dp), compact = true) {
                    AnimatedCheck(Secondary, Modifier.size(30.dp), delayMs = 250)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Room to grow", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Here's what the sweep cleared", color = TextSecondary, fontSize = 13.sp)
                }
            }
            AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // 结果逐条打勾：每条错开出现，对勾随后描出
                    var line = 0
                    cleaned.sortedBy { it.ordinal }.forEach { CheckLine(Copy.title(it), index = line++) }
                    CheckLine("${plural(count, "file")} cleared away", index = line++)
                    val freedShown = animateCountUp(freed.toFloat(), durationMs = 900)
                    CheckLine("${formatSize(freedShown.toLong())} of space back", index = line++)
                    val after = (vm.storage.after * 100).toInt()
                    val before = (vm.storage.before * 100).toInt()
                    // 清理很少时前后百分比一样，不显示“原来”
                    CheckLine(if (before > after) "Now $after% used, down from $before%" else "Now $after% used", index = line, last = true)
                }
            }
            SectionTitle("Next patch to tidy")
            AppCard(Modifier.fillMaxWidth()) {
                recommends.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().enterUp(i + 5).clickable { onOpen(r.kind) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(r.kind.icon(), null, tint = r.kind.tint(), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(Copy.title(r.kind), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        r.found?.let { Text(formatSize(r.size ?: 0), color = TextSecondary, fontSize = 13.sp) }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
                    }
                    if (i < recommends.lastIndex) HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            GradientButton("Back to the grove", onClick = onHome)
        }
        AdBanner(Placements.DONE_BANNER, Modifier.fillMaxWidth().navigationBarsPadding())
    }
}

@Composable
private fun CheckLine(text: String, index: Int, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().enterUp(index).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Secondary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            AnimatedCheck(Secondary, Modifier.size(20.dp), delayMs = 200L + index * Motion.STAGGER_MS * 2)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp)
    }
    if (!last) HorizontalDivider(color = Line, modifier = Modifier.padding(start = 48.dp, end = 16.dp))
}
