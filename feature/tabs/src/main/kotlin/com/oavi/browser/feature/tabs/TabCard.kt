package com.oavi.browser.feature.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oavi.browser.engine.TabState
import com.oavi.browser.engine.TabsController
import com.oavi.browser.model.UrlInput
import com.oavi.browser.ui.LetterTile
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.OaviIconButton
import com.oavi.browser.ui.OaviMotion
import com.oavi.browser.ui.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * One tab in the grid: a picture of the page and its name. Swiping it sideways closes it;
 * the card follows the finger and fades as it goes, so the outcome is clear before release.
 */
@Composable
internal fun TabCard(
    tab: TabState,
    selected: Boolean,
    groupColor: Color?,
    controller: TabsController,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Oavi.colors
    val scope = rememberCoroutineScope()
    val offset = remember(tab.id) { Animatable(0f) }
    val close by rememberUpdatedState(onClose)
    val shape = RoundedCornerShape(22.dp)

    val preview by produceState<ImageBitmap?>(null, tab.id, tab.thumbnailVersion) {
        value = controller.preview(tab.id)?.asImageBitmap()
    }
    val host = UrlInput.displayHost(tab.url)
    val title = tab.tab.label.ifBlank { stringResource(R.string.tabs_untitled) }

    Column(
        modifier = modifier
            .graphicsLayer {
                translationX = offset.value
                alpha = (1f - abs(offset.value) / (size.width.coerceAtLeast(1f) * 0.9f)).coerceIn(0f, 1f)
            }
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val width = size.width.toFloat()
                        if (abs(offset.value) > width * 0.34f) {
                            scope.launch {
                                offset.animateTo(if (offset.value > 0) width * 1.2f else -width * 1.2f, OaviMotion.spatial())
                                close()
                            }
                        } else {
                            scope.launch { offset.animateTo(0f, OaviMotion.lively()) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, OaviMotion.spatial()) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + amount) }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(shape)
                .background(colors.surface)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) colors.accent else colors.faint,
                    shape = shape,
                )
                .combinedClickable(role = Role.Button, onLongClick = onHold, onClick = onOpen),
        ) {
            val image = preview
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (tab.isBlank) {
                        Icon(
                            painterResource(if (tab.isPrivate) R.drawable.ic_private_mode else R.drawable.ic_search),
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        LetterTile(host.ifEmpty { title }, size = 56.dp)
                    }
                }
            }
            OaviIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.cd_close_tab),
                onClick = onClose,
                tint = Color.White,
                background = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(34.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (groupColor != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(groupColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = Oavi.type.label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
