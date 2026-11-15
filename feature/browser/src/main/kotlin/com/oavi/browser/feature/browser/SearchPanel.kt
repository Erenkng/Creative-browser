package com.oavi.browser.feature.browser

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
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
import com.oavi.browser.model.SearchShortcuts
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
 * The address field. The current address arrives selected, so typing replaces it and one tap
 * edits it. Suggestions come only from this device (open tabs, bookmarks, history) and the
 * best match sits nearest the field: above it when the field is at the bottom, below it
 * when the field is at the top ([fieldOnTop]).
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
    fieldOnTop: Boolean = false,
) {
    val colors = Oavi.colors
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var field by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val query = field.text.trim()
    val untouched = query == initialText && initialText.isNotEmpty()

    // Only the clipboard's description is read here (no "pasted" notice); its text is read
    // when the chip is tapped.
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val canPaste = remember {
        try {
            val description = clipboard?.primaryClipDescription
            description != null && (
                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                    description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST) ||
                    description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                )
        } catch (_: SecurityException) {
            false
        }
    }
    val pasteAndGo: () -> Unit = {
        val text = try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        } catch (_: SecurityException) {
            null
        }
        if (!text.isNullOrEmpty()) onSubmit(text)
    }

    val suggestions by produceState(emptyList<Suggestion>(), query, untouched) {
        if (query.isEmpty() || untouched || query.startsWith("!")) {
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
    val shortcut = SearchShortcuts.typing(field.text)
    val shortcutHelp = if (shortcut == null && query.startsWith("!") && !query.contains(' ')) {
        val typed = query.removePrefix("!").lowercase()
        SearchShortcuts.all.filter { s -> s.keys.any { it.startsWith(typed) } }.take(6)
    } else {
        emptyList()
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    val fieldRow: @Composable () -> Unit = {
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
            } else {
                VoiceSearchButton(onResult = onSubmit)
            }
        }
    }

    val chips: @Composable () -> Unit = {
        val showPaste = canPaste && (query.isEmpty() || untouched)
        if (untouched || showPaste) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = if (fieldOnTop) 0.dp else 14.dp, bottom = if (fieldOnTop) 12.dp else 0.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showPaste) {
                    OaviChip(stringResource(R.string.search_paste_go), selected = false, onClick = pasteAndGo)
                    Spacer(Modifier.width(8.dp))
                }
                if (untouched) {
                    OaviChip(stringResource(R.string.search_copy_link), selected = false, onClick = { onCopy(initialText) })
                    Spacer(Modifier.width(8.dp))
                    OaviChip(stringResource(R.string.search_share), selected = false, onClick = { onShare(initialText) })
                }
            }
        }
    }

    val shortcutRows: @Composable () -> Unit = {
        if (shortcut != null) {
            HintRow(
                icon = R.drawable.ic_search,
                title = stringResource(R.string.search_shortcut_hint, shortcut.name),
                detail = SearchShortcuts.match(field.text)?.query,
                onClick = { if (SearchShortcuts.match(field.text) != null) onSubmit(field.text.trim()) },
            )
        }
        for (option in shortcutHelp) {
            HintRow(
                icon = R.drawable.ic_search,
                title = "!" + option.keys.first() + "  ·  " + option.name,
                detail = null,
                onClick = {
                    val text = "!" + option.keys.first() + " "
                    field = TextFieldValue(text, selection = TextRange(text.length))
                },
            )
        }
    }

    val suggestionRows: @Composable (bestFirst: Boolean) -> Unit = { bestFirst ->
        for (suggestion in if (bestFirst) suggestions else suggestions.asReversed()) {
            SuggestionRow(suggestion) {
                val tabId = suggestion.tabId
                if (suggestion.kind == Suggestion.Kind.OPEN_TAB && tabId != null) onSelectTab(tabId) else onSubmit(suggestion.url)
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (fieldOnTop) {
            fieldRow()
            chips()
            shortcutRows()
            suggestionRows(true)
            if (suggestions.isNotEmpty() || shortcutHelp.isNotEmpty()) Spacer(Modifier.height(8.dp))
        } else {
            if (suggestions.isNotEmpty() || shortcutHelp.isNotEmpty()) Spacer(Modifier.height(8.dp))
            suggestionRows(false)
            shortcutRows()
            chips()
            fieldRow()
        }
    }
}

@Composable
private fun HintRow(icon: Int, title: String, detail: String?, onClick: () -> Unit) {
    val colors = Oavi.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Oavi.type.body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) {
                Text(detail, style = Oavi.type.caption, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Speak instead of typing. Uses the phone's own speech recogniser, so nothing extra is
 * installed and the button simply does not appear on phones without one.
 */
@Composable
private fun VoiceSearchButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val prompt = stringResource(R.string.voice_prompt)
    val available = remember {
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onResult(spoken)
    }
    if (!available) return
    OaviIconButton(
        icon = R.drawable.ic_mic,
        contentDescription = stringResource(R.string.cd_voice_search),
        tint = Oavi.colors.accent,
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            try {
                launcher.launch(intent)
            } catch (_: Exception) {
                // The recogniser went away after the check; nothing to do.
            }
        },
    )
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
