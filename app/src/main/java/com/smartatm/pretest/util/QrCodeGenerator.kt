package com.smartatm.pretest.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeGenerator {

    /**
     * Generates a high-contrast black/white QR Bitmap for ATM Raspberry Pi Camera recognition.
     * Quiet zone margin is set to ensure reliable scanning.
     */
    fun generateQrBitmap(content: String, size: Int = 800): Bitmap? {
        return try {
            // CHARACTER_SET=UTF-8은 넣지 않는다. 내용이 영문·숫자뿐이라 필요 없고, 넣으면 ECI 머리말이
            // 붙어 라즈베리파이 OpenCV가 QR을 읽을 때마다 "ECI is not supported properly" 경고를 찍는다.
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 3 // Quiet zone
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
