package com.example.scanner.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
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

    fun importPdf(
        sourceUri: Uri,
        type: DocumentType,
        pageCount: Int,
        resolver: ContentResolver,
        titleOverride: String? = null,
        identityType: IdentityType? = null,
    ): ScannedDocument {
        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Could not read PDF" }
            return importPdf(input, type, pageCount, titleOverride, identityType)
        }
    }

    fun importPdf(
        input: InputStream,
        type: DocumentType,
        pageCount: Int,
        titleOverride: String? = null,
        identityType: IdentityType? = null,
    ): ScannedDocument {
        val id = UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val label = identityType?.defaultTitleLabel()
            ?: if (type == DocumentType.ID) "ID" else "Document"
        val title = titleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: "$label $stamp"
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val fileName = "${safeTitle}_$stamp.pdf"
        val target = File(rootDir, fileName)

        FileOutputStream(target).use { output -> input.copyTo(output) }

        val doc = ScannedDocument(
            id = id,
            title = title,
            type = type,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
            pageCount = pageCount.coerceAtLeast(1),
            identityType = identityType,
            exportFormat = ExportFormat.PDF,
        )
        val updated = readIndex().toMutableList()
        updated.add(0, doc)
        writeIndex(updated)
        return doc
    }

    fun importJpegs(
        jpegFiles: List<File>,
        type: DocumentType,
        titleOverride: String?,
        identityType: IdentityType?,
    ): ScannedDocument {
        require(jpegFiles.isNotEmpty()) { "At least one image is required" }
        val id = UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val label = identityType?.defaultTitleLabel()
            ?: if (type == DocumentType.ID) "ID" else "Document"
        val title = titleOverride?.trim()?.takeIf { it.isNotEmpty() } ?: "$label $stamp"
        val stored = jpegFiles.mapIndexed { index, src ->
            val dest = File(rootDir, "${id}_p$index.jpg")
            src.copyTo(dest, overwrite = true)
            dest
        }
        val doc = ScannedDocument(
            id = id,
            title = title,
            type = type,
            fileName = stored.first().name,
            createdAt = System.currentTimeMillis(),
            pageCount = stored.size,
            identityType = identityType,
            exportFormat = ExportFormat.JPEG,
        )
        val updated = readIndex().toMutableList()
        updated.add(0, doc)
        writeIndex(updated)
        return doc
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
