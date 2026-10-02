package id.devtools.browser.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Viewport screenshot via PixelCopy (M3). PixelCopy captures the composited
 * surface, which works where drawing-cache tricks fail on hardware layers.
 */

/** Deterministic UTC filename, e.g. devtools-20261002-120000.png */
fun screenshotFileName(timestampMs: Long): String =
    "devtools-" + utcFormat("yyyyMMdd-HHmmss").format(Date(timestampMs)) + ".png"

/**
 * Captures the view's current viewport. The callback runs on the main thread
 * with the bitmap, or null when the view is not laid out or the copy failed.
 */
fun captureViewport(view: View, onDone: (Bitmap?) -> Unit) {
    if (view.width <= 0 || view.height <= 0) {
        onDone(null)
        return
    }
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        view,
        bitmap,
        { copyResult -> onDone(if (copyResult == PixelCopy.SUCCESS) bitmap else null) },
        Handler(Looper.getMainLooper()),
    )
}

/**
 * Saves a PNG under Pictures/DevToolsBrowser via MediaStore. Returns the
 * content Uri, or null on failure (a half-written entry is deleted).
 */
fun saveBitmapToPictures(context: Context, bitmap: Bitmap, fileName: String): Uri? {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DevToolsBrowser")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(collection, values) ?: return null
    try {
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                resolver.delete(uri, null, null)
                return null
            }
        } ?: run {
            resolver.delete(uri, null, null)
            return null
        }
    } catch (e: IOException) {
        resolver.delete(uri, null, null)
        return null
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val done = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(uri, done, null, null)
    }
    return uri
}

private fun utcFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
