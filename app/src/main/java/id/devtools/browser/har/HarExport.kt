package id.devtools.browser.har

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * Writes a HAR document to the Downloads collection via MediaStore (M3).
 * On API 29+ the file lands in Downloads/ without any storage permission;
 * on API 26-28 it falls back to the public Downloads directory, which needs
 * WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion 28 in the manifest).
 *
 * Returns the content Uri (API 29+) or file Uri (older), or null on failure.
 */
fun saveHarToDownloads(context: Context, harJson: String, fileName: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveViaMediaStore(context, harJson, fileName)
    } else {
        saveViaPublicDir(harJson, fileName)
    }
}

private fun saveViaMediaStore(context: Context, harJson: String, fileName: String): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/json")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: return null
    try {
        resolver.openOutputStream(uri)?.use { out ->
            out.write(harJson.toByteArray(Charsets.UTF_8))
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }
    } catch (e: IOException) {
        resolver.delete(uri, null, null)
        return null
    }
    val done = ContentValues().apply {
        put(MediaStore.Downloads.IS_PENDING, 0)
    }
    resolver.update(uri, done, null, null)
    return uri
}

private fun saveViaPublicDir(harJson: String, fileName: String): Uri? {
    return try {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(harJson, Charsets.UTF_8)
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}
