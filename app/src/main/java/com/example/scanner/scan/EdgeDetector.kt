package com.example.scanner.scan

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Lightweight document edge detection without OpenCV.
 *
 * Works on a downscaled grayscale copy: computes Sobel gradient magnitudes,
 * then finds the strongest horizontal/vertical edge lines near each border
 * via projection profiles. Returns an axis-aligned quad the user can refine
 * with draggable corners.
 */
object EdgeDetector {

    private const val WORK_SIZE = 320

    /**
     * Returns corners in bitmap coordinates ordered TL, TR, BR, BL
     * as [x0, y0, x1, y1, x2, y2, x3, y3].
     */
    fun detectQuad(bitmap: Bitmap): FloatArray {
        val scale = WORK_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(8)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)

        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = ((p shr 16 and 0xFF) * 30 + (p shr 8 and 0xFF) * 59 + (p and 0xFF) * 11) / 100
        }

        // Sobel gradients, split by direction so lines don't cross-contaminate
        val gradX = IntArray(w * h)
        val gradY = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = (gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]) -
                    (gray[i - w - 1] + 2 * gray[i - 1] + gray[i + w - 1])
                val gy = (gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]) -
                    (gray[i - w - 1] + 2 * gray[i - w] + gray[i - w + 1])
                gradX[i] = abs(gx)
                gradY[i] = abs(gy)
            }
        }

        // Projection profiles: vertical edges -> column sums of |gx|,
        // horizontal edges -> row sums of |gy|
        val colSum = LongArray(w)
        val rowSum = LongArray(h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                colSum[x] += gradX[y * w + x].toLong()
                rowSum[y] += gradY[y * w + x].toLong()
            }
        }

        val left = strongestLine(colSum, 1, (w * 0.45f).toInt(), fallback = (w * 0.04f).toInt())
        val right = strongestLine(colSum, (w * 0.55f).toInt(), w - 1, fallback = (w * 0.96f).toInt())
        val top = strongestLine(rowSum, 1, (h * 0.45f).toInt(), fallback = (h * 0.04f).toInt())
        val bottom = strongestLine(rowSum, (h * 0.55f).toInt(), h - 1, fallback = (h * 0.96f).toInt())

        val inv = 1f / scale
        val l = left * inv
        val r = right * inv
        val t = top * inv
        val b = bottom * inv
        return floatArrayOf(l, t, r, t, r, b, l, b)
    }

    /**
     * Picks the index with the strongest projection score inside [from, to).
     * Falls back when the peak isn't clearly above the average (no real edge).
     */
    private fun strongestLine(profile: LongArray, from: Int, to: Int, fallback: Int): Int {
        if (from >= to) return fallback
        var bestIdx = -1
        var bestVal = 0L
        var sum = 0L
        for (i in from until to) {
            sum += profile[i]
            if (profile[i] > bestVal) {
                bestVal = profile[i]
                bestIdx = i
            }
        }
        val avg = sum / (to - from).coerceAtLeast(1)
        return if (bestIdx >= 0 && bestVal > avg * 2) bestIdx else fallback
    }
}
