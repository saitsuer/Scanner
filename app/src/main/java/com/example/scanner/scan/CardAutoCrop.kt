package com.example.scanner.scan

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Automatically trims leftover background around a scanned ID card.
 *
 * The background color is estimated from the image border; the card is found
 * as the bounding box of pixels that differ from it. The result is then
 * center-cropped to the exact ID-1 aspect ratio so the card fills the frame.
 */
object CardAutoCrop {

    private const val WORK_SIZE = 400
    private const val CARD_RATIO = 85.6f / 53.98f

    // Minimum per-pixel color difference from background to count as card
    private const val COLOR_THRESHOLD = 90

    // A row/column needs this fraction of "card" pixels to be inside the box
    private const val LINE_FILL_RATIO = 0.06f

    fun tighten(source: Bitmap): Bitmap {
        val scale = WORK_SIZE.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).roundToInt().coerceAtLeast(8)
        val h = (source.height * scale).roundToInt().coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        val (bgR, bgG, bgB) = borderMedianColor(pixels, w, h)

        val colCount = IntArray(w)
        val rowCount = IntArray(h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val diff = abs((p shr 16 and 0xFF) - bgR) +
                    abs((p shr 8 and 0xFF) - bgG) +
                    abs((p and 0xFF) - bgB)
                if (diff > COLOR_THRESHOLD) {
                    colCount[x]++
                    rowCount[y]++
                }
            }
        }

        val minColFill = (h * LINE_FILL_RATIO).toInt()
        val minRowFill = (w * LINE_FILL_RATIO).toInt()
        val left = colCount.indexOfFirst { it > minColFill }
        val right = colCount.indexOfLast { it > minColFill }
        val top = rowCount.indexOfFirst { it > minRowFill }
        val bottom = rowCount.indexOfLast { it > minRowFill }

        // Detection failed or box implausibly small: keep original framing
        if (left < 0 || right - left < w * 0.3f || bottom - top < h * 0.3f) {
            return cropToCardRatio(source, 0, 0, source.width, source.height)
        }

        val inv = 1f / scale
        // Shave a sliver inward to remove any halo left at the card edge
        val insetX = (right - left) * 0.01f
        val insetY = (bottom - top) * 0.01f
        val x0 = ((left + insetX) * inv).roundToInt().coerceIn(0, source.width - 2)
        val y0 = ((top + insetY) * inv).roundToInt().coerceIn(0, source.height - 2)
        val x1 = ((right - insetX) * inv).roundToInt().coerceIn(x0 + 1, source.width)
        val y1 = ((bottom - insetY) * inv).roundToInt().coerceIn(y0 + 1, source.height)

        return cropToCardRatio(source, x0, y0, x1 - x0, y1 - y0)
    }

    /** Median border color; robust against the card touching one edge. */
    private fun borderMedianColor(pixels: IntArray, w: Int, h: Int): Triple<Int, Int, Int> {
        val ring = max(2, (min(w, h) * 0.02f).toInt())
        val rs = ArrayList<Int>()
        val gs = ArrayList<Int>()
        val bs = ArrayList<Int>()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x < ring || x >= w - ring || y < ring || y >= h - ring) {
                    val p = pixels[y * w + x]
                    rs.add(p shr 16 and 0xFF)
                    gs.add(p shr 8 and 0xFF)
                    bs.add(p and 0xFF)
                }
            }
        }
        rs.sort(); gs.sort(); bs.sort()
        val mid = rs.size / 2
        return Triple(rs[mid], gs[mid], bs[mid])
    }

    /** Center-crops the given region to the exact ID-1 aspect ratio. */
    private fun cropToCardRatio(source: Bitmap, x: Int, y: Int, cw: Int, ch: Int): Bitmap {
        var outX = x
        var outY = y
        var outW = cw
        var outH = ch
        val ratio = cw.toFloat() / ch
        if (ratio > CARD_RATIO) {
            outW = (ch * CARD_RATIO).roundToInt().coerceAtLeast(1)
            outX = x + (cw - outW) / 2
        } else {
            outH = (cw / CARD_RATIO).roundToInt().coerceAtLeast(1)
            outY = y + (ch - outH) / 2
        }
        outX = outX.coerceIn(0, source.width - 1)
        outY = outY.coerceIn(0, source.height - 1)
        outW = outW.coerceAtMost(source.width - outX)
        outH = outH.coerceAtMost(source.height - outY)
        if (outX == 0 && outY == 0 && outW == source.width && outH == source.height) {
            return source
        }
        return Bitmap.createBitmap(source, outX, outY, outW, outH)
    }
}
