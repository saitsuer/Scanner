package com.example.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.scanner.ui.IdLayoutChooser
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.scanner.data.DocumentRepository
import com.example.scanner.data.PdfBuilder
import com.example.scanner.databinding.ActivityCaptureBinding
import com.example.scanner.model.DocumentType
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val EXTRA_DOCUMENT_ID = "document_id"
    }

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var repository: DocumentRepository
    private lateinit var documentType: DocumentType
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private val pageFiles = mutableListOf<File>()
    private lateinit var sessionDir: File
    private var pendingCropFile: File? = null

    private val maxPages: Int
        get() = if (documentType == DocumentType.ID) 2 else 30

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) addPageFromUri(uri)
        }

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val file = pendingCropFile ?: return@registerForActivityResult
            pendingCropFile = null
            if (result.resultCode == RESULT_OK) {
                pageFiles.add(file)
                updatePageCount()
                val message = if (documentType == DocumentType.ID && pageFiles.size >= maxPages) {
                    // Both sides captured; saving stays under the user's control
                    getString(R.string.id_pages_ready)
                } else {
                    getString(R.string.page_captured, pageFiles.size)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                file.delete()
            }
        }

    private fun launchCrop(file: File) {
        pendingCropFile = file
        cropLauncher.launch(
            Intent(this, CropActivity::class.java)
                .putExtra(CropActivity.EXTRA_IMAGE_PATH, file.absolutePath)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository(this)
        documentType = runCatching {
            DocumentType.valueOf(intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: DocumentType.DOCUMENT.name)
        }.getOrDefault(DocumentType.DOCUMENT)

        sessionDir = File(cacheDir, "capture_${System.currentTimeMillis()}").apply { mkdirs() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.toolbar.title = if (documentType == DocumentType.ID) {
            getString(R.string.scan_id)
        } else {
            getString(R.string.scan_document)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textHint.text = if (documentType == DocumentType.ID) {
            getString(R.string.hint_id)
        } else {
            getString(R.string.hint_document)
        }
        binding.idGuide.visibility = if (documentType == DocumentType.ID) View.VISIBLE else View.GONE

        binding.buttonCapture.setOnClickListener { takePhoto() }
        binding.buttonGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.buttonDone.setOnClickListener { finishAndSave() }
        updatePageCount()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                cameraProvider.unbindAll()
                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                }
                if (selector == null) {
                    Toast.makeText(this, R.string.no_camera_use_gallery, Toast.LENGTH_LONG).show()
                    return@addListener
                }
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(R.string.no_camera_use_gallery) + "\n${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (pageFiles.size >= maxPages) {
            Toast.makeText(this, getString(R.string.max_pages_reached, maxPages), Toast.LENGTH_SHORT).show()
            return
        }
        val capture = imageCapture ?: return
        val file = File(sessionDir, "page_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    launchCrop(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CaptureActivity, exception.message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private fun addPageFromUri(uri: Uri) {
        if (pageFiles.size >= maxPages) {
            Toast.makeText(this, getString(R.string.max_pages_reached, maxPages), Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(sessionDir, "page_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: run {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_SHORT).show()
            return
        }
        launchCrop(file)
    }

    private fun updatePageCount() {
        binding.textPageCount.text = getString(R.string.pages_captured, pageFiles.size, maxPages)
        binding.buttonDone.isEnabled = pageFiles.isNotEmpty()
    }

    private fun finishAndSave() {
        if (pageFiles.isEmpty()) return
        if (documentType == DocumentType.ID) {
            IdLayoutChooser.show(this) { sideBySide ->
                savePdf(idSideBySide = sideBySide)
            }
        } else {
            savePdf(idSideBySide = null)
        }
    }

    /** [idSideBySide] null = regular multi-page document PDF. */
    private fun savePdf(idSideBySide: Boolean?) {
        binding.buttonDone.isEnabled = false
        try {
            val tempPdf = File(cacheDir, "scan_${System.currentTimeMillis()}.pdf")
            // Keep IDs in color; enhance text documents for a scanned look
            val count = if (idSideBySide != null) {
                PdfBuilder.idCardOnLetter(pageFiles, tempPdf, sideBySide = idSideBySide)
            } else {
                PdfBuilder.fromImageFiles(pageFiles, tempPdf, enhance = true)
            }
            tempPdf.inputStream().use { input ->
                val doc = repository.importPdf(input, documentType, count)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_DOCUMENT_ID, doc.id),
                )
            }
            tempPdf.delete()
            finish()
        } catch (e: Exception) {
            binding.buttonDone.isEnabled = true
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        sessionDir.deleteRecursively()
    }
}
