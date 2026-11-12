package com.oavi.browser.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Small page previews for the tab grid, kept in the cache directory. */
class ThumbnailStore(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "thumbnails")

    suspend fun save(tabId: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            directory.mkdirs()
            val scale = TARGET_WIDTH.toFloat() / bitmap.width.coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    TARGET_WIDTH,
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            file(tabId).outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 72, out)
            }
        } catch (_: Exception) {
            // A missing preview is harmless; the card falls back to its letter tile.
        }
    }

    suspend fun load(tabId: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(tabId)
        if (!file.exists()) null else try {
            BitmapFactory.decodeFile(file.path)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun delete(tabId: String) = withContext(Dispatchers.IO) {
        file(tabId).delete()
        Unit
    }

    private fun file(tabId: String) = File(directory, "$tabId.webp")

    private companion object {
        const val TARGET_WIDTH = 540
    }
}

/** Writes downloads into the phone's shared Downloads folder. */
class DownloadWriter(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    /** Copies [input] into Downloads as [name]. Returns the new file's address, or null on failure. */
    suspend fun save(name: String, mimeType: String?, input: InputStream): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            if (!mimeType.isNullOrBlank()) put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target: Uri? = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {
            null
        }
        if (target == null) {
            closeQuietly(input)
            return@withContext null
        }
        try {
            input.use { source ->
                val sink = resolver.openOutputStream(target) ?: error("no output stream")
                sink.use { source.copyTo(it, 64 * 1024) }
            }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(target, done, null, null)
            target
        } catch (_: Exception) {
            try {
                resolver.delete(target, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun closeQuietly(input: InputStream) {
        try {
            input.close()
        } catch (_: Exception) {
        }
    }
}
