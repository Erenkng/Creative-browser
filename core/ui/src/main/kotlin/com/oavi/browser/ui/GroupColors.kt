package com.oavi.browser.ui

import androidx.compose.ui.graphics.Color
import com.oavi.browser.model.GroupColor

/** The swatch for a tab group's colour, the same on every screen that shows groups. */
fun GroupColor.toColor(): Color = when (this) {
    GroupColor.AQUA -> Color(0xFF12B5A6)
    GroupColor.CORAL -> Color(0xFFE0653A)
    GroupColor.VIOLET -> Color(0xFF8B6CFF)
    GroupColor.AMBER -> Color(0xFFC08A12)
    GroupColor.LEAF -> Color(0xFF4E9A3B)
    GroupColor.ROSE -> Color(0xFFD6457A)
    GroupColor.SLATE -> Color(0xFF5B6B8C)
}
