package com.oavi.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** Shrinks slightly while pressed and springs back: every tappable thing answers the finger. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.94f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, OaviMotion.lively(), label = "press")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The floating "glass" look used by the island and other surfaces above a page: a nearly
 * opaque tint, a light catching the top edge and a soft shadow underneath.
 */
@Composable
fun Modifier.glass(shape: Shape, elevation: Dp = 18.dp, opacity: Float = 0.93f): Modifier {
    val colors = Oavi.colors
    val sheen = if (colors.isDark) 0.07f else 0.5f
    val edge = if (colors.isDark) 0.2f else 0.9f
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.2f),
            spotColor = Color.Black.copy(alpha = 0.3f),
        )
        .clip(shape)
        .background(colors.raised.copy(alpha = opacity))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = sheen), Color.Transparent)))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = edge), colors.faint.copy(alpha = 0.6f))),
            shape = shape,
        )
}

@Composable
fun OaviIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Oavi.colors.onSurface,
    background: Color = Color.Transparent,
    onLongClick: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .pressScale(source)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(24.dp),
        )
    }
}

enum class ButtonKind { PRIMARY, TONAL, QUIET, DANGER }

@Composable
fun OaviButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    icon: Int? = null,
) {
    val colors = Oavi.colors
    val container = when (kind) {
        ButtonKind.PRIMARY -> colors.accent
        ButtonKind.TONAL -> colors.accentSoft
        ButtonKind.QUIET -> Color.Transparent
        ButtonKind.DANGER -> colors.danger
    }
    val content = when (kind) {
        ButtonKind.PRIMARY -> colors.onAccent
        ButtonKind.TONAL -> colors.onSurface
        ButtonKind.QUIET -> colors.accent
        ButtonKind.DANGER -> if (colors.isDark) Color(0xFF2A0A06) else Color.White
    }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, OaviMotion.fade(), label = "enabled")
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(source, pressed = 0.96f)
            .graphicsLayer { this.alpha = alpha }
            .defaultMinSize(minWidth = 64.dp)
            .height(52.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Oavi.type.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun OaviChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dot: Color? = null,
) {
    val colors = Oavi.colors
    val container by animateColorAsState(if (selected) colors.accent else colors.surface, OaviMotion.fade(), label = "chip")
    val content by animateColorAsState(if (selected) colors.onAccent else colors.onSurface, OaviMotion.fade(), label = "chipText")
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(source)
            .height(40.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Oavi.type.label, color = content, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Oavi.type.caption,
        color = Oavi.colors.muted,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** A full-width row: icon, title, optional supporting line, optional trailing content. */
@Composable
fun OaviRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    iconTint: Color = Oavi.colors.muted,
    titleColor: Color = Oavi.colors.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 60.dp)
        .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
    val clickable = if (onClick != null) base.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else base
    Row(
        modifier = clickable.padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Oavi.type.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = Oavi.type.caption, color = Oavi.colors.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun OaviSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = Oavi.colors
    val haptics = rememberHaptics()
    val toggle: (Boolean) -> Unit = { value ->
        haptics.tick()
        onCheckedChange(value)
    }
    OaviRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        onClick = { toggle(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = toggle,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.muted,
                    uncheckedTrackColor = colors.faint,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        },
    )
}

@Composable
fun OaviTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = Oavi.type.body,
) {
    val colors = Oavi.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.faint.copy(alpha = 0.55f))
            .padding(horizontal = 18.dp),
        singleLine = true,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = 52.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        },
    )
}

private val tileColors = listOf(
    Color(0xFF0E9F94),
    Color(0xFF3D7BFF),
    Color(0xFF8B6CFF),
    Color(0xFFE0653A),
    Color(0xFFD6457A),
    Color(0xFF4E9A3B),
    Color(0xFFC08A12),
    Color(0xFF5B6B8C),
)

/** A stable colour for a site, derived from its name. */
fun tileColor(seed: String): Color = tileColors[seed.hashCode().absoluteValue % tileColors.size]

/** The n-th tile colour, for short fixed lists where every entry should look different. */
fun tileColorAt(index: Int): Color = tileColors[index.absoluteValue % tileColors.size]

/** Stands in for a site icon: the site's first letter on its own colour. */
@Composable
fun LetterTile(label: String, modifier: Modifier = Modifier, size: Dp = 40.dp, color: Color = tileColor(label)) {
    val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "·"
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = Oavi.type.headline.copy(fontSize = Oavi.type.headline.fontSize * (size.value / 40f)),
            color = Color.White,
        )
    }
}
