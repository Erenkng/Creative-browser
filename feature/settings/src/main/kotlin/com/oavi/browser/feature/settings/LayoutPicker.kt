package com.oavi.browser.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oavi.browser.model.Accent
import com.oavi.browser.model.LayoutStyle
import com.oavi.browser.ui.Oavi
import com.oavi.browser.ui.R
import com.oavi.browser.ui.accentColor

/** The name of a layout as people see it. */
internal fun LayoutStyle.label(): Int = when (this) {
    LayoutStyle.ISLAND -> R.string.layout_island
    LayoutStyle.CLASSIC -> R.string.layout_classic
    LayoutStyle.STRIP -> R.string.layout_strip
    LayoutStyle.DOCK -> R.string.layout_dock
    LayoutStyle.FOCUS -> R.string.layout_focus
}

internal fun LayoutStyle.description(): Int = when (this) {
    LayoutStyle.ISLAND -> R.string.layout_island_body
    LayoutStyle.CLASSIC -> R.string.layout_classic_body
    LayoutStyle.STRIP -> R.string.layout_strip_body
    LayoutStyle.DOCK -> R.string.layout_dock_body
    LayoutStyle.FOCUS -> R.string.layout_focus_body
}

/** Every layout as a small drawing of a phone, three to a row; tap one to use it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayoutPicker(current: LayoutStyle, leftHanded: Boolean, onPick: (LayoutStyle) -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        val columns = if (maxWidth > 560.dp) LayoutStyle.entries.size else 3
        val gap = 10.dp
        val cardWidth = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns,
        ) {
            for (style in LayoutStyle.entries) {
                LayoutCard(style, selected = style == current, leftHanded = leftHanded, width = cardWidth) { onPick(style) }
            }
        }
    }
    Text(
        text = stringResource(current.description()),
        style = Oavi.type.caption,
        color = Oavi.colors.muted,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun LayoutCard(style: LayoutStyle, selected: Boolean, leftHanded: Boolean, width: Dp, onClick: () -> Unit) {
    val colors = Oavi.colors
    val border by animateColorAsState(if (selected) colors.accent else colors.faint, tween(200), label = "layoutBorder")
    val fill by animateColorAsState(if (selected) colors.accentSoft else Color.Transparent, tween(200), label = "layoutFill")
    val name = stringResource(style.label())
    val phoneWidth = (width - 20.dp).coerceAtMost(96.dp)
    val phoneHeight = phoneWidth * 1.8f
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = name
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = phoneWidth, height = phoneHeight)
                .clip(RoundedCornerShape(16.dp))
                .border(if (selected) 2.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
                .background(colors.background),
        ) {
            val accent = colors.accent
            val bar = colors.raised
            val ink = colors.onSurface.copy(alpha = 0.16f)
            val line = colors.faint
            Canvas(Modifier.size(width = phoneWidth, height = phoneHeight)) {
                drawLayout(style, accent, bar, ink, line, colors.background, leftHanded)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = Oavi.type.label,
            color = if (selected) colors.accent else colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A sketch of [style]: page text as grey lines, bars in the surface colour, the accent where the thumb goes. */
private fun DrawScope.drawLayout(
    style: LayoutStyle,
    accent: Color,
    bar: Color,
    ink: Color,
    line: Color,
    background: Color,
    leftHanded: Boolean,
) {
    val w = size.width
    val h = size.height
    val u = w / 76f
    fun textLines(top: Float, bottom: Float, left: Float = 8 * u, right: Float = w - 8 * u) {
        var y = top
        var i = 0
        while (y < bottom) {
            val end = if (i % 3 == 2) left + (right - left) * 0.6f else right
            drawRoundRect(ink, Offset(left, y), Size(end - left, 3 * u), CornerRadius(1.5f * u))
            y += 7 * u
            i++
        }
    }
    fun pill(x: Float, y: Float, width: Float, height: Float, color: Color) =
        drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(height / 2))
    fun dot(x: Float, y: Float, r: Float, color: Color) = drawCircle(color, r, Offset(x, y))

    when (style) {
        LayoutStyle.ISLAND -> {
            textLines(12 * u, h - 30 * u)
            pill(5 * u, h - 22 * u, w - 10 * u, 14 * u, bar)
            drawRoundRect(line, Offset(5 * u, h - 22 * u), Size(w - 10 * u, 14 * u), CornerRadius(7 * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            val start = if (leftHanded) w - 16 * u else 11 * u
            dot(start, h - 15 * u, 2.5f * u, accent)
            val buttons = if (leftHanded) listOf(9 * u, 15 * u) else listOf(w - 15 * u, w - 9 * u)
            for (x in buttons) dot(x, h - 15 * u, 2f * u, ink.copy(alpha = 0.6f))
        }
        LayoutStyle.CLASSIC -> {
            drawRect(bar, Offset.Zero, Size(w, 20 * u))
            pill(5 * u, 6 * u, w - 26 * u, 10 * u, line)
            dot(w - 15 * u, 11 * u, 2f * u, ink.copy(alpha = 0.6f))
            dot(w - 8 * u, 11 * u, 2f * u, ink.copy(alpha = 0.6f))
            drawRect(accent, Offset(0f, 19 * u), Size(w * 0.55f, 1.2f * u))
            textLines(28 * u, h - 8 * u)
        }
        LayoutStyle.STRIP -> {
            drawRect(bar, Offset.Zero, Size(w, 30 * u))
            pill(5 * u, 5 * u, w - 18 * u, 9 * u, line)
            dot(w - 7 * u, 9.5f * u, 2f * u, ink.copy(alpha = 0.6f))
            drawRoundRect(accent.copy(alpha = 0.25f).compositeOver(bar), Offset(5 * u, 18 * u), Size(22 * u, 8 * u), CornerRadius(2.5f * u))
            drawRect(accent, Offset(8 * u, 25 * u), Size(16 * u, 1.2f * u))
            drawRoundRect(line, Offset(30 * u, 18 * u), Size(18 * u, 8 * u), CornerRadius(2.5f * u))
            drawRoundRect(line, Offset(51 * u, 18 * u), Size(14 * u, 8 * u), CornerRadius(2.5f * u))
            textLines(38 * u, h - 22 * u)
            drawRect(bar, Offset(0f, h - 16 * u), Size(w, 16 * u))
            val xs = listOf(0.12f, 0.31f, 0.5f, 0.69f, 0.88f)
            for ((i, f) in xs.withIndex()) dot(w * f, h - 8 * u, if (i == 2) 3.5f * u else 2f * u, if (i == 2) accent else ink.copy(alpha = 0.6f))
        }
        LayoutStyle.DOCK -> {
            textLines(12 * u, h - 38 * u)
            drawRoundRect(bar, Offset(0f, h - 32 * u), Size(w, 32 * u), CornerRadius(8 * u))
            pill(5 * u, h - 28 * u, w - 22 * u, 10 * u, line)
            dot(if (leftHanded) 9 * u else w - 9 * u, h - 23 * u, 4f * u, accent.copy(alpha = 0.3f).compositeOver(bar))
            val xs = listOf(0.12f, 0.31f, 0.5f, 0.69f, 0.88f)
            for ((i, f) in xs.withIndex()) dot(w * f, h - 9 * u, if (i == 2) 3.5f * u else 2f * u, if (i == 2) accent else ink.copy(alpha = 0.6f))
        }
        LayoutStyle.FOCUS -> {
            drawRect(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.35f).compositeOver(background), background, accent.copy(alpha = 0.15f).compositeOver(background)),
                    start = Offset.Zero,
                    end = Offset(w, h),
                ),
            )
            drawRoundRect(background, Offset(4 * u, 6 * u), Size(w - 8 * u, h - 12 * u), CornerRadius(7 * u))
            textLines(14 * u, h - 26 * u, 10 * u, w - 10 * u)
            pill(w / 2 - 17 * u, h - 21 * u, 34 * u, 9 * u, bar)
            drawRoundRect(line, Offset(w / 2 - 17 * u, h - 21 * u), Size(34 * u, 9 * u), CornerRadius(4.5f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            dot(w / 2 - 11 * u, h - 16.5f * u, 1.8f * u, accent)
        }
    }
}

/** Round swatches of the accent colours Oavi offers. */
@Composable
internal fun AccentPicker(current: Accent, onPick: (Accent) -> Unit) {
    val dark = Oavi.colors.isDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        for (accent in Accent.entries) {
            val color = accentColor(accent, dark)
            val selected = accent == current
            val name = stringResource(accent.label())
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(if (selected) 3.dp else 0.dp, Oavi.colors.onSurface.copy(alpha = if (selected) 0.85f else 0f), CircleShape)
                    .padding(if (selected) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(role = Role.RadioButton) { onPick(accent) }
                    .semantics {
                        this.selected = selected
                        contentDescription = name
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun Accent.label(): Int = when (this) {
    Accent.TEAL -> R.string.accent_teal
    Accent.BLUE -> R.string.accent_blue
    Accent.VIOLET -> R.string.accent_violet
    Accent.ROSE -> R.string.accent_rose
    Accent.AMBER -> R.string.accent_amber
}
