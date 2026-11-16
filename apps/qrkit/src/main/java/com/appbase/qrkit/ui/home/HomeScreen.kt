/**
 * [INPUT]: 依赖 data 的 HistoryStore / QrImages，依赖 ui/components，依赖 :core:ui 组件，依赖 :core:ads 的 AdBanner，依赖 :core:common 的 ExternalLaunch
 * [OUTPUT]: 对外提供 HomeScreen
 * [POS]: ui/home 的首页：两个核心入口（生成二维码、从照片识别）+ 快捷类型 + 最近记录；横幅固定在底部，不夹在内容之间。
 *        识别：系统照片选择器选图（无需权限）→ 后台识别 → 找到交给 onScanned，找不到提示原因
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.ui.home

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ads.AdBanner
import com.appbase.core.common.ExternalLaunch
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextDim
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.qrkit.BuildConfig
import com.appbase.qrkit.data.HistoryEntry
import com.appbase.qrkit.data.HistoryKind
import com.appbase.qrkit.data.HistoryStore
import com.appbase.qrkit.data.QrImages
import com.appbase.qrkit.data.QrType
import com.appbase.qrkit.ui.Placements
import com.appbase.qrkit.ui.components.TypeChip
import com.appbase.qrkit.ui.components.icon
import com.appbase.qrkit.ui.components.tint
import com.appbase.qrkit.ui.create.CREATABLE_TYPES
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onCreate: (QrType) -> Unit, onScanned: (raw: String) -> Unit, onOpen: (id: Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by HistoryStore.entries.collectAsState()
    var reading by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        reading = true
        scope.launch {
            val raw = QrImages.decodeFromUri(context, uri)
            reading = false
            if (raw.isNullOrEmpty()) {
                Toast.makeText(context, "No QR code found. Try a sharper photo with the whole code in view.", Toast.LENGTH_LONG).show()
            } else {
                onScanned(raw)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(BuildConfig.APP_NAME, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(BuildConfig.APP_TAGLINE, color = TextSecondary, fontSize = 14.sp)
                }
                item {
                    ActionCard(Icons.Rounded.QrCode2, Primary, "Create a QR code", "Links, text, Wi-Fi or a contact card") {
                        onCreate(QrType.LINK)
                    }
                }
                item {
                    ActionCard(Icons.Rounded.PhotoLibrary, Secondary, "Read from a photo", "Pick a screenshot or photo that contains a QR code") {
                        ExternalLaunch.mark()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                item {
                    Text("Quick create", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CREATABLE_TYPES.drop(1).forEach { t -> TypeChip(t, selected = false) { onCreate(t) } }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Recent", Modifier.weight(1f))
                        if (history.isNotEmpty()) {
                            TextButton(onClick = { confirmClear = true }) { Text("Clear", color = TextSecondary) }
                        }
                    }
                }
                if (history.isEmpty()) {
                    item {
                        Text(
                            "QR codes you create or read will show up here. They stay on this phone.",
                            color = TextDim,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    items(history, key = { it.id }) { entry -> HistoryRow(entry) { onOpen(entry.id) } }
                }
            }
            AdBanner(Placements.HOME_BANNER, Modifier.fillMaxWidth())
        }
        if (reading) {
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading the photo…", color = TextSecondary)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = DialogBg,
            title = { Text("Clear history?") },
            text = { Text("This removes the list of recent QR codes from this phone. Images you saved are not affected.") },
            confirmButton = { TextButton(onClick = { HistoryStore.clear(); confirmClear = false }) { Text("Clear", color = Primary) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = TextSecondary) } },
        )
    }
}

@Composable
private fun ActionCard(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextDim)
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    val content = remember(entry.raw) { entry.content }
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(content.type.icon(), content.type.tint(), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(content.title(), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val verb = if (entry.kind == HistoryKind.CREATED) "Created" else "Read"
                val now = System.currentTimeMillis()
                val time = if (now - entry.createdAt < DateUtils.MINUTE_IN_MILLIS) "Just now"
                else DateUtils.getRelativeTimeSpanString(entry.createdAt, now, DateUtils.MINUTE_IN_MILLIS)
                Text("$verb · ${content.type.label} · $time", fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}
