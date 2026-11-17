package org.coloradomesh.meshcore.tak

/**
 * Escape text for use in XML character data or a double-quoted attribute value. Characters XML 1.0
 * does not allow at all (control characters other than tab, newline and carriage return, unpaired
 * surrogates, U+FFFE and U+FFFF) are dropped, since no escape makes them legal and a strict parser
 * would reject the whole event.
 */
fun escapeXml(text: String): String {
    val out = StringBuilder(text.length + 16)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '&' -> out.append("&amp;")
            ch == '<' -> out.append("&lt;")
            ch == '>' -> out.append("&gt;")
            ch == '"' -> out.append("&quot;")
            ch == '\'' -> out.append("&apos;")
            ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                out.append(ch).append(text[i + 1])
                i++
            }
            isXmlChar(ch) -> out.append(ch)
        }
        i++
    }
    return out.toString()
}

/** Whether [ch], standing alone, is a character XML 1.0 allows. */
private fun isXmlChar(ch: Char): Boolean = when {
    ch == '\t' || ch == '\n' || ch == '\r' -> true
    ch < ' ' -> false
    ch.isSurrogate() -> false
    ch == '\uFFFE' || ch == '\uFFFF' -> false
    else -> true
}

private val ENTITY = Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|lt|gt|quot|apos|amp);")

/**
 * Reverse [escapeXml], and resolve numeric character references, in one pass over [text]: what a
 * reference produces is text, never read again as a reference, so `&#38;lt;` is `&lt;`, not `<`.
 * Unknown entities are left as is; a numeric reference to no valid character is dropped.
 */
fun unescapeXml(text: String): String {
    if (!text.contains('&')) return text
    return ENTITY.replace(text) { match ->
        when (val body = match.groupValues[1]) {
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "amp" -> "&"
            else -> {
                val code = if (body.startsWith("#x")) body.substring(2).toIntOrNull(16) else body.substring(1).toIntOrNull()
                if (code != null && code > 0 && Character.isValidCodePoint(code)) String(Character.toChars(code)) else ""
            }
        }
    }
}
