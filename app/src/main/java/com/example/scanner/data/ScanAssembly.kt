package com.example.scanner.data

import com.example.scanner.model.DocumentType
import com.example.scanner.model.IdentityType
import java.io.File

/**
 * Picks the right [PdfBuilder] layout for a scan's type, shared by the
 * initial save (FinalizeActivity) and re-editing an already-saved scan
 * (EditScanActivity) so the two never drift apart.
 */
object ScanAssembly {

    fun buildPdf(
        documentType: DocumentType,
        identityType: IdentityType?,
        sideBySide: Boolean,
        imageFiles: List<File>,
        output: File,
    ): Int = when {
        documentType == DocumentType.ID && identityType?.isPassport == true ->
            PdfBuilder.passportOnLetter(imageFiles, output)
        documentType == DocumentType.ID ->
            PdfBuilder.idCardOnLetter(imageFiles, output, sideBySide = sideBySide)
        else ->
            PdfBuilder.fromImageFiles(imageFiles, output)
    }
}
