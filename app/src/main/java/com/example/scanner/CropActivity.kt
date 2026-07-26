package com.example.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.example.scanner.databinding.ActivityCropBinding
import com.example.scanner.scan.EdgeDetector
import com.example.scanner.scan.ImageFilter
import com.example.scanner.scan.ImageFilters
import com.example.scanner.scan.Perspective
import java.io.File

/**
 * Post-capture crop step: auto-detected document edges shown as a draggable
 * quad; confirming applies a perspective-corrected crop to the image file.
 */
class CropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        private const val MAX_DIMENSION = 2400
    }

    private lateinit var binding: ActivityCropBinding
    private lateinit var imageFile: File

    /** Current-orientation, unfiltered source (survives filter switches). */
    private var originalBitmap: Bitmap? = null

    /** [originalBitmap] with the selected filter applied; what's shown and cropped. */
    private var displayedBitmap: Bitmap? = null
    private var selectedFilter: ImageFilter = ImageFilter.COLOR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (path == null) {
            finish()
            return
        }
        imageFile = File(path)
        val loaded = loadOrientedBitmap(imageFile)
        if (loaded == null) {
            Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        originalBitmap = loaded

        binding.toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        binding.buttonUse.setOnClickListener { applyCropAndFinish() }
        binding.buttonFullPage.setOnClickListener { binding.cropView.resetToFullImage() }
        binding.buttonRotate.setOnClickListener { rotateImage() }
        binding.filterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedFilter = when (checkedId) {
                R.id.buttonFilterEnhance -> ImageFilter.ENHANCE
                R.id.buttonFilterGrayscale -> ImageFilter.GRAYSCALE
                R.id.buttonFilterBw -> ImageFilter.BLACK_WHITE
                else -> ImageFilter.COLOR
            }
            refreshDisplayed()
        }

        binding.cropView.post {
            refreshDisplayed(newQuad = EdgeDetector.detectQuad(loaded))
        }
    }

    private fun refreshDisplayed(newQuad: FloatArray? = null) {
        val source = originalBitmap ?: return
        val old = displayedBitmap
        val filtered = ImageFilters.apply(source, selectedFilter)
        if (old != null && old !== source && old !== filtered) old.recycle()
        displayedBitmap = filtered
        if (newQuad != null) {
            binding.cropView.setImage(filtered, newQuad)
        } else {
            binding.cropView.updateBitmap(filtered)
        }
    }

    private fun rotateImage() {
        val current = originalBitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
        current.recycle()
        originalBitmap = rotated
        refreshDisplayed(newQuad = EdgeDetector.detectQuad(rotated))
    }

    private fun applyCropAndFinish() {
        val source = displayedBitmap ?: return
        binding.buttonUse.isEnabled = false
        try {
            val cropped = Perspective.crop(source, binding.cropView.getQuad())
            imageFile.outputStream().use { out ->
                cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            cropped.recycle()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            binding.buttonUse.isEnabled = true
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    /** Decodes downsampled to save memory and applies EXIF rotation. */
    private fun loadOrientedBitmap(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val rotation = when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return decoded

        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    override fun onDestroy() {
        val original = originalBitmap
        displayedBitmap?.let { if (it !== original) it.recycle() }
        original?.recycle()
        displayedBitmap = null
        originalBitmap = null
        super.onDestroy()
    }
}
