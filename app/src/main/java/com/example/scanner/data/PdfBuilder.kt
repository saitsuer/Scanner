package com.example.scanner.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.scanner.scan.CardAutoCrop
import java.io.File
import java.io.FileOutputStream

object PdfBuilder {

    // US Letter in PDF points (8.5 x 11 inch at 72 dpi)
    private const val LETTER_WIDTH = 612
    private const val LETTER_HEIGHT = 792

    fun fromImageFiles(
        images: List<File>,
        output: File,
        enhance: Boolean = true,
    ): Int {
        require(images.isNotEmpty()) { "At least one page is required" }
        val pdf = PdfDocument()
        var writtenPages = 0
        try {
            images.forEach { file ->
                val original = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
                val bitmap = if (enhance) toDocumentBitmap(original) else original
                if (enhance && bitmap !== original) original.recycle()

                val pageInfo = PdfDocument.PageInfo.Builder(
                    bitmap.width,
                    bitmap.height,
                    writtenPages + 1,
                ).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)
                bitmap.recycle()
                writtenPages++
            }
            require(writtenPages > 0) { "Could not read image" }
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            try {
                pdf.close()
            } catch (_: IllegalStateException) {
                // Some shadows close the document during writeTo.
            }
        }
        return writtenPages
    }

    // US driver's license / state ID (CR-80 / ID-1: 85.6 × 53.98 mm) in PDF
    // points so a 100% Letter printout is life-size
    private const val CARD_WIDTH_PT = 242.65f
    private const val CARD_HEIGHT_PT = 153.01f
    private const val CARD_GAP_PT = 30f

    // Standard ID-1 corner radius (~3.18 mm)
    private const val CARD_CORNER_RADIUS_PT = 9f

    // Cards sit in the top half of the US Letter page
    private const val TOP_HALF_HEIGHT = LETTER_HEIGHT / 2f

    /**
     * Places US ID / license front (and back, if present) on one US Letter
     * page at real physical size in the top half.
     * [sideBySide] places sides next to each other; otherwise stacked.
     */
    fun idCardOnLetter(images: List<File>, output: File, sideBySide: Boolean = false): Int {
        require(images.isNotEmpty()) { "At least one page is required" }
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(LETTER_WIDTH, LETTER_HEIGHT, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val count = minOf(images.size, 2)
            val slots = if (sideBySide) {
                val blockWidth = count * CARD_WIDTH_PT + (count - 1) * CARD_GAP_PT
                val startX = (LETTER_WIDTH - blockWidth) / 2f
                val top = (TOP_HALF_HEIGHT - CARD_HEIGHT_PT) / 2f
                (0 until count).map { i ->
                    val left = startX + i * (CARD_WIDTH_PT + CARD_GAP_PT)
                    RectF(left, top, left + CARD_WIDTH_PT, top + CARD_HEIGHT_PT)
                }
            } else {
                val blockHeight = count * CARD_HEIGHT_PT + (count - 1) * CARD_GAP_PT
                val startY = (TOP_HALF_HEIGHT - blockHeight) / 2f
                val left = (LETTER_WIDTH - CARD_WIDTH_PT) / 2f
                (0 until count).map { i ->
                    val top = startY + i * (CARD_HEIGHT_PT + CARD_GAP_PT)
                    RectF(left, top, left + CARD_WIDTH_PT, top + CARD_HEIGHT_PT)
                }
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            images.take(2).forEachIndexed { index, file ->
                val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                // Auto-trim leftover background around the card and lock the
                // frame to the real card aspect ratio
                val bitmap = CardAutoCrop.tighten(decoded)
                if (bitmap !== decoded) decoded.recycle()
                val slot = slots[index]

                // Fill the life-size card box; slight overflow is trimmed by
                // the rounded-corner clip below, hiding background remnants
                val scale = maxOf(slot.width() / bitmap.width, slot.height() / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val x = slot.centerX() - w / 2f
                val y = slot.centerY() - h / 2f

                val clip = Path().apply {
                    addRoundRect(slot, CARD_CORNER_RADIUS_PT, CARD_CORNER_RADIUS_PT, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(clip)
                canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), paint)
                canvas.restore()
                bitmap.recycle()
            }

            pdf.finishPage(page)
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            try {
                pdf.close()
            } catch (_: IllegalStateException) {
                // Some shadows close the document during writeTo.
            }
        }
        return 1
    }

    /** Increases contrast / grayscale for a scanned-document look. */
    private fun toDocumentBitmap(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        val contrast = 1.25f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        matrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }
}
