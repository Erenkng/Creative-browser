package com.oavi.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Site icons, one per host, kept small (96 px) in the cache directory and in memory.
 * [revision] ticks whenever a new icon arrives, so lists showing letter tiles can refresh.
 */
class IconStore(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "icons")
    private val memory = LruCache<String, Bitmap>(96)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    suspend fun icon(host: String): Bitmap? {
        val key = key(host) ?: return null
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = file(key)
            if (!file.exists()) {
                null
            } else {
                try {
                    BitmapFactory.decodeFile(file.path)?.also { memory.put(key, it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    suspend fun has(host: String): Boolean {
        val key = key(host) ?: return true
        if (memory.get(key) != null) return true
        return withContext(Dispatchers.IO) { file(key).exists() }
    }

    suspend fun save(host: String, icon: Bitmap) {
        val key = key(host) ?: return
        val scaled = if (icon.width > SIZE || icon.height > SIZE) {
            val scale = SIZE.toFloat() / maxOf(icon.width, icon.height)
            Bitmap.createScaledBitmap(icon, (icon.width * scale).toInt().coerceAtLeast(1), (icon.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            icon
        }
        withContext(Dispatchers.IO) {
            try {
                directory.mkdirs()
                file(key).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {
                // Without a stored copy the icon is simply fetched again next time.
            }
        }
        memory.put(key, scaled)
        _revision.update { it + 1 }
    }

    /** One icon per site: hosts are compared without "www." and in lower case. */
    private fun key(host: String): String? =
        host.trim().lowercase().removePrefix("www.").takeIf { it.isNotEmpty() }

    private fun file(key: String) = File(directory, key.map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '_' }.joinToString("") + ".png")

    private companion object {
        const val SIZE = 96
    }
}
