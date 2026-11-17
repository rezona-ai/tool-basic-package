/**
 * [INPUT]: 依赖 :features:cleaner 的 StorageViewModel / FileKind / Cleaner，依赖 ui/scene 的 CleanScene，依赖 data/Copy 的类别文案，
 *          依赖 :core:ui 的通用组件，依赖 :core:ads 的 AdBanner
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: 首页（变体：grid 宫格）：顶部一张紧凑的用量卡（小场景 + 数字 + 扫描按钮），下面两列宫格放全部入口
 *        （三个清理工具、我的文件、设备信息、六个类型），底部固定横幅。接口在所有首页变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.home

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdBanner
import com.appbase.core.common.formatSize
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.FileKind
import com.appbase.feature.cleaner.state.StorageViewModel
import com.appbase.tidygrove.BuildConfig
import com.appbase.tidygrove.data.Copy
import com.appbase.tidygrove.ui.Placements
import com.appbase.tidygrove.ui.components.icon
import com.appbase.tidygrove.ui.components.tint
import com.appbase.tidygrove.ui.motion.animateCountUp
import com.appbase.tidygrove.ui.motion.enterUp
import com.appbase.tidygrove.ui.motion.pressScale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.appbase.tidygrove.ui.scene.CleanScene
import com.appbase.tidygrove.ui.scene.ScenePhase

private class Entry(val icon: ImageVector, val tint: Color, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onKind: (FileKind) -> Unit,
    onDeviceInfo: () -> Unit,
    onFiles: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
    val vm: StorageViewModel = viewModel { StorageViewModel(Cleaner.scans) }
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val storage = vm.storage
    // 第一次打开时用量从 0 滚到实际值；从子页面回来不重播
    var counted by rememberSaveable { mutableStateOf(false) }
    val firstShow = remember { !counted }
    val used = animateCountUp(storage.usedFraction, play = firstShow)
    LaunchedEffect(Unit) { counted = true }
    val tools = FileKind.CLEANABLE.map { Entry(it.icon(), it.tint(), Copy.title(it), Copy.blurb(it)) { onKind(it) } } +
        listOfNotNull(
            onFiles?.let { Entry(Icons.Rounded.Folder, Secondary, "Your folders", "Open, move and rename", it) },
            Entry(Icons.Rounded.PhoneAndroid, Primary, "Phone health", "Battery, memory, chip", onDeviceInfo),
        )
    val types = FileKind.BROWSABLE.map { Entry(it.icon(), it.tint(), Copy.title(it), "", { onKind(it) }) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(BuildConfig.APP_NAME, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppCard(Modifier.fillMaxWidth().enterUp(0, enabled = firstShow)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CleanScene(ScenePhase.IDLE, used, Modifier.width(112.dp), compact = true)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${(used * 100).toInt()}% used", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("${formatSize(storage.free)} still free", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            GradientButton("Start a sweep", height = 40.dp, onClick = onScan)
                        }
                    }
                }
            }
            itemsIndexed(tools) { i, e -> Tile(e, Modifier.enterUp(i + 1, enabled = firstShow)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Find by kind", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(types) { i, e -> Tile(e, Modifier.enterUp(i + tools.size + 2, enabled = firstShow)) }
        }
        AdBanner(Placements.HOME_BANNER, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Tile(e: Entry, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().pressScale(), onClick = e.onClick) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(e.icon, e.tint, 40.dp)
            Spacer(Modifier.height(10.dp))
            Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (e.subtitle.isNotEmpty()) {
                // 固定两行高度，同一行的两格一样高
                Text(e.subtitle, color = TextSecondary, fontSize = 12.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
