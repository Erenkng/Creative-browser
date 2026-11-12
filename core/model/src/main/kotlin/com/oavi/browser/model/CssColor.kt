package com.oavi.browser.model

/** Parses the colours sites put in `<meta name="theme-color">`, as ARGB ints. */
object CssColor {
    private val rgbPattern = Regex(
        "^rgba?\\(\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*(?:[,/]\\s*([0-9.]+%?)\\s*)?\\)$",
    )

    fun parse(text: String?): Int? {
        val value = text?.trim()?.lowercase() ?: return null
        if (value.startsWith("#")) return parseHex(value.substring(1))
        val match = rgbPattern.matchEntire(value) ?: return null
        val (r, g, b) = match.groupValues.subList(1, 4).map { it.toInt() }
        if (r > 255 || g > 255 || b > 255) return null
        return argb(255, r, g, b)
    }

    /** Relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Float {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    /** HSV-style saturation, 0 (grey) to 1 (pure colour). */
    fun saturation(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0) 0f else (max - min) / max.toFloat()
    }

    /**
     * Whether a site colour carries enough identity to tint the interface. Greys, near-white
     * and near-black say nothing about the site, so the default accent stays.
     */
    fun usableAsAccent(argb: Int): Boolean {
        val luminance = luminance(argb)
        return saturation(argb) >= 0.28f && luminance in 0.04f..0.80f
    }

    private fun parseHex(hex: String): Int? {
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return when (hex.length) {
            3 -> {
                val r = hex[0].digitToInt(16) * 17
                val g = hex[1].digitToInt(16) * 17
                val b = hex[2].digitToInt(16) * 17
                argb(255, r, g, b)
            }
            6 -> argb(255, hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
            8 -> argb(
                hex.substring(6, 8).toInt(16),
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
            )
            else -> null
        }
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
}
