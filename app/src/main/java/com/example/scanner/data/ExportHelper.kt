package com.example.scanner.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.scanner.model.JpegQuality
import java.io.File
import java.io.FileOutputStream

object ExportHelper {

    fun pdfToJpegs(pdfFile: File, outputDir: File, quality: JpegQuality): List<File> {
        outputDir.mkdirs()
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val out = mutableListOf<File>()
        try {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val width = (page.width * 2).coerceAtMost(2048)
                    val height = (page.height * width) / page.width
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val file = File(outputDir, "page_$i.jpg")
                    FileOutputStream(file).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.value, stream)
                    }
                    bitmap.recycle()
                    out.add(file)
                }
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
        return out
    }

    fun renderPdfPreview(pdfFile: File): Bitmap? {
        if (!pdfFile.exists()) return null
        val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        return try {
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val width = (page.width * 2).coerceAtMost(1600)
                val height = (page.height * width) / page.width
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }
}
