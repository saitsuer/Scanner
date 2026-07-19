package com.example.scanner.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Shows a bitmap (fit-center) with a four-corner crop quad the user can drag,
 * similar to CamScanner's manual edge adjustment.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bitmap: Bitmap? = null

    /** Quad in bitmap coordinates: TL, TR, BR, BL. */
    private val quad = FloatArray(8)
    private val viewQuad = FloatArray(8)

    private val imageMatrix = Matrix()
    private val inverseMatrix = Matrix()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88000000")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF35C388")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF35C388")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val path = Path()

    private var draggedCorner = -1
    private val touchRadius = 36f * resources.displayMetrics.density
    private val handleRadius = 12f * resources.displayMetrics.density

    fun setImage(source: Bitmap, initialQuad: FloatArray) {
        bitmap = source
        System.arraycopy(initialQuad, 0, quad, 0, 8)
        updateMatrix()
        invalidate()
    }

    fun getQuad(): FloatArray = quad.copyOf()

    fun resetToFullImage() {
        val b = bitmap ?: return
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        quad[0] = 0f; quad[1] = 0f
        quad[2] = w; quad[3] = 0f
        quad[4] = w; quad[5] = h
        quad[6] = 0f; quad[7] = h
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun updateMatrix() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width / b.width.toFloat(), height / b.height.toFloat())
        val dx = (width - b.width * scale) / 2f
        val dy = (height - b.height * scale) / 2f
        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
        imageMatrix.invert(inverseMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        canvas.drawBitmap(b, imageMatrix, bitmapPaint)

        imageMatrix.mapPoints(viewQuad, quad)

        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.moveTo(viewQuad[0], viewQuad[1])
        path.lineTo(viewQuad[2], viewQuad[3])
        path.lineTo(viewQuad[4], viewQuad[5])
        path.lineTo(viewQuad[6], viewQuad[7])
        path.close()
        path.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(path, dimPaint)

        path.reset()
        path.moveTo(viewQuad[0], viewQuad[1])
        path.lineTo(viewQuad[2], viewQuad[3])
        path.lineTo(viewQuad[4], viewQuad[5])
        path.lineTo(viewQuad[6], viewQuad[7])
        path.close()
        canvas.drawPath(path, linePaint)

        for (i in 0 until 4) {
            val x = viewQuad[i * 2]
            val y = viewQuad[i * 2 + 1]
            canvas.drawCircle(x, y, handleRadius, handleFill)
            canvas.drawCircle(x, y, handleRadius, handleStroke)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val b = bitmap ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                imageMatrix.mapPoints(viewQuad, quad)
                draggedCorner = -1
                var best = touchRadius
                for (i in 0 until 4) {
                    val d = hypot(event.x - viewQuad[i * 2], event.y - viewQuad[i * 2 + 1])
                    if (d < best) {
                        best = d
                        draggedCorner = i
                    }
                }
                if (draggedCorner >= 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggedCorner < 0) return false
                val pt = floatArrayOf(event.x, event.y)
                inverseMatrix.mapPoints(pt)
                quad[draggedCorner * 2] = max(0f, min(b.width.toFloat(), pt[0]))
                quad[draggedCorner * 2 + 1] = max(0f, min(b.height.toFloat(), pt[1]))
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggedCorner = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
