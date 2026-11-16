/**
 * [INPUT]: 依赖 :features:cleaner 的 StorageViewModel / FileKind / Cleaner，依赖 ui/scene 的 CleanScene，依赖 data/Copy 的类别文案，
 *          依赖 :core:ui 的通用组件，依赖 :core:ads 的 AdBanner
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: 首页（变体：grid 宫格）：顶部一张紧凑的用量卡（小场景 + 数字 + 扫描按钮），下面两列宫格放全部入口
 *        （三个清理工具、我的文件、设备信息、六个类型），底部固定横幅。接口在所有首页变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.home

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
import androidx.compose.foundation.lazy.grid.items
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
import {{CODE_PACKAGE}}.BuildConfig
import {{CODE_PACKAGE}}.data.Copy
import {{CODE_PACKAGE}}.ui.Placements
import {{CODE_PACKAGE}}.ui.components.icon
import {{CODE_PACKAGE}}.ui.components.tint
import {{CODE_PACKAGE}}.ui.scene.CleanScene
import {{CODE_PACKAGE}}.ui.scene.ScenePhase

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
    val tools = FileKind.CLEANABLE.map { Entry(it.icon(), it.tint(), Copy.title(it), Copy.blurb(it)) { onKind(it) } } +
        listOfNotNull(
            onFiles?.let { Entry(Icons.Rounded.Folder, Secondary, "My files", "Browse and organize", it) },
            Entry(Icons.Rounded.PhoneAndroid, Primary, "This phone", "Memory and battery", onDeviceInfo),
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
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CleanScene(ScenePhase.IDLE, storage.usedFraction, Modifier.width(112.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${(storage.usedFraction * 100).toInt()}% full", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("${formatSize(storage.free)} left", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            GradientButton("Scan", height = 40.dp, onClick = onScan)
                        }
                    }
                }
            }
            items(tools) { Tile(it) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("By type", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items(types) { Tile(it) }
        }
        AdBanner(Placements.HOME_BANNER, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Tile(e: Entry) {
    AppCard(Modifier.fillMaxWidth(), onClick = e.onClick) {
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
