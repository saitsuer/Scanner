package com.example.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.scanner.data.DocumentRepository
import com.example.scanner.data.ExportHelper
import com.example.scanner.databinding.ActivityDocumentViewerBinding
import com.example.scanner.model.DocumentType
import com.example.scanner.model.ExportFormat
import com.example.scanner.model.JpegQuality
import com.example.scanner.model.ScannedDocument
import com.example.scanner.ui.BusinessCardEditorActivity
import com.example.scanner.ui.EditScanActivity
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
    private var imagePages: List<File> = emptyList()
    private var currentPage = 0
    private var pageCount = 0
    private var currentBitmap: Bitmap? = null

    private val editLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            document = repository.get(document.id) ?: document
            binding.toolbar.title = document.title
            reopenDocument()
        }

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
        binding.toolbar.inflateMenu(R.menu.menu_document_viewer)
        binding.toolbar.menu.findItem(R.id.action_edit)?.isVisible =
            document.type == DocumentType.BUSINESS_CARD || repository.sourcePageFiles(document).isNotEmpty()
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    val editIntent = if (document.type == DocumentType.BUSINESS_CARD) {
                        Intent(this, BusinessCardEditorActivity::class.java)
                            .putExtra(BusinessCardEditorActivity.EXTRA_DOCUMENT_ID, document.id)
                    } else {
                        Intent(this, EditScanActivity::class.java)
                            .putExtra(EditScanActivity.EXTRA_DOCUMENT_ID, document.id)
                    }
                    editLauncher.launch(editIntent)
                    true
                }
                R.id.action_rename -> {
                    promptRename()
                    true
                }
                R.id.action_export_jpeg -> {
                    exportJpegShare()
                    true
                }
                else -> false
            }
        }
        binding.buttonPrev.setOnClickListener { showPage(currentPage - 1) }
        binding.buttonNext.setOnClickListener { showPage(currentPage + 1) }
        binding.buttonShare.setOnClickListener { sharePrimary() }
        binding.buttonOcr.setOnClickListener { runOcr() }

        openDocument()
    }

    private fun reopenDocument() {
        currentBitmap?.recycle()
        currentBitmap = null
        renderer?.close()
        descriptor?.close()
        renderer = null
        descriptor = null
        currentPage = 0
        openDocument()
    }

    private fun openDocument() {
        if (document.exportFormat == ExportFormat.JPEG) {
            imagePages = repository.pageFiles(document)
            pageCount = imagePages.size.coerceAtLeast(1)
            showPage(0)
        } else {
            val file = repository.fileFor(document)
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            pageCount = renderer!!.pageCount
            showPage(0)
        }
    }

    private fun showPage(index: Int) {
        if (index !in 0 until pageCount) return
        currentPage = index
        currentBitmap?.recycle()
        currentBitmap = null

        if (document.exportFormat == ExportFormat.JPEG) {
            val file = imagePages.getOrNull(index) ?: return
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            currentBitmap = bitmap
            binding.imagePage.setImageBitmap(bitmap)
        } else {
            val pdf = renderer ?: return
            pdf.openPage(index).use { page ->
                val width = (page.width * 2).coerceAtMost(2048)
                val height = (page.height * width) / page.width
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                currentBitmap = bitmap
                binding.imagePage.setImageBitmap(bitmap)
            }
        }

        binding.textPageIndicator.text = getString(R.string.page_indicator, currentPage + 1, pageCount)
        binding.buttonPrev.isEnabled = currentPage > 0
        binding.buttonNext.isEnabled = currentPage < pageCount - 1
        binding.buttonPrev.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        binding.buttonNext.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
    }

    private fun sharePrimary() {
        val file = if (document.exportFormat == ExportFormat.JPEG) {
            imagePages.firstOrNull() ?: repository.fileFor(document)
        } else {
            repository.fileFor(document)
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val mime = if (document.exportFormat == ExportFormat.JPEG) "image/jpeg" else "application/pdf"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun exportJpegShare() {
        try {
            val shareFile: File
            if (document.exportFormat == ExportFormat.JPEG) {
                shareFile = imagePages.getOrElse(currentPage) { imagePages.first() }
            } else {
                val pdf = repository.fileFor(document)
                val dir = File(cacheDir, "share_jpeg").apply { mkdirs() }
                val pages = ExportHelper.pdfToJpegs(pdf, dir, JpegQuality.HIGH)
                shareFile = pages.getOrElse(currentPage) { pages.first() }
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", shareFile)
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    getString(R.string.action_export_jpeg),
                )
            )
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun promptRename() {
        val input = EditText(this).apply {
            setText(document.title)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                val ok = repository.rename(document.id, input.text.toString())
                if (ok) {
                    document = repository.get(document.id) ?: document
                    binding.toolbar.title = document.title
                    Toast.makeText(this, R.string.renamed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
