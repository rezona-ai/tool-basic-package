/**
 * [INPUT]: 依赖 data 的 QrContent / QrType / WifiSecurity，依赖 ui/components 的 AppTextField / TypeChip，依赖 :core:ui 的 TopBar / AppCard / GradientButton
 * [OUTPUT]: 对外提供 CreateScreen
 * [POS]: ui/create 的生成页：选类型（文本、链接、Wi-Fi、联系人）→ 填内容 → 校验通过后交给 onCreated（由 AppNav 记入历史并进入结果页）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.ui.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ui.AppCard
import com.appbase.core.ui.GradientButton
import com.appbase.core.ui.TopBar
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.Danger
import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.qrkit.data.QrContent
import com.appbase.qrkit.data.QrType
import com.appbase.qrkit.data.WifiSecurity
import com.appbase.qrkit.ui.components.AppTextField
import com.appbase.qrkit.ui.components.TypeChip

/** 生成页能选的类型；邮箱、电话只在识别时出现 */
val CREATABLE_TYPES = listOf(QrType.LINK, QrType.TEXT, QrType.WIFI, QrType.CONTACT)

@Composable
fun CreateScreen(initialType: QrType, onBack: () -> Unit, onCreated: (raw: String) -> Unit) {
    var type by rememberSaveable { mutableStateOf(initialType.takeIf { it in CREATABLE_TYPES } ?: QrType.LINK) }
    var text by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var wifiPassword by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf(WifiSecurity.WPA) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun content(): QrContent = when (type) {
        QrType.LINK -> QrContent.Link(url)
        QrType.WIFI -> QrContent.Wifi(ssid.trim(), wifiPassword, security, hidden)
        QrType.CONTACT -> QrContent.Contact(name.trim(), phone.trim(), email.trim(), org.trim())
        else -> QrContent.Text(text)
    }

    Column(Modifier.fillMaxSize().background(Bg).imePadding()) {
        TopBar("Create QR code", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CREATABLE_TYPES.forEach { t ->
                    TypeChip(t, selected = t == type) { type = t; error = null }
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (type) {
                        QrType.LINK -> {
                            Hint("Anyone who scans it opens this page.")
                            AppTextField(url, { url = it; error = null }, "Web address", keyboardType = KeyboardType.Uri)
                        }
                        QrType.WIFI -> {
                            Hint("Guests scan it to join your network without typing the password.")
                            AppTextField(ssid, { ssid = it; error = null }, "Network name (SSID)")
                            Text("Security", fontSize = 13.sp, color = TextSecondary)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                WifiSecurity.entries.forEach { s ->
                                    Choice(s.label, selected = s == security) { security = s; error = null }
                                }
                            }
                            if (security != WifiSecurity.NONE) {
                                AppTextField(wifiPassword, { wifiPassword = it; error = null }, "Password", keyboardType = KeyboardType.Password, password = true)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hidden network", Modifier.weight(1f), color = TextPrimary)
                                Switch(
                                    checked = hidden,
                                    onCheckedChange = { hidden = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Primary, checkedThumbColor = OnAccent),
                                )
                            }
                        }
                        QrType.CONTACT -> {
                            Hint("Scanning it offers to save you as a new contact.")
                            AppTextField(name, { name = it; error = null }, "Name")
                            AppTextField(phone, { phone = it; error = null }, "Phone", keyboardType = KeyboardType.Phone)
                            AppTextField(email, { email = it; error = null }, "Email", keyboardType = KeyboardType.Email)
                            AppTextField(org, { org = it; error = null }, "Company (optional)")
                        }
                        else -> {
                            Hint("Any note, code or message, up to ${QrContent.MAX_LENGTH} characters.")
                            AppTextField(text, { text = it; error = null }, "Text", singleLine = false, minLines = 4)
                        }
                    }
                }
            }
            error?.let { Text(it, color = Danger, fontSize = 14.sp) }
            Spacer(Modifier.height(4.dp))
        }
        GradientButton(
            "Create QR code",
            Modifier.padding(16.dp),
        ) {
            val c = content()
            val problem = c.problem()
            if (problem != null) error = problem else onCreated(c.encode())
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = 14.sp, color = TextSecondary)

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(BrandTheme.style.corner(8.dp)))
            .background(if (selected) Primary.copy(alpha = 0.14f) else Bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Primary else TextSecondary)
    }
}
