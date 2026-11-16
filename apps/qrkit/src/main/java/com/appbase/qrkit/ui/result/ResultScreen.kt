/**
 * [INPUT]: 依赖 data 的 HistoryStore / QrContent / QrImages，依赖 ui/components，依赖 :core:ui 组件，依赖 :core:ads 的 awaitInterstitial，依赖 :core:common 的 ReviewPrompt / ExternalLaunch
 * [OUTPUT]: 对外提供 ResultScreen
 * [POS]: ui/result 的结果页（生成和识别共用，也用于打开历史记录）：
 *        生成的显示二维码 + 保存 / 分享；识别的按类型给出打开链接、复制、加联系人、拨号等操作。
 *        fresh=true（刚完成一次生成或识别）时，结果显示出来后弹一次插屏，然后按规则请求应用内评分；打开历史记录时不弹
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ads.awaitInterstitial
import com.appbase.core.common.ExternalLaunch
import com.appbase.core.common.ReviewPrompt
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.IconBadge
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.qrkit.data.HistoryEntry
import com.appbase.qrkit.data.HistoryKind
import com.appbase.qrkit.data.HistoryStore
import com.appbase.qrkit.data.QrContent
import com.appbase.qrkit.data.QrImages
import com.appbase.qrkit.data.WifiSecurity
import com.appbase.qrkit.ui.Placements
import com.appbase.qrkit.ui.components.QrCodeImage
import com.appbase.qrkit.ui.components.SecondaryButton
import com.appbase.qrkit.ui.components.icon
import com.appbase.qrkit.ui.components.tint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 结果先显示一会儿再弹插屏，让用户看到结果已经出来了 */
private const val RESULT_VISIBLE_BEFORE_AD_MS = 700L

@Composable
fun ResultScreen(entryId: Long, fresh: Boolean, onBack: () -> Unit) {
    val entry = remember(entryId) { HistoryStore.get(entryId) }
    if (entry == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val activity = LocalActivity.current
    var adDone by rememberSaveable(entryId) { mutableStateOf(!fresh) }
    LaunchedEffect(entryId) {
        if (adDone || activity == null) return@LaunchedEffect
        delay(RESULT_VISIBLE_BEFORE_AD_MS)
        awaitInterstitial(activity, Placements.RESULT_INTERSTITIAL)
        adDone = true
        // 完成一次核心操作（生成或识别）记一次，满足条件时请求 Play 应用内评分
        val count = ReviewPrompt.recordClean(activity)
        delay(ReviewPrompt.DELAY_MS)
        ReviewPrompt.maybeAsk(activity, count)
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(if (entry.kind == HistoryKind.CREATED) "Your QR code" else "Scan result", onBack = onBack) {
            IconButton(onClick = { HistoryStore.remove(entry.id); onBack() }) {
                Icon(Icons.Rounded.DeleteOutline, "Delete from history", tint = TextSecondary)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (entry.kind == HistoryKind.CREATED) CreatedResult(entry) else ScannedResult(entry)
        }
    }
}

@Composable
private fun Header(content: QrContent) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(content.type.icon(), content.type.tint())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(content.type.label, fontSize = 13.sp, color = TextSecondary)
            Text(content.title(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 2)
        }
    }
}

@Composable
private fun CreatedResult(entry: HistoryEntry) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileName = "qr-${entry.id}.png"
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = QrImages.savePng(context, entry.raw, uri)
            Toast.makeText(context, if (ok) "Saved" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
        }
    }
    Header(entry.content)
    QrCodeImage(entry.raw, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    Text(
        "Point any phone camera at the code to open it.",
        Modifier.fillMaxWidth(),
        fontSize = 13.sp,
        color = TextSecondary,
        textAlign = TextAlign.Center,
    )
    GradientButton("Save image") {
        ExternalLaunch.mark()
        saver.launch(fileName)
    }
    SecondaryButton("Share") { scope.launch { QrImages.sharePng(context, entry.raw, fileName) } }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ScannedResult(entry: HistoryEntry) {
    val context = LocalContext.current
    val content = entry.content
    val rows = details(content)
    Header(content)
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Line)
                DetailRow(label, value) { copy(context, label, value) }
            }
        }
    }
    when (content) {
        is QrContent.Link -> GradientButton("Open link") {
            QrImages.launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(content.encode())))
        }
        is QrContent.Wifi -> {
            if (content.security != WifiSecurity.NONE) GradientButton("Copy password") { copy(context, "Password", content.password) }
            SecondaryButton("Open Wi-Fi settings") { QrImages.launch(context, Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }
        is QrContent.Contact -> GradientButton("Add to contacts") {
            val insert = Intent(ContactsContract.Intents.Insert.ACTION)
                .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.NAME, content.name)
                .putExtra(ContactsContract.Intents.Insert.PHONE, content.phone)
                .putExtra(ContactsContract.Intents.Insert.EMAIL, content.email)
                .putExtra(ContactsContract.Intents.Insert.COMPANY, content.org)
            QrImages.launch(context, insert)
        }
        is QrContent.Email -> GradientButton("Write email") {
            QrImages.launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${content.address}")))
        }
        is QrContent.Phone -> GradientButton("Call") {
            QrImages.launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${content.number}")))
        }
        is QrContent.Text -> GradientButton("Share text") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content.text)
            QrImages.launch(context, Intent.createChooser(send, null))
        }
    }
    // 只有一项时每行已有复制按钮，不再重复
    if (rows.size > 1) SecondaryButton("Copy all") { copy(context, "QR code", rows.joinToString("\n") { (k, v) -> "$k: $v" }) }
    Spacer(Modifier.height(8.dp))
}

/** 识别结果按类型拆成「标签 - 值」 */
private fun details(content: QrContent): List<Pair<String, String>> = when (content) {
    is QrContent.Link -> listOf("Link" to content.encode())
    is QrContent.Wifi -> listOfNotNull(
        "Network" to content.ssid,
        "Security" to content.security.label,
        ("Password" to content.password).takeIf { content.security != WifiSecurity.NONE },
        ("Hidden" to "Yes").takeIf { content.hidden },
    )
    is QrContent.Contact -> listOfNotNull(
        ("Name" to content.name).takeIf { content.name.isNotBlank() },
        ("Phone" to content.phone).takeIf { content.phone.isNotBlank() },
        ("Email" to content.email).takeIf { content.email.isNotBlank() },
        ("Company" to content.org).takeIf { content.org.isNotBlank() },
    )
    is QrContent.Email -> listOf("Email" to content.address)
    is QrContent.Phone -> listOf("Phone" to content.number)
    is QrContent.Text -> listOf("Text" to content.text)
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontSize = 15.sp, color = TextPrimary)
        }
        IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, "Copy $label", Modifier.size(20.dp), tint = Primary) }
    }
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
