package org.coloradomesh.meshcore.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MECP's conformance cases for its free-text conventions, from `freetext/tests.json` in
 * github.com/mecp-org/MECP at tag v1.2.1 (CC0), one row per case, in the same order. Each case gives
 * a message and what a conforming decoder takes from it for one convention; null means nothing.
 */
class MecpConformanceTest {
    private class Case(val convention: String, val input: String, val expected: Any?)

    private val cases = listOf(
        Case("timestamp", "MECP/3/L06 @2460 @1400 @1530", "14:00"),
        Case("timestamp", "MECP/3/L06 @9999", null),
        Case("timestamp", "MECP/3/L01 @123", "01:23"),
        Case("timestamp", "MECP/2/T05 @1430,", "14:30"),
        Case("timestamp", "MECP/2/T05 meet@1430", null),
        Case("reference", "MECP/2/C08 #base-camp,", "base-camp"),
        Case("reference", "MECP/2/C08 (#tag)", "tag"),
        Case("pax", "MECP/1/S01 3paxmore", null),
        Case("pax", "MECP/1/S01 0pax 2pax", 2),
        Case("pax", "MECP/1/S01 04pax", 4),
        Case("gps", "MECP/3/L06 95.0,200.0 47.3,8.5", "47.3,8.5"),
        Case("gps", "MECP/3/L02 2,5 km", null),
        Case("callsign", "MECP/3/L20 ~DL/OK1ABC", "DL/OK1ABC"),
        Case("callsign", "MECP/3/L20 ~ABCDEFGHIJKLM ~OK1ABC", "OK1ABC"),
        Case("language", "MECP/3/L04 @zh_CN", "zh-cn"),
        Case("duration", "MECP/2/R03 45min", 45),
        Case("duration", "MECP/2/R03 2d", 2880),
        Case("duration", "MECP/2/R03 48h", 2880),
        Case("duration", "MECP/2/R03 2880min", 2880),
        Case("duration", "MECP/2/R03 ETA 45min", 45),
        Case("duration", "MECP/2/R03 45", 45),
        Case("duration", "MECP/2/C01 R03 P05 47.3769,12.1048 45min", 45),
        Case("duration", "MECP/3/L06 45min", null),
        Case("duration", "MECP/2/C01 R03 1pax 45", null),
        Case("message", "MECP/1/T05 ", "valid" to false),
        Case("message", "MECP/1/t05 x", "valid" to false),
        Case("message", "MECP/1/T05 T05 damaged", "valid" to true),
    )

    /** What [Mecp.parse] took from [input] for [convention], in the form the cases use. */
    private fun decoded(convention: String, input: String): Any? {
        val m = Mecp.parse(input)
        if (convention == "message") return "valid" to (m != null)
        m ?: return "not parsed"
        return when (convention) {
            "timestamp" -> m.timeUtc?.let { "${it.substring(0, 2)}:${it.substring(2)}" }
            "reference" -> m.reference
            "pax" -> m.people
            "gps" -> if (m.hasPosition) "${m.latitude},${m.longitude}" else null
            "callsign" -> m.callsign
            "language" -> m.language
            "duration" -> m.etaMinutes
            else -> "unknown convention"
        }
    }

    @Test
    fun everyCasePasses() {
        val failures = cases.mapNotNull { case ->
            val got = decoded(case.convention, case.input)
            if (got == case.expected) null else "${case.convention} \"${case.input}\": expected ${case.expected}, got $got"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun allOfThemAreHere() {
        assertEquals(27, cases.size)
    }
}
