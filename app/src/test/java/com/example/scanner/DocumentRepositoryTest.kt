package com.example.scanner

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.scanner.data.DocumentRepository
import com.example.scanner.model.BusinessCard
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

    @Test
    fun corruptIndexIsBackedUpInsteadOfLosingData() {
        repository.importPdf(
            ByteArrayInputStream("%PDF-1.4 fake".toByteArray()),
            DocumentType.DOCUMENT,
            1,
        )
        val documentsDir = File(app.filesDir, "documents")
        File(documentsDir, "index.json").writeText("{not valid json")

        assertTrue(repository.list().isEmpty())
        val backups = documentsDir.listFiles { f -> f.name.startsWith("index_corrupt_") }
        assertTrue(backups != null && backups.isNotEmpty())
    }

    @Test
    fun saveLoadAndDeleteBusinessCard() {
        val pdfFile = File(app.cacheDir, "test_card.pdf").apply { writeBytes("%PDF-1.4 fake".toByteArray()) }
        val logoFile = File(app.cacheDir, "test_logo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val card = BusinessCard(
            fullName = "Jane Smith",
            title = "CEO",
            phone = "555-1234",
            email = "jane@example.com",
            website = "example.com",
            address = "123 Main St",
            services = listOf("Consulting", "Advisory"),
            qrValue = "example.com",
            logoFileName = null,
            primaryColor = 0xFF16324F.toInt(),
            accentColor = 0xFFD4A017.toInt(),
            fontScale = 1.15f,
            blankBack = true,
        )

        val doc = repository.saveBusinessCard(card, pdfFile, logoFile, titleOverride = null)
        assertEquals(DocumentType.BUSINESS_CARD, doc.type)
        assertTrue(repository.fileFor(doc).exists())
        assertEquals(1, repository.list().size)

        val loaded = repository.loadBusinessCard(doc)
        assertEquals("Jane Smith", loaded?.fullName)
        assertEquals(listOf("Consulting", "Advisory"), loaded?.services)
        assertEquals(1.15f, loaded?.fontScale ?: 0f, 0.001f)
        assertTrue(loaded?.blankBack == true)
        assertTrue(repository.businessCardLogoFile(loaded!!)?.exists() == true)

        assertTrue(repository.delete(doc.id))
        assertFalse(repository.fileFor(doc).exists())
        assertTrue(repository.list().isEmpty())
    }
}
