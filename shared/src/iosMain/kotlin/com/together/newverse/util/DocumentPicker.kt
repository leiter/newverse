package com.together.newverse.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS implementation of DocumentPicker
 * Uses UIDocumentPickerViewController for document selection
 */
actual class DocumentPicker {

    /**
     * Pick a document file (text files for BNN import)
     */
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun pickDocument(): DocumentPickerResult {
        return suspendCancellableCoroutine { continuation ->
            val delegate = DocumentPickerDelegate { result ->
                continuation.resume(result)
            }

            // Create document picker for text files
            val documentTypes = listOf(UTTypeText, UTTypePlainText, UTTypeData)
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = documentTypes,
                asCopy = true
            )

            picker.delegate = delegate
            picker.allowsMultipleSelection = false

            // Present the picker
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (rootViewController != null) {
                rootViewController.presentViewController(
                    picker,
                    animated = true,
                    completion = null
                )
            } else {
                continuation.resume(DocumentPickerResult.Error("No root view controller available"))
            }

            continuation.invokeOnCancellation {
                picker.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }
}

/**
 * Delegate for handling document picker callbacks
 */
@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate(
    private val onResult: (DocumentPickerResult) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        @Suppress("UNCHECKED_CAST")
        val urls = didPickDocumentsAtURLs as? List<NSURL>
        val url = urls?.firstOrNull()

        if (url == null) {
            onResult(DocumentPickerResult.Cancelled)
            return
        }

        try {
            // Start security-scoped resource access
            val accessing = url.startAccessingSecurityScopedResource()

            // Read file content
            val data = NSData.dataWithContentsOfURL(url)
            if (data == null) {
                if (accessing) url.stopAccessingSecurityScopedResource()
                onResult(DocumentPickerResult.Error("Failed to read document data"))
                return
            }

            // Convert to string
            val content = NSString.create(data, NSUTF8StringEncoding) as? String
            if (content == null) {
                if (accessing) url.stopAccessingSecurityScopedResource()
                onResult(DocumentPickerResult.Error("Failed to decode document as text"))
                return
            }

            // Get filename
            val filename = url.lastPathComponent ?: "import.bnn"

            // Stop security-scoped resource access
            if (accessing) url.stopAccessingSecurityScopedResource()

            println("Document picked: $filename (${content.length} chars)")
            onResult(DocumentPickerResult.Success(content, filename))

        } catch (e: Exception) {
            println("Error reading document: ${e.message}")
            onResult(DocumentPickerResult.Error("Failed to read document: ${e.message}"))
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        println("Document picker cancelled")
        onResult(DocumentPickerResult.Cancelled)
    }
}
