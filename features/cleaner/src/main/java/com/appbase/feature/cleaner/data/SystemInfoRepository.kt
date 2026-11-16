/**
 * [OUTPUT]: 对外提供 SystemInfo、BatteryStatus、BatteryHealth、SystemInfoRepository
 * [POS]: features/cleaner 的设备信息读取（CPU、电池、内存、系统版本）；状态用枚举返回，文案由品牌的设备信息页决定
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.feature.cleaner.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File

data class SystemInfo(
    val cpuName: String,
    val cpuCores: Int,
    val cpuMaxFreqMhz: Int?,
    val batteryPercent: Int,
    val batteryTempC: Float?,
    val batteryStatus: BatteryStatus,
    val batteryHealth: BatteryHealth,
    val ramTotal: Long,
    val ramAvailable: Long,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
) {
    val ramUsed: Long get() = ramTotal - ramAvailable
}

enum class BatteryStatus { CHARGING, FULL, DISCHARGING, NOT_CHARGING, UNKNOWN }

enum class BatteryHealth { GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, COLD, UNKNOWN }

class SystemInfoRepository(private val context: Context) {

    fun read(): SystemInfo {
        val battery = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val health = battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1

        val mem = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(mem)

        return SystemInfo(
            cpuName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
            } else {
                Build.HARDWARE
            },
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuMaxFreqMhz = runCatching {
                File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toInt() / 1000
            }.getOrNull()?.takeIf { it > 0 },
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            batteryTempC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            batteryStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
                BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
                BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
                else -> BatteryStatus.UNKNOWN
            },
            batteryHealth = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
                BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
                BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
                else -> BatteryHealth.UNKNOWN
            },
            ramTotal = mem.totalMem,
            ramAvailable = mem.availMem,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
}
