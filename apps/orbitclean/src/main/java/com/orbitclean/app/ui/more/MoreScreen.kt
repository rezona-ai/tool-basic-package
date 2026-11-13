/**
 * [INPUT]: 依赖 data 的 ScanRepository/SystemInfoRepository，依赖 core/ui 的 AppCard/GradientButton
 * [OUTPUT]: 对外提供 MoreScreen
 * [POS]: ui/more 的更多页：文件管理器入口、系统信息宫格、按文件类型分类入口（含最近文件）、Banner
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ads.AdBanner
import com.appbase.core.ads.AdPlacement
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.color
import com.appbase.core.ui.icon
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextSecondary
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.Placements
import com.orbitclean.app.ui.components.color
import com.orbitclean.app.ui.components.icon
import java.util.Locale
import kotlinx.coroutines.delay

private val categories = listOf(FileKind.IMAGES, FileKind.VIDEOS, FileKind.AUDIO, FileKind.DOCS, FileKind.ZIPS, FileKind.RECENT)

private fun gb(bytes: Long) = String.format(Locale.US, "%.1fG", bytes / (1024.0 * 1024 * 1024))

@Composable
fun MoreScreen(onBrowse: () -> Unit, onCategory: (FileKind) -> Unit, onDeviceInfo: () -> Unit) {
    val fileRepo = App.instance.scans
    val sysRepo = App.instance.systemInfoRepository
    val storage = remember { fileRepo.store.storageInfo() }
    val results by fileRepo.results.collectAsState()
    val initialInfo = remember { sysRepo.read() }
    val info by produceState(initialInfo) {
        while (true) {
            delay(3000)
            value = sysRepo.read()
        }
    }
    LaunchedEffect(Unit) { categories.forEach { fileRepo.ensureFresh(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("More", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { storage.usedFraction },
                        modifier = Modifier.size(76.dp),
                        color = Primary,
                        trackColor = Primary.copy(alpha = 0.15f),
                        strokeWidth = 7.dp,
                    )
                    Text("${(storage.usedFraction * 100).toInt()}%", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("File Manager", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${formatSize(storage.used)} / ${formatSize(storage.total)}", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    GradientButton("Open", Modifier.width(110.dp), height = 36.dp, onClick = onBrowse)
                }
                Icon(Icons.Rounded.Storage, null, tint = Primary.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
            }
        }

        SectionTitle("System Info")
        GridRow(
            listOf(
                GridItem(Icons.Rounded.Memory, Secondary, "Cpu", "Cores: ${info.cpuCores}", onDeviceInfo),
                GridItem(Icons.Rounded.BatteryChargingFull, Primary, "Battery", "${info.batteryPercent}%", onDeviceInfo),
                GridItem(Icons.Rounded.DeveloperBoard, Secondary, "RAM", "${gb(info.ramUsed)}/${gb(info.ramTotal)}", onDeviceInfo),
            )
        )

        SectionTitle("Files")
        categories.chunked(3).forEach { row ->
            GridRow(
                row.map { kind ->
                    val groups = results[kind]
                    val subtitle = groups?.let { g -> plural(g.sumOf { it.items.size }, "file") } ?: "…"
                    GridItem(kind.icon(), kind.color(), kind.title, subtitle) { onCategory(kind) }
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        AdBanner(Placements.MORE_BANNER)
    }
}

private class GridItem(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun GridRow(items: List<GridItem>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            AppCard(Modifier.weight(1f), onClick = item.onClick) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(item.icon, item.color)
                    Spacer(Modifier.height(8.dp))
                    Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(item.subtitle, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        repeat(3 - items.size) { Spacer(Modifier.weight(1f)) }
    }
}
