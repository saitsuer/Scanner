package com.example.scanner.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.scanner.R
import com.example.scanner.data.BusinessCardRenderer
import com.example.scanner.data.DocumentRepository
import com.example.scanner.data.ExportHelper
import com.example.scanner.data.PdfBuilder
import com.example.scanner.data.QrCodeGenerator
import com.example.scanner.databinding.ActivityBusinessCardEditorBinding
import com.example.scanner.model.BusinessCard
import com.example.scanner.model.ColorPreset
import java.io.File
import java.io.FileOutputStream

class BusinessCardEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"
        private const val PREVIEW_SCALE = 4f
        private const val LOGO_MAX_DIM = 1024
    }

    private lateinit var binding: ActivityBusinessCardEditorBinding
    private lateinit var repository: DocumentRepository
    private var editingId: String? = null

    private var logoFile: File? = null
    private var selectedPresetIndex: Int = 0
    private var primaryColor: Int = ColorPreset.PRESETS[0].primary
    private var accentColor: Int = ColorPreset.PRESETS[0].accent

    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null
    private var showingFront = true

    private val logoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadLogoFromUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessCardEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository(this)
        editingId = intent.getStringExtra(EXTRA_DOCUMENT_ID)
        binding.toolbar.title = if (editingId != null) {
            getString(R.string.card_editor_title_edit)
        } else {
            getString(R.string.card_editor_title)
        }
        binding.toolbar.setNavigationOnClickListener {
            if (binding.previewSection.visibility == View.VISIBLE) showForm() else finish()
        }

        buildColorSwatches()
        binding.buttonPickLogo.setOnClickListener { logoLauncher.launch("image/*") }
        binding.buttonPreview.setOnClickListener { showPreview() }
        binding.buttonBackToForm.setOnClickListener { showForm() }
        binding.buttonShowFront.setOnClickListener { showingFront = true; updatePreviewImage() }
        binding.buttonShowBack.setOnClickListener { showingFront = false; updatePreviewImage() }
        binding.buttonSaveCard.setOnClickListener { save() }

        editingId?.let { loadExisting(it) }
    }

    private fun loadExisting(id: String) {
        val doc = repository.get(id) ?: return
        val card = repository.loadBusinessCard(doc) ?: return
        binding.inputName.setText(card.fullName)
        binding.inputTitle.setText(card.title)
        binding.inputPhone.setText(card.phone)
        binding.inputEmail.setText(card.email)
        binding.inputWebsite.setText(card.website)
        binding.inputAddress.setText(card.address)
        binding.inputServices.setText(card.services.joinToString("\n"))
        binding.inputQrValue.setText(card.qrValue)
        binding.inputCardTitle.setText(doc.title)

        primaryColor = card.primaryColor
        accentColor = card.accentColor
        selectedPresetIndex = ColorPreset.PRESETS.indexOfFirst {
            it.primary == primaryColor && it.accent == accentColor
        }
        buildColorSwatches()

        repository.businessCardLogoFile(card)?.let { file ->
            logoFile = file
            showLogoPreview(file)
        }
    }

    private fun buildColorSwatches() {
        binding.colorSwatchRow.removeAllViews()
        val density = resources.displayMetrics.density
        val size = (40 * density).toInt()
        val margin = (10 * density).toInt()
        ColorPreset.PRESETS.forEachIndexed { index, preset ->
            val view = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                background = swatchDrawable(preset, index == selectedPresetIndex, density)
                setOnClickListener {
                    selectedPresetIndex = index
                    primaryColor = preset.primary
                    accentColor = preset.accent
                    buildColorSwatches()
                }
            }
            binding.colorSwatchRow.addView(view)
        }
    }

    private fun swatchDrawable(preset: ColorPreset, selected: Boolean, density: Float): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(preset.primary)
        }
        val accentDot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(preset.accent)
        }
        val layer = LayerDrawable(arrayOf(base, accentDot))
        val inset = (16 * density).toInt()
        layer.setLayerInset(1, inset, inset, 0, 0)
        if (selected) {
            layer.setLayerInset(0, 0, 0, 0, 0)
        }
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(if (selected) (2.5f * density).toInt() else 0, Color.DKGRAY)
        }
        return LayerDrawable(arrayOf(layer, ring))
    }

    private fun loadLogoFromUri(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException(getString(R.string.scan_failed))
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            var sample = 1
            while (boundsOptions.outWidth / (sample * 2) > LOGO_MAX_DIM || boundsOptions.outHeight / (sample * 2) > LOGO_MAX_DIM) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: throw IllegalStateException(getString(R.string.scan_failed))

            val dir = File(cacheDir, "card_logo").apply { mkdirs() }
            val dest = File(dir, "logo_${System.currentTimeMillis()}.png")
            FileOutputStream(dest).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bitmap.recycle()

            logoFile = dest
            showLogoPreview(dest)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun showLogoPreview(file: File) {
        binding.imageLogo.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        binding.imageLogo.visibility = View.VISIBLE
        binding.buttonPickLogo.text = getString(R.string.card_change_logo)
    }

    private fun buildCard(): BusinessCard {
        val services = binding.inputServices.text?.toString().orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val website = binding.inputWebsite.text?.toString()?.trim().orEmpty()
        val qrValue = binding.inputQrValue.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: website
        return BusinessCard(
            fullName = binding.inputName.text?.toString()?.trim().orEmpty(),
            title = binding.inputTitle.text?.toString()?.trim().orEmpty(),
            phone = binding.inputPhone.text?.toString()?.trim().orEmpty(),
            email = binding.inputEmail.text?.toString()?.trim().orEmpty(),
            website = website,
            address = binding.inputAddress.text?.toString()?.trim().orEmpty(),
            services = services,
            qrValue = qrValue,
            logoFileName = null,
            primaryColor = primaryColor,
            accentColor = accentColor,
        )
    }

    private fun showPreview() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            binding.inputName.error = getString(R.string.required_field)
            return
        }
        val card = buildCard()
        renderPreview(card)
        if (binding.inputCardTitle.text.isNullOrBlank()) {
            binding.inputCardTitle.setText(card.fullName)
        }
        binding.formSection.visibility = View.GONE
        binding.previewSection.visibility = View.VISIBLE
    }

    private fun showForm() {
        binding.previewSection.visibility = View.GONE
        binding.formSection.visibility = View.VISIBLE
    }

    private fun renderPreview(card: BusinessCard) {
        val logoBitmap = logoFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
        val qrBitmap = QrCodeGenerator.generate(card.qrValue, 300)
        val w = (BusinessCardRenderer.CARD_WIDTH_PT * PREVIEW_SCALE).toInt()
        val h = (BusinessCardRenderer.CARD_HEIGHT_PT * PREVIEW_SCALE).toInt()
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())

        frontBitmap?.recycle()
        backBitmap?.recycle()
        frontBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            BusinessCardRenderer.drawFront(this, Canvas(bmp), bounds, card, logoBitmap, qrBitmap)
        }
        backBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            BusinessCardRenderer.drawBack(this, Canvas(bmp), bounds, card, logoBitmap)
        }
        showingFront = true
        updatePreviewImage()
    }

    private fun updatePreviewImage() {
        binding.imageCardPreview.setImageBitmap(if (showingFront) frontBitmap else backBitmap)
        binding.buttonShowFront.isEnabled = !showingFront
        binding.buttonShowBack.isEnabled = showingFront
    }

    private fun save() {
        binding.buttonSaveCard.isEnabled = false
        try {
            val card = buildCard()
            val logoBitmap = logoFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
            val qrBitmap = QrCodeGenerator.generate(card.qrValue, 600)
            val pdfFile = File(cacheDir, "card_${System.currentTimeMillis()}.pdf")
            PdfBuilder.businessCard(this, card, logoBitmap, qrBitmap, pdfFile)

            val titleOverride = binding.inputCardTitle.text?.toString()
            val doc = repository.saveBusinessCard(card, pdfFile, logoFile, titleOverride, existingId = editingId)

            if (binding.radioCardPng.isChecked) {
                sharePngExport(doc.let { repository.fileFor(it) })
            }

            setResult(RESULT_OK, Intent().putExtra(EXTRA_DOCUMENT_ID, doc.id))
            finish()
        } catch (e: Exception) {
            binding.buttonSaveCard.isEnabled = true
            Toast.makeText(this, e.message ?: getString(R.string.scan_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePngExport(pdfFile: File) {
        val dir = File(cacheDir, "card_png_${System.currentTimeMillis()}").apply { mkdirs() }
        val pngs = ExportHelper.cardPngs(pdfFile, dir)
        if (pngs.isEmpty()) return
        val uris = pngs.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_export_jpeg)))
    }

    override fun onDestroy() {
        frontBitmap?.recycle()
        backBitmap?.recycle()
        super.onDestroy()
    }
}
