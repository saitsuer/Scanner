package com.example.scanner

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.scanner.data.DocumentRepository
import com.example.scanner.data.ExportHelper
import com.example.scanner.data.PdfBuilder
import com.example.scanner.databinding.ActivityFinalizeBinding
import com.example.scanner.model.DocumentType
import com.example.scanner.model.ExportFormat
import com.example.scanner.model.IdentityType
import com.example.scanner.model.JpegQuality
import java.io.File

/**
 * Preview + rename + export format. Camera/crop stay unchanged upstream.
 */
class FinalizeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATHS = "image_paths"
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val EXTRA_IDENTITY_TYPE = "identity_type"
        const val EXTRA_SIDE_BY_SIDE = "side_by_side"
        const val EXTRA_DOCUMENT_ID = "document_id"
    }

    private lateinit var binding: ActivityFinalizeBinding
    private lateinit var repository: DocumentRepository
    private lateinit var documentType: DocumentType
    private var identityType: IdentityType? = null
    private var sideBySide: Boolean = false
    private lateinit var imageFiles: List<File>
    private lateinit var previewPdf: File
    private var previewBitmap: Bitmap? = null
    private var pageCount: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFinalizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository(this)
        documentType = runCatching {
            DocumentType.valueOf(intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: DocumentType.DOCUMENT.name)
        }.getOrDefault(DocumentType.DOCUMENT)
        identityType = intent.getStringExtra(EXTRA_IDENTITY_TYPE)?.let {
            runCatching { IdentityType.valueOf(it) }.getOrNull()
        }
        sideBySide = intent.getBooleanExtra(EXTRA_SIDE_BY_SIDE, false)
        imageFiles = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS)
            ?.map { File(it) }
            ?.filter { it.exists() }
            .orEmpty()

        if (imageFiles.isEmpty()) {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.inputTitle.setText(
            identityType?.defaultTitleLabel()
                ?: if (documentType == DocumentType.ID) getString(R.string.type_id) else getString(R.string.type_document)
        )

        binding.groupFormat.setOnCheckedChangeListener { _, checkedId ->
            binding.qualityRow.visibility =
                if (checkedId == R.id.radioJpeg) View.VISIBLE else View.GONE
        }

        binding.buttonSave.setOnClickListener { save() }

        previewPdf = File(cacheDir, "preview_${System.currentTimeMillis()}.pdf")
        try {
            pageCount = buildPdf(previewPdf)
            previewBitmap = ExportHelper.renderPdfPreview(previewPdf)
            binding.imagePreview.setImageBitmap(previewBitmap)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun buildPdf(output: File): Int {
        return when {
            documentType == DocumentType.ID && identityType?.isPassport == true ->
                PdfBuilder.passportOnLetter(imageFiles, output)
            documentType == DocumentType.ID ->
                PdfBuilder.idCardOnLetter(imageFiles, output, sideBySide = sideBySide)
            else ->
                PdfBuilder.fromImageFiles(imageFiles, output)
        }
    }

    private fun save() {
        binding.buttonSave.isEnabled = false
        val title = binding.inputTitle.text?.toString().orEmpty()
        val format = if (binding.radioJpeg.isChecked) ExportFormat.JPEG else ExportFormat.PDF
        val quality = if (binding.radioQualityMedium.isChecked) JpegQuality.MEDIUM else JpegQuality.HIGH
        try {
            val doc = if (format == ExportFormat.PDF) {
                previewPdf.inputStream().use { input ->
                    repository.importPdf(
                        input = input,
                        type = documentType,
                        pageCount = pageCount,
                        titleOverride = title,
                        identityType = identityType,
                    )
                }
            } else {
                val jpegDir = File(cacheDir, "jpeg_export_${System.currentTimeMillis()}").apply { mkdirs() }
                val jpegs = ExportHelper.pdfToJpegs(previewPdf, jpegDir, quality)
                repository.importJpegs(jpegs, documentType, title, identityType)
            }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_DOCUMENT_ID, doc.id))
            finish()
        } catch (e: Exception) {
            binding.buttonSave.isEnabled = true
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        previewBitmap?.recycle()
        previewPdf.delete()
        super.onDestroy()
    }
}
