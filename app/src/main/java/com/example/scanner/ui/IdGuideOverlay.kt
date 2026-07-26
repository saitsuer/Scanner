package com.example.scanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Semi-transparent overlay with a card/passport-shaped cutout. */
class IdGuideOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class GuideShape {
        /** Landscape CR-80 / ID-1 card */
        CARD_LANDSCAPE,
        /** ICAO TD3 passport data page (125 × 88 mm) */
        PASSPORT,
    }

    var guideShape: GuideShape = GuideShape.CARD_LANDSCAPE
        set(value) {
            field = value
            invalidate()
        }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val cutout = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val useRatio = when (guideShape) {
            GuideShape.CARD_LANDSCAPE -> 1.585f
            GuideShape.PASSPORT -> 125f / 88f
        }
        var boxW = w * if (guideShape == GuideShape.PASSPORT) 0.90f else 0.86f
        var boxH = boxW / useRatio
        val maxH = if (guideShape == GuideShape.PASSPORT) h * 0.55f else h * 0.45f
        if (boxH > maxH) {
            boxH = maxH
            boxW = boxH * useRatio
        }
        cutout.set(
            (w - boxW) / 2f,
            (h - boxH) / 2f - h * 0.05f,
            (w + boxW) / 2f,
            (h + boxH) / 2f - h * 0.05f,
        )

        path.reset()
        path.addRect(0f, 0f, w, h, Path.Direction.CW)
        path.addRoundRect(cutout, 24f, 24f, Path.Direction.CCW)
        canvas.drawPath(path, dimPaint)
        canvas.drawRoundRect(cutout, 24f, 24f, borderPaint)
    }
}
