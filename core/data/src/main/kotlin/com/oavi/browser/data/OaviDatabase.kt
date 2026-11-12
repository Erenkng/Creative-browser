package com.oavi.browser.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * One small SQLite file for everything that must outlive the process: open tabs, groups,
 * history, bookmarks and per-site rules. Private tabs are never written here.
 */
class OaviDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "oavi.db", null, VERSION) {

    /** All access goes through one thread so writes land in the order they were made. */
    val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tabs (
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                group_id TEXT,
                parent_id TEXT,
                created_at INTEGER NOT NULL,
                last_access_at INTEGER NOT NULL,
                archived INTEGER NOT NULL DEFAULT 0,
                position INTEGER NOT NULL DEFAULT 0,
                session_state TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE tab_groups (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                visited_at INTEGER NOT NULL,
                visit_count INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX history_visited_at ON history(visited_at DESC)")
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE site_rules (host TEXT PRIMARY KEY, protection_off INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First schema; nothing to migrate yet.
    }

    private companion object {
        const val VERSION = 1
    }
}
