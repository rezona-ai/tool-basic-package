package com.orbitclean.app.data

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
    val batteryStatus: String,
    val batteryHealth: String,
    val ramTotal: Long,
    val ramAvailable: Long,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
) {
    val ramUsed: Long get() = ramTotal - ramAvailable
}

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
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            },
            batteryHealth = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
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
