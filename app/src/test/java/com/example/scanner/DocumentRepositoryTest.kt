package com.example.scanner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.scanner.data.DocumentRepository
import com.example.scanner.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentRepositoryTest {

    private lateinit var repository: DocumentRepository
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        File(app.filesDir, "documents").deleteRecursively()
        repository = DocumentRepository(app)
    }

    @Test
    fun importAndListDocument() {
        val doc = repository.importPdf(
            ByteArrayInputStream("%PDF-1.4 fake".toByteArray()),
            DocumentType.DOCUMENT,
            3,
        )

        assertEquals(DocumentType.DOCUMENT, doc.type)
        assertEquals(3, doc.pageCount)
        assertTrue(repository.fileFor(doc).exists())
        assertEquals(1, repository.list().size)
        assertEquals(doc.id, repository.get(doc.id)?.id)
    }

    @Test
    fun deleteRemovesFileAndIndex() {
        val doc = repository.importPdf(
            ByteArrayInputStream("%PDF-1.4 fake".toByteArray()),
            DocumentType.ID,
            2,
        )
        assertTrue(repository.delete(doc.id))
        assertFalse(repository.fileFor(doc).exists())
        assertTrue(repository.list().isEmpty())
    }
}
