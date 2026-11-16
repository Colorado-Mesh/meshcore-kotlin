package org.coloradomesh.meshcore.tak

/** Escape text for use in XML character data or a double-quoted attribute value. */
fun escapeXml(text: String): String {
    val out = StringBuilder(text.length + 16)
    for (ch in text) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> out.append(ch)
        }
    }
    return out.toString()
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
