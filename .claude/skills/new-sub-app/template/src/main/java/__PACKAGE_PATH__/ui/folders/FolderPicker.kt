<!--IF:FOLDER-->
/**
 * [INPUT]: 依赖 :core:storage 的 FolderAccess / FileStore（经 CoreServices.files），依赖系统文件夹选择器（SAF），依赖 :core:common 的 ExternalLaunch
 * [OUTPUT]: 对外提供 rememberFolderPicker
 * [POS]: ui/folders 的「现场选文件夹」：功能需要文件夹的那一步（选要处理的文件夹、选导出位置…）直接弹系统文件夹选择器，
 *        选中后长期授权并返回路径。不申请任何存储权限。
 *        同领域包不要用 :core:ui 的 FolderGate 当首屏：那个首屏和 Orbit Clean 的完全一样。已授权的文件夹在设置页「Folder access」里管理
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui.folders

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.appbase.core.CoreServices
import com.appbase.core.common.ExternalLaunch
import com.appbase.core.storage.FolderAccess
import com.appbase.core.storage.files

/** 系统文件夹选择器；Android 8/9 默认隐藏内部存储，SHOW_ADVANCED 让它直接可见 */
private class PickFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra("android.content.extra.SHOW_ADVANCED", true)
}

/**
 * 返回一个「打开文件夹选择器」的函数，参数是建议的起始位置（内部存储下的相对路径，如 "DCIM"、"Download/Work"；null 表示系统默认）。
 * 用户选中并授权后回调 [onPicked]（文件夹的绝对路径，可直接交给 CoreServices.files 的 listDir / rename / transfer 等）；
 * 取消时不回调。Android 11 起系统不允许选内部存储根目录和 Download 本身，可以选其中的子文件夹。
 */
@Composable
fun rememberFolderPicker(onPicked: (path: String) -> Unit): (startAt: String?) -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(PickFolder()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val store = CoreServices.files
        val path = runCatching { store.access.pathOf(DocumentsContract.getTreeDocumentId(uri)) }.getOrNull()
        if (path == null || !store.addFolder(uri)) {
            Toast.makeText(context, "Please choose a folder on this phone", Toast.LENGTH_SHORT).show()
        } else {
            callback(path)
        }
    }
    return { startAt ->
        ExternalLaunch.mark()
        try {
            launcher.launch(startAt?.let(FolderAccess::initialUri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No folder picker on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
<!--ENDIF-->
