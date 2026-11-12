package com.oavi.browser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Follows the system back gesture. The returned value runs from 0 to 1 while the user is
 * swiping back, so a surface can shrink away under the finger and show what is behind it;
 * [onBack] runs if the swipe is completed, and the value eases back to 0 if it is abandoned.
 */
@Composable
fun rememberBackProgress(enabled: Boolean, onBack: () -> Unit): State<Float> {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnBack by rememberUpdatedState(onBack)

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> progress.snapTo(event.progress) }
            currentOnBack()
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(0f, OaviMotion.spatial()) }
            throw e
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            // Let the surface finish leaving before it is reset for next time.
            delay(350)
            progress.snapTo(0f)
        }
    }
    return progress.asState()
}

/**
 * A sheet rising from the bottom edge, where thumbs are. Dismissed by tapping outside,
 * dragging the handle down, or the back gesture (which previews the dismissal as it goes).
 * Fills its parent; place it last inside a full-screen Box.
 */
@Composable
fun OaviSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Oavi.colors
    val back by rememberBackProgress(visible, onDismiss)
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    LaunchedEffect(visible) {
        if (visible) drag.snapTo(0f)
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(OaviMotion.fade()),
            exit = fadeOut(OaviMotion.fade()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(OaviMotion.spatial()) { it } + fadeIn(OaviMotion.fade()),
            exit = slideOutVertically(OaviMotion.gentle()) { it } + fadeOut(OaviMotion.fade()),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = 1f - back * 0.06f
                        scaleX = scale
                        scaleY = scale
                        translationY = drag.value + back * 40.dp.toPx()
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(colors.surface)
                    // Swallow taps so they do not fall through to the scrim behind the sheet.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(Unit) {
                            val dismissAt = 96.dp.toPx()
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (drag.value > dismissAt) {
                                        haptics.tick()
                                        onDismiss()
                                    } else {
                                        scope.launch { drag.animateTo(0f, OaviMotion.spatial()) }
                                    }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(0f, OaviMotion.spatial()) } },
                            ) { change, amount ->
                                change.consume()
                                scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(colors.faint),
                    )
                }
                content()
            }
        }
    }
}

/**
 * A question for the user, asked from the bottom of the screen. The affirmative action sits
 * bottom-right under the thumb; the safe way out is always present on its left.
 */
@Composable
fun OaviPrompt(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    OaviSheet(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(title, style = Oavi.type.title, color = Oavi.colors.onSurface)
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = Oavi.type.body, color = Oavi.colors.muted, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
        }
        if (content != null) {
            Spacer(Modifier.height(16.dp))
            content()
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissLabel != null) {
                OaviButton(dismissLabel, onClick = onDismiss, kind = ButtonKind.QUIET)
                Spacer(Modifier.width(8.dp))
            }
            OaviButton(
                text = confirmLabel,
                onClick = onConfirm,
                kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY,
                enabled = confirmEnabled,
            )
        }
    }
}

data class Snack(
    val id: Int,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** Brief messages that appear above the island, optionally with one action such as "Undo". */
@Stable
class SnackbarState {
    private var counter = 0

    var current by mutableStateOf<Snack?>(null)
        private set

    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        counter += 1
        current = Snack(counter, message, actionLabel, onAction)
    }

    fun dismiss() {
        current = null
    }
}

@Composable
fun OaviSnackbarHost(state: SnackbarState, modifier: Modifier = Modifier) {
    val snack = state.current
    LaunchedEffect(snack?.id) {
        if (snack != null) {
            // An undo deserves time to be noticed and reached; plain notices can leave sooner.
            delay(if (snack.actionLabel != null) 6_000 else 3_200)
            if (state.current?.id == snack.id) state.dismiss()
        }
    }
    AnimatedContent(
        targetState = snack,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically(OaviMotion.spatial()) { it / 2 } + fadeIn(OaviMotion.fade())) togetherWith
                fadeOut(OaviMotion.fade())
        },
        contentAlignment = Alignment.BottomCenter,
        label = "snackbar",
    ) { shown ->
        if (shown == null) {
            Spacer(Modifier.size(0.dp))
        } else {
            val colors = Oavi.colors
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.onSurface)
                    .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shown.message,
                    style = Oavi.type.label,
                    color = colors.background,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 10.dp),
                )
                if (shown.actionLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    OaviButton(
                        text = shown.actionLabel,
                        kind = ButtonKind.TONAL,
                        modifier = Modifier.height(40.dp),
                        onClick = {
                            shown.onAction?.invoke()
                            state.dismiss()
                        },
                    )
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

/**
 * A full screen laid over the browser (tabs, settings, library). It arrives from below,
 * shrinks under the back gesture to show the page behind it, and keeps its way out at the
 * bottom of the screen rather than in a far corner.
 */
@Composable
fun OaviScreen(
    visible: Boolean,
    onClose: () -> Unit,
    title: String,
    closeLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Oavi.colors
    val back by rememberBackProgress(visible, onClose)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(OaviMotion.fade()) + slideInVertically(OaviMotion.spatial()) { it / 6 },
        exit = fadeOut(OaviMotion.fade()) + slideOutVertically(OaviMotion.gentle()) { it / 8 },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - back * 0.08f
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape((36 * back).dp)
                    clip = back > 0f
                }
                .background(colors.background)
                // Nothing behind this screen should receive touches.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Text(
                    text = title,
                    style = Oavi.type.display,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp),
                )
                content()
            }
            OaviButton(
                text = closeLabel,
                onClick = onClose,
                kind = ButtonKind.PRIMARY,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp),
            )
        }
    }
}

/** Space to leave at the end of scrolling content so it clears [OaviScreen]'s close button. */
val OaviScreenBottomInset = 112.dp
