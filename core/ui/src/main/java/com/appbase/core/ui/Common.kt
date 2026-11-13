/**
 * [INPUT]: 依赖 core/ui/theme 的颜色 token 与 BrandTheme.buttonText，依赖 core/storage 的 FileItem/FileType 与 CoreServices.files，依赖 coil 的 AsyncImage
 * [OUTPUT]: 对外提供 GradientButton、TopBar、AppCard、SectionTitle、IconBadge、FileThumb、FileRow、LoadingState、EmptyState 及 FileType 的 icon()/color()
 * [POS]: core/ui 的通用列表与卡片组件，所有品牌的常规页面共用；品牌自己的大场景组件放在品牌代码里（Orbit Clean 见 ui/components）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.ui

import com.appbase.core.ui.theme.DialogBg
import com.appbase.core.ui.theme.SurfaceStyle
import com.appbase.core.ui.theme.ButtonStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.appbase.core.CoreServices
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.FileType
import com.appbase.core.storage.files
import com.appbase.core.ui.theme.AccentGradient
import com.appbase.core.ui.theme.BrandTheme
import com.appbase.core.ui.theme.CardBg
import com.appbase.core.ui.theme.Line
import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextPrimary
import com.appbase.core.ui.theme.TextSecondary

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    onClick: () -> Unit,
) {
    val style = BrandTheme.style
    val shape = RoundedCornerShape(if (style.pillButton) height / 2 else style.corner(18.dp))
    val fill = when (style.button) {
        ButtonStyle.Gradient -> Modifier.background(AccentGradient)
        ButtonStyle.Solid -> Modifier.background(Primary)
        ButtonStyle.Outlined -> Modifier.border(1.5.dp, Primary, shape)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .then(fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (style.uppercaseButton) text.uppercase() else text,
            style = BrandTheme.buttonText,
            color = if (style.button == ButtonStyle.Outlined) Primary else OnAccent,
            fontSize = if (height < 44.dp) 12.sp else 14.sp,
        )
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = if (onBack == null) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(BrandTheme.style.corner(18.dp))
    Column(
        modifier
            .surface(shape, card = true)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextPrimary,
    )
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(if (BrandTheme.style.circleBadge) CircleShape else RoundedCornerShape(BrandTheme.style.corner(12.dp)))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

fun FileType.icon(): ImageVector = when (this) {
    FileType.FOLDER -> Icons.Rounded.Folder
    FileType.IMAGE -> Icons.Rounded.Image
    FileType.VIDEO -> Icons.Rounded.VideoLibrary
    FileType.AUDIO -> Icons.Rounded.MusicNote
    FileType.DOC -> Icons.Rounded.Description
    FileType.ARCHIVE -> Icons.Rounded.FolderZip
    FileType.APK -> Icons.Rounded.Android
    FileType.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

fun FileType.color(): Color = when (this) {
    FileType.FOLDER -> Secondary
    FileType.IMAGE -> Secondary
    FileType.VIDEO -> Primary
    FileType.AUDIO -> Primary
    FileType.DOC -> Secondary
    FileType.ARCHIVE -> Secondary
    FileType.APK -> Primary
    FileType.OTHER -> TextSecondary
}

@Composable
fun FileThumb(item: FileItem, modifier: Modifier = Modifier) {
    val type = item.type
    if (type == FileType.IMAGE || type == FileType.VIDEO) {
        AsyncImage(
            // 文件在用户授权的文件夹里，通过文档 Uri 读取
            model = remember(item.path) { CoreServices.files.access.uriOf(item.path) },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(44.dp)
                .clip(RoundedCornerShape(BrandTheme.style.corner(10.dp)))
                .background(type.color().copy(alpha = 0.12f)),
        )
    } else {
        Box(modifier) { IconBadge(type.icon(), type.color()) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .surface(RoundedCornerShape(BrandTheme.style.corner(16.dp)), card = false)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumb(item)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
        }
        trailing()
    }
}

@Composable
fun LoadingState(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Primary)
        Spacer(Modifier.height(16.dp))
        Text(text, color = TextSecondary)
    }
}

@Composable
fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Inbox, null, tint = Primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(text, color = TextSecondary)
    }
}

/**
 * 按 BrandStyle.surface 画卡片 / 列表行的底：Tinted 时卡片带细描边、列表行只有填充（与 Orbit Clean 原样一致）
 */
private fun Modifier.surface(shape: Shape, card: Boolean): Modifier = when (BrandTheme.style.surface) {
    SurfaceStyle.Tinted -> if (card) clip(shape).background(CardBg).border(1.dp, Line, shape) else clip(shape).background(CardBg)
    SurfaceStyle.Outlined -> clip(shape).border(1.dp, Line, shape)
    SurfaceStyle.Elevated -> shadow(if (card) 6.dp else 3.dp, shape).clip(shape).background(DialogBg)
    SurfaceStyle.Flat -> clip(shape).background(CardBg)
}
