package org.coloradomesh.meshcore.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MECP's conformance cases for its free-text conventions, from `freetext/tests.json` in
 * github.com/mecp-org/MECP at tag v1.4.1 (CC0), one row per case, in the same order. Each case
 * gives a message and everything a conforming decoder takes from it, as MECP's own checker
 * (`scripts/check-freetext.mjs`) compares it: a convention left out means nothing is taken for
 * it, and a null expectation means the text is not an MECP message.
 */
class MecpConformanceTest {
    private class Case(val title: String, val input: String, val expected: Map<String, Any>?)

    private val cases = listOf(
        Case("Timestamp: out-of-range values are skipped and the first valid one is used", "MECP/3/L06 @2460 @1400 @1530", mapOf("timestamp" to "14:00")),
        Case("Timestamp: an out-of-range value is not decoded", "MECP/3/L06 @9999", mapOf()),
        Case("Timestamp: a three-digit value implies a leading zero hour", "MECP/3/L01 @123", mapOf("timestamp" to "01:23")),
        Case("Timestamp: trailing punctuation is a boundary", "MECP/2/T05 @1430,", mapOf("timestamp" to "14:30")),
        Case("Timestamp: a sigil inside a word is not decoded", "MECP/2/T05 meet@1430", mapOf()),
        Case("Timestamp: an equals sign is not a boundary", "MECP/3/L06 eta=@1430", mapOf()),
        Case("Reference: trailing punctuation is a boundary", "MECP/2/C08 #base-camp,", mapOf("reference" to "base-camp")),
        Case("Reference: parentheses are boundaries", "MECP/2/C08 (#tag)", mapOf("reference" to "tag")),
        Case("Reference: the full tag is kept, not truncated", "MECP/2/C08 #basecamp", mapOf("reference" to "basecamp")),
        Case("Reference: an ideographic space (U+3000) is a boundary", "MECP/2/C08 #hut\u3000ok", mapOf("reference" to "hut")),
        Case("Reference: an Arabic comma (U+060C) is a boundary", "MECP/2/C08 #hut\u060Cok", mapOf("reference" to "hut")),
        Case("Reference: a right double quotation mark (U+201D) is a boundary", "MECP/2/C08 #hut\u201Dok", mapOf("reference" to "hut")),
        Case("Reference: an ideographic comma (U+3001) is a boundary", "MECP/2/C08 #hut\u3001ok", mapOf("reference" to "hut")),
        Case("Reference: a fullwidth comma (U+FF0C) is a boundary", "MECP/2/C08 #hut\uFF0Cok", mapOf("reference" to "hut")),
        Case("Reference: a slash is not a boundary", "MECP/2/C08 #hut2/basecamp", mapOf()),
        Case("Reference: a fragment inside a URL is not decoded", "MECP/3/L06 see https://example.org/#notes", mapOf()),
        Case("Pax: letters after the unit prevent a match", "MECP/1/S01 3paxmore", mapOf()),
        Case("Pax: zero fails validation and the next value is used", "MECP/1/S01 0pax 2pax", mapOf("pax" to 2)),
        Case("Pax: leading zeros are ignored", "MECP/1/S01 04pax", mapOf("pax" to 4)),
        Case("Pax: parentheses are boundaries", "MECP/1/S01 (4pax)", mapOf("pax" to 4)),
        Case("Pax: the unit is case-insensitive and a trailing period is a boundary", "MECP/1/S01 4Pax.", mapOf("pax" to 4)),
        Case("Pax: a no-break space (U+00A0) is a boundary", "MECP/1/S01 4pax\u00A0water", mapOf("pax" to 4)),
        Case("Pax: a vertical tab (U+000B) is not a boundary", "MECP/1/S01 4pax\u000Bwater", mapOf()),
        Case("Pax: the first value wins", "MECP/1/S01 2pax 5pax", mapOf("pax" to 2)),
        Case("Pax: an asterisk is not a boundary", "MECP/1/S01 3pax*", mapOf()),
        Case("GPS: an out-of-range position is skipped and the next valid one is used", "MECP/3/L06 95.0,200.0 47.3,8.5", mapOf("gps" to "47.3,8.5")),
        Case("GPS: integers with a decimal comma are not a position", "MECP/3/L02 2,5 km", mapOf()),
        Case("GPS: a plus sign and a space after the comma are accepted", "MECP/0/M04 +48.8566, 2.3522", mapOf("gps" to "48.8566,2.3522")),
        Case("GPS: a latitude just above 90 is not decoded", "MECP/0/M04 90.0001,0.0", mapOf()),
        Case("GPS: letters before the position prevent a match", "MECP/0/M01 x48.65,20.13", mapOf()),
        Case("Callsign: a prefix separated by a slash is kept", "MECP/3/L20 ~DL/OK1ABC", mapOf("callsign" to "DL/OK1ABC")),
        Case("Callsign: one longer than 12 characters is skipped and the next valid one is used", "MECP/3/L20 ~ABCDEFGHIJKLM ~OK1ABC", mapOf("callsign" to "OK1ABC")),
        Case("Callsign: exactly 12 characters is accepted", "MECP/3/L20 ~ABCDEFGHIJKL", mapOf("callsign" to "ABCDEFGHIJKL")),
        Case("Callsign: an APRS/AX.25 SSID is not decoded, and the message is still valid", "MECP/3/L20 ~W1AW-9", mapOf()),
        Case("Language: an underscore region tag is normalised to lowercase with a hyphen", "MECP/3/L04 @zh_CN", mapOf("language" to "zh-cn")),
        Case("Language: an uppercase tag is normalised to lowercase", "MECP/3/L04 @DE", mapOf("language" to "de")),
        Case("Duration: minutes", "MECP/2/R03 45min", mapOf("duration" to 45)),
        Case("Duration: days are converted to minutes, with no upper bound", "MECP/2/R03 2d", mapOf("duration" to 2880)),
        Case("Duration: hours are converted to minutes", "MECP/2/R03 48h", mapOf("duration" to 2880)),
        Case("Duration: a large minute count is not capped", "MECP/2/R03 2880min", mapOf("duration" to 2880)),
        Case("Duration: an ETA prefix is accepted", "MECP/2/R03 ETA 45min", mapOf("duration" to 45)),
        Case("Duration: a bare number as the first freetext token is minutes", "MECP/2/R03 45", mapOf("duration" to 45)),
        Case("Duration: R03 applies when it is not the first code, alongside a position", "MECP/2/C01 R03 P05 47.3769,12.1048 45min", mapOf("gps" to "47.3769,12.1048", "duration" to 45)),
        Case("Duration: not decoded without R03", "MECP/3/L06 45min", mapOf()),
        Case("Duration: a bare number that is not the first freetext token is not decoded", "MECP/2/C01 R03 1pax 45", mapOf("pax" to 1)),
        Case("Duration: seconds are not a unit", "MECP/2/R03 30s", mapOf()),
        Case("Duration: a value in seconds is skipped and the next value is used", "MECP/2/R03 30s 45min", mapOf("duration" to 45)),
        Case("Duration: a position as the first freetext token is not a bare number", "MECP/2/R03 47.3769,12.1048", mapOf("gps" to "47.3769,12.1048")),
        Case("Duration: a bare number followed by a period is minutes", "MECP/2/R03 45.", mapOf("duration" to 45)),
        Case("Duration: a zero value is skipped and the bare number is used", "MECP/2/R03 45 0h", mapOf("duration" to 45)),
        Case("Duration: a value with a unit is preferred over a bare number", "MECP/2/R03 45 2h", mapOf("duration" to 120)),
        Case("Duration: the first value by position wins (days before minutes)", "MECP/2/R03 2d 45min", mapOf("duration" to 2880)),
        Case("Duration: the first value by position wins (minutes before hours)", "MECP/2/R03 45min 2h", mapOf("duration" to 45)),
        Case("Duration: hours and minutes separated by a space are combined", "MECP/2/R03 1h 30min", mapOf("duration" to 90)),
        Case("Duration: a space between the number and the unit is accepted", "MECP/2/R03 2 h", mapOf("duration" to 120)),
        Case("Duration: a decimal hour is not read from its fractional part", "MECP/2/R03 1.5h", mapOf()),
        Case("Duration: a decimal with a comma is not read from its fractional part", "MECP/2/R03 2,5h", mapOf()),
        Case("Duration: a decimal below one is not read from its fractional part", "MECP/2/R03 0.5h", mapOf()),
        Case("Duration: a decimal day is not read from its fractional part", "MECP/2/R03 1.25d", mapOf()),
        Case("Duration: a decimal minute value is not read from its fractional part", "MECP/2/R03 2.5min", mapOf()),
        Case("Duration: a skipped decimal value lets the next value be used", "MECP/2/R03 1.5h 45min", mapOf("duration" to 45)),
        Case("Duration: a period after a letter still separates a value", "MECP/2/R03 hut.2h", mapOf("duration" to 120)),
        Case("Message: a trailing space is invalid", "MECP/1/T05 ", null),
        Case("Message: a lowercase first code is not a code, so the message has no codes and is invalid", "MECP/1/t05 x", null),
        Case("Message: a repeated code is still valid", "MECP/1/T05 T05 damaged", mapOf()),
        Case("Message: an unknown but well-formed code is still a valid message", "MECP/3/Z99 hello", mapOf()),
        Case("Message: a lowercase code ends the code section, so a lowercase r03 does not enable duration", "MECP/2/L01 r03 45min", mapOf()),
        Case("Message: no codes after the severity is invalid", "MECP/0/", null),
        Case("Message: a missing code section is invalid", "MECP/0", null),
        Case("Message: severity above 3 is invalid", "MECP/4/M01", null),
        Case("Message: one trailing LF is removed before matching", "MECP/1/T05 T05 damaged\n", mapOf()),
        Case("Message: one trailing CRLF is removed before matching", "MECP/1/T05 T05 damaged\r\n", mapOf()),
        Case("Message: one trailing CR is removed before matching", "MECP/1/T05 T05 damaged\r", mapOf()),
        Case("Message: an embedded LF is invalid", "MECP/1/T05 foo\nbar", null),
        Case("Message: an embedded line separator (U+2028) is invalid", "MECP/1/T05 foo\u2028bar", null),
        Case("Message: an embedded form feed is invalid", "MECP/1/T05 foo\u000Cbar", null),
        Case("Message: decoders accept a message longer than 200 bytes (202 bytes)", "MECP/0/M01 " + "A".repeat(191), mapOf()),
    )

    /** Everything [Mecp.parse] took from [input], in the form the cases use, or null if not MECP. */
    private fun decoded(input: String): Map<String, Any>? {
        val m = Mecp.parse(input) ?: return null
        return buildMap {
            m.time?.let { put("timestamp", "${it.substring(0, 2)}:${it.substring(2)}") }
            m.reference?.let { put("reference", it) }
            m.people?.let { put("pax", it) }
            if (m.hasPosition) put("gps", "${m.latitude},${m.longitude}")
            m.callsign?.let { put("callsign", it) }
            m.language?.let { put("language", it) }
            m.etaMinutes?.let { put("duration", it) }
        }
    }

    @Test
    fun everyCasePasses() {
        val failures = cases.mapNotNull { case ->
            val got = decoded(case.input)
            if (got == case.expected) null else "${case.title} (\"${case.input}\"): expected ${case.expected}, got $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun allOfThemAreHere() {
        assertEquals(77, cases.size)
    }
}
