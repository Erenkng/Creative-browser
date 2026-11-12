package com.oavi.browser.model

import java.net.URLDecoder

object DownloadNames {
    private val extensionByMime = mapOf(
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "application/vnd.android.package-archive" to "apk",
        "application/json" to "json",
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "image/gif" to "gif",
        "image/webp" to "webp",
        "image/svg+xml" to "svg",
        "video/mp4" to "mp4",
        "audio/mpeg" to "mp3",
        "text/plain" to "txt",
        "text/html" to "html",
        "text/csv" to "csv",
    )

    /** A safe file name for a download, from the server's hint, else the address, else "download". */
    fun guess(url: String, contentDisposition: String?, mimeType: String?): String {
        val fromHeader = contentDisposition?.let(::fromContentDisposition)
        val fromUrl = fromUrl(url)
        var name = sanitize(fromHeader ?: fromUrl ?: "download")
        if (name.isEmpty()) name = "download"
        if (!name.contains('.') || name.endsWith('.')) {
            val mime = mimeType?.substringBefore(';')?.trim()?.lowercase()
            val extension = extensionByMime[mime]
            if (extension != null) name = name.trimEnd('.') + "." + extension
        }
        return name
    }

    private fun fromContentDisposition(header: String): String? {
        val extended = Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(header)
        if (extended != null) {
            val charset = extended.groupValues[1].ifBlank { "UTF-8" }
            decode(extended.groupValues[2].trim(), charset)?.let { return it }
        }
        val quoted = Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(header)
        if (quoted != null) return quoted.groupValues[1].takeIf { it.isNotBlank() }
        val bare = Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(header)
        return bare?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun fromUrl(url: String): String? {
        if (url.startsWith("data:") || url.startsWith("blob:")) return null
        val path = url.substringBefore('#').substringBefore('?')
        val afterScheme = path.substringAfter("://", path)
        if (!afterScheme.contains('/')) return null
        val segment = afterScheme.substringAfterLast('/')
        if (segment.isBlank()) return null
        return decode(segment, "UTF-8") ?: segment
    }

    private fun decode(value: String, charset: String): String? = try {
        URLDecoder.decode(value.replace("+", "%2B"), charset)
    } catch (_: Exception) {
        null
    }

    private fun sanitize(name: String): String {
        val cleaned = name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it >= ' ' && it !in "<>:\"|?*" }
            .trim()
            .trimStart('.')
        return if (cleaned.length <= 120) cleaned else {
            val extension = cleaned.substringAfterLast('.', "").take(10)
            val stem = cleaned.take(120 - extension.length - 1)
            if (extension.isEmpty()) stem else "$stem.$extension"
        }
    }
}
