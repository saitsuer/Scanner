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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.scanner.databinding.ActivityCaptureBinding
import com.example.scanner.model.DocumentType
import com.example.scanner.model.IdentityType
import com.example.scanner.ui.IdGuideOverlay
import com.example.scanner.ui.IdLayoutChooser
import java.io.File
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_TYPE = "document_type"
        const val EXTRA_IDENTITY_TYPE = "identity_type"
        const val EXTRA_DOCUMENT_ID = "document_id"
    }

    private lateinit var binding: ActivityCaptureBinding
    private lateinit var documentType: DocumentType
    private var identityType: IdentityType? = null
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private val pageFiles = mutableListOf<File>()
    private lateinit var sessionDir: File
    private var pendingCropFile: File? = null

    private val isPassport: Boolean
        get() = identityType?.isPassport == true

    private val maxPages: Int
        get() = when {
            documentType != DocumentType.ID -> 30
            isPassport -> 2 // photo + optional visa
            else -> 2
        }

    private val minPagesToFinish: Int
        get() = when {
            isPassport -> 1
            documentType == DocumentType.ID -> 2 // front and back both required
            else -> 1
        }

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
                val message = when {
                    isPassport && pageFiles.size == 1 -> getString(R.string.passport_photo_ready)
                    isPassport && pageFiles.size >= 2 -> getString(R.string.passport_pages_ready)
                    documentType == DocumentType.ID && pageFiles.size >= maxPages ->
                        getString(R.string.id_pages_ready)
                    documentType == DocumentType.ID && pageFiles.size == 1 ->
                        getString(R.string.id_front_captured)
                    else -> getString(R.string.page_captured, pageFiles.size)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                file.delete()
            }
        }

    private val finalizeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val id = result.data?.getStringExtra(FinalizeActivity.EXTRA_DOCUMENT_ID)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_DOCUMENT_ID, id),
                )
                finish()
            } else {
                binding.buttonDone.isEnabled = pageFiles.isNotEmpty()
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

        documentType = runCatching {
            DocumentType.valueOf(intent.getStringExtra(EXTRA_DOCUMENT_TYPE) ?: DocumentType.DOCUMENT.name)
        }.getOrDefault(DocumentType.DOCUMENT)
        identityType = intent.getStringExtra(EXTRA_IDENTITY_TYPE)?.let {
            runCatching { IdentityType.valueOf(it) }.getOrNull()
        }

        sessionDir = File(cacheDir, "capture_${System.currentTimeMillis()}").apply { mkdirs() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.toolbar.title = when {
            isPassport -> getString(R.string.identity_passport)
            documentType == DocumentType.ID -> identityType?.defaultTitleLabel() ?: getString(R.string.scan_id)
            else -> getString(R.string.scan_document)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textHint.text = when {
            isPassport -> getString(R.string.hint_passport)
            documentType == DocumentType.ID -> getString(R.string.hint_id)
            else -> getString(R.string.hint_document)
        }
        if (documentType == DocumentType.ID) {
            binding.idGuide.visibility = View.VISIBLE
            binding.idGuide.guideShape = if (isPassport) {
                IdGuideOverlay.GuideShape.PASSPORT
            } else {
                IdGuideOverlay.GuideShape.CARD_LANDSCAPE
            }
        } else {
            binding.idGuide.visibility = View.GONE
        }

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
        binding.buttonDone.isEnabled = pageFiles.size >= minPagesToFinish
        if (documentType == DocumentType.ID && !isPassport) {
            binding.textHint.text = if (pageFiles.isEmpty()) {
                getString(R.string.hint_id)
            } else {
                getString(R.string.hint_id_back)
            }
        }
    }

    private fun finishAndSave() {
        if (pageFiles.size < minPagesToFinish) return
        binding.buttonDone.isEnabled = false
        when {
            documentType == DocumentType.ID && isPassport -> openFinalize(sideBySide = false)
            documentType == DocumentType.ID -> {
                IdLayoutChooser.show(this) { sideBySide ->
                    openFinalize(sideBySide)
                }
                binding.buttonDone.isEnabled = true
            }
            else -> openFinalize(sideBySide = false)
        }
    }

    private fun openFinalize(sideBySide: Boolean) {
        // Copy pages out of sessionDir so onDestroy cleanup won't delete them mid-finalize
        val stableDir = File(cacheDir, "finalize_src_${System.currentTimeMillis()}").apply { mkdirs() }
        val stableFiles = pageFiles.mapIndexed { index, src ->
            File(stableDir, "page_$index.jpg").also { src.copyTo(it, overwrite = true) }
        }
        finalizeLauncher.launch(
            Intent(this, FinalizeActivity::class.java)
                .putStringArrayListExtra(
                    FinalizeActivity.EXTRA_IMAGE_PATHS,
                    ArrayList(stableFiles.map { it.absolutePath }),
                )
                .putExtra(FinalizeActivity.EXTRA_DOCUMENT_TYPE, documentType.name)
                .putExtra(FinalizeActivity.EXTRA_IDENTITY_TYPE, identityType?.name)
                .putExtra(FinalizeActivity.EXTRA_SIDE_BY_SIDE, sideBySide)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        sessionDir.deleteRecursively()
    }
}
