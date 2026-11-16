/**
 * [INPUT]: 依赖 data 的 HistoryStore / Renamer，依赖 ui/components，依赖 :core:ui 组件，依赖 :core:ads 的 awaitInterstitial，依赖 :core:common 的 ReviewPrompt
 * [OUTPUT]: 对外提供 ResultScreen
 * [POS]: ui/result 的结果页（刚改完名，或从首页的最近记录打开）：改了多少、失败多少、每个文件的旧名 → 新名，可以撤销整批。
 *        fresh=true（刚完成一次改名）时，结果显示出来后弹一次插屏，再按规则请求应用内评分；从记录打开、撤销时都不弹
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.result

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.CoreServices
import com.appbase.core.ads.awaitInterstitial
import com.appbase.core.common.ReviewPrompt
import com.appbase.core.storage.files
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.renamer.data.HistoryStore
import com.appbase.renamer.data.RenameOp
import com.appbase.renamer.data.Renamer
import com.appbase.renamer.ui.Placements
import com.appbase.renamer.ui.components.NameChange
import com.appbase.renamer.ui.components.SecondaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 结果先显示一会儿再弹插屏，让用户看到已经改好了 */
private const val RESULT_VISIBLE_BEFORE_AD_MS = 700L

@Composable
fun ResultScreen(batchId: Long, fresh: Boolean, onDone: () -> Unit) {
    val batches by HistoryStore.batches.collectAsState()
    val batch = batches.firstOrNull { it.id == batchId }
    if (batch == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adDone by rememberSaveable(batchId) { mutableStateOf(!fresh) }
    var undoing by remember { mutableStateOf(false) }

    LaunchedEffect(batchId) {
        if (adDone || activity == null) return@LaunchedEffect
        delay(RESULT_VISIBLE_BEFORE_AD_MS)
        awaitInterstitial(activity, Placements.RESULT_INTERSTITIAL)
        adDone = true
        // 完成一次核心操作（一批改名）记一次，满足条件时请求 Play 应用内评分
        val count = ReviewPrompt.recordClean(activity)
        delay(ReviewPrompt.DELAY_MS)
        ReviewPrompt.maybeAsk(activity, count)
    }

    val access = CoreServices.files.access
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(if (batch.undone) "Undone" else "Renamed", onBack = onDone)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val n = batch.ops.size
                            Text(
                                if (batch.undone) "Original names restored" else "$n file${if (n == 1) "" else "s"} renamed",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (batch.undone) Secondary else TextPrimary,
                            )
                            Text("${batch.rule.label} · ${access.display(batch.folder)}", color = TextSecondary, fontSize = 13.sp)
                            if (batch.failed > 0) {
                                Text("${batch.failed} file${if (batch.failed == 1) "" else "s"} couldn't be renamed and kept the old name.", color = Danger, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                items(batch.ops, key = { it.oldName }) { op ->
                    val shown = if (batch.undone) RenameOp(op.newName, op.oldName) else op
                    NameChange(shown.oldName, shown.newName, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    HorizontalDivider(color = Line)
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!batch.undone && batch.ops.isNotEmpty()) {
                    SecondaryButton("Undo", enabled = !undoing) {
                        if (access.uriOf(batch.folder) == null) {
                            Toast.makeText(context, "Access to this folder was removed. Allow it again in Settings → Folder access.", Toast.LENGTH_LONG).show()
                            return@SecondaryButton
                        }
                        undoing = true
                        scope.launch {
                            val outcome = Renamer.run(batch.folder, batch.ops.map { RenameOp(it.newName, it.oldName) })
                            undoing = false
                            if (outcome.failed.isEmpty()) {
                                HistoryStore.markUndone(batch.id)
                            } else {
                                Toast.makeText(context, "${outcome.failed.size} file(s) couldn't be changed back. They may have been moved or renamed.", Toast.LENGTH_LONG).show()
                                if (outcome.done.isNotEmpty()) HistoryStore.markUndone(batch.id)
                            }
                        }
                    }
                }
                GradientButton("Done", onClick = onDone)
            }
        }
        if (undoing) {
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
    }
}
