package com.example.scanner.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.scanner.model.DocumentType
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

    fun importPdf(
        sourceUri: Uri,
        type: DocumentType,
        pageCount: Int,
        resolver: ContentResolver,
    ): ScannedDocument {
        resolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Could not read PDF" }
            return importPdf(input, type, pageCount)
        }
    }

    fun importPdf(
        input: InputStream,
        type: DocumentType,
        pageCount: Int,
    ): ScannedDocument {
        val id = UUID.randomUUID().toString()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val label = if (type == DocumentType.ID) "ID" else "Document"
        val fileName = "${label}_$stamp.pdf"
        val target = File(rootDir, fileName)

        FileOutputStream(target).use { output -> input.copyTo(output) }

        val doc = ScannedDocument(
            id = id,
            title = "$label $stamp",
            type = type,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
            pageCount = pageCount.coerceAtLeast(1),
        )
        val updated = readIndex().toMutableList()
        updated.add(0, doc)
        writeIndex(updated)
        return doc
    }

    fun delete(id: String): Boolean {
        val docs = readIndex().toMutableList()
        val target = docs.find { it.id == id } ?: return false
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
                    val o = array.getJSONObject(i)
                    add(
                        ScannedDocument(
                            id = o.getString("id"),
                            title = o.getString("title"),
                            type = DocumentType.valueOf(o.getString("type")),
                            fileName = o.getString("fileName"),
                            createdAt = o.getLong("createdAt"),
                            pageCount = o.getInt("pageCount"),
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
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
            )
        }
        indexFile.writeText(array.toString())
    }
}
