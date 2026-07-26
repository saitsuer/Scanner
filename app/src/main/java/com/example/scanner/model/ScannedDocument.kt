package com.example.scanner.model

enum class DocumentType {
    DOCUMENT,
    ID,
    BUSINESS_CARD,
}

data class ScannedDocument(
    val id: String,
    val title: String,
    val type: DocumentType,
    val fileName: String,
    val createdAt: Long,
    val pageCount: Int,
    val identityType: IdentityType? = null,
    val exportFormat: ExportFormat = ExportFormat.PDF,
    val cardDataFileName: String? = null,
)
