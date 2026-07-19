package com.example.scanner.model

enum class DocumentType {
    DOCUMENT,
    ID,
}

data class ScannedDocument(
    val id: String,
    val title: String,
    val type: DocumentType,
    val fileName: String,
    val createdAt: Long,
    val pageCount: Int,
)
