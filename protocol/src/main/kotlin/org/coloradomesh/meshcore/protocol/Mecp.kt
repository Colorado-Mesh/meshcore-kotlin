package org.coloradomesh.meshcore.protocol

import java.util.Locale

/**
 * MECP, the Mesh Emergency Communication Protocol (mecp.radio): emergency and everyday messages
 * as short codes, so they can be read in any language. A message is plain text,
 * `MECP/<severity>/<codes> [free text]`, for example `MECP/0/M01 M07 2pax P05 48.6520,20.1305`:
 * MAYDAY, injury, fracture, 2 people, at those coordinates.
 *
 * [parse] follows MECP 1.4.0 (github.com/mecp-org/MECP, `MECP_Comprehensive_Brief.md` §6.1): the
 * message pattern, then each free-text convention by its decoding pattern, searched between
 * boundaries, with the first match that passes the convention's validation used and a failed one
 * left in the free text. It passes the protocol's conformance cases (`freetext/tests.json`).
 * [describe] turns a message into English with the project's code table ([MecpCodes]).
 */
object Mecp {
    /** A decoded MECP message. */
    data class Message(
        /** 0 MAYDAY, 1 URGENT, 2 SAFETY, 3 ROUTINE. */
        val severity: Int,
        /** Codes in the order sent, such as `M01`, at least one; ones this table does not know are kept too. */
        val codes: List<String>,
        /**
         * Known codes that come after the free text has begun, as in MECP's own example
         * `M01 M07 2pax P05 …`. The protocol counts them as free text; they are shown with their
         * meaning, but take no part in deciding what the message is (a drill, say).
         */
        val laterCodes: List<String> = emptyList(),
        /** Everything after the last code, or null. */
        val freeText: String?,
        /** The `Npax` head count. */
        val people: Int?,
        val latitude: Double?,
        val longitude: Double?,
        /**
         * The ETA in minutes, from a duration in the free text when `R03` (ETA) is among the codes:
         * `45min`, `2h`, `1h30min`, `2d`, `ETA 45min`, or a bare whole number as the first word.
         * The first by position wins, and a bare number counts only when no value with a unit does.
         */
        val etaMinutes: Int?,
        /** An incident tag, from `#tag`, as written. */
        val reference: String?,
        /** The sender's language tag, from `@xx`, in lowercase with `-` (`zh-cn`). */
        val language: String?,
        /**
         * When it was written, as `HHMM`, from `@HHMM`, `@H:MM` and the like: the sender's local
         * time (MECP 1.4.0). The message carries no zone, so it is shown as sent, never converted.
         */
        val time: String?,
        /** The sender's callsign, from `~CALL`, in capitals. */
        val callsign: String?,
        /** The message as it arrived, from `MECP/` on, without a final line ending. */
        val raw: String,
        /**
         * The free text with every convention used above taken out, and the later codes, or null
         * when nothing is left. A convention that failed validation stays in.
         */
        val remainder: String? = null,
    ) {
        /** `D01` (drill) or `D02` (test): practice, not a real emergency. `D03` and `D04` are not. */
        val isDrill: Boolean
            get() = "D01" in codes || "D02" in codes

        val hasPosition: Boolean
            get() = latitude != null && longitude != null
    }

    /** A received chat text that is MECP: who sent it, when the text names them, and the message. */
    data class Received(val sender: String?, val message: Message)

    /** §6.1: one line, a severity, one or more codes, and optional free text with no space at either end. */
    private val MESSAGE = Regex(
        """MECP/([0-3])/([A-Z][0-9]{2}(?: [A-Z][0-9]{2})*)""" +
            """(?: ([^ \t\n\f\r\u0085\u2028\u2029](?:[^\n\f\r\u0085\u2028\u2029]*[^ \t\n\f\r\u0085\u2028\u2029])?))?""",
    )
    private val CODE = Regex("""[A-Z][0-9]{2}""")

    /**
     * Boundary `B`, §6.1 rule 4 as MECP 1.4.0 lists it: tab, line feed, form feed, carriage return,
     * space, `, . ; : ! ? ' " ( ) [ ] { }`, and the listed Unicode spaces and punctuation. Nothing
     * else is a boundary, so `/`, `*` or `=` next to a detail stop it being read (`#hut2/basecamp`).
     */
    private val BOUNDARY_CHARS: Set<Char> = buildSet {
        addAll(listOf('\t', '\n', '\u000C', '\r', ' '))
        addAll(",.;:!?'\"()[]{}".toList())
        addAll(listOf('\u00A0', '\u1680', '\u202F', '\u205F', '\u3000', '\u060C', '\u061B', '\u061F', '\u06D4'))
        addAll(listOf('\u00AB', '\u00BB', '\u2013', '\u2014', '\u2026', '\u3001', '\u3002', '\u3010', '\u3011'))
        addAll(listOf('\uFF01', '\uFF08', '\uFF09', '\uFF0C', '\uFF0E', '\uFF1A', '\uFF1B', '\uFF1F'))
        addAll('\u2000'..'\u200A')
        addAll('\u2018'..'\u201D')
        addAll('\u300C'..'\u300F')
    }
    private val B = BOUNDARY_CHARS.joinToString("", "[", "]") { String.format(Locale.ROOT, "\\u%04X", it.code) }

    /**
     * A decoding pattern, matched as a token: preceded by the start of the free text or a boundary,
     * and followed by a boundary or the end. The boundaries are looked at, not taken, so a match is
     * exactly the token (rule 7) and two tokens can share the space between them.
     */
    private fun token(pattern: String) = Regex("(?:^|(?<=$B))(?:$pattern)(?=$B|$)")

    private val PEOPLE = token("""0*([1-9][0-9]*) ?[Pp][Aa][Xx]""")
    private val GPS = token("""([+-]?[0-9]+\.[0-9]+), ?([+-]?[0-9]+\.[0-9]+)""")
    private val TIME = token("""@([01]?[0-9]|2[0-3]):?([0-5][0-9])""")
    private val LANGUAGE = token("""@([A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*)""")
    private val CALLSIGN = token("""~([A-Za-z0-9/]+)""")
    private val REFERENCE = token("""#([A-Za-z0-9_-]+)""")

    /**
     * The duration patterns with a unit, scoped to `R03`, each with its conversion to minutes. The
     * first match by position wins; at the same position, the earlier pattern here (§6.1).
     */
    private val DURATIONS: List<Pair<Regex, (MatchResult) -> Long?>> = listOf(
        token("""(?:[Ee][Tt][Aa] ?)?([0-9]+) ?[Hh] ?([0-9]+) ?[Mm][Ii][Nn]""") to { m ->
            sum(times(number(m, 1), 60), number(m, 2))
        },
        token("""(?:[Ee][Tt][Aa] ?)?([0-9]+) ?[Hh]""") to { m -> times(number(m, 1), 60) },
        token("""(?:[Ee][Tt][Aa] ?)?([0-9]+) ?[Mm][Ii][Nn]""") to { m -> number(m, 1) },
        token("""(?:[Ee][Tt][Aa] ?)?([0-9]+) ?[Dd]""") to { m -> times(number(m, 1), 1440) },
    )

    /**
     * A bare number of minutes, read only as the first word of the free text and only when no
     * duration with a unit is found. One with a decimal part (`47.3769`) is not a duration.
     */
    private val BARE_DURATION = token("""^(?:[Ee][Tt][Aa] ?)?([0-9]+)([.,][0-9]+)?""")

    /**
     * Decodes [text] when it is an MECP message, or returns null. The prefix and codes are
     * case-sensitive, a message needs a severity from 0 to 3 and at least one code, and it must be a
     * single line: one final line ending is allowed and dropped. A message over MECP's 200 bytes is
     * still read, as MECP 1.4.0 asks of receivers.
     */
    fun parse(text: String): Message? {
        val line = when {
            text.endsWith("\r\n") -> text.dropLast(2)
            text.endsWith('\n') || text.endsWith('\r') -> text.dropLast(1)
            else -> text
        }
        if (line.any { it in LINE_BREAKS }) return null
        val match = MESSAGE.matchEntire(line) ?: return null
        val severity = match.groupValues[1].toInt()
        val codes = match.groupValues[2].split(' ')
        val freeText = match.groups[3]?.value
        val search = freeText.orEmpty()
        val used = ArrayList<IntRange>()

        fun take(pattern: Regex, accept: (MatchResult) -> Boolean = { true }): MatchResult? =
            firstValid(pattern, search, accept)?.also { used.add(it.range) }

        var people: Int? = null
        take(PEOPLE) { m -> m.groupValues[1].toIntOrNull()?.also { people = it } != null }

        var position: Pair<Double, Double>? = null
        take(GPS) { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            val ok = lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
            if (ok) position = lat!! to lon!!
            ok
        }

        val time = take(TIME)?.let { m -> m.groupValues[1].padStart(2, '0') + m.groupValues[2] }
        val language = take(LANGUAGE)?.groupValues?.get(1)?.lowercase()?.replace('_', '-')
        val callsign = take(CALLSIGN) { m -> m.groupValues[1].length <= MAX_CALLSIGN }?.groupValues?.get(1)?.uppercase()
        val reference = take(REFERENCE)?.groupValues?.get(1)

        var eta: Int? = null
        if ("R03" in codes) {
            fun valid(minutes: Long?): Int? = minutes?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
            // The first valid match of each pattern; the earliest of those, the earlier pattern on a tie.
            val found = DURATIONS.mapNotNull { (pattern, minutes) ->
                firstValid(pattern, search) { valid(minutes(it)) != null }?.let { it to minutes }
            }.minByOrNull { (match, _) -> match.range.first }
            if (found != null) {
                eta = valid(found.second(found.first))
                used.add(found.first.range)
            } else {
                take(BARE_DURATION) { m -> m.groups[2] == null && valid(number(m, 1))?.also { eta = it } != null }
            }
        }

        val laterCodes = search.split(' ').filter { CODE.matches(it) && it in MecpCodes.CODES }
        return Message(
            severity = severity,
            codes = codes,
            laterCodes = laterCodes,
            freeText = freeText,
            people = people,
            latitude = position?.first,
            longitude = position?.second,
            etaMinutes = eta,
            reference = reference,
            language = language,
            time = time,
            callsign = callsign,
            raw = line,
            remainder = remainder(search, used, laterCodes),
        )
    }

    /**
     * Finds MECP in a received chat text: a channel message in MeshCore's `Name: text` form, or a
     * direct message that is MECP from its first character.
     */
    fun find(text: String): Received? {
        parse(text)?.let { return Received(null, it) }
        val sender = ChannelReplies.senderOf(text) ?: return null
        val rest = text.substring(text.indexOf(": ") + 2)
        return parse(rest)?.let { Received(sender, it) }
    }

    /** "🆘 MAYDAY", "DRILL 🆘 MAYDAY", and so on. */
    fun headline(message: Message): String {
        val label = "${SEVERITY_SIGNS[message.severity]} ${MecpCodes.SEVERITIES[message.severity]}"
        return if (message.isDrill) "DRILL $label" else label
    }

    /** What each code means, in order, with `[N]` and `[time]` filled in when the message gives them. */
    fun codeMeanings(message: Message): List<String> = (message.codes + message.laterCodes).map { code ->
        val meaning = MecpCodes.CODES[code] ?: return@map "unknown code $code"
        var text = meaning
        message.people?.let { text = text.replace("[N]pax", "$it people") }
        message.etaMinutes?.let { text = text.replace("[time]", duration(it)) }
        text
    }

    /**
     * The message in English, on one line: the headline, then what each code means, then the
     * details from the free text, then whatever free text is left, in quotes, followed by the
     * sender's language when the message names one.
     * `🆘 MAYDAY: Injury · Fracture / immobile · At GPS coordinates · 2 people · 48.6520, 20.1305`
     */
    fun describe(message: Message): String {
        val parts = ArrayList(codeMeanings(message))
        val filled = parts.joinToString(" ")
        message.people?.let { if ("$it people" !in filled) parts.add("$it people") }
        if (message.latitude != null && message.longitude != null) parts.add(coordinates(message.latitude, message.longitude))
        // The sender's local time, with no zone on the wire, so it is shown as sent.
        message.time?.let { parts.add("${it.substring(0, 2)}:${it.substring(2)} sender's time") }
        message.etaMinutes?.let { duration(it).let { eta -> if (eta !in filled) parts.add("ETA $eta") } }
        message.reference?.let { parts.add("incident #$it") }
        message.callsign?.let { parts.add("callsign $it") }
        // §6.1: the language hint is shown, an unknown one as written, after the text it describes.
        val language = message.language?.let { languageName(it) ?: it }
        when {
            message.remainder != null && language != null -> parts.add("“${message.remainder}” ($language)")
            message.remainder != null -> parts.add("“${message.remainder}”")
            language != null -> parts.add(if (language == message.language) "language $language" else "in $language")
        }
        val head = headline(message)
        return if (parts.isEmpty()) head else "$head: ${parts.joinToString(" · ")}"
    }

    /**
     * The English name of the language a hint names (`German` for `de`, `Chinese` for `zh-cn`),
     * or null when it names none that the system knows. Such a hint is shown as written.
     */
    fun languageName(tag: String): String? {
        val locale = Locale.forLanguageTag(tag)
        val name = locale.getDisplayLanguage(Locale.ENGLISH)
        return name.takeIf { it.isNotEmpty() && !it.equals(locale.language, ignoreCase = true) }
    }

    /** Coordinates as MECP writes them, with up to 4 decimals (about 11 m). */
    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%.4f, %.4f", latitude, longitude)

    /** A duration for people: `45 min`, `1 h 30 min`, `2 h`, `2 d`. */
    fun duration(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 1440 == 0 -> "${minutes / 1440} d"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    /**
     * The first match of [pattern] in [text] that [accept] takes. A match it refuses is skipped and
     * the search goes on from the end of it (§6.1 rule 6).
     */
    private fun firstValid(pattern: Regex, text: String, accept: (MatchResult) -> Boolean): MatchResult? {
        var from = 0
        while (from <= text.length) {
            val m = pattern.find(text, from) ?: return null
            if (accept(m)) return m
            from = maxOf(m.range.last + 1, m.range.first + 1)
        }
        return null
    }

    /**
     * [text] without the [used] tokens and the [laterCodes], or null when nothing is left. Boundary
     * marks touching a token go with it, so `(#tag),` leaves nothing; any other punctuation stays
     * as written, so `A / B` is kept whole.
     */
    private fun remainder(text: String, used: List<IntRange>, laterCodes: List<String>): String? {
        if (text.isEmpty()) return null
        val marked = StringBuilder()
        text.forEachIndexed { i, c -> marked.append(if (used.any { i in it }) TAKEN else c) }
        return marked.split(' ')
            .map { word -> word.replace(TAKEN_WITH_MARKS, "") }
            .filter { word -> word.isNotEmpty() && word !in laterCodes }
            .joinToString(" ")
            .ifEmpty { null }
    }

    /** Stands in for a character of a token that was used; NUL cannot arrive in chat text from the radio. */
    private const val TAKEN = '\u0000'

    /** A used token with the boundary marks on either side of it, within one word. */
    private val TAKEN_WITH_MARKS = Regex("$B*$TAKEN+$B*")

    private fun number(m: MatchResult, group: Int): Long? = m.groupValues[group].toLongOrNull()

    private fun times(a: Long?, b: Long): Long? = a?.let { runCatching { Math.multiplyExact(it, b) }.getOrNull() }

    private fun sum(a: Long?, b: Long?): Long? = if (a == null || b == null) null else runCatching { Math.addExact(a, b) }.getOrNull()

    /** §6.1: characters a message must not contain, once one final line ending is dropped. */
    private val LINE_BREAKS = setOf('\r', '\n', '\u000C', '\u0085', '\u2028', '\u2029')

    /** §6.1: a callsign is at most 12 characters. */
    private const val MAX_CALLSIGN = 12

    private val SEVERITY_SIGNS = listOf("🆘", "🚨", "⚠️", "ℹ️")
}
