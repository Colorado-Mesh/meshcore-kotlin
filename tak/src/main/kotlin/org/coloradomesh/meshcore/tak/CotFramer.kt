package org.coloradomesh.meshcore.tak

/** A client sent more than [CotFramer.maxFrameBytes] without completing an event. */
class CotFrameTooLargeException(limit: Int) : Exception("CoT event exceeds $limit bytes")

/**
 * Splits a TAK streaming connection into complete `<event>…</event>` strings.
 *
 * Events arrive back to back or separated by whitespace, sometimes each with its own XML
 * declaration, and a read can end anywhere, including in the middle of a multi-byte character.
 * Bytes are buffered until an event closes, and only a whole event is decoded as UTF-8.
 *
 * Event tags are looked for only in markup: text inside a CDATA section or a comment, which may
 * legally read `<event` or `</event>`, is passed over. Every delimiter is ASCII, so the bytes can
 * be scanned without decoding them.
 */
class CotFramer(val maxFrameBytes: Int = DEFAULT_MAX_FRAME_BYTES) {
    private enum class Lexical { MARKUP, CDATA, COMMENT }

    private var buffer = ByteArray(0)

    /** Where scanning resumes in [buffer]; everything before it has been read. */
    private var scanned = 0

    /** Start in [buffer] of the open event, or -1 when none is open. */
    private var eventStart = -1
    private var lexical = Lexical.MARKUP

    /** A partial event passed the limit after events that completed; reported on the next push. */
    private var overflowed = false

    /**
     * Add [length] bytes from [chunk] and return the events they completed, in order.
     *
     * @throws CotFrameTooLargeException when the buffered partial event passes the limit. The
     * framer is reset, and the caller should drop the connection. When the same read also completed
     * events, they are returned first and the exception comes from the next call, so no complete
     * event is lost.
     */
    fun push(chunk: ByteArray, length: Int = chunk.size): List<String> {
        if (overflowed) {
            overflowed = false
            throw CotFrameTooLargeException(maxFrameBytes)
        }
        val bytes = buffer.copyOf(buffer.size + length)
        System.arraycopy(chunk, 0, bytes, buffer.size, length)
        val frames = ArrayList<String>()
        var i = scanned
        scan@ while (i < bytes.size) {
            when (lexical) {
                Lexical.CDATA, Lexical.COMMENT -> {
                    val close = if (lexical == Lexical.CDATA) CDATA_CLOSE else COMMENT_CLOSE
                    when (match(bytes, i, close)) {
                        Match.YES -> { lexical = Lexical.MARKUP; i += close.size }
                        Match.NO -> i++
                        Match.NEED_MORE -> break@scan
                    }
                }
                Lexical.MARKUP -> {
                    if (bytes[i] != LT) { i++; continue@scan }
                    val cdata = match(bytes, i, CDATA_OPEN)
                    val comment = match(bytes, i, COMMENT_OPEN)
                    val close = match(bytes, i, EVENT_END)
                    val open = eventOpen(bytes, i)
                    when {
                        cdata == Match.YES -> { lexical = Lexical.CDATA; i += CDATA_OPEN.size }
                        comment == Match.YES -> { lexical = Lexical.COMMENT; i += COMMENT_OPEN.size }
                        close == Match.YES -> {
                            val frameEnd = i + EVENT_END.size
                            if (eventStart >= 0) frames.add(String(bytes, eventStart, frameEnd - eventStart, Charsets.UTF_8))
                            eventStart = -1
                            i = frameEnd
                        }
                        open == Match.YES -> { eventStart = i; i += EVENT_OPEN.size }
                        Match.NEED_MORE in listOf(cdata, comment, close, open) -> break@scan
                        else -> i++
                    }
                }
            }
        }
        // Keep the open event, or else only what is still to be scanned.
        val keep = if (eventStart >= 0) eventStart else i
        val remaining = bytes.size - keep
        if (remaining > maxFrameBytes) {
            reset()
            if (frames.isEmpty()) throw CotFrameTooLargeException(maxFrameBytes)
            overflowed = true
            return frames
        }
        buffer = bytes.copyOfRange(keep, bytes.size)
        scanned = i - keep
        if (eventStart >= 0) eventStart -= keep
        return frames
    }

    private fun reset() {
        buffer = ByteArray(0)
        scanned = 0
        eventStart = -1
        lexical = Lexical.MARKUP
    }

    private enum class Match { YES, NO, NEED_MORE }

    /** Whether [needle] starts at [at]; NEED_MORE when the bytes so far match but run out first. */
    private fun match(haystack: ByteArray, at: Int, needle: ByteArray): Match {
        for (j in needle.indices) {
            if (at + j >= haystack.size) return Match.NEED_MORE
            if (haystack[at + j] != needle[j]) return Match.NO
        }
        return Match.YES
    }

    /** `<event` followed by whitespace, `>` or `/`, so a longer name such as `<eventual` is not an event. */
    private fun eventOpen(bytes: ByteArray, at: Int): Match {
        val name = match(bytes, at, EVENT_OPEN)
        if (name != Match.YES) return name
        val next = bytes.getOrNull(at + EVENT_OPEN.size)?.toInt()?.toChar() ?: return Match.NEED_MORE
        return if (next == '>' || next == '/' || next.isWhitespace()) Match.YES else Match.NO
    }

    companion object {
        /** A sane CoT event is a few kilobytes. */
        const val DEFAULT_MAX_FRAME_BYTES = 64 * 1024
        private const val LT = '<'.code.toByte()
        private val EVENT_OPEN = ascii("<event")
        private val EVENT_END = ascii("</event>")
        private val CDATA_OPEN = ascii("<![CDATA[")
        private val CDATA_CLOSE = ascii("]]>")
        private val COMMENT_OPEN = ascii("<!--")
        private val COMMENT_CLOSE = ascii("-->")

        private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)
    }
}
