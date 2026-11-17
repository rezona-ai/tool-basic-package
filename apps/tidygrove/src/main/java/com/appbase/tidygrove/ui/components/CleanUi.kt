/**
 * [INPUT]: 依赖 :features:cleaner 的 FileKind，依赖 :core:ui 的颜色 token / BrandTheme，依赖 :core:common 的 Reminders
 * [OUTPUT]: 对外提供 FileKind.icon / tint、UsageBar、ConfirmDialog、RecordCleanAndMaybeReview、RequestNotificationPermissionOnce
 * [POS]: Tidy Grove 清理相关页面共用的小组件：类别图标、用量条、确认弹窗、完成页评分、通知权限
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.tidygrove.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.appbase.core.common.ReviewPrompt
import kotlinx.coroutines.delay
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appbase.core.common.Reminders
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.ProgressGradient
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.feature.cleaner.FileKind

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.REDUNDANT -> Icons.Rounded.DeleteSweep
    FileKind.DUPLICATE -> Icons.Rounded.ContentCopy
    FileKind.LARGE -> Icons.Rounded.Straighten
    FileKind.RECENT -> Icons.Rounded.Schedule
    FileKind.IMAGES -> Icons.Rounded.Image
    FileKind.VIDEOS -> Icons.Rounded.VideoFile
    FileKind.AUDIO -> Icons.Rounded.AudioFile
    FileKind.DOCS -> Icons.Rounded.Description
    FileKind.ZIPS -> Icons.Rounded.FolderZip
}

/** 只用两个品牌强调色和警示色，不写字面量颜色 */
fun FileKind.tint(): Color = when (this) {
    FileKind.REDUNDANT, FileKind.LARGE -> Primary
    FileKind.DUPLICATE -> Danger
    else -> Secondary
}

/** 横向用量条（0..1） */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val shape = RoundedCornerShape(BrandTheme.style.corner(height / 2))
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "usage")
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(shape).background(ProgressGradient))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = { Text(title, color = TextPrimary) },
        text = { Text(text, color = TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = TextSecondary) } },
    )
}

/**
 * 完成页进入时调用一次：记一次成功清理，满足 ReviewPrompt 规则时请求 Play 应用内评分。
 * 用 rememberSaveable 防止从推荐页返回、旋转等重组时重复计数
 */
@Composable
fun RecordCleanAndMaybeReview() {
    val activity = LocalActivity.current
    var recorded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (recorded || activity == null) return@LaunchedEffect
        recorded = true
        val cleans = ReviewPrompt.recordClean(activity)
        delay(ReviewPrompt.DELAY_MS)
        ReviewPrompt.maybeAsk(activity, cleans)
    }
}

/** Android 13+ 首次进入主界面时申请一次通知权限；拒绝后不再弹，设置页的提醒开关可再次引导 */
@Composable
fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!Reminders.isPermissionAsked(context) && !Reminders.notificationsAllowed(context)) {
            Reminders.setPermissionAsked(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
