package com.oavi.browser.model

/** Prepares page text for a speech engine, which takes limited pieces at a time. */
object SpeechText {
    /**
     * Splits [text] into pieces of at most [max] characters, ending at the close of a sentence
     * where possible, then at a comma or space, so the voice never stops mid-word.
     */
    fun chunks(text: String, max: Int = 1200): List<String> {
        val result = ArrayList<String>()
        for (paragraph in text.split(Regex("\\n\\s*\\n"))) {
            var rest = paragraph.replace(Regex("\\s+"), " ").trim()
            while (rest.length > max) {
                val window = rest.substring(0, max)
                val cut = listOf(". ", "! ", "? ", "… ", "; ", ": ", ", ", " ")
                    .map { window.lastIndexOf(it).let { at -> if (at > max / 3) at + it.length else -1 } }
                    .firstOrNull { it > 0 } ?: max
                result += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) result += rest
        }
        return result
    }
}
