package com.example.scanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Semi-transparent overlay with a card-shaped cutout for ID scanning. */
class IdGuideOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

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
        val cardRatio = 1.585f
        var boxW = w * 0.86f
        var boxH = boxW / cardRatio
        if (boxH > h * 0.45f) {
            boxH = h * 0.45f
            boxW = boxH * cardRatio
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
