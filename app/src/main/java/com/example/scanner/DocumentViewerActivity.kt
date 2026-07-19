package com.example.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.scanner.data.DocumentRepository
import com.example.scanner.databinding.ActivityDocumentViewerBinding
import com.example.scanner.model.ScannedDocument
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class DocumentViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"
    }

    private lateinit var binding: ActivityDocumentViewerBinding
    private lateinit var repository: DocumentRepository
    private lateinit var document: ScannedDocument

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var currentPage = 0
    private var pageCount = 0
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository(this)
        val id = intent.getStringExtra(EXTRA_DOCUMENT_ID)
        val doc = id?.let { repository.get(it) }
        if (doc == null) {
            finish()
            return
        }
        document = doc

        binding.toolbar.title = document.title
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.buttonPrev.setOnClickListener { showPage(currentPage - 1) }
        binding.buttonNext.setOnClickListener { showPage(currentPage + 1) }
        binding.buttonShare.setOnClickListener { sharePdf() }
        binding.buttonOcr.setOnClickListener { runOcr() }

        openPdf(repository.fileFor(document))
    }

    private fun openPdf(file: File) {
        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(descriptor!!)
        pageCount = renderer!!.pageCount
        showPage(0)
    }

    private fun showPage(index: Int) {
        val pdf = renderer ?: return
        if (index !in 0 until pageCount) return
        currentPage = index

        pdf.openPage(index).use { page ->
            val width = (page.width * 2).coerceAtMost(2048)
            val height = (page.height * width) / page.width
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            currentBitmap?.recycle()
            currentBitmap = bitmap
            binding.imagePage.setImageBitmap(bitmap)
        }

        binding.textPageIndicator.text = getString(R.string.page_indicator, currentPage + 1, pageCount)
        binding.buttonPrev.isEnabled = currentPage > 0
        binding.buttonNext.isEnabled = currentPage < pageCount - 1
        binding.buttonPrev.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        binding.buttonNext.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
    }

    private fun sharePdf() {
        val file = repository.fileFor(document)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun runOcr() {
        val bitmap = currentBitmap
        if (bitmap == null) {
            Toast.makeText(this, R.string.ocr_empty, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.ocr_running, Toast.LENGTH_SHORT).show()
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.ocr_empty, Toast.LENGTH_SHORT).show()
                } else {
                    showOcrDialog(text)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message ?: getString(R.string.ocr_empty), Toast.LENGTH_LONG).show()
            }
    }

    private fun showOcrDialog(text: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.ocr_title)
            .setMessage(text)
            .setPositiveButton(R.string.copy_text) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ocr", text))
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    override fun onDestroy() {
        currentBitmap?.recycle()
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}
