package com.example.scanner.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.hypot

object Perspective {

    /**
     * Warps the quad (TL, TR, BR, BL in bitmap coordinates) to an upright
     * rectangle, correcting perspective like a flatbed scan.
     */
    fun crop(source: Bitmap, quad: FloatArray): Bitmap {
        val topW = hypot(quad[2] - quad[0], quad[3] - quad[1])
        val bottomW = hypot(quad[4] - quad[6], quad[5] - quad[7])
        val leftH = hypot(quad[6] - quad[0], quad[7] - quad[1])
        val rightH = hypot(quad[4] - quad[2], quad[5] - quad[3])

        val outW = (((topW + bottomW) / 2f).toInt()).coerceIn(32, 4096)
        val outH = (((leftH + rightH) / 2f).toInt()).coerceIn(32, 4096)

        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat(),
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(quad, 0, dst, 0, 4)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return output
    }
}
