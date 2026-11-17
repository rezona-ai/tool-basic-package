/**
 * [INPUT]: 依赖 :features:cleaner 的 StorageViewModel / FileKind / Cleaner，依赖 ui/scene 的 CleanScene，依赖 data/Copy 的类别文案，
 *          依赖 :core:ui 的通用组件，依赖 :core:ads 的 AdBanner
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: 首页（变体：dashboard 仪表盘）：大场景显示存储占用 + 扫描主按钮，下面是三个清理工具列表、按类型浏览、
 *        我的文件和设备信息入口，底部固定横幅。接口在所有首页变体里一致
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.appbase.core.ads.AdBanner
import com.appbase.core.common.formatSize
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.CardBg
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
import {{CODE_PACKAGE}}.ui.motion.animateCountUp
import {{CODE_PACKAGE}}.ui.motion.enterUp
import {{CODE_PACKAGE}}.ui.motion.pressScale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import {{CODE_PACKAGE}}.ui.scene.CleanScene
import {{CODE_PACKAGE}}.ui.scene.ScenePhase

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
    // 第一次打开时用量从 0 滚到实际值、入口依次浮现；从子页面回来不重播
    var counted by rememberSaveable { mutableStateOf(false) }
    val firstShow = remember { !counted }
    val used = animateCountUp(storage.usedFraction, play = firstShow)
    LaunchedEffect(Unit) { counted = true }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(BuildConfig.APP_NAME, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }
            CleanScene(ScenePhase.IDLE, used, Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(used * 100).toInt()}%", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("in use", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Text(
                "${formatSize(storage.free)} free of ${formatSize(storage.total)}",
                color = TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
            )
            GradientButton("Scan my folders", onClick = onScan)
            SectionTitle("Clean up")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FileKind.CLEANABLE.forEachIndexed { i, kind ->
                    EntryRow(kind.icon(), kind.tint(), Copy.title(kind), Copy.blurb(kind), Modifier.enterUp(i, firstShow)) { onKind(kind) }
                }
            }
            SectionTitle("Browse by type")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                itemsIndexed(FileKind.BROWSABLE) { i, kind -> TypeChip(kind, Modifier.enterUp(i + 3, firstShow)) { onKind(kind) } }
            }
            SectionTitle("More")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                if (onFiles != null) EntryRow(Icons.Rounded.Folder, Secondary, "My files", "Browse, move and rename", Modifier.enterUp(9, firstShow), onFiles)
                EntryRow(Icons.Rounded.PhoneAndroid, Primary, "About this phone", "Storage, memory, battery", Modifier.enterUp(10, firstShow), onDeviceInfo)
            }
        }
        AdBanner(Placements.HOME_BANNER, Modifier.fillMaxWidth())
    }
}

@Composable
private fun EntryRow(icon: ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier.fillMaxWidth().pressScale(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}

@Composable
private fun TypeChip(kind: FileKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .pressScale()
            .clip(RoundedCornerShape(BrandTheme.style.corner(16.dp)))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(kind.icon(), kind.tint(), 40.dp)
        Spacer(Modifier.height(6.dp))
        Text(Copy.title(kind), fontSize = 12.sp)
    }
}
