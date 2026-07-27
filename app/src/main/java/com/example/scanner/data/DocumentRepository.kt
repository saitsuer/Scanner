package com.example.scanner.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.scanner.model.BusinessCard
import com.example.scanner.model.CardTemplate
import com.example.scanner.model.DocumentType
import com.example.scanner.model.ExportFormat
import com.example.scanner.model.IdentityType
import com.example.scanner.model.ScannedDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(context: Context) {

    private val rootDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")

    fun list(): List<ScannedDocument> = readIndex().sortedByDescending { it.createdAt }

    fun get(id: String): ScannedDocument? = readIndex().find { it.id == id }

    fun fileFor(doc: ScannedDocument): File = File(rootDir, doc.fileName)

    fun pageFiles(doc: ScannedDocument): List<File> {
        val primary = fileFor(doc)
        if (doc.exportFormat == ExportFormat.PDF || doc.pageCount <= 1) {
            return listOf(primary).filter { it.exists() }
        }
        val pages = (0 until doc.pageCount).mapNotNull { index ->
            File(rootDir, "${doc.id}_p$index.jpg").takeIf { it.exists() }
        }
        if (pages.isNotEmpty()) return pages
        return listOf(primary).filter { it.exists() }
    }

    data class ScanSourceMeta(val pageCount: Int, val sideBySide: Boolean)

    /**
     * Persists the per-page images a document/ID scan was built from (before
     * they get composed into a single PDF/JPEG), so the scan can be reopened
     * later and individual pages re-cropped/re-filtered.
     */
    fun saveScanSources(id: String, imageFiles: List<File>, sideBySide: Boolean) {
        var oldCount = 0
        while (File(rootDir, "${id}_src_p$oldCount.jpg").exists()) oldCount++

        imageFiles.forEachIndexed { index, file ->
            file.copyTo(File(rootDir, "${id}_src_p$index.jpg"), overwrite = true)
        }
        for (i in imageFiles.size until oldCount) {
            File(rootDir, "${id}_src_p$i.jpg").delete()
        }
        JSONObject()
            .put("pageCount", imageFiles.size)
            .put("sideBySide", sideBySide)
            .let { File(rootDir, "${id}_src_meta.json").writeText(it.toString()) }
    }

    fun sourcePageFiles(doc: ScannedDocument): List<File> {
        val meta = loadScanMeta(doc) ?: return emptyList()
        return (0 until meta.pageCount).mapNotNull { i ->
            File(rootDir, "${doc.id}_src_p$i.jpg").takeIf { it.exists() }
        }
    }

    fun loadScanMeta(doc: ScannedDocument): ScanSourceMeta? {
        val file = File(rootDir, "${doc.id}_src_meta.json")
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            ScanSourceMeta(pageCount = o.getInt("pageCount"), sideBySide = o.optBoolean("sideBySide", false))
        }.getOrNull()
    }

    fun importPdf(
        sourceUri: Uri,
        type: DocumentType,
        pageCount: Int,
        resolver: ContentResolver,
        titleOverride: String? = null,
        identityType: IdentityType? = null,
        existingId: String? = null,
    ): ScannedDocument {
        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Could not read PDF" }
            return importPdf(input, type, pageCount, titleOverride, identityType, existingId)
        }
    }

    fun importPdf(
        input: InputStream,
        type: DocumentType,
        pageCount: Int,
        titleOverride: String? = null,
        identityType: IdentityType? = null,
        existingId: String? = null,
    ): ScannedDocument {
        val id = existingId ?: UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val label = identityType?.defaultTitleLabel()
            ?: if (type == DocumentType.ID) "ID" else "Document"
        val title = titleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: "$label $stamp"
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${safeTitle}_$stamp.pdf"
        val target = File(rootDir, fileName)

        FileOutputStream(target).use { output -> input.copyTo(output) }

        val docs = readIndex().toMutableList()
        val existingIndex = docs.indexOfFirst { it.id == id }
        val previous = docs.getOrNull(existingIndex)

        val doc = ScannedDocument(
            id = id,
            title = title,
            type = type,
            fileName = fileName,
            createdAt = previous?.createdAt ?: System.currentTimeMillis(),
            pageCount = pageCount.coerceAtLeast(1),
            identityType = identityType,
            exportFormat = ExportFormat.PDF,
        )
        replaceOrInsert(docs, previous, existingIndex, doc, fileName)
        writeIndex(docs)
        return doc
    }

    fun importJpegs(
        jpegFiles: List<File>,
        type: DocumentType,
        titleOverride: String?,
        identityType: IdentityType?,
        existingId: String? = null,
    ): ScannedDocument {
        require(jpegFiles.isNotEmpty()) { "At least one image is required" }
        val id = existingId ?: UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val label = identityType?.defaultTitleLabel()
            ?: if (type == DocumentType.ID) "ID" else "Document"
        val title = titleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: "$label $stamp"
        val stored = jpegFiles.mapIndexed { index, src ->
            val dest = File(rootDir, "${id}_p$index.jpg")
            src.copyTo(dest, overwrite = true)
            dest
        }

        val docs = readIndex().toMutableList()
        val existingIndex = docs.indexOfFirst { it.id == id }
        val previous = docs.getOrNull(existingIndex)

        val doc = ScannedDocument(
            id = id,
            title = title,
            type = type,
            fileName = stored.first().name,
            createdAt = previous?.createdAt ?: System.currentTimeMillis(),
            pageCount = stored.size,
            identityType = identityType,
            exportFormat = ExportFormat.JPEG,
        )
        // Clean up stale per-page JPEGs beyond the new page count on re-save
        if (previous != null) {
            for (i in stored.size until previous.pageCount) File(rootDir, "${id}_p$i.jpg").delete()
        }
        replaceOrInsert(docs, previous, existingIndex, doc, stored.first().name)
        writeIndex(docs)
        return doc
    }

    private fun replaceOrInsert(
        docs: MutableList<ScannedDocument>,
        previous: ScannedDocument?,
        existingIndex: Int,
        doc: ScannedDocument,
        newFileName: String,
    ) {
        if (previous != null) {
            if (previous.fileName != newFileName) File(rootDir, previous.fileName).delete()
            docs[existingIndex] = doc
        } else {
            docs.add(0, doc)
        }
    }

    fun saveBusinessCard(
        card: BusinessCard,
        pdfFile: File,
        logoFile: File?,
        titleOverride: String?,
        existingId: String? = null,
    ): ScannedDocument {
        val id = existingId ?: UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val title = titleOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: card.fullName.trim().takeIf { it.isNotEmpty() }
            ?: "Business Card $stamp"
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val pdfFileName = "${safeTitle}_$stamp.pdf"
        pdfFile.copyTo(File(rootDir, pdfFileName), overwrite = true)

        val logoFileName = if (logoFile != null && logoFile.exists()) {
            val name = "${id}_logo.png"
            logoFile.copyTo(File(rootDir, name), overwrite = true)
            name
        } else {
            null
        }

        val cardFileName = "${id}_card.json"
        writeBusinessCard(cardFileName, card.copy(logoFileName = logoFileName))

        val docs = readIndex().toMutableList()
        val existingIndex = docs.indexOfFirst { it.id == id }
        val previous = docs.getOrNull(existingIndex)

        val doc = ScannedDocument(
            id = id,
            title = title,
            type = DocumentType.BUSINESS_CARD,
            fileName = pdfFileName,
            createdAt = previous?.createdAt ?: System.currentTimeMillis(),
            pageCount = 2,
            exportFormat = ExportFormat.PDF,
            cardDataFileName = cardFileName,
        )

        if (previous != null) {
            // Clean up the previous PDF/logo when re-saving an edited card under a new name/stamp
            if (previous.fileName != pdfFileName) File(rootDir, previous.fileName).delete()
            if (previous.cardDataFileName != null && previous.cardDataFileName != cardFileName) {
                File(rootDir, previous.cardDataFileName).delete()
            }
            docs[existingIndex] = doc
        } else {
            docs.add(0, doc)
        }
        writeIndex(docs)
        return doc
    }

    fun loadBusinessCard(doc: ScannedDocument): BusinessCard? {
        val name = doc.cardDataFileName ?: return null
        val file = File(rootDir, name)
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            val servicesArray = o.optJSONArray("services")
            val services = buildList {
                if (servicesArray != null) {
                    for (i in 0 until servicesArray.length()) add(servicesArray.getString(i))
                }
            }
            BusinessCard(
                fullName = o.optString("fullName"),
                title = o.optString("title"),
                phone = o.optString("phone"),
                email = o.optString("email"),
                website = o.optString("website"),
                address = o.optString("address"),
                services = services,
                qrValue = o.optString("qrValue"),
                logoFileName = o.optString("logoFileName").takeIf { it.isNotEmpty() },
                primaryColor = o.getInt("primaryColor"),
                accentColor = o.getInt("accentColor"),
                template = runCatching { CardTemplate.valueOf(o.optString("template")) }.getOrDefault(CardTemplate.QUANTUM),
                fontScale = if (o.has("fontScale")) o.getDouble("fontScale").toFloat() else 1f,
                blankBack = o.optBoolean("blankBack", false),
            )
        }.getOrNull()
    }

    fun businessCardLogoFile(card: BusinessCard): File? =
        card.logoFileName?.let { File(rootDir, it) }?.takeIf { it.exists() }

    private fun writeBusinessCard(fileName: String, card: BusinessCard) {
        val o = JSONObject()
            .put("fullName", card.fullName)
            .put("title", card.title)
            .put("phone", card.phone)
            .put("email", card.email)
            .put("website", card.website)
            .put("address", card.address)
            .put("services", JSONArray(card.services))
            .put("qrValue", card.qrValue)
            .put("logoFileName", card.logoFileName ?: "")
            .put("primaryColor", card.primaryColor)
            .put("accentColor", card.accentColor)
            .put("template", card.template.name)
            .put("fontScale", card.fontScale.toDouble())
            .put("blankBack", card.blankBack)
        File(rootDir, fileName).writeText(o.toString())
    }

    fun rename(id: String, newTitle: String): Boolean {
        val title = newTitle.trim()
        if (title.isEmpty()) return false
        val docs = readIndex().toMutableList()
        val index = docs.indexOfFirst { it.id == id }
        if (index < 0) return false
        docs[index] = docs[index].copy(title = title)
        writeIndex(docs)
        return true
    }

    fun delete(id: String): Boolean {
        val docs = readIndex().toMutableList()
        val target = docs.find { it.id == id } ?: return false
        pageFiles(target).forEach { it.delete() }
        fileFor(target).delete()
        target.cardDataFileName?.let { cardFileName ->
            loadBusinessCard(target)?.logoFileName?.let { File(rootDir, it).delete() }
            File(rootDir, cardFileName).delete()
        }
        sourcePageFiles(target).forEach { it.delete() }
        File(rootDir, "${id}_src_meta.json").delete()
        docs.removeAll { it.id == id }
        writeIndex(docs)
        return true
    }

    private fun readIndex(): List<ScannedDocument> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    // Skip individually malformed entries instead of losing the whole library
                    val doc = runCatching { parseDocument(array.getJSONObject(i)) }.getOrNull()
                    if (doc != null) add(doc)
                }
            }
        } catch (_: Exception) {
            // Whole file isn't valid JSON: keep it around for manual recovery instead
            // of silently discarding it the next time writeIndex() runs.
            backupCorruptIndex()
            emptyList()
        }
    }

    private fun parseDocument(o: JSONObject): ScannedDocument = ScannedDocument(
        id = o.getString("id"),
        title = o.getString("title"),
        type = DocumentType.valueOf(o.getString("type")),
        fileName = o.getString("fileName"),
        createdAt = o.getLong("createdAt"),
        pageCount = o.getInt("pageCount"),
        identityType = o.optString("identityType").takeIf { it.isNotEmpty() }
            ?.let { runCatching { IdentityType.valueOf(it) }.getOrNull() },
        exportFormat = o.optString("exportFormat").takeIf { it.isNotEmpty() }
            ?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
            ?: ExportFormat.PDF,
        cardDataFileName = o.optString("cardDataFileName").takeIf { it.isNotEmpty() },
    )

    private fun backupCorruptIndex() {
        runCatching {
            indexFile.copyTo(File(rootDir, "index_corrupt_${System.currentTimeMillis()}.json"), overwrite = true)
        }
    }

    private fun writeIndex(docs: List<ScannedDocument>) {
        val array = JSONArray()
        docs.forEach { doc ->
            array.put(
                JSONObject()
                    .put("id", doc.id)
                    .put("title", doc.title)
                    .put("type", doc.type.name)
                    .put("fileName", doc.fileName)
                    .put("createdAt", doc.createdAt)
                    .put("pageCount", doc.pageCount)
                    .put("identityType", doc.identityType?.name ?: "")
                    .put("exportFormat", doc.exportFormat.name)
                    .put("cardDataFileName", doc.cardDataFileName ?: "")
            )
        }
        // Write to a temp file and rename over the index so a crash mid-write
        // can't leave index.json half-written and unparseable.
        val tmp = File(rootDir, "index.json.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            tmp.renameTo(indexFile)
        }
    }
}
