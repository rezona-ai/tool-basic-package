/**
 * [INPUT]: 依赖 data 的 QrType / QrImages，依赖 :core:ui 的颜色 token 与 BrandTheme 风格
 * [OUTPUT]: 对外提供 QrType.icon / QrType.tint、QrCodeImage、AppTextField、SecondaryButton、TypeChip
 * [POS]: ui/components 的本应用通用小组件：二维码图片、表单输入框、类型标签；颜色全部来自 token
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.CardBg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.qrkit.data.QrImages
import com.appbase.qrkit.data.QrType

fun QrType.icon(): ImageVector = when (this) {
    QrType.TEXT -> Icons.AutoMirrored.Rounded.Notes
    QrType.LINK -> Icons.Rounded.Link
    QrType.WIFI -> Icons.Rounded.Wifi
    QrType.CONTACT -> Icons.Rounded.Person
    QrType.EMAIL -> Icons.Rounded.Email
    QrType.PHONE -> Icons.Rounded.Phone
}

fun QrType.tint(): Color = when (this) {
    QrType.LINK, QrType.EMAIL -> Primary
    QrType.WIFI, QrType.PHONE -> Secondary
    QrType.TEXT, QrType.CONTACT -> TextSecondary
}

/** 白底卡片里的二维码；像素放大时不做平滑，边缘保持清晰 */
@Composable
fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { QrImages.render(text, size = 720).asImageBitmap() }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(BrandTheme.style.corner(16.dp)))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(BrandTheme.style.corner(16.dp)))
            .padding(12.dp),
    ) {
        Image(bitmap, "QR code", Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = FilterQuality.None)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(BrandTheme.style.corner(12.dp)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = Line,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Primary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
        ),
    )
}

/** 次要操作按钮：描边，和主按钮（GradientButton）同高同圆角 */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val height = 50.dp
    val shape = RoundedCornerShape(if (BrandTheme.style.pillButton) height / 2 else BrandTheme.style.corner(18.dp))
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.5.dp, Primary, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BrandTheme.buttonText, color = Primary, fontSize = 14.sp)
    }
}

/** 生成页顶部的类型切换 */
@Composable
fun TypeChip(type: QrType, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(BrandTheme.style.corner(10.dp))
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Primary else CardBg)
            .border(1.dp, if (selected) Primary else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(type.icon(), null, Modifier.size(18.dp), tint = if (selected) OnAccent else TextSecondary)
        Spacer(Modifier.width(6.dp))
        Text(type.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) OnAccent else TextPrimary)
    }
}
