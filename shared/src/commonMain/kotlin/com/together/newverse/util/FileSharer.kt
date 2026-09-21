package com.together.newverse.util

/**
 * Hands a generated text file to the user so they can send or save it. An interface,
 * so view models can be tested without a platform.
 */
interface TextFileSharer {
    /**
     * Writes [content] as UTF-8 to a temporary file named [fileName] and opens the
     * share sheet for it. Succeeds once the sheet is shown; what the user then does
     * with the file is up to them.
     */
    suspend fun shareTextFile(fileName: String, mimeType: String, content: String): Result<Unit>
}

/** The platform's share sheet. Created per platform by Koin. */
expect class FileSharer : TextFileSharer
