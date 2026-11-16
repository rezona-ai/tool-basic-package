/**
 * [INPUT]: 依赖 ZXing core（纯 Java）
 * [OUTPUT]: 对外提供 QrCodec：encode（文本 → 模块矩阵）、decode（像素 → 二维码文本）
 * [POS]: data 的二维码编解码，不依赖 Android，单测直接覆盖；Bitmap 的转换在 QrImages
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodec {

    /** 四周留白的模块数：标准要求 4，2 已足够识别，图片更紧凑 */
    private const val QUIET_ZONE = 2

    /** 文本 → 二维码矩阵（每个模块 1 像素，渲染时再放大）；M 级纠错，UTF-8 */
    fun encode(text: String): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE,
        ),
    )

    /**
     * 像素（ARGB）→ 二维码文本；找不到返回 null。
     * 依次尝试两种二值化，再试反色（深底浅码），照片、截图、屏幕翻拍都能覆盖大部分情况。
     */
    fun decode(width: Int, height: Int, argb: IntArray): String? {
        val source = RGBLuminanceSource(width, height, argb)
        return tryDecode(source) ?: tryDecode(source.invert())
    }

    private fun tryDecode(source: LuminanceSource): String? {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
        )
        for (bitmap in listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(GlobalHistogramBinarizer(source)))) {
            try {
                return MultiFormatReader().decode(bitmap, hints).text
            } catch (_: NotFoundException) {
            } catch (_: Exception) {
                // 校验和、格式错误等：换下一种二值化再试
            }
        }
        return null
    }
}
