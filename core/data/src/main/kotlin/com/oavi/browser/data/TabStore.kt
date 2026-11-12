package com.oavi.browser.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.oavi.browser.model.GroupColor
import com.oavi.browser.model.Tab
import com.oavi.browser.model.TabGroup
import kotlinx.coroutines.withContext

data class SavedTabs(
    val tabs: List<Tab>,
    val sessionStates: Map<String, String>,
    val groups: List<TabGroup>,
    val selectedId: String?,
)

/** Saves open tabs so the browser comes back exactly where it was left. */
class TabStore(private val database: OaviDatabase) {

    suspend fun load(): SavedTabs = withContext(database.dispatcher) {
        val db = database.readableDatabase
        val tabs = ArrayList<Tab>()
        val states = HashMap<String, String>()
        db.rawQuery(
            "SELECT id, url, title, group_id, parent_id, created_at, last_access_at, archived, session_state " +
                "FROM tabs ORDER BY position, created_at",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                tabs += Tab(
                    id = id,
                    url = cursor.getString(1),
                    title = cursor.getString(2),
                    isPrivate = false,
                    groupId = if (cursor.isNull(3)) null else cursor.getString(3),
                    parentId = if (cursor.isNull(4)) null else cursor.getString(4),
                    createdAt = cursor.getLong(5),
                    lastAccessAt = cursor.getLong(6),
                    archived = cursor.getInt(7) != 0,
                )
                if (!cursor.isNull(8)) states[id] = cursor.getString(8)
            }
        }
        val groups = ArrayList<TabGroup>()
        db.rawQuery("SELECT id, name, color, created_at FROM tab_groups ORDER BY created_at", null).use { cursor ->
            while (cursor.moveToNext()) {
                val color = GroupColor.entries.firstOrNull { it.name == cursor.getString(2) } ?: GroupColor.AQUA
                groups += TabGroup(cursor.getString(0), cursor.getString(1), color, cursor.getLong(3))
            }
        }
        val selected = db.rawQuery("SELECT value FROM meta WHERE key = 'selected_tab'", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        SavedTabs(tabs, states, groups, selected)
    }

    suspend fun save(tab: Tab, position: Int) {
        if (tab.isPrivate) return
        withContext(database.dispatcher) {
            val db = database.writableDatabase
            val values = ContentValues().apply {
                put("url", tab.url)
                put("title", tab.title)
                put("group_id", tab.groupId)
                put("parent_id", tab.parentId)
                put("created_at", tab.createdAt)
                put("last_access_at", tab.lastAccessAt)
                put("archived", if (tab.archived) 1 else 0)
                put("position", position)
            }
            val updated = db.update("tabs", values, "id = ?", arrayOf(tab.id))
            if (updated == 0) {
                values.put("id", tab.id)
                db.insertWithOnConflict("tabs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    suspend fun saveSessionState(tabId: String, state: String?) = withContext(database.dispatcher) {
        val values = ContentValues().apply { put("session_state", state) }
        database.writableDatabase.update("tabs", values, "id = ?", arrayOf(tabId))
        Unit
    }

    suspend fun saveOrder(tabIds: List<String>) = withContext(database.dispatcher) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            tabIds.forEachIndexed { index, id ->
                db.execSQL("UPDATE tabs SET position = ? WHERE id = ?", arrayOf<Any>(index, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun delete(tabId: String) = withContext(database.dispatcher) {
        database.writableDatabase.delete("tabs", "id = ?", arrayOf(tabId))
        Unit
    }

    suspend fun saveSelected(tabId: String?) = withContext(database.dispatcher) {
        val values = ContentValues().apply {
            put("key", "selected_tab")
            put("value", tabId)
        }
        database.writableDatabase.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun saveGroup(group: TabGroup) = withContext(database.dispatcher) {
        val values = ContentValues().apply {
            put("id", group.id)
            put("name", group.name)
            put("color", group.color.name)
            put("created_at", group.createdAt)
        }
        database.writableDatabase.insertWithOnConflict("tab_groups", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun deleteGroup(groupId: String) = withContext(database.dispatcher) {
        database.writableDatabase.delete("tab_groups", "id = ?", arrayOf(groupId))
        Unit
    }
}
