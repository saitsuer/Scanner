package com.example.scanner.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class ImageFilter {
    COLOR,
    ENHANCE,
    GRAYSCALE,
    BLACK_WHITE,
}

/** Per-page look applied at crop time, so a page is only ever filtered once. */
object ImageFilters {

    fun apply(source: Bitmap, filter: ImageFilter): Bitmap = when (filter) {
        ImageFilter.COLOR -> source
        ImageFilter.ENHANCE -> adjust(source, saturation = 1.15f, contrast = 1.1f, brightness = 6f)
        ImageFilter.GRAYSCALE -> adjust(source, saturation = 0f, contrast = 1.05f, brightness = 0f)
        ImageFilter.BLACK_WHITE -> adjust(source, saturation = 0f, contrast = 1.45f, brightness = -12f)
    }

    private fun adjust(source: Bitmap, saturation: Float, contrast: Float, brightness: Float): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val matrix = ColorMatrix().apply { setSaturation(saturation) }
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        matrix.postConcat(contrastMatrix)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
