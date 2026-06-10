package ule.jescuj00.fridgey.ui.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code bitmap of [sizePx] × [sizePx].
 *
 * Pure zxing-core (no zxing-android-embedded): we only need the matrix
 * encoder. Error correction M is plenty for an 8-char invite code, and
 * MARGIN=1 keeps the quiet zone minimal because the UI draws its own
 * padding around the code.
 *
 * CPU-bound (matrix + pixel fill) — call from a background dispatcher;
 * the composable side wraps it in `produceState` / `withContext(Default)`.
 */
fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        ),
    )
    val pixels = IntArray(sizePx * sizePx) { i ->
        if (matrix.get(i % sizePx, i / sizePx)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.RGB_565)
}
