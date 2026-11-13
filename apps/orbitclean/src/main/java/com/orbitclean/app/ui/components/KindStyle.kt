/**
 * [INPUT]: 依赖 data 的 FileKind，依赖 core/ui/theme 的强调色
 * [OUTPUT]: 对外提供 FileKind.icon()、FileKind.color()
 * [POS]: ui/components 里 Orbit Clean 扫描类别的图标与颜色（通用的 FileType 图标在 core/ui/Common.kt）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.orbitclean.app.data.FileKind

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.REDUNDANT -> Icons.Rounded.DeleteSweep
    FileKind.LARGE -> Icons.Rounded.Folder
    FileKind.DUPLICATE -> Icons.Rounded.ContentCopy
    FileKind.RECENT -> Icons.Rounded.History
    FileKind.IMAGES -> Icons.Rounded.Image
    FileKind.VIDEOS -> Icons.Rounded.VideoLibrary
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.DOCS -> Icons.Rounded.Description
    FileKind.ZIPS -> Icons.Rounded.FolderZip
}

fun FileKind.color(): Color = when (this) {
    FileKind.REDUNDANT -> Primary
    FileKind.LARGE -> Secondary
    FileKind.DUPLICATE -> Secondary
    FileKind.RECENT -> Primary
    FileKind.IMAGES -> Secondary
    FileKind.VIDEOS -> Primary
    FileKind.AUDIO -> Primary
    FileKind.DOCS -> Secondary
    FileKind.ZIPS -> Secondary
}

/** 图片/视频显示缩略图，其他类型显示图标 */
