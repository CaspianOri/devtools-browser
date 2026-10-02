package id.devtools.browser.media

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.View
import android.view.Window
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Viewport screenshot via PixelCopy (M3). PixelCopy has no View overload, so
 * we capture the window region covering the view's rect — this grabs the
 * composited surface, which works where drawing-cache tricks fail on
 * hardware layers. Callers should dismiss any overlay (e.g. the DevTools
 * sheet) before capturing so it is not part of the shot.
 */

/** Deterministic UTC filename, e.g. devtools-20261002-120000.png */
fun screenshotFileName(timestampMs: Long): String =
    "devtools-" + utcFormat("yyyyMMdd-HHmmss").format(Date(timestampMs)) + ".png"

/**
 * Captures the view's current viewport. The callback runs on the main thread
 * with the bitmap, or null when the view is not laid out, has no Activity
 * window, or the copy failed.
 */
fun captureViewport(view: View, onDone: (Bitmap?) -> Unit) {
    if (view.width <= 0 || view.height <= 0) {
        onDone(null)
        return
    }
    val window = findActivityWindow(view) ?: run {
        onDone(null)
        return
    }
    val loc = IntArray(2)
    view.getLocationInWindow(loc)
    val rect = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        window,
        rect,
        bitmap,
        PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            onDone(if (copyResult == PixelCopy.SUCCESS) bitmap else null)
        },
        Handler(Looper.getMainLooper()),
    )
}

private fun findActivityWindow(view: View): Window? {
    var ctx: Context? = view.context
    while (ctx != null) {
        if (ctx is Activity) return ctx.window
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
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
