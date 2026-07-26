package com.example.scanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.example.scanner.R
import com.example.scanner.model.BusinessCard
import kotlin.math.ceil
import kotlin.math.min

/**
 * Draws the front/back of a business card into a [Canvas], working in a
 * fixed 252x144pt (3.5x2in) logical space that's scaled to fit whatever
 * destination [RectF] is given. Used for both the on-screen preview bitmap
 * and the PDF page canvas so the layout exists in exactly one place.
 */
object BusinessCardRenderer {

    const val CARD_WIDTH_PT = 252f
    const val CARD_HEIGHT_PT = 144f

    private const val BACKGROUND = Color.WHITE
    private const val ICON_ROW_COUNT = 4
    private val iconResIds = intArrayOf(R.drawable.ic_phone, R.drawable.ic_email, R.drawable.ic_web, R.drawable.ic_location)

    fun drawFront(
        context: Context,
        canvas: Canvas,
        bounds: RectF,
        card: BusinessCard,
        logoBitmap: Bitmap?,
        qrBitmap: Bitmap?,
    ) {
        inCardSpace(canvas, bounds) {
            drawBackground(canvas)
            drawAccentLine(canvas, card.accentColor)
            drawNameBlock(canvas, card)
            drawIconRows(context, canvas, card)
            drawLogoSlot(canvas, logoBitmap, RectF(148f, 12f, 240f, 62f))
            if (qrBitmap != null) {
                val qrSlot = RectF(177f, 83f, 219f, 125f)
                canvas.drawBitmap(qrBitmap, null, qrSlot, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }

    fun drawBack(
        context: Context,
        canvas: Canvas,
        bounds: RectF,
        card: BusinessCard,
        logoBitmap: Bitmap?,
    ) {
        inCardSpace(canvas, bounds) {
            drawBackground(canvas)
            if (!card.blankBack) {
                drawAccentLine(canvas, card.accentColor)
                drawWatermark(canvas, logoBitmap)
                drawServicesRibbon(canvas, card)
                drawServicesColumns(canvas, card)
                drawLogoSlot(canvas, logoBitmap, RectF(198f, 18f, 232f, 42f))
            }
        }
    }

    private inline fun inCardSpace(canvas: Canvas, bounds: RectF, block: () -> Unit) {
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        canvas.scale(bounds.width() / CARD_WIDTH_PT, bounds.height() / CARD_HEIGHT_PT)
        block()
        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(BACKGROUND)
    }

    private fun drawAccentLine(canvas: Canvas, accentColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            strokeWidth = 1.2f
        }
        canvas.drawLine(13f, 10f, 13f, CARD_HEIGHT_PT - 10f, paint)
    }

    private fun drawNameBlock(canvas: Canvas, card: BusinessCard) {
        val namePaint = textPaint(card.primaryColor, 12.5f * card.fontScale, bold = true)
        canvas.drawText(card.fullName.uppercase(), 24f, 42f, namePaint)

        val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.accentColor
            strokeWidth = 1.4f
        }
        canvas.drawLine(24f, 46f, 52f, 46f, underline)

        val titlePaint = textPaint(mix(card.primaryColor, Color.WHITE, 0.35f), 7f * card.fontScale)
        canvas.drawText(card.title, 24f, 58f, titlePaint)
    }

    private fun drawIconRows(context: Context, canvas: Canvas, card: BusinessCard) {
        val rows = listOf(card.phone, card.email, card.website, card.address)
        val badgeColors = intArrayOf(card.primaryColor, card.primaryColor, card.accentColor, card.primaryColor)
        val badgeRadius = 4.6f
        val startY = 76f
        val rowSpacing = 13f
        val textPaint = textPaint(mix(card.primaryColor, Color.BLACK, 0.15f), 6.2f * card.fontScale)

        for (i in 0 until ICON_ROW_COUNT) {
            val value = rows.getOrNull(i)?.trim().orEmpty()
            if (value.isEmpty()) continue
            val cy = startY + i * rowSpacing
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeColors[i] }
            canvas.drawCircle(28.6f, cy, badgeRadius, badgePaint)

            val icon = iconBitmap(context, iconResIds[i], 64)
            val iconSize = badgeRadius * 1.15f
            val iconRect = RectF(28.6f - iconSize / 2f, cy - iconSize / 2f, 28.6f + iconSize / 2f, cy + iconSize / 2f)
            canvas.drawBitmap(icon, null, iconRect, Paint(Paint.FILTER_BITMAP_FLAG))

            canvas.drawText(ellipsize(value, textPaint, 102f), 40f, cy + 2.4f, textPaint)
        }
    }

    private fun drawLogoSlot(canvas: Canvas, logoBitmap: Bitmap?, slot: RectF) {
        if (logoBitmap == null || logoBitmap.width <= 0 || logoBitmap.height <= 0) return
        val scale = min(slot.width() / logoBitmap.width, slot.height() / logoBitmap.height)
        val w = logoBitmap.width * scale
        val h = logoBitmap.height * scale
        val dest = RectF(
            slot.right - w,
            slot.top,
            slot.right,
            slot.top + h,
        )
        canvas.drawBitmap(logoBitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawWatermark(canvas: Canvas, logoBitmap: Bitmap?) {
        if (logoBitmap == null) return
        val size = 90f
        val slot = RectF(CARD_WIDTH_PT - size - 6f, CARD_HEIGHT_PT - size - 6f, CARD_WIDTH_PT - 6f, CARD_HEIGHT_PT - 6f)
        val scale = min(slot.width() / logoBitmap.width, slot.height() / logoBitmap.height)
        val w = logoBitmap.width * scale
        val h = logoBitmap.height * scale
        val dest = RectF(slot.right - w, slot.bottom - h, slot.right, slot.bottom)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = 22 }
        canvas.drawBitmap(logoBitmap, null, dest, paint)
    }

    private fun drawServicesRibbon(canvas: Canvas, card: BusinessCard) {
        val path = Path().apply {
            moveTo(13f, 19f)
            lineTo(90f, 19f)
            lineTo(83f, 32.5f)
            lineTo(13f, 32.5f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card.primaryColor }
        canvas.drawPath(path, fill)

        val label = textPaint(Color.WHITE, 8f * card.fontScale, bold = true)
        canvas.drawText("SERVICES", 18f, 29.5f, label)

        val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.accentColor
            strokeWidth = 1.2f
        }
        canvas.drawLine(13f, 37f, 51f, 37f, underline)
    }

    private fun drawServicesColumns(canvas: Canvas, card: BusinessCard) {
        val services = card.services.map { it.trim() }.filter { it.isNotEmpty() }
        if (services.isEmpty()) return
        val col1Count = ceil(services.size / 2.0).toInt()
        val col1 = services.take(col1Count)
        val col2 = services.drop(col1Count)

        val startY = 49f
        val bottomLimit = CARD_HEIGHT_PT - 8f
        val maxRows = maxOf(col1.size, col2.size).coerceAtLeast(1)
        val rowSpacing = min(8.5f, (bottomLimit - startY) / maxRows)
        val fontSize = min(5.4f * card.fontScale, rowSpacing - 1.2f).coerceAtLeast(3.2f)

        drawServiceColumn(canvas, col1, 21f, 24f, startY, rowSpacing, fontSize, card.accentColor, 78f)
        drawServiceColumn(canvas, col2, 102f, 105f, startY, rowSpacing, fontSize, card.accentColor, 78f)
    }

    private fun drawServiceColumn(
        canvas: Canvas,
        items: List<String>,
        dotX: Float,
        textX: Float,
        startY: Float,
        rowSpacing: Float,
        fontSize: Float,
        dotColor: Int,
        maxTextWidth: Float,
    ) {
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
        val textPaint = textPaint(Color.rgb(0x2A, 0x33, 0x40), fontSize)
        items.forEachIndexed { index, item ->
            val cy = startY + index * rowSpacing
            canvas.drawCircle(dotX, cy - fontSize / 3f, fontSize / 5f, dotPaint)
            canvas.drawText(ellipsize(item, textPaint, maxTextWidth), textX, cy, textPaint)
        }
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun mix(color: Int, with: Int, ratio: Float): Int {
        val r = (Color.red(color) * (1 - ratio) + Color.red(with) * ratio).toInt()
        val g = (Color.green(color) * (1 - ratio) + Color.green(with) * ratio).toInt()
        val b = (Color.blue(color) * (1 - ratio) + Color.blue(with) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun iconBitmap(context: Context, @DrawableRes resId: Int, sizePx: Int): Bitmap {
        val drawable = AppCompatResources.getDrawable(context, resId)!!
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
