/**
 * [INPUT]: 依赖 data 的 RuleType / RenameOp，依赖 :core:ui 的颜色 token 与 BrandTheme
 * [OUTPUT]: 对外提供 RuleType.icon、StepLabel、NameChange（旧名 → 新名）、MonoText
 * [POS]: ui/components 的本应用小组件：规则图标、分步标题、文件名对照行；文件名统一用等宽字体，方便对比
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.TextDim
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary
import com.appbase.renamer.data.RuleType

fun RuleType.icon(): ImageVector = when (this) {
    RuleType.NUMBER -> Icons.Rounded.FormatListNumbered
    RuleType.DATE -> Icons.Rounded.CalendarMonth
    RuleType.AFFIX -> Icons.Rounded.TextFields
    RuleType.REPLACE -> Icons.Rounded.FindReplace
    RuleType.CASE -> Icons.Rounded.TextFormat
}

/** 分步标题：「1 · FOLDER」这样的小号大写标签 */
@Composable
fun StepLabel(step: Int, text: String, modifier: Modifier = Modifier) {
    Text(
        "$step · ${text.uppercase()}",
        modifier.padding(top = 18.dp, bottom = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = Primary,
    )
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    size: TextUnit = 14.sp,
    strike: Boolean = false,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.MiddleEllipsis,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )
}

/** 旧名（划掉）→ 新名；名字没变时只显示一行 */
@Composable
fun NameChange(oldName: String, newName: String, modifier: Modifier = Modifier, problem: String? = null, problemColor: Color = TextSecondary) {
    Column(modifier) {
        if (oldName == newName) {
            MonoText(oldName, color = TextSecondary)
            Text("unchanged", fontSize = 12.sp, color = TextDim)
        } else {
            MonoText(oldName, color = TextDim, size = 12.sp, strike = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(14.dp), tint = Primary)
                Spacer(Modifier.width(4.dp))
                MonoText(newName)
            }
            if (problem != null) Text(problem, fontSize = 12.sp, color = problemColor)
        }
    }
}
