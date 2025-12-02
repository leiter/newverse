package com.together.newverse.util

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android implementation of DocumentPicker
 * Uses Activity Result API for document selection
 * Uses Storage Access Framework which doesn't require permissions
 */
actual class DocumentPicker(private val activity: ComponentActivity) {

    private var pickDocumentLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        setupLauncher()
    }

    /**
     * Setup Activity Result launcher
     * Must be called before activity is started
     */
    private fun setupLauncher() {
        println("📄 DocumentPicker: Setting up launcher")
        pickDocumentLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            println("📄 DocumentPicker: Callback received, uri=$uri")
            val continuation = pendingDocumentContinuation
            pendingDocumentContinuation = null
            if (continuation != null) {
                println("📄 DocumentPicker: Resuming continuation")
                continuation.resume(uri)
            } else {
                println("📄 DocumentPicker: WARNING - No pending continuation!")
            }
        }
    }

    companion object {
        private var pendingDocumentContinuation: kotlin.coroutines.Continuation<Uri?>? = null
    }

    /**
     * Pick a document file (text/csv/bnn)
     */
    actual suspend fun pickDocument(): DocumentPickerResult {
        println("📄 DocumentPicker: pickDocument() called")
        return try {
            val uri = suspendCancellableCoroutine<Uri?> { continuation ->
                println("📄 DocumentPicker: Setting continuation and launching picker")
                pendingDocumentContinuation = continuation
                // Accept text files and any file type
                pickDocumentLauncher?.launch(arrayOf("text/*", "*/*"))
                    ?: println("📄 DocumentPicker: ERROR - launcher is null!")
            }

            println("📄 DocumentPicker: Got URI result: $uri")
            if (uri != null) {
                val result = processDocumentUri(uri)
                println("📄 DocumentPicker: Processing result: $result")
                result
            } else {
                println("📄 DocumentPicker: Cancelled")
                DocumentPickerResult.Cancelled
            }
        } catch (e: Exception) {
            println("📄 DocumentPicker: Exception: ${e.message}")
            e.printStackTrace()
            DocumentPickerResult.Error("Failed to open document picker: ${e.message}")
        }
    }

    /**
     * Process document URI and read content
     */
    private fun processDocumentUri(uri: Uri): DocumentPickerResult {
        return try {
            val inputStream = activity.contentResolver.openInputStream(uri)
                ?: return DocumentPickerResult.Error("Failed to open document stream")

            val content = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            // Extract filename from URI
            val filename = getFilenameFromUri(uri) ?: "import_${System.currentTimeMillis()}.bnn"

            DocumentPickerResult.Success(content, filename)

        } catch (e: Exception) {
            DocumentPickerResult.Error("Failed to read document: ${e.message}")
        }
    }

    /**
     * Extract filename from content URI
     */
    private fun getFilenameFromUri(uri: Uri): String? {
        return try {
            activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
