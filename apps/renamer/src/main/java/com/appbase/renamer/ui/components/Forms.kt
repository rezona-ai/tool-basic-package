/**
 * [INPUT]: 依赖 :core:ui 的颜色 token 与 BrandTheme 风格（圆角、按钮形状、按钮文字）
 * [OUTPUT]: 对外提供 AppTextField、SecondaryButton、ChoiceChip
 * [POS]: ui/components 的表单组件：输入框、次要操作按钮（描边，和 :core:ui 的 GradientButton 同高同圆角）、可选标签。
 *        一个页面只放一个主按钮（GradientButton），其余操作用 SecondaryButton；颜色全部来自 token
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary

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
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val height = 50.dp
    val shape = RoundedCornerShape(if (BrandTheme.style.pillButton) height / 2 else BrandTheme.style.corner(18.dp))
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .border(1.5.dp, Primary.copy(alpha = if (enabled) 1f else 0.4f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val label = if (BrandTheme.style.uppercaseButton) text.uppercase() else text
        Text(label, style = BrandTheme.buttonText, color = Primary.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 14.sp)
    }
}

/** 单选标签：类型切换、参数选择（质量、尺寸、格式…） */
@Composable
fun ChoiceChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
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
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (selected) OnAccent else TextSecondary)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) OnAccent else TextPrimary)
    }
}
