package com.together.newverse.util

/** The web app is the buyer app; it has nothing to export. */
actual class FileSharer : TextFileSharer {
    override suspend fun shareTextFile(fileName: String, mimeType: String, content: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("File sharing is not supported on web"))
}
