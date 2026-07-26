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
import com.example.scanner.databinding.ActivityMainBinding
import com.example.scanner.model.DocumentType
import com.example.scanner.model.IdentityType
import com.example.scanner.scan.MlKitScanner
import com.example.scanner.ui.BusinessCardEditorActivity
import com.example.scanner.ui.DocumentListAdapter
import com.example.scanner.ui.IdLayoutChooser
import com.example.scanner.ui.IdentityTypeChooser
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DocumentRepository
    private lateinit var adapter: DocumentListAdapter

    private var pendingType: DocumentType = DocumentType.DOCUMENT
    private var pendingIdentityType: IdentityType? = null

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
            if (pendingType == DocumentType.ID && pageUris.isNotEmpty()) {
                // Defensive cap: ID scans are front+back only, regardless of
                // how many pages the ML Kit scanner UI happened to return.
                if (pageUris.size > 2) {
                    Toast.makeText(this, R.string.id_pages_capped, Toast.LENGTH_LONG).show()
                }
                val cappedUris = pageUris.take(2)
                val identity = pendingIdentityType
                if (identity?.isPassport == true) {
                    openFinalizeFromUris(cappedUris, sideBySide = false)
                } else {
                    IdLayoutChooser.show(this) { sideBySide ->
                        openFinalizeFromUris(cappedUris, sideBySide)
                    }
                }
            } else {
                importMlKitPdf(scanResult)
            }
        }

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val id = result.data?.getStringExtra(CaptureActivity.EXTRA_DOCUMENT_ID)
                ?: return@registerForActivityResult
            Toast.makeText(this, R.string.scan_saved, Toast.LENGTH_SHORT).show()
            refreshLibrary()
            openDocument(id)
        }

    private val finalizeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val id = result.data?.getStringExtra(FinalizeActivity.EXTRA_DOCUMENT_ID)
                ?: return@registerForActivityResult
            Toast.makeText(this, R.string.scan_saved, Toast.LENGTH_SHORT).show()
            refreshLibrary()
            openDocument(id)
        }

    private val cardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val id = result.data?.getStringExtra(BusinessCardEditorActivity.EXTRA_DOCUMENT_ID)
                ?: return@registerForActivityResult
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
        binding.buttonScanId.setOnClickListener {
            IdentityTypeChooser.show(this) { identity ->
                pendingIdentityType = identity
                startScan(DocumentType.ID)
            }
        }
        binding.buttonCreateCard.setOnClickListener {
            cardLauncher.launch(Intent(this, BusinessCardEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    private fun startScan(type: DocumentType) {
        pendingType = type
        if (type == DocumentType.DOCUMENT) pendingIdentityType = null

        // Driver's license / state ID always use our own capture -> crop -> capture ->
        // crop -> layout flow, so front/back and filters stay fully under our control
        // instead of Google's document-scanner UI.
        val identity = pendingIdentityType
        if (type == DocumentType.ID && (identity == IdentityType.DRIVERS_LICENSE || identity == IdentityType.STATE_ID)) {
            startBuiltInScan(type)
            return
        }

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
                .putExtra(CaptureActivity.EXTRA_IDENTITY_TYPE, pendingIdentityType?.name)
        )
    }

    private fun openFinalizeFromUris(pageUris: List<android.net.Uri>, sideBySide: Boolean) {
        try {
            val dir = File(cacheDir, "mlkit_finalize_${System.currentTimeMillis()}").apply { mkdirs() }
            val files = pageUris.mapIndexed { index, uri ->
                File(dir, "side_$index.jpg").also { file ->
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            finalizeLauncher.launch(
                Intent(this, FinalizeActivity::class.java)
                    .putStringArrayListExtra(
                        FinalizeActivity.EXTRA_IMAGE_PATHS,
                        ArrayList(files.map { it.absolutePath }),
                    )
                    .putExtra(FinalizeActivity.EXTRA_DOCUMENT_TYPE, DocumentType.ID.name)
                    .putExtra(FinalizeActivity.EXTRA_IDENTITY_TYPE, pendingIdentityType?.name)
                    .putExtra(FinalizeActivity.EXTRA_SIDE_BY_SIDE, sideBySide)
            )
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
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
