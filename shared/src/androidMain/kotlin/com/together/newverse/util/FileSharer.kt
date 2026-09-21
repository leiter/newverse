package com.together.newverse.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android: writes the file to the cache and opens the system chooser. The file is
 * shared through the app's FileProvider (`cache-path` in file_paths.xml), so the
 * receiving app gets a temporary read grant and no storage permission is needed.
 */
actual class FileSharer(private val context: Context) : TextFileSharer {

    override suspend fun shareTextFile(fileName: String, mimeType: String, content: String): Result<Unit> =
        runCatching {
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
                    .resolve(fileName)
                    .apply { writeText(content, Charsets.UTF_8) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                // The clip data carries the read grant through the chooser to the target app.
                clipData = ClipData.newRawUri(fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Started from the application context, so the chooser needs its own task.
            val chooser = Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(chooser) }
        }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
