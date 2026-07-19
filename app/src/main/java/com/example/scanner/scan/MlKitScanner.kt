package com.example.scanner.scan

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.scanner.model.DocumentType
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Google's full-screen document scanner (auto edge detection, manual corner
 * adjustment, filters). Requires the Play Services document-scanner module;
 * callers must provide a fallback via [onUnavailable].
 */
object MlKitScanner {

    fun start(
        activity: Activity,
        type: DocumentType,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        onUnavailable: (Exception) -> Unit,
    ) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(if (type == DocumentType.ID) 2 else 30)
            // JPEG pages are needed for the ID front+back Letter layout
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener(onUnavailable)
    }
}
