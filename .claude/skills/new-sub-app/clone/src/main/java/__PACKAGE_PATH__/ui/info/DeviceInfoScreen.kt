/**
 * [INPUT]: 依赖 :features:cleaner 的 Cleaner.system（SystemInfo / BatteryStatus / BatteryHealth），依赖 :core:ui 的 TopBar / AppCard
 * [OUTPUT]: 对外提供 DeviceInfoScreen
 * [POS]: 设备信息页：存储、内存、电池、处理器、系统，分组卡片展示；只读，不做“加速”之类的操作
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.CoreServices
import com.appbase.core.common.formatSize
import com.appbase.core.storage.files
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.SectionTitle
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.Cleaner
import com.appbase.feature.cleaner.data.BatteryHealth
import com.appbase.feature.cleaner.data.BatteryStatus
import {{CODE_PACKAGE}}.ui.components.UsageBar

@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val info = remember { Cleaner.system.read() }
    val storage = remember { CoreServices.files.storageInfo() }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("About this phone", onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            SectionTitle("Storage")
            InfoCard {
                UsageBar(storage.usedFraction, Modifier.padding(bottom = 8.dp))
                InfoLine("Used", formatSize(storage.used))
                InfoLine("Free", formatSize(storage.free))
                InfoLine("Total", formatSize(storage.total))
            }
            SectionTitle("Memory")
            InfoCard {
                UsageBar(if (info.ramTotal > 0) info.ramUsed.toFloat() / info.ramTotal else 0f, Modifier.padding(bottom = 8.dp))
                InfoLine("In use", formatSize(info.ramUsed))
                InfoLine("Available", formatSize(info.ramAvailable))
            }
            SectionTitle("Battery")
            InfoCard {
                InfoLine("Level", "${info.batteryPercent}%")
                InfoLine("State", info.batteryStatus.label())
                InfoLine("Health", info.batteryHealth.label())
                info.batteryTempC?.let { InfoLine("Temperature", "%.1f °C".format(it)) }
            }
            SectionTitle("Processor")
            InfoCard {
                InfoLine("Chip", info.cpuName)
                InfoLine("Cores", info.cpuCores.toString())
                info.cpuMaxFreqMhz?.let { InfoLine("Max speed", "$it MHz") }
            }
            SectionTitle("System")
            InfoCard {
                InfoLine("Device", "${info.manufacturer} ${info.model}")
                InfoLine("Android", "${info.androidVersion} (API ${info.sdkInt})")
            }
        }
    }
}

private fun BatteryStatus.label() = when (this) {
    BatteryStatus.CHARGING -> "Charging"
    BatteryStatus.FULL -> "Full"
    BatteryStatus.DISCHARGING -> "On battery"
    BatteryStatus.NOT_CHARGING -> "Not charging"
    BatteryStatus.UNKNOWN -> "Unknown"
}

private fun BatteryHealth.label() = when (this) {
    BatteryHealth.GOOD -> "Good"
    BatteryHealth.OVERHEAT -> "Too hot"
    BatteryHealth.DEAD -> "Worn out"
    BatteryHealth.OVER_VOLTAGE -> "Over voltage"
    BatteryHealth.COLD -> "Too cold"
    BatteryHealth.UNKNOWN -> "Unknown"
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
