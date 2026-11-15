package com.oavi.browser.engine

import android.app.Activity
import android.view.MenuItem
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoSession

/**
 * The toolbar over selected text on a page: the engine's own Cut, Copy, Paste, Select all and
 * other apps' text actions (translate, define …), plus "Search" in a new tab.
 */
internal class SelectionToolbar(
    activity: Activity,
    private val label: () -> String,
    private val onSearch: (String) -> Unit,
) : BasicSelectionActionDelegate(activity) {

    override fun getAllActions(): Array<String> = super.getAllActions() + ACTION_SEARCH

    override fun isActionAvailable(id: String): Boolean {
        if (id != ACTION_SEARCH) return super.isActionAvailable(id)
        val selection = mSelection ?: return false
        val editable = (selection.flags and GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE) != 0
        return !editable && !selection.text.isNullOrBlank()
    }

    override fun prepareAction(id: String, item: MenuItem) {
        if (id == ACTION_SEARCH) {
            item.title = label()
            return
        }
        super.prepareAction(id, item)
    }

    override fun performAction(id: String, item: MenuItem): Boolean {
        if (id != ACTION_SEARCH) return super.performAction(id, item)
        val text = mSelection?.text?.trim()
        if (!text.isNullOrEmpty()) onSearch(text.take(MAX_QUERY))
        clearSelection()
        return true
    }

    private companion object {
        const val ACTION_SEARCH = "com.oavi.browser.SEARCH_SELECTION"
        const val MAX_QUERY = 400
    }
}
