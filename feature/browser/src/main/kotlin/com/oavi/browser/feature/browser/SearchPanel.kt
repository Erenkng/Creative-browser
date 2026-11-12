package com.oavi.browser.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.data.LibraryStore
import com.oavi.browser.model.Suggestion
import com.oavi.browser.model.Suggestions
import com.oavi.browser.model.Tab
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.R
import kotlinx.coroutines.delay

/**
 * The island as an address field. The current address arrives selected, so typing replaces
 * it and one tap edits it. Suggestions come only from this device (open tabs, bookmarks,
 * history) and the best match sits nearest the field, closest to the thumb.
 */
@Composable
internal fun SearchPanel(
    initialText: String,
    openTabs: List<Tab>,
    library: LibraryStore,
    onSubmit: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val colors = Oavi.colors
    val focus = remember { FocusRequester() }
    var field by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val query = field.text.trim()
    val untouched = query == initialText && initialText.isNotEmpty()

    val suggestions by produceState(emptyList<Suggestion>(), query, untouched) {
        if (query.isEmpty() || untouched) {
            value = emptyList()
        } else {
            delay(70)
            value = Suggestions.rank(
                query = query,
                openTabs = openTabs,
                bookmarks = library.searchBookmarks(query),
                history = library.searchHistory(query),
                limit = 5,
            )
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxWidth()) {
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            for (suggestion in suggestions.asReversed()) {
                SuggestionRow(suggestion) {
                    val tabId = suggestion.tabId
                    if (suggestion.kind == Suggestion.Kind.OPEN_TAB && tabId != null) onSelectTab(tabId) else onSubmit(suggestion.url)
                }
            }
        }
        if (untouched) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OaviChip(stringResource(R.string.search_copy_link), selected = false, onClick = { onCopy(initialText) })
                Spacer(Modifier.width(8.dp))
                OaviChip(stringResource(R.string.search_share), selected = false, onClick = { onShare(initialText) })
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                singleLine = true,
                textStyle = Oavi.type.headline.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { if (query.isNotEmpty()) onSubmit(query) }),
                decorationBox = { inner ->
                    if (field.text.isEmpty()) {
                        Text(
                            stringResource(R.string.island_placeholder),
                            style = Oavi.type.headline,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                },
            )
            if (field.text.isNotEmpty()) {
                OaviIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.cd_clear),
                    onClick = { field = TextFieldValue("") },
                    tint = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: Suggestion, onClick: () -> Unit) {
    val colors = Oavi.colors
    val icon = when (suggestion.kind) {
        Suggestion.Kind.OPEN_TAB -> R.drawable.ic_tab
        Suggestion.Kind.BOOKMARK -> R.drawable.ic_bookmark
        Suggestion.Kind.HISTORY -> R.drawable.ic_history
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = suggestion.title.ifBlank { UrlInput.displayHost(suggestion.url) },
                style = Oavi.type.body,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (suggestion.kind == Suggestion.Kind.OPEN_TAB) {
                    stringResource(R.string.search_switch_to_tab)
                } else {
                    UrlInput.displayHost(suggestion.url)
                },
                style = Oavi.type.caption,
                color = if (suggestion.kind == Suggestion.Kind.OPEN_TAB) colors.accent else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The island as a find-in-page bar. */
@Composable
internal fun FindBar(
    current: Int,
    total: Int,
    onQuery: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Oavi.colors
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_find), contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onQuery(it)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
            singleLine = true,
            textStyle = Oavi.type.headline.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(stringResource(R.string.find_placeholder), style = Oavi.type.headline, color = colors.muted, maxLines = 1)
                }
                inner()
            },
        )
        if (text.isNotEmpty()) {
            Text(
                text = "$current/$total",
                style = Oavi.type.caption,
                color = if (total == 0) colors.danger else colors.muted,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        OaviIconButton(R.drawable.ic_chevron_up, stringResource(R.string.cd_previous), onPrevious, Modifier.size(44.dp), enabled = total > 0)
        OaviIconButton(R.drawable.ic_chevron_down, stringResource(R.string.cd_next), onNext, Modifier.size(44.dp), enabled = total > 0)
        OaviIconButton(R.drawable.ic_close, stringResource(R.string.cd_close), onClose, Modifier.size(44.dp))
    }
}
