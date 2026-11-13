/**
 * [INPUT]: 依赖 core/storage 的 FolderAccess（经 CoreServices.files 的 addFolder/removeFolder 改动授权），依赖系统文件夹选择器（SAF），
 *          依赖 ./Common 的 AppCard/IconBadge/SectionTitle/TopBar/GradientButton
 * [OUTPUT]: 对外提供 FolderAccessScreen
 * [POS]: core/ui 的文件夹授权页：应用不申请任何存储权限，用户在这里挑选允许管理的文件夹。
 *        首次启动时作为门槛（[onContinue] 非空，见 FolderGate）；设置页 "Folder access" 进入时可增删文件夹（[onBack] 非空）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbase.core.CoreServices
import com.appbase.core.common.ExternalLaunch
import com.appbase.core.storage.FolderAccess
import com.appbase.core.storage.StoragePaths
import com.appbase.core.storage.files
import com.appbase.core.ui.theme.AccentGradient
import com.appbase.core.ui.theme.Bg
import com.appbase.core.ui.theme.OnAccent
import com.appbase.core.ui.theme.Primary
import com.appbase.core.ui.theme.Secondary
import com.appbase.core.ui.theme.TextSecondary

/** 常用文件夹：[relative] 是内部存储下的相对路径，空串表示整个内部存储 */
private data class Suggestion(val relative: String, val title: String, val hint: String, val icon: ImageVector = Icons.Rounded.Folder)

private val SUGGESTIONS = buildList {
    // Android 10 及以下可以直接选内部存储根目录；11 起系统不允许选根目录和 Download 本身
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        add(Suggestion("", "Internal storage", "Every folder on this phone", Icons.Rounded.PhoneAndroid))
    }
    add(Suggestion("DCIM", "DCIM", "Camera photos and videos"))
    add(Suggestion("Pictures", "Pictures", "Screenshots and saved images"))
    add(Suggestion("Movies", "Movies", "Saved and recorded videos"))
    add(Suggestion("Music", "Music", "Songs and recordings"))
    add(Suggestion("Documents", "Documents", "PDFs and office files"))
}

/** 系统文件夹选择器；在 Android 8/9 上默认隐藏内部存储，SHOW_ADVANCED 让它直接可见 */
private class PickFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra("android.content.extra.SHOW_ADVANCED", true)
}

/**
 * @param onBack 非空时为设置页里的管理模式（顶部返回）
 * @param onContinue 非空时为首次启动的门槛，至少添加一个文件夹后可继续
 */
@Composable
fun FolderAccessScreen(onBack: (() -> Unit)? = null, onContinue: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = CoreServices.files
    val access = repo.access
    val grants by access.grants.collectAsState()

    val picker = rememberLauncherForActivityResult(PickFolder()) { uri ->
        if (uri != null && !repo.addFolder(uri)) {
            Toast.makeText(context, "Please choose a folder on this phone", Toast.LENGTH_SHORT).show()
        }
    }
    val pick = { relative: String? ->
        ExternalLaunch.mark()
        try {
            picker.launch(relative?.let(FolderAccess::initialUri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No folder picker on this device", Toast.LENGTH_SHORT).show()
        }
    }
    val suggestions = SUGGESTIONS.filter { s ->
        val path = if (s.relative.isEmpty()) access.primaryRoot else "${access.primaryRoot}/${s.relative}"
        grants.none { StoragePaths.covers(it.path, path) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (onBack == null) Modifier.background(Bg).safeDrawingPadding() else Modifier),
    ) {
        if (onBack != null) TopBar("Folder access", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (onBack == null) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .size(80.dp)
                        .background(AccentGradient, CircleShape)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = OnAccent, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Choose folders to manage",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "${CoreServices.config.appName} only works with the folders you choose and never asks for access to your whole storage. " +
                    "Pick a folder, then tap \"Use this folder\" and \"Allow\". You can add or remove folders any time in Settings.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = if (onBack == null) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )

            if (grants.isNotEmpty()) {
                SectionTitle("Your folders")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grants.forEach { g ->
                        FolderRow(
                            icon = if (g.path == access.primaryRoot) Icons.Rounded.PhoneAndroid else Icons.Rounded.Folder,
                            tint = Primary,
                            title = if (g.path == access.primaryRoot) "Internal storage" else g.path.substringAfterLast('/'),
                            subtitle = access.display(g.path),
                        ) {
                            IconButton(onClick = { repo.removeFolder(g) }) { Icon(Icons.Rounded.Close, "Remove ${g.path.substringAfterLast('/')}", tint = TextSecondary) }
                        }
                    }
                }
            }

            SectionTitle(if (grants.isEmpty()) "Pick a folder" else "Add another folder")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { s ->
                    FolderRow(s.icon, Secondary, s.title, s.hint, onClick = { pick(s.relative) }) {
                        Icon(Icons.Rounded.Add, "Add ${s.title}", tint = Primary, modifier = Modifier.padding(12.dp))
                    }
                }
                FolderRow(Icons.Rounded.CreateNewFolder, Secondary, "Choose another folder", "Any folder on this phone or SD card", onClick = { pick(null) }) {
                    Icon(Icons.Rounded.Add, "Choose another folder", tint = Primary, modifier = Modifier.padding(12.dp))
                }
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                Text(
                    "Android doesn't allow picking the storage root or the Download folder itself. Choose a folder inside them instead.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                )
            }
        }
        if (onContinue != null) {
            GradientButton(
                text = "Continue",
                enabled = grants.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = onContinue,
            )
        }
    }
}

@Composable
private fun FolderRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}
