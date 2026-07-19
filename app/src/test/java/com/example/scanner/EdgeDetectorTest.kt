package com.example.scanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.scanner.scan.EdgeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EdgeDetectorTest {

    @Test
    fun returnsValidQuadWithinBounds() {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.DKGRAY)
        // Draw a bright "document" rectangle in the middle
        Canvas(bitmap).drawRect(
            60f, 90f, 340f, 510f,
            Paint().apply { color = Color.WHITE },
        )

        val quad = EdgeDetector.detectQuad(bitmap)
        assertEquals(8, quad.size)
        for (i in 0 until 8 step 2) {
            assertTrue(quad[i] >= 0f && quad[i] <= 400f)
            assertTrue(quad[i + 1] >= 0f && quad[i + 1] <= 600f)
        }
        // TL must be left of and above BR
        assertTrue(quad[0] < quad[4])
        assertTrue(quad[1] < quad[5])
        bitmap.recycle()
    }
}
