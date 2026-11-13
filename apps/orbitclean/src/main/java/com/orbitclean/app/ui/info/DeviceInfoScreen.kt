/**
 * [INPUT]: 依赖 data/SystemInfoRepository 与 ScanRepository 的读数，依赖 core/ui 的 AppCard
 * [OUTPUT]: 对外提供 DeviceInfoScreen
 * [POS]: ui/info 的设备信息页：设备、CPU、内存、电池、存储分组列表
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.common.formatSize
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.TextSecondary
import com.orbitclean.app.App
import kotlinx.coroutines.delay

@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val sysRepo = App.instance.systemInfoRepository
    val storage = remember { App.instance.scans.store.storageInfo() }
    val initialInfo = remember { sysRepo.read() }
    val info by produceState(initialInfo) {
        while (true) {
            delay(2000)
            value = sysRepo.read()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Device Info", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            InfoSection(
                "Device",
                "Manufacturer" to info.manufacturer,
                "Model" to info.model,
                "Android" to "${info.androidVersion} (API ${info.sdkInt})",
            )
            InfoSection(
                "CPU",
                "Chipset" to info.cpuName,
                "Cores" to "${info.cpuCores}",
                "Max frequency" to (info.cpuMaxFreqMhz?.let { "$it MHz" } ?: "Unknown"),
            )
            InfoSection(
                "Memory",
                "Total" to formatSize(info.ramTotal),
                "Used" to formatSize(info.ramUsed),
                "Available" to formatSize(info.ramAvailable),
            )
            InfoSection(
                "Battery",
                "Level" to "${info.batteryPercent}%",
                "Status" to info.batteryStatus,
                "Health" to info.batteryHealth,
                "Temperature" to (info.batteryTempC?.let { "%.1f °C".format(it) } ?: "Unknown"),
            )
            InfoSection(
                "Storage",
                "Total" to formatSize(storage.total),
                "Used" to formatSize(storage.used),
                "Free" to formatSize(storage.free),
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, vararg rows: Pair<String, String>) {
    SectionTitle(title)
    AppCard(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) HorizontalDivider(color = Line)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(label, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }
    }
}
