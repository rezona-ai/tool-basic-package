/**
 * [INPUT]: 依赖 data 的 ScanRepository（storageInfo / results / ensureFresh），依赖 core/ui 的 AppCard/IconBadge/SectionTitle
 * [OUTPUT]: 对外提供 FilesOverview
 * [POS]: ui/browser 的文件管理首页头部（只在存储根目录显示）：存储用量卡、按类型浏览、清理工具入口，下面接 "Folders"（用户授权的文件夹）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.common.formatSize
import com.appbase.core.common.plural
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.color
import com.appbase.core.ui.icon
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextSecondary
import com.orbitclean.app.App
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.components.color
import com.orbitclean.app.ui.components.icon

/** 按类型浏览的入口，两行三列 */
private val CATEGORIES = listOf(FileKind.IMAGES, FileKind.VIDEOS, FileKind.AUDIO, FileKind.DOCS, FileKind.ZIPS, FileKind.RECENT)

@Composable
fun FilesOverview(onCategory: (FileKind) -> Unit, onCleanup: () -> Unit) {
    val repo = App.instance.scans
    val storage = remember { repo.store.storageInfo() }
    val results by repo.results.collectAsState()
    LaunchedEffect(Unit) { CATEGORIES.forEach { repo.ensureFresh(it) } }

    Column {
        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.PhoneAndroid, Primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Internal storage", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "${formatSize(storage.used)} used of ${formatSize(storage.total)} · ${formatSize(storage.free)} free",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { storage.usedFraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Primary,
                        trackColor = Primary.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                    )
                }
            }
        }

        SectionTitle("Browse by type")
        CATEGORIES.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    val count = results[kind]?.let { g -> plural(g.sumOf { it.items.size }, "file") } ?: "…"
                    AppCard(Modifier.weight(1f), onClick = { onCategory(kind) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconBadge(kind.icon(), kind.color(), size = 38.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(kind.title.removeSuffix(" Files"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                            Text(count, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        AppCard(Modifier.fillMaxWidth(), onClick = onCleanup) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.CleaningServices, Secondary, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Storage cleanup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Large, duplicate and leftover files", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
            }
        }

        SectionTitle("Folders")
    }
}
