package com.oavi.browser.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.oavi.browser.model.Bookmark
import com.oavi.browser.model.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** History and bookmarks. [revision] ticks on every change so screens know to reload. */
class LibraryStore(private val database: OaviDatabase) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    // ---- History ----

    suspend fun recordVisit(url: String, title: String, at: Long) = withContext(database.dispatcher) {
        database.writableDatabase.execSQL(
            "INSERT INTO history (url, title, visited_at, visit_count) VALUES (?, ?, ?, 1) " +
                "ON CONFLICT(url) DO UPDATE SET visit_count = visit_count + 1, visited_at = excluded.visited_at, " +
                "title = CASE WHEN excluded.title = '' THEN history.title ELSE excluded.title END",
            arrayOf<Any>(url, title, at),
        )
        changed()
    }

    suspend fun updateTitle(url: String, title: String) = withContext(database.dispatcher) {
        if (title.isBlank()) return@withContext
        val values = ContentValues().apply { put("title", title) }
        val rows = database.writableDatabase.update("history", values, "url = ?", arrayOf(url))
        if (rows > 0) changed()
    }

    suspend fun recentHistory(limit: Int = 200): List<HistoryEntry> = withContext(database.dispatcher) {
        database.readableDatabase.rawQuery(
            "SELECT id, url, title, visited_at, visit_count FROM history ORDER BY visited_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use(::readHistory)
    }

    suspend fun searchHistory(query: String, limit: Int = 30): List<HistoryEntry> = withContext(database.dispatcher) {
        val like = likePattern(query)
        database.readableDatabase.rawQuery(
            "SELECT id, url, title, visited_at, visit_count FROM history " +
                "WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' " +
                "ORDER BY visit_count DESC, visited_at DESC LIMIT ?",
            arrayOf(like, like, limit.toString()),
        ).use(::readHistory)
    }

    /** The sites visited most often, one row per page, for the new-tab shortcuts. */
    suspend fun topSites(limit: Int = 8): List<HistoryEntry> = withContext(database.dispatcher) {
        database.readableDatabase.rawQuery(
            "SELECT id, url, title, visited_at, visit_count FROM history " +
                "WHERE visit_count >= 2 ORDER BY visit_count DESC, visited_at DESC LIMIT ?",
            arrayOf((limit * 4).toString()),
        ).use(::readHistory)
    }

    suspend fun deleteHistory(id: Long) = withContext(database.dispatcher) {
        database.writableDatabase.delete("history", "id = ?", arrayOf(id.toString()))
        changed()
    }

    suspend fun clearHistory(): List<HistoryEntry> = withContext(database.dispatcher) {
        val removed = database.readableDatabase.rawQuery(
            "SELECT id, url, title, visited_at, visit_count FROM history ORDER BY visited_at DESC",
            null,
        ).use(::readHistory)
        database.writableDatabase.delete("history", null, null)
        changed()
        removed
    }

    /** Puts entries back, used by "Undo" after deleting history. */
    suspend fun restoreHistory(entries: List<HistoryEntry>) = withContext(database.dispatcher) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            for (entry in entries) {
                val values = ContentValues().apply {
                    put("url", entry.url)
                    put("title", entry.title)
                    put("visited_at", entry.visitedAt)
                    put("visit_count", entry.visitCount)
                }
                db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        changed()
    }

    // ---- Bookmarks ----

    suspend fun addBookmark(url: String, title: String, at: Long) = withContext(database.dispatcher) {
        val values = ContentValues().apply {
            put("url", url)
            put("title", title)
            put("created_at", at)
        }
        database.writableDatabase.insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        changed()
    }

    suspend fun removeBookmark(url: String) = withContext(database.dispatcher) {
        database.writableDatabase.delete("bookmarks", "url = ?", arrayOf(url))
        changed()
    }

    suspend fun isBookmarked(url: String): Boolean = withContext(database.dispatcher) {
        database.readableDatabase.rawQuery("SELECT 1 FROM bookmarks WHERE url = ? LIMIT 1", arrayOf(url))
            .use { it.moveToFirst() }
    }

    suspend fun bookmarks(): List<Bookmark> = withContext(database.dispatcher) {
        database.readableDatabase.rawQuery(
            "SELECT id, url, title, created_at FROM bookmarks ORDER BY created_at DESC",
            null,
        ).use(::readBookmarks)
    }

    suspend fun searchBookmarks(query: String, limit: Int = 20): List<Bookmark> = withContext(database.dispatcher) {
        val like = likePattern(query)
        database.readableDatabase.rawQuery(
            "SELECT id, url, title, created_at FROM bookmarks " +
                "WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\' ORDER BY created_at DESC LIMIT ?",
            arrayOf(like, like, limit.toString()),
        ).use(::readBookmarks)
    }

    // ---- Per-site rules ----

    suspend fun protectionOffHosts(): Set<String> = withContext(database.dispatcher) {
        val hosts = HashSet<String>()
        database.readableDatabase.rawQuery("SELECT host FROM site_rules WHERE protection_off = 1", null).use { cursor ->
            while (cursor.moveToNext()) hosts += cursor.getString(0)
        }
        hosts
    }

    suspend fun setProtectionOff(host: String, off: Boolean) = withContext(database.dispatcher) {
        val db = database.writableDatabase
        if (off) {
            val values = ContentValues().apply {
                put("host", host)
                put("protection_off", 1)
            }
            db.insertWithOnConflict("site_rules", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } else {
            db.delete("site_rules", "host = ?", arrayOf(host))
        }
        Unit
    }

    private fun changed() {
        _revision.update { it + 1 }
    }

    private fun readHistory(cursor: Cursor): List<HistoryEntry> {
        val result = ArrayList<HistoryEntry>(cursor.count)
        while (cursor.moveToNext()) {
            result += HistoryEntry(
                id = cursor.getLong(0),
                url = cursor.getString(1),
                title = cursor.getString(2),
                visitedAt = cursor.getLong(3),
                visitCount = cursor.getInt(4),
            )
        }
        return result
    }

    private fun readBookmarks(cursor: Cursor): List<Bookmark> {
        val result = ArrayList<Bookmark>(cursor.count)
        while (cursor.moveToNext()) {
            result += Bookmark(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))
        }
        return result
    }

    private fun likePattern(query: String): String {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }
}
