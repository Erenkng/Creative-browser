package com.oavi.browser.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Oavi's colours. Three palettes share one shape: light, dark and private. */
@Immutable
data class OaviColors(
    /** The canvas behind everything. */
    val background: Color,
    /** Cards and sheets resting on the canvas. */
    val surface: Color,
    /** The island and anything floating above a page. */
    val raised: Color,
    val onSurface: Color,
    /** Secondary text and quiet icons. */
    val muted: Color,
    /** Hairlines, placeholders, disabled fills. */
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    /** A soft wash of the accent for selected and filled states. */
    val accentSoft: Color,
    val danger: Color,
    val scrim: Color,
    val isDark: Boolean,
)

private val LightPalette = OaviColors(
    background = Color(0xFFF6F3EE),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1A1F),
    muted = Color(0xFF6B6872),
    faint = Color(0xFFE4DFD6),
    accent = Color(0xFF0B8E84),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD7F2EE),
    danger = Color(0xFFC8372D),
    scrim = Color(0x66121114),
    isDark = false,
)

private val DarkPalette = OaviColors(
    background = Color(0xFF121114),
    surface = Color(0xFF1C1B20),
    raised = Color(0xFF28262E),
    onSurface = Color(0xFFEFECF4),
    muted = Color(0xFFA09CAB),
    faint = Color(0xFF36333D),
    accent = Color(0xFF4FD8CB),
    onAccent = Color(0xFF04201D),
    accentSoft = Color(0xFF17403C),
    danger = Color(0xFFFF8A7A),
    scrim = Color(0x99000000),
    isDark = true,
)

private val PrivatePalette = OaviColors(
    background = Color(0xFF100D1A),
    surface = Color(0xFF1A1628),
    raised = Color(0xFF272039),
    onSurface = Color(0xFFEEEAFB),
    muted = Color(0xFFA79FC4),
    faint = Color(0xFF372F50),
    accent = Color(0xFFB49BFF),
    onAccent = Color(0xFF1A0F3A),
    accentSoft = Color(0xFF32275A),
    danger = Color(0xFFFF8A7A),
    scrim = Color(0xAA000000),
    isDark = true,
)

/** Text styles. Display and titles are tight and heavy; body text is relaxed. */
@Immutable
data class OaviType(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

private val DefaultType = OaviType(
    display = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight(720), letterSpacing = (-0.025).em),
    title = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight(680), letterSpacing = (-0.015).em),
    headline = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight(600), letterSpacing = (-0.005).em),
    body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight(420)),
    label = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight(560)),
    caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight(520), letterSpacing = 0.01.em),
)

private val LocalOaviColors = staticCompositionLocalOf { LightPalette }
private val LocalOaviType = staticCompositionLocalOf { DefaultType }

object Oavi {
    val colors: OaviColors
        @Composable @ReadOnlyComposable get() = LocalOaviColors.current

    val type: OaviType
        @Composable @ReadOnlyComposable get() = LocalOaviType.current
}

/**
 * @param private the palette for private tabs; always dark so the mode is unmistakable.
 * @param siteAccent a colour taken from the page being viewed. When present the interface
 *   leans towards it, so the island feels like part of the site.
 */
@Composable
fun OaviTheme(
    dark: Boolean,
    private: Boolean = false,
    siteAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val base = when {
        private -> PrivatePalette
        dark -> DarkPalette
        else -> LightPalette
    }
    val target = if (siteAccent != null && !private) readableAccent(siteAccent, base.isDark) else base.accent
    val accent by animateColorAsState(target, tween(450), label = "accent")
    val background by animateColorAsState(base.background, tween(300), label = "background")
    val surface by animateColorAsState(base.surface, tween(300), label = "surface")
    val raised by animateColorAsState(base.raised, tween(300), label = "raised")

    val colors = base.copy(
        background = background,
        surface = surface,
        raised = raised,
        accent = accent,
        onAccent = if (accent.luminance() > 0.45f) Color(0xFF0D0C10) else Color.White,
        accentSoft = accent.copy(alpha = if (base.isDark) 0.24f else 0.16f).compositeOver(base.surface),
    )

    val material = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.muted,
            outline = colors.faint,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.onSurface,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.raised,
            onSurfaceVariant = colors.muted,
            outline = colors.faint,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(LocalOaviColors provides colors, LocalOaviType provides DefaultType) {
        MaterialTheme(colorScheme = material, content = content)
    }
}

/**
 * Bends a site's brand colour until it reads well on Oavi's surfaces: lighter and a little
 * calmer on dark, deeper on light. The hue, which carries the identity, is kept.
 */
fun readableAccent(site: Color, onDark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(site.toArgb(), hsv)
    if (onDark) {
        hsv[1] = hsv[1].coerceAtMost(0.62f)
        hsv[2] = hsv[2].coerceAtLeast(0.86f)
    } else {
        hsv[1] = hsv[1].coerceAtLeast(0.55f)
        hsv[2] = hsv[2].coerceAtMost(0.62f)
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}
