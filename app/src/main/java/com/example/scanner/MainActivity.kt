package com.example.scanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scanner.data.DocumentRepository
import com.example.scanner.data.PdfBuilder
import com.example.scanner.databinding.ActivityMainBinding
import com.example.scanner.model.DocumentType
import com.example.scanner.scan.MlKitScanner
import com.example.scanner.ui.DocumentListAdapter
import com.example.scanner.ui.IdLayoutChooser
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DocumentRepository
    private lateinit var adapter: DocumentListAdapter

    private var pendingType: DocumentType = DocumentType.DOCUMENT

    /** Google's scanner UI (auto edges, corner adjust, filters) on real devices. */
    private val mlKitLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                Toast.makeText(this, R.string.scan_cancelled, Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult == null) {
                Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val pageUris = scanResult.pages?.mapNotNull { it.imageUri } ?: emptyList()
            // IDs are composed life-size onto a single Letter page (top half)
            if (pendingType == DocumentType.ID && pageUris.isNotEmpty()) {
                askIdLayout(pageUris)
            } else {
                importMlKitPdf(scanResult)
            }
        }

    private fun askIdLayout(pageUris: List<android.net.Uri>) {
        IdLayoutChooser.show(this) { sideBySide ->
            importIdAsLetter(pageUris, sideBySide)
        }
    }

    private fun importMlKitPdf(scanResult: GmsDocumentScanningResult) {
        val pdf = scanResult.pdf
        if (pdf?.uri == null) {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_LONG).show()
            return
        }
        val pageCount = pdf.pageCount.takeIf { it > 0 } ?: scanResult.pages?.size ?: 1
        val doc = repository.importPdf(pdf.uri, pendingType, pageCount, contentResolver)
        Toast.makeText(this, R.string.scan_saved, Toast.LENGTH_SHORT).show()
        refreshLibrary()
        openDocument(doc.id)
    }

    private fun importIdAsLetter(pageUris: List<android.net.Uri>, sideBySide: Boolean) {
        try {
            val dir = File(cacheDir, "id_letter_${System.currentTimeMillis()}").apply { mkdirs() }
            val files = pageUris.mapIndexed { index, uri ->
                File(dir, "side_$index.jpg").also { file ->
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            val tempPdf = File(cacheDir, "id_letter_${System.currentTimeMillis()}.pdf")
            PdfBuilder.idCardOnLetter(files, tempPdf, sideBySide = sideBySide)
            val doc = tempPdf.inputStream().use { input ->
                repository.importPdf(input, DocumentType.ID, 1)
            }
            tempPdf.delete()
            dir.deleteRecursively()
            Toast.makeText(this, R.string.scan_saved, Toast.LENGTH_SHORT).show()
            refreshLibrary()
            openDocument(doc.id)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    /** Built-in camera + crop flow, used when the Play Services module is unavailable. */
    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val id = result.data?.getStringExtra(CaptureActivity.EXTRA_DOCUMENT_ID) ?: return@registerForActivityResult
            Toast.makeText(this, R.string.scan_saved, Toast.LENGTH_SHORT).show()
            refreshLibrary()
            openDocument(id)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository(this)
        adapter = DocumentListAdapter(
            onClick = { openDocument(it.id) },
            onLongClick = { confirmDelete(it.id, it.title) },
        )

        binding.listDocuments.layoutManager = LinearLayoutManager(this)
        binding.listDocuments.adapter = adapter

        binding.buttonScanDocument.setOnClickListener { startScan(DocumentType.DOCUMENT) }
        binding.buttonScanId.setOnClickListener { startScan(DocumentType.ID) }
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    private fun startScan(type: DocumentType) {
        pendingType = type
        MlKitScanner.start(
            activity = this,
            type = type,
            launcher = mlKitLauncher,
            onUnavailable = { startBuiltInScan(type) },
        )
    }

    private fun startBuiltInScan(type: DocumentType) {
        captureLauncher.launch(
            Intent(this, CaptureActivity::class.java)
                .putExtra(CaptureActivity.EXTRA_DOCUMENT_TYPE, type.name)
        )
    }

    private fun refreshLibrary() {
        val docs = repository.list()
        adapter.submit(docs)
        binding.textEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        binding.listDocuments.visibility = if (docs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openDocument(id: String) {
        startActivity(
            Intent(this, DocumentViewerActivity::class.java)
                .putExtra(DocumentViewerActivity.EXTRA_DOCUMENT_ID, id)
        )
    }

    private fun confirmDelete(id: String, title: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                repository.delete(id)
                refreshLibrary()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
