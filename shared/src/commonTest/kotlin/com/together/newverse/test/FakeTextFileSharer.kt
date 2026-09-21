package com.together.newverse.test

import com.together.newverse.util.TextFileSharer

/** Records the files that would have been shared instead of opening a share sheet. */
class FakeTextFileSharer : TextFileSharer {

    data class SharedFile(val fileName: String, val mimeType: String, val content: String)

    private val _shared = mutableListOf<SharedFile>()
    val shared: List<SharedFile> get() = _shared.toList()

    var shouldFail = false

    override suspend fun shareTextFile(fileName: String, mimeType: String, content: String): Result<Unit> {
        if (shouldFail) return Result.failure(Exception("Share failed"))
        _shared.add(SharedFile(fileName, mimeType, content))
        return Result.success(Unit)
    }
}
