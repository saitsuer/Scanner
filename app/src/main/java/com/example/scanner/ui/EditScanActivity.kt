package com.example.scanner.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scanner.CropActivity
import com.example.scanner.R
import com.example.scanner.data.DocumentRepository
import com.example.scanner.data.ExportHelper
import com.example.scanner.data.ScanAssembly
import com.example.scanner.databinding.ActivityEditScanBinding
import com.example.scanner.model.ExportFormat
import com.example.scanner.model.JpegQuality
import com.example.scanner.model.ScannedDocument
import java.io.File

/**
 * Reopens an already-saved document/ID scan for per-page re-crop/re-filter,
 * then rebuilds and overwrites the same library entry. Camera/finalize
 * screens stay unchanged; this only touches already-saved scans.
 */
class EditScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"
    }

    private lateinit var binding: ActivityEditScanBinding
    private lateinit var repository: DocumentRepository
    private lateinit var document: ScannedDocument
    private lateinit var meta: DocumentRepository.ScanSourceMeta
    private lateinit var workingDir: File
    private lateinit var workingFiles: MutableList<File>
    private var pendingIndex: Int = -1

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && pendingIndex >= 0) {
                binding.listPages.adapter?.notifyItemChanged(pendingIndex)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository(this)
        val id = intent.getStringExtra(EXTRA_DOCUMENT_ID)
        val doc = id?.let { repository.get(it) }
        val loadedMeta = doc?.let { repository.loadScanMeta(it) }
        val sourceFiles = doc?.let { repository.sourcePageFiles(it) }.orEmpty()
        if (doc == null || loadedMeta == null || sourceFiles.isEmpty()) {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        document = doc
        meta = loadedMeta

        workingDir = File(cacheDir, "edit_scan_${id}_${System.currentTimeMillis()}").apply { mkdirs() }
        workingFiles = sourceFiles.mapIndexed { index, file ->
            File(workingDir, "page_$index.jpg").also { file.copyTo(it, overwrite = true) }
        }.toMutableList()

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.listPages.layoutManager = LinearLayoutManager(this)
        binding.listPages.adapter = EditScanPageAdapter(workingFiles) { index -> launchCrop(index) }
        binding.buttonSaveScan.setOnClickListener { save() }
    }

    private fun launchCrop(index: Int) {
        pendingIndex = index
        cropLauncher.launch(
            Intent(this, CropActivity::class.java)
                .putExtra(CropActivity.EXTRA_IMAGE_PATH, workingFiles[index].absolutePath)
        )
    }

    private fun save() {
        binding.buttonSaveScan.isEnabled = false
        try {
            val output = File(cacheDir, "edit_scan_rebuild_${System.currentTimeMillis()}.pdf")
            val pageCount = ScanAssembly.buildPdf(document.type, document.identityType, meta.sideBySide, workingFiles, output)

            val updatedDoc = if (document.exportFormat == ExportFormat.JPEG) {
                val jpegDir = File(cacheDir, "edit_scan_jpeg_${System.currentTimeMillis()}").apply { mkdirs() }
                val jpegs = ExportHelper.pdfToJpegs(output, jpegDir, JpegQuality.HIGH)
                repository.importJpegs(jpegs, document.type, document.title, document.identityType, existingId = document.id)
            } else {
                output.inputStream().use { input ->
                    repository.importPdf(
                        input = input,
                        type = document.type,
                        pageCount = pageCount,
                        titleOverride = document.title,
                        identityType = document.identityType,
                        existingId = document.id,
                    )
                }
            }
            repository.saveScanSources(document.id, workingFiles, meta.sideBySide)

            setResult(RESULT_OK, Intent().putExtra(EXTRA_DOCUMENT_ID, updatedDoc.id))
            finish()
        } catch (e: Exception) {
            binding.buttonSaveScan.isEnabled = true
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        workingDir.deleteRecursively()
        super.onDestroy()
    }
}
