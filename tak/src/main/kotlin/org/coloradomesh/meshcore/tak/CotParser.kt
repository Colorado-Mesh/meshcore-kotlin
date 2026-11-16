package org.coloradomesh.meshcore.tak

/** A GeoChat message a TAK client sent. */
data class InboundChat(
    val room: String,
    /** The uid the event claims as its sender; the caller decides whether to trust it. */
    val senderUid: String,
    /** Empty when the event carried none. */
    val senderCallsign: String,
    val text: String,
    /** Message id from the event uid, when it has a usable one. */
    val messageId: String?,
    /** The conversation id: the recipient's uid for a direct message, else usually the room name. */
    val roomId: String? = null,
)

/** The TAK client's own position report, which names the device and its user. */
data class ClientIdentity(val uid: String, val callsign: String)

/**
 * Reads the few things a mesh app needs out of CoT events sent by ATAK or WearTAK.
 *
 * This is deliberately not a general XML parser: it pulls named attributes out of named opening
 * tags, bounds every field, and returns null for anything it does not recognise.
 */
object CotParser {
    const val MAX_ROOM_LENGTH = 64
    const val MAX_CALLSIGN_LENGTH = 64
    const val MAX_TEXT_LENGTH = 1024
    private const val MAX_UID_LENGTH = 128

    private val UID_PATTERN = Regex("^[A-Za-z0-9_.:@-]{1,128}$")
    /** What a message id may be: the last dot-separated part of a GeoChat uid, so no dots. */
    internal val MESSAGE_ID_PATTERN = Regex("^[A-Za-z0-9-]{1,64}$")
    private val CONTROL_CHARS = Regex("\\p{Cc}+")

    /** One attribute, `name="value"` or `name='value'`; matched in order so a value is never read as an attribute. */
    private val ATTRIBUTE = Regex("\\s([A-Za-z_:][\\w:.-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

    /** Longest opening tag read; a real CoT tag is a few hundred characters. */
    private const val MAX_TAG_LENGTH = 4096
    private const val NO_QUOTE = '\u0000'

    /**
     * Where the first opening tag named [name] sits in [xml], from its `<` to its closing `>`.
     * The tag ends at the first `>` that is not inside a quoted attribute value, since XML allows
     * `>` in one (`chatroom="Ops > east"`). A plain scan, not a regex, so a long tag costs no stack.
     * Null when there is no such tag, or it is unterminated or longer than [MAX_TAG_LENGTH].
     */
    private fun openTagRange(xml: String, name: String): IntRange? {
        val prefix = "<$name"
        var from = 0
        while (true) {
            val start = xml.indexOf(prefix, from)
            if (start < 0) return null
            val next = xml.getOrNull(start + prefix.length)
            if (next != null && (next.isWhitespace() || next == '/' || next == '>')) {
                val end = tagEnd(xml, start + prefix.length, start + MAX_TAG_LENGTH)
                return if (end < 0) null else start..end
            }
            from = start + 1
        }
    }

    /** Index of the `>` closing a tag, scanning [xml] from [from] up to [limit], or -1. */
    private fun tagEnd(xml: String, from: Int, limit: Int): Int {
        var quote = NO_QUOTE
        val stop = minOf(xml.length, limit)
        for (i in from until stop) {
            val c = xml[i]
            when {
                quote != NO_QUOTE -> if (c == quote) quote = NO_QUOTE
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return i
            }
        }
        return -1
    }

    private fun openTag(xml: String, name: String): String? =
        openTagRange(xml, name)?.let { xml.substring(it.first, it.last + 1) }

    /**
     * The value of attribute [name] in [tag]. Attributes are read one after another, each with its
     * whole quoted value, so `id='x'` written inside another attribute's value is not mistaken for
     * an `id` attribute (XML allows a raw `'` in a double-quoted value).
     */
    private fun attr(tag: String?, name: String): String? {
        if (tag == null) return null
        for (match in ATTRIBUTE.findAll(tag)) {
            if (match.groupValues[1] != name) continue
            val raw = match.groups[2]?.value ?: match.groups[3]?.value ?: ""
            return unescapeXml(raw)
        }
        return null
    }

    /** The event's `type` attribute, or null when the text is not an event. */
    fun eventType(xml: String): String? = attr(openTag(xml, "event"), "type")

    /** True for a client keepalive ping, which the server answers with a pong. */
    fun isPing(xml: String): Boolean = eventType(xml) == CotEvents.PING_TYPE

    /** The event's `uid` attribute, or null when the text is not an event. */
    fun eventUid(xml: String): String? = attr(openTag(xml, "event"), "uid")

    /**
     * Read who the client is from its own position report: an atom event that carries the
     * `takv` version element only a TAK client adds about itself.
     */
    fun parseClientIdentity(xml: String): ClientIdentity? {
        val eventTag = openTag(xml, "event")
        if (attr(eventTag, "type")?.startsWith("a-") != true) return null
        if (openTag(xml, "takv") == null) return null
        val uid = attr(eventTag, "uid")?.takeIf { UID_PATTERN.matches(it) } ?: return null
        val callsign = label(attr(openTag(xml, "contact"), "callsign"), MAX_CALLSIGN_LENGTH) ?: return null
        return ClientIdentity(uid, callsign)
    }

    /** Single line, trimmed and cut to [max]; null when nothing is left. */
    private fun label(text: String?, max: Int): String? {
        val oneLine = text?.replace(CONTROL_CHARS, " ")?.trim()
        if (oneLine.isNullOrEmpty()) return null
        return if (oneLine.length > max) oneLine.substring(0, max) else oneLine
    }

    private fun remarksText(xml: String): String? {
        val open = openTagRange(xml, "remarks") ?: return null
        if (xml[open.last - 1] == '/') return null
        val bodyStart = open.last + 1
        val end = xml.indexOf("</remarks>", bodyStart)
        return if (end < 0) null else unescapeXml(xml.substring(bodyStart, end))
    }

    /**
     * The sender uid from `<sender uid>.<conversation id>` in a GeoChat event uid. Both parts may
     * contain dots, so when the event names its conversation ([conversationId], `__chat`'s `id`)
     * and the uid ends with it, the sender is everything before; otherwise it is the first part.
     */
    private fun senderFrom(senderAndRoom: String, conversationId: String?): String? {
        if (!conversationId.isNullOrEmpty() && senderAndRoom.endsWith(".$conversationId")) {
            return senderAndRoom.removeSuffix(".$conversationId").takeIf { it.isNotEmpty() }
        }
        val firstDot = senderAndRoom.indexOf('.')
        return if (firstDot > 0) senderAndRoom.substring(0, firstDot) else null
    }

    /**
     * Parse a GeoChat (`b-t-f`) event into room, sender and text.
     *
     * ATAK's event uids are `GeoChat.<sender uid>.<room>.<message id>`, and the sender is read
     * only from there (see [senderFrom]). The message id is read from `__chat`'s `messageId`, as
     * ATAK does, or else from the end of the uid. Delivery (`b-t-f-d`) and read (`b-t-f-r`) receipts are not messages.
     * Returns null for anything else and for a message without a room, sender uid or text.
     */
    fun parseGeoChat(xml: String): InboundChat? {
        val eventTag = openTag(xml, "event")
        if (attr(eventTag, "type") != CotEvents.GEOCHAT_TYPE) return null
        val chatTag = openTag(xml, "__chat")
        val room = label(attr(chatTag, "chatroom"), MAX_ROOM_LENGTH) ?: return null
        val text = label(remarksText(xml), MAX_TEXT_LENGTH) ?: return null

        val eventUid = attr(eventTag, "uid") ?: return null
        if (!eventUid.startsWith("GeoChat.")) return null
        val uidRest = eventUid.removePrefix("GeoChat.")
        val lastDot = uidRest.lastIndexOf('.')
        if (lastDot <= 0) return null
        val uidMessageId = uidRest.substring(lastDot + 1)
        val senderAndRoom = uidRest.substring(0, lastDot)
        val senderUid = senderFrom(senderAndRoom, attr(chatTag, "id")) ?: return null
        if (!UID_PATTERN.matches(senderUid)) return null
        // ATAK writes the id on `__chat` and at the end of the uid; it reads the attribute first.
        val messageId = listOfNotNull(attr(chatTag, "messageId"), uidMessageId)
            .firstOrNull { MESSAGE_ID_PATTERN.matches(it) }

        return InboundChat(
            room = room,
            senderUid = senderUid,
            senderCallsign = label(attr(chatTag, "senderCallsign"), MAX_CALLSIGN_LENGTH) ?: "",
            text = text,
            messageId = messageId,
            roomId = label(attr(chatTag, "id"), MAX_UID_LENGTH),
        )
    }
}
