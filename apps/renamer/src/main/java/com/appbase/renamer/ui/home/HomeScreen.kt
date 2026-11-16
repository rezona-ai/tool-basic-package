/**
 * [INPUT]: 依赖 data 的 RuleType / HistoryStore，依赖 ui/components 的 RenameUi，依赖 :core:ui 组件与颜色 token，依赖 :core:ads 的 AdBanner
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: ui/home 的首页：五种改名规则（每种带一个改名示例）+ 最近的改名批次（可点开查看、撤销）；横幅固定在底部。
 *        首页不是文件浏览器：文件夹在规则页里选
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
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
import com.appbase.core.CoreServices
import com.appbase.core.ads.AdBanner
import com.appbase.core.storage.files
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextDim
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.renamer.BuildConfig
import com.appbase.renamer.data.Batch
import com.appbase.renamer.data.HistoryStore
import com.appbase.renamer.data.RuleType
import com.appbase.renamer.ui.Placements
import com.appbase.renamer.ui.components.MonoText
import com.appbase.renamer.ui.components.StepLabel
import com.appbase.renamer.ui.components.icon

@Composable
fun HomeScreen(onRule: (RuleType) -> Unit, onOpenBatch: (Long) -> Unit) {
    val batches by HistoryStore.batches.collectAsState()
    Column(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(BuildConfig.APP_NAME, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(BuildConfig.APP_TAGLINE, color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("PICK A RULE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Primary, modifier = Modifier.padding(top = 14.dp))
            }
            items(RuleType.entries) { rule -> RuleCard(rule) { onRule(rule) } }
            item {
                Text("RECENT RENAMES", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Primary, modifier = Modifier.padding(top = 14.dp))
            }
            if (batches.isEmpty()) {
                item {
                    Text("Each batch you rename shows up here, so you can check it or undo it later.", color = TextDim, fontSize = 14.sp)
                }
            } else {
                items(batches.take(10), key = { it.id }) { b -> BatchRow(b) { onOpenBatch(b.id) } }
            }
        }
        AdBanner(Placements.HOME_BANNER, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RuleCard(rule: RuleType, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(rule.icon(), Primary, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(rule.summary, fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                MonoText(rule.example, color = Secondary, size = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextDim)
        }
    }
}

@Composable
private fun BatchRow(batch: Batch, onClick: () -> Unit) {
    val access = CoreServices.files.access
    val now = System.currentTimeMillis()
    val time = if (now - batch.createdAt < DateUtils.MINUTE_IN_MILLIS) "Just now"
    else DateUtils.getRelativeTimeSpanString(batch.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.History, if (batch.undone) TextSecondary else Secondary, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val n = batch.ops.size
                Text("$n file${if (n == 1) "" else "s"} · ${batch.rule.label}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(
                    access.display(batch.folder) + " · " + time + if (batch.undone) " · undone" else "",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
