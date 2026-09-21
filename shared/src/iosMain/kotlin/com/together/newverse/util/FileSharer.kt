package com.together.newverse.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * iOS: writes the file to the temporary directory and presents the share sheet
 * (UIActivityViewController) on top of whatever is on screen.
 */
actual class FileSharer : TextFileSharer {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun shareTextFile(fileName: String, mimeType: String, content: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            runCatching {
                val path = NSTemporaryDirectory() + fileName
                val written = NSString.create(string = content)
                    .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
                check(written) { "Could not write $fileName" }

                val presenter = topViewController() ?: error("No view controller to present the share sheet")
                val sheet = UIActivityViewController(
                    activityItems = listOf(NSURL.fileURLWithPath(path)),
                    applicationActivities = null
                )
                // On iPad the sheet is a popover and needs an anchor, or UIKit throws.
                sheet.popoverPresentationController?.let { popover ->
                    popover.sourceView = presenter.view
                    presenter.view.bounds.useContents {
                        popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
                    }
                }
                presenter.presentViewController(sheet, animated = true, completion = null)
            }
        }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}
