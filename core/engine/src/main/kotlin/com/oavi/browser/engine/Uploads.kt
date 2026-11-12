package com.oavi.browser.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Files chosen for a page's upload field arrive from the system picker as content addresses,
 * which the engine cannot open by itself. Each is copied into the app's cache, under its
 * original name, and handed over as a plain file. Copies are removed after a few hours.
 */
internal object Uploads {
    private const val TAG = "OaviUploads"
    private const val KEEP_MS = 6L * 60 * 60 * 1000
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Copies [uris] off the main thread; [onReady] runs on the main thread with the files that worked. */
    fun prepare(context: Context, uris: List<Uri>, onReady: (List<Uri>) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val root = File(app.cacheDir, "uploads")
            removeOld(root)
            val files = uris.mapNotNull { copy(app, root, it) }
            main.post { onReady(files) }
        }
    }

    private fun copy(context: Context, root: File, uri: Uri): Uri? {
        if (uri.scheme == "file") return uri
        return try {
            val folder = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            val target = File(folder, safeName(displayName(context, uri)))
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source -> target.outputStream().use { source.copyTo(it, 64 * 1024) } }
            Uri.fromFile(target)
        } catch (e: Exception) {
            Log.w(TAG, "Could not prepare $uri for upload", e)
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun safeName(name: String?): String {
        val cleaned = name.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it >= ' ' && it !in "<>:\"|?*" }
            .trim()
            .trimStart('.')
        return cleaned.ifEmpty { "upload" }.takeLast(120)
    }

    private fun removeOld(root: File) {
        val cutoff = System.currentTimeMillis() - KEEP_MS
        root.listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
    }
}
