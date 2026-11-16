/**
 * [INPUT]: 依赖 Android Intent / FileProvider / ClipboardManager，依赖 :core:common 的 ExternalLaunch
 * [OUTPUT]: 对外提供 ExternalApps：launch（跳出应用）、shareDir / shareFile（分享应用自己生成的文件）、shareText、copyText
 * [POS]: data 的「交给其他应用」出口。跳出前都会 ExternalLaunch.mark()，回来时不弹热启动开屏。
 *        分享应用自己生成的文件（PDF、压缩后的图片、二维码图片等）：先写进 shareDir()，再 shareFile；
 *        FileProvider 只暴露 cacheDir/shared（见 AndroidManifest 的 provider 和 res/xml/file_paths.xml），不需要任何存储权限。
 *        用户授权文件夹里的文件用 :core:storage 的 shareFiles / openFile，不走这里
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.appbase.core.common.ExternalLaunch
import java.io.File

object ExternalApps {

    /** 打开其他应用（浏览器、拨号、通讯录、系统设置、分享面板…）；没有能处理的应用时提示 */
    fun launch(context: Context, intent: Intent) {
        try {
            ExternalLaunch.mark()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can handle this", Toast.LENGTH_SHORT).show()
        }
    }

    /** 分享用的临时目录（cacheDir/shared）；每次取用时清掉上一次留下的文件，避免缓存越积越多 */
    fun shareDir(context: Context): File =
        File(context.cacheDir, "shared").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

    /** 分享 shareDir() 里的文件；[mime] 如 image/png、application/pdf */
    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, Intent.createChooser(send, null))
    }

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, Intent.createChooser(send, null))
    }

    fun copyText(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
