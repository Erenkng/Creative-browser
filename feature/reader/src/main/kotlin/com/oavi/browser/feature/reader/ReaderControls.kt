package com.oavi.browser.feature.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oavi.browser.model.ReaderTheme
import com.oavi.browser.model.Settings
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviChip
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviRow
import com.oavi.browser.ui.OaviSheet
import com.oavi.browser.ui.R
import com.oavi.browser.ui.rememberHaptics
import kotlin.math.roundToInt

private const val MIN_SCALE = 0.85f
private const val MAX_SCALE = 1.75f
private const val STEP = 0.15f

/** Text size and paper colour for reading view. Changes show on the page as they are made. */
@Composable
fun ReaderControls(
    visible: Boolean,
    settings: Settings,
    onFontScale: (Float) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Oavi.colors
    val haptics = rememberHaptics()
    OaviSheet(visible = visible, onDismiss = onDismiss) {
        Text(
            stringResource(R.string.reader_title),
            style = Oavi.type.title,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OaviIconButton(
                icon = R.drawable.ic_text_size,
                contentDescription = stringResource(R.string.reader_smaller),
                enabled = settings.readerFontScale > MIN_SCALE + 0.01f,
                background = colors.accentSoft,
                onClick = {
                    haptics.tick()
                    onFontScale((settings.readerFontScale - STEP).coerceAtLeast(MIN_SCALE))
                },
            )
            Text(
                text = "${stringResource(R.string.reader_text_size)} · ${(settings.readerFontScale * 100).roundToInt()}%",
                style = Oavi.type.label,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            OaviIconButton(
                icon = R.drawable.ic_plus,
                contentDescription = stringResource(R.string.reader_larger),
                enabled = settings.readerFontScale < MAX_SCALE - 0.01f,
                background = colors.accentSoft,
                onClick = {
                    haptics.tick()
                    onFontScale((settings.readerFontScale + STEP).coerceAtMost(MAX_SCALE))
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeChip(ReaderTheme.AUTO, R.string.reader_theme_auto, settings, onTheme)
            ThemeChip(ReaderTheme.LIGHT, R.string.reader_theme_light, settings, onTheme)
            ThemeChip(ReaderTheme.SEPIA, R.string.reader_theme_sepia, settings, onTheme)
            ThemeChip(ReaderTheme.DARK, R.string.reader_theme_dark, settings, onTheme)
        }
        Spacer(Modifier.height(10.dp))
        OaviRow(stringResource(R.string.reader_exit), icon = R.drawable.ic_globe, onClick = onExit)
    }
}

@Composable
private fun ThemeChip(theme: ReaderTheme, label: Int, settings: Settings, onTheme: (ReaderTheme) -> Unit) {
    OaviChip(stringResource(label), selected = settings.readerTheme == theme, onClick = { onTheme(theme) })
}
