package com.oavi.browser.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.data.LibraryStore
import com.oavi.browser.model.Bookmark
import com.oavi.browser.model.HistoryEntry
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.LetterTile
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviScreen
import com.oavi.browser.ui.OaviScreenBottomInset
import com.oavi.browser.ui.OaviTextField
import com.oavi.browser.ui.R
import com.oavi.browser.ui.SnackbarState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class LibraryItem(
    val key: String,
    val url: String,
    val title: String,
    val detail: String,
    val historyId: Long?,
)

/**
 * History and bookmarks in one place. Deleting never asks "are you sure?"; it just offers
 * an Undo, which is both faster and kinder.
 */
@Composable
fun LibraryScreen(
    visible: Boolean,
    library: LibraryStore,
    snackbar: SnackbarState,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    val scope = rememberCoroutineScope()
    var showBookmarks by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val revision by library.revision.collectAsState()

    LaunchedEffect(visible) {
        if (!visible) query = ""
    }

    val items by produceState(emptyList<LibraryItem>(), visible, showBookmarks, query, revision) {
        if (!visible) return@produceState
        if (query.isNotEmpty()) delay(90)
        val now = System.currentTimeMillis()
        value = if (showBookmarks) {
            val found: List<Bookmark> = if (query.isBlank()) library.bookmarks() else library.searchBookmarks(query, limit = 100)
            found.map { LibraryItem("b${it.id}", it.url, it.title, UrlInput.displayHost(it.url), historyId = null) }
        } else {
            val found: List<HistoryEntry> = if (query.isBlank()) library.recentHistory() else library.searchHistory(query, limit = 100)
            found.map { entry ->
                val time = DateUtils.getRelativeTimeSpanString(entry.visitedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
                LibraryItem("h${entry.id}", entry.url, entry.title, "${UrlInput.displayHost(entry.url)} · $time", entry.id)
            }
        }
    }

    val historyCleared = stringResource(R.string.snack_history_cleared)
    val bookmarkRemoved = stringResource(R.string.snack_bookmark_removed)
    val undo = stringResource(R.string.snack_undo)

    OaviScreen(
        visible = visible,
        onClose = onClose,
        title = stringResource(if (showBookmarks) R.string.library_bookmarks else R.string.library_history),
        closeLabel = stringResource(R.string.tabs_done),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OaviChip(stringResource(R.string.library_history), selected = !showBookmarks, onClick = { showBookmarks = false })
            Spacer(Modifier.width(8.dp))
            OaviChip(stringResource(R.string.library_bookmarks), selected = showBookmarks, onClick = { showBookmarks = true })
            Spacer(Modifier.weight(1f))
            if (!showBookmarks && items.isNotEmpty() && query.isBlank()) {
                OaviIconButton(
                    icon = R.drawable.ic_trash,
                    contentDescription = stringResource(R.string.library_clear_history),
                    tint = colors.danger,
                    onClick = {
                        scope.launch {
                            val removed = library.clearHistory()
                            snackbar.show(historyCleared, undo) {
                                scope.launch { library.restoreHistory(removed) }
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OaviTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.library_search_hint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                text = stringResource(
                    when {
                        query.isNotBlank() -> R.string.library_no_results
                        showBookmarks -> R.string.library_empty_bookmarks
                        else -> R.string.library_empty_history
                    },
                ),
                style = Oavi.type.body,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = OaviScreenBottomInset),
            ) {
                items(items, key = { it.key }) { item ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onOpen(item.url) }
                            .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LetterTile(UrlInput.displayHost(item.url).ifEmpty { item.title })
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title.ifBlank { item.url },
                                style = Oavi.type.body,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(item.detail, style = Oavi.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OaviIconButton(
                            icon = R.drawable.ic_close,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = colors.muted,
                            modifier = Modifier.size(44.dp),
                            onClick = {
                                scope.launch {
                                    val historyId = item.historyId
                                    if (historyId != null) {
                                        library.deleteHistory(historyId)
                                    } else {
                                        library.removeBookmark(item.url)
                                        snackbar.show(bookmarkRemoved, undo) {
                                            scope.launch { library.addBookmark(item.url, item.title, System.currentTimeMillis()) }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
