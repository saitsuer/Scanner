package com.example.scanner.model

enum class ExportFormat {
    PDF,
    JPEG,
}

enum class JpegQuality(val value: Int) {
    HIGH(92),
    MEDIUM(75),
}
