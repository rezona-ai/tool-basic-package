/**
 * [INPUT]: 依赖 ./Common 的 AppCard/IconBadge，依赖 core 的 CoreServices（版本、链接、文件夹授权）、Reminders、Ads
 * [OUTPUT]: 对外提供 SettingsScreen、ReminderLabels
 * [POS]: core/ui 的通用设置页：文件夹授权入口（可选）、提醒开关（可选）、分享、评分、隐私、反馈、版本号（debug 连点 5 次开广告调试器）。
 *        品牌只决定显示哪些可选项和提醒文案
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.appbase.core.CoreServices
import com.appbase.core.ads.Ads
import com.appbase.core.common.ExternalLaunch
import com.appbase.core.common.Reminders
import com.appbase.core.common.plural
import com.appbase.core.storage.files
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextSecondary

private fun Context.launch(intent: Intent): Boolean {
    ExternalLaunch.mark()
    return runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

private const val PLAY_STORE = "com.android.vending"

/**
 * 打开本应用的 Play 商店详情页去评分。
 * market:// 不指定包名时，部分国产 ROM 会交给其他应用（应用商店、搜索）处理，所以显式指定 Play 商店；
 * 没装 Play 商店时退回网页版。
 */
private fun Context.openStoreListing() {
    val id = packageName
    val play = Intent(Intent.ACTION_VIEW, "market://details?id=$id".toUri()).setPackage(PLAY_STORE)
    if (!launch(play)) launch(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$id".toUri()))
}

/** 提醒开关的标题和说明，由品牌按自己的提醒内容填写 */
data class ReminderLabels(val title: String, val subtitle: String)

/**
 * @param onFolders 非空时显示 "Folder access"（基于文件夹授权的品牌）
 * @param reminder 非空且品牌配置了提醒时显示提醒开关
 */
@Composable
fun SettingsScreen(onFolders: (() -> Unit)? = null, reminder: ReminderLabels? = null) {
    val config = CoreServices.config
    val context = LocalContext.current
    val activity = LocalActivity.current
    val storeUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (onFolders != null) {
            val folders by CoreServices.files.access.grants.collectAsState()
            SettingRow(
                Icons.Rounded.FolderOpen,
                Primary,
                "Folder access",
                subtitle = "${plural(folders.size, "folder")} · add or remove folders",
                onClick = onFolders,
            )
        }
        val showReminder = reminder != null && config.reminder != null
        if (showReminder) ReminderRow(reminder!!)
        if (showReminder && config.debug) {
            SettingRow(Icons.Rounded.NotificationsActive, Secondary, "Send test reminder") {
                Reminders.sendTestNow(context)
                Toast.makeText(context, "Reminder will show in a few seconds", Toast.LENGTH_SHORT).show()
            }
        }
        SettingRow(Icons.Rounded.Share, Primary, "Share app") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, storeUrl)
            context.launch(Intent.createChooser(send, null))
        }
        SettingRow(Icons.Rounded.StarRate, Secondary, "Rate us") { context.openStoreListing() }
        SettingRow(Icons.Rounded.Policy, Primary, "Privacy policy") {
            context.launch(Intent(Intent.ACTION_VIEW, config.privacyUrl.toUri()))
        }
        // 受 GDPR 等法规约束的地区：随时可修改广告授权（隐私政策中承诺的入口）
        if (Ads.provider.privacyOptionsRequired) {
            SettingRow(
                Icons.Rounded.PrivacyTip,
                Primary,
                "Privacy settings",
                subtitle = "Manage how ads use your data",
            ) {
                activity?.let { Ads.provider.showPrivacyOptions(it) }
            }
        }
        SettingRow(Icons.Rounded.Email, Secondary, "Feedback") {
            context.launch(Intent(Intent.ACTION_SENDTO, "mailto:${config.feedbackEmail}".toUri()))
        }
        // debug 包：连点版本号 5 次打开广告调试工具（MAX Mediation Debugger）
        var versionTaps by remember { mutableIntStateOf(0) }
        SettingRow(
            Icons.Rounded.Info,
            TextSecondary,
            "Version",
            value = config.versionName,
            chevron = false,
            onClick = if (config.debug) {
                {
                    if (++versionTaps >= 5) {
                        versionTaps = 0
                        activity?.let { Ads.provider.openDebugger(it) }
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * 开关状态 = 用户开关 && 系统允许通知。打开时如果系统没给通知权限：
 * Android 13+ 还能弹窗就申请，否则（已永久拒绝或低版本被关掉）跳到系统通知设置。
 */
@Composable
private fun ReminderRow(labels: ReminderLabels) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun current() = Reminders.isEnabled(context) && Reminders.notificationsAllowed(context)
    var checked by remember { mutableStateOf(current()) }
    // 从系统通知设置页返回时刷新
    LifecycleResumeEffect(Unit) {
        checked = current()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Reminders.setEnabled(context, true)
            checked = true
        }
    }
    fun toggle(on: Boolean) {
        when {
            !on -> {
                Reminders.setEnabled(context, false)
                checked = false
            }
            Reminders.notificationsAllowed(context) -> {
                Reminders.setEnabled(context, true)
                checked = true
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
                (!Reminders.isPermissionAsked(context) ||
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) -> {
                Reminders.setPermissionAsked(context)
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                Reminders.setEnabled(context, true)
                context.launch(Reminders.notificationSettingsIntent(context))
            }
        }
    }
    SettingRow(
        Icons.Rounded.Notifications,
        Primary,
        labels.title,
        subtitle = labels.subtitle,
        chevron = false,
        onClick = { toggle(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = ::toggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Primary),
            )
        },
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (subtitle != null) Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            if (value != null) Text(value, color = TextSecondary, fontSize = 14.sp)
            trailing?.invoke()
            if (onClick != null && chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}
