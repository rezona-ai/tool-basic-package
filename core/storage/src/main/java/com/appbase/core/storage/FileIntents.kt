/**
 * [INPUT]: 依赖 ./FolderAccess（经 CoreServices.files）把路径换成文档 Uri，依赖 core/ads 的 AdPolicy
 * [OUTPUT]: 对外提供 openFile、shareFiles
 * [POS]: core/storage 的外部打开与分享：用文档 Uri 临时授权给接收方，不需要 FileProvider 和存储权限
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.appbase.core.CoreServices
import com.appbase.core.common.ExternalLaunch

private fun mimeOf(path: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase()) ?: "*/*"

/** 通过系统分享面板发送文件（调用方只传文件，不含文件夹）；文件来自用户授权的文件夹，用其文档 Uri 临时授权给接收方 */
fun shareFiles(context: Context, paths: List<String>) {
    val access = CoreServices.files.access
    val shared = paths.mapNotNull { p -> access.uriOf(p)?.let { p to it } }
    if (shared.isEmpty()) return
    try {
        val uris = ArrayList(shared.map { it.second })
        val mimes = shared.map { mimeOf(it.first) }.distinct()
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = mimes.singleOrNull() ?: "*/*"
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ExternalLaunch.mark()
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Can't share these files", Toast.LENGTH_SHORT).show()
    }
}

fun openFile(context: Context, path: String) {
    val uri = CoreServices.files.access.uriOf(path)
    if (uri == null) {
        Toast.makeText(context, "Can't open this file", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeOf(path))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ExternalLaunch.mark()
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}
