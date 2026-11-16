/**
 * [INPUT]: 依赖 QrCodec，依赖 Android Bitmap / ContentResolver / FileProvider，依赖 :core:common 的 ExternalLaunch
 * [OUTPUT]: 对外提供 QrImages：render（文本 → Bitmap）、decodeFromUri（用户选的图片 → 二维码文本）、savePng（写入用户选的位置）、sharePng
 * [POS]: data 的图片读写：只读用户在照片选择器里选中的图片，只写用户在保存对话框里选的位置，分享走应用私有缓存 + FileProvider；不申请任何存储权限
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.appbase.core.common.ExternalLaunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object QrImages {

    /** 保存和分享的图片边长 */
    const val EXPORT_SIZE = 1024

    /** 识别前把大图缩到这个长边以内：够清楚，也不会 OOM */
    private const val DECODE_MAX_SIDE = 1600

    /** 文本 → 白底黑码的正方形图片，边长尽量接近 [size] 且是模块数的整数倍，保证边缘清晰 */
    fun render(text: String, size: Int = EXPORT_SIZE): Bitmap {
        val matrix = QrCodec.encode(text)
        val scale = (size / matrix.width).coerceAtLeast(1)
        val side = matrix.width * scale
        val pixels = IntArray(side * side)
        for (y in 0 until side) {
            val row = y * side
            val my = y / scale
            for (x in 0 until side) {
                pixels[row + x] = if (matrix.get(x / scale, my)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }

    /** 读取用户选中的图片并识别；读不了或找不到二维码返回 null */
    suspend fun decodeFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        val bitmap = loadScaled(context, uri) ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            QrCodec.decode(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    /** 写进用户在系统保存对话框里选的位置 */
    suspend fun savePng(context: Context, text: String, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = render(text)
            context.contentResolver.openOutputStream(target)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        }.getOrDefault(false)
    }

    /** 先写到应用私有缓存，再通过 FileProvider 临时授权给分享目标 */
    suspend fun sharePng(context: Context, text: String, fileName: String) {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                File(dir, fileName).also { f -> f.outputStream().use { render(text).compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }.getOrNull()
        } ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, Intent.createChooser(send, null))
    }

    /** 跳出应用（分享、打开链接、添加联系人）：先标记，回来时不弹热启动开屏 */
    fun launch(context: Context, intent: Intent) {
        try {
            ExternalLaunch.mark()
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can handle this", Toast.LENGTH_SHORT).show()
        }
    }
}
