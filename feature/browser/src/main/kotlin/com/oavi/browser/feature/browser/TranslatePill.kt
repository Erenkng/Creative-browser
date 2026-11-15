package com.oavi.browser.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.ui.ButtonKind
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviButton
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import com.oavi.browser.ui.glass

/**
 * A quiet offer above the island when a page is in another language: one tap translates it
 * on the phone itself, the cross makes it go away for this page.
 */
@Composable
internal fun TranslatePill(
    visible: Boolean,
    translating: Boolean,
    languageName: String,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(OaviMotion.spatial()) { it / 2 } + fadeIn(OaviMotion.fade()),
        exit = slideOutVertically(OaviMotion.gentle()) { it / 2 } + fadeOut(OaviMotion.fade()),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 520.dp)
                .glass(CircleShape, elevation = 10.dp)
                .height(52.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_translate), contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (translating) stringResource(R.string.translating) else stringResource(R.string.translate_offer, languageName),
                style = Oavi.type.label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!translating) {
                Spacer(Modifier.width(6.dp))
                OaviButton(
                    text = stringResource(R.string.translate_action),
                    onClick = onTranslate,
                    kind = ButtonKind.TONAL,
                    modifier = Modifier.height(40.dp),
                )
                OaviIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.cd_close),
                    onClick = onDismiss,
                    tint = colors.muted,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}
