package org.coloradomesh.meshcore.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Examples from the MECP project's README and brief, read as MECP 1.4.0 says. */
class MecpTest {
    private fun describe(text: String) = Mecp.describe(Mecp.parse(text)!!)

    @Test
    fun aMaydayWithPeopleAndAPosition() {
        val m = Mecp.parse("MECP/0/M01 M07 2pax P05 48.6520,20.1305")!!
        assertEquals(0, m.severity)
        assertEquals(listOf("M01", "M07"), m.codes)
        assertEquals(listOf("P05"), m.laterCodes)
        assertEquals(2, m.people)
        assertEquals(48.652, m.latitude!!, 1e-9)
        assertEquals(20.1305, m.longitude!!, 1e-9)
        assertFalse(m.isDrill)
        assertEquals(
            "🆘 MAYDAY: Injury · Fracture / immobile · At GPS coordinates · 2 people · 48.6520, 20.1305",
            describe(m.raw),
        )
    }

    @Test
    fun aSafetyWarningWithAPlaceName() {
        assertEquals("⚠️ SAFETY: Flooding · Road blocked · Evacuating toward · “Poprad”", describe("MECP/2/T04 T01 P02 Poprad"))
    }

    @Test
    fun aSocialMessage() {
        assertEquals("ℹ️ ROUTINE: Beer / drinks · Running late · “30min”", describe("MECP/3/L01 L06 30min"))
    }

    @Test
    fun aDrillSaysSoFirst() {
        val m = Mecp.parse("MECP/0/D01 M01 M07 2pax P05 48.65,20.13")!!
        assertTrue(m.isDrill)
        assertTrue(Mecp.describe(m).startsWith("DRILL 🆘 MAYDAY: This is a drill · Injury"))
        assertFalse(Mecp.parse("MECP/0/D03")!!.isDrill)
    }

    @Test
    fun countsAndEtasFillTheirPlaceholders() {
        assertEquals("⚠️ SAFETY: Have medical supplies · Have shelter / space for 5 people · At GPS coordinates · 48.6500, 20.1300",
            describe("MECP/2/H03 H07 5pax P05 48.65,20.13"))
        assertEquals("ℹ️ ROUTINE: Help coming · ETA 15 min", describe("MECP/3/R02 R03 15"))
    }

    @Test
    fun aBeaconWithTimeCallsignAndReference() {
        assertEquals(
            "🆘 MAYDAY: Automated distress beacon active — sender may be unresponsive · Injury · At GPS coordinates · " +
                "48.6500, 20.1300 · 14:30 sender's time · incident #A1 · callsign W0RMT · in English",
            describe("MECP/0/B01 M01 P05 48.65,20.13 @1430 #A1 ~W0RMT @en"),
        )
    }

    @Test
    fun theLanguageHintIsShownAndAnUnknownOneAsWritten() {
        assertEquals("ℹ️ ROUTINE: Summit reached · “Gipfel erreicht” (German)", describe("MECP/3/L04 Gipfel erreicht @de"))
        assertEquals("ℹ️ ROUTINE: Summit reached · “Gipfel” (zzz-zz)", describe("MECP/3/L04 Gipfel @zzz-ZZ"))
        assertEquals("ℹ️ ROUTINE: Summit reached · language zzz-zz", describe("MECP/3/L04 @zzz-ZZ"))
        assertEquals("Chinese", Mecp.languageName("zh-cn"))
        assertNull(Mecp.languageName("zzz-zz"))
    }

    @Test
    fun unknownCodesAreNamedNotDropped() {
        assertEquals("🚨 URGENT: unknown code Q99 · Injury", describe("MECP/1/Q99 M01"))
    }

    @Test
    fun aMessageNeedsAtLeastOneCode() {
        // Since MECP 1.2 a severity alone is not a message, so it is left as ordinary text.
        assertNull(Mecp.parse("MECP/2/"))
        assertNull(Mecp.parse("MECP/2"))
    }

    @Test
    fun notMecpOrABadSeverityIsLeftAlone() {
        assertNull(Mecp.parse("mecp/0/M01"))
        assertNull(Mecp.parse("MECP/7/M01"))
        assertNull(Mecp.parse("MECP/"))
        assertNull(Mecp.parse("hello MECP/0/M01"))
        assertNull(Mecp.parse("MECP/1/m01 x"))
        assertNull(Mecp.parse("MECP/1/M01 "))
        assertNull(Mecp.parse("MECP/1/M01  hurt"))
    }

    @Test
    fun aMessageIsOneLine() {
        assertEquals("MECP/1/M01 hurt", Mecp.parse("MECP/1/M01 hurt\r\n")!!.raw)
        assertEquals("MECP/1/M01", Mecp.parse("MECP/1/M01\n")!!.raw)
        assertNull(Mecp.parse("MECP/1/M01 hurt\nsecond line"))
        assertNull(Mecp.parse("MECP/1/M01 hurt\u2028more"))
        assertNull(Mecp.parse("MECP/1/M01 hurt\u000Cmore"))
        assertNull(Mecp.parse("MECP/1/M01\n\n"))
    }

    @Test
    fun badDetailsAreNotTakenAsPositionsOrTimes() {
        val m = Mecp.parse("MECP/1/P05 95.0,20.0 @2599")!!
        assertNull(m.latitude)
        assertNull(m.time)
        assertEquals("95.0,20.0 @2599", m.remainder)
    }

    @Test
    fun aGoodPositionAfterABadOneIsFound() {
        val m = Mecp.parse("MECP/0/M01 P05 95.0,200.0 39.7392,-104.9903")!!
        assertEquals(39.7392, m.latitude!!, 1e-9)
        assertEquals(-104.9903, m.longitude!!, 1e-9)
        assertEquals("95.0,200.0", m.remainder)
    }

    @Test
    fun etasInEveryUnit() {
        assertEquals("ℹ️ ROUTINE: Help coming · ETA 45 min", describe("MECP/3/R02 R03 45min"))
        assertEquals("ℹ️ ROUTINE: Help coming · ETA 1 h 30 min", describe("MECP/3/R02 R03 1h30min"))
        assertEquals("ℹ️ ROUTINE: Help coming · ETA 1 h 30 min", describe("MECP/3/R02 R03 ETA 1h 30min"))
        assertEquals("ℹ️ ROUTINE: Help coming · ETA 2 h", describe("MECP/3/R02 R03 2h"))
        assertEquals("ℹ️ ROUTINE: Help coming · ETA 2 d", describe("MECP/3/R02 R03 2d"))
        assertNull(Mecp.parse("MECP/3/R03 0min")!!.etaMinutes)
        assertNull(Mecp.parse("MECP/3/R03 99999999999999999999min")!!.etaMinutes)
    }

    @Test
    fun anEtaComesAnywhereAfterR03ButABareNumberOnlyFirst() {
        val m = Mecp.parse("MECP/2/C01 R03 P05 47.3769,12.1048 45min")!!
        assertEquals(45, m.etaMinutes)
        assertTrue(m.hasPosition)
        assertNull(m.remainder)
        assertNull(Mecp.parse("MECP/2/C01 R03 1pax 45")!!.etaMinutes)
    }

    @Test
    fun theFirstEtaByPositionWins() {
        // MECP 1.4.0: position decides, not the order the patterns are tried in.
        assertEquals(2880, Mecp.parse("MECP/2/R03 2d 45min")!!.etaMinutes)
        assertEquals(45, Mecp.parse("MECP/2/R03 45min 2h")!!.etaMinutes)
        assertEquals(90, Mecp.parse("MECP/2/R03 1h 30min")!!.etaMinutes)
        // A bare number counts only when no duration with a unit does.
        assertEquals(120, Mecp.parse("MECP/2/R03 45 2h")!!.etaMinutes)
        assertEquals(45, Mecp.parse("MECP/2/R03 45 0h")!!.etaMinutes)
    }

    @Test
    fun secondsAndDecimalsAreNotEtas() {
        val seconds = Mecp.parse("MECP/2/R03 30s")!!
        assertNull(seconds.etaMinutes)
        assertEquals("30s", seconds.remainder)
        val position = Mecp.parse("MECP/2/R03 47.3769,12.1048")!!
        assertNull(position.etaMinutes)
        assertTrue(position.hasPosition)
        assertEquals(45, Mecp.parse("MECP/2/R03 45.")!!.etaMinutes)
    }

    @Test
    fun aDecimalEtaIsNotReadFromEitherPart() {
        // MECP 1.4.1 (issue #46): not 5 h from the fraction, nor 1 min from the whole part.
        for (text in listOf("1.5h", "2,5h", "0.5h", "1.25d", "2.5min", "ETA 1.5h")) {
            val m = Mecp.parse("MECP/2/R03 $text")!!
            assertNull(text, m.etaMinutes)
            assertEquals(text, m.remainder)
        }
        assertEquals(45, Mecp.parse("MECP/2/R03 1.5h 45min")!!.etaMinutes)
        // A period after a letter is still a boundary.
        assertEquals(120, Mecp.parse("MECP/2/R03 hut.2h")!!.etaMinutes)
    }

    @Test
    fun theTimeIsTheSendersAndShownAsSent() {
        assertEquals("ℹ️ ROUTINE: Running late · 18:45 sender's time", describe("MECP/3/L06 @1845"))
    }

    @Test
    fun theEtaFillsTheCodeText() {
        assertEquals(listOf("ETA 2 h"), Mecp.codeMeanings(Mecp.parse("MECP/2/R03 2h")!!))
    }

    @Test
    fun detailsInTheirLooserForms() {
        val m = Mecp.parse("MECP/0/M01 4 PAX +48.8566, 2.3522 @6:30 ~vk2/ok1abc/p @zh_CN #base_camp")!!
        assertEquals(4, m.people)
        assertEquals(48.8566, m.latitude!!, 1e-9)
        assertEquals("0630", m.time)
        assertEquals("VK2/OK1ABC/P", m.callsign)
        assertEquals("zh-cn", m.language)
        assertEquals("base_camp", m.reference)
        assertNull(m.remainder)
    }

    @Test
    fun aDetailNeedsBoundariesAroundIt() {
        val m = Mecp.parse("MECP/3/L01 meet@1430 3paxmore #café")!!
        assertNull(m.time)
        assertNull(m.people)
        assertNull(m.reference)
        assertEquals("meet@1430 3paxmore #café", m.remainder)
    }

    @Test
    fun punctuationAroundADetailIsNotLeftOver() {
        val m = Mecp.parse("MECP/2/C08 (#tag), @1430.")!!
        assertEquals("tag", m.reference)
        assertEquals("1430", m.time)
        assertNull(m.remainder)
    }

    @Test
    fun otherPunctuationIsKeptAsWritten() {
        assertEquals("A / B", Mecp.parse("MECP/2/C08 A / B")!!.remainder)
        assertEquals("x", Mecp.parse("MECP/3/L01 x,@1430")!!.remainder)
        assertEquals("near - the bridge.", Mecp.parse("MECP/2/T02 near - the bridge. @1430")!!.remainder)
    }

    @Test
    fun onlyTheListedPunctuationSeparatesADetail() {
        val m = Mecp.parse("MECP/3/L06 #hut2/basecamp 3pax* eta=@1430")!!
        assertNull(m.reference)
        assertNull(m.people)
        assertNull(m.time)
        assertEquals("#hut2/basecamp 3pax* eta=@1430", m.remainder)
        assertEquals("hut", Mecp.parse("MECP/2/C08 #hut\u3001ok")!!.reference)
    }

    @Test
    fun aMessageOver200BytesIsStillRead() {
        // MECP 1.4.0: senders keep to 200 bytes, but a receiver must not refuse a longer message.
        // "MECP/3/L15 " is 11 bytes, and each 界 is 3, so 64 of them make 203.
        val over = "MECP/3/L15 " + "界".repeat(64)
        assertEquals(203, over.toByteArray(Charsets.UTF_8).size)
        assertEquals("界".repeat(64), Mecp.parse(over)!!.freeText)
        assertEquals(over, Mecp.parse(over + "\r\n")!!.raw)
    }

    @Test
    fun languageTagsAreCheckedSubtagBySubtag() {
        assertEquals("zh-hans-cn", Mecp.parse("MECP/3/L04 @zh-Hans-CN")!!.language)
        assertEquals("sr-latn-rs", Mecp.parse("MECP/3/L04 @sr_Latn_RS")!!.language)
        assertNull(Mecp.parse("MECP/3/L04 @abcd")!!.language)
        assertNull(Mecp.parse("MECP/3/L04 @de-x")!!.language)
        assertNull(Mecp.parse("MECP/3/L04 @de--fr")!!.language)
        assertNull(Mecp.parse("MECP/3/L04 @de-")!!.language)
        assertNull(Mecp.parse("MECP/3/L04 @en-abcdefghi")!!.language)
        assertEquals("en", Mecp.parse("MECP/3/L04 @de-x @en")!!.language)
    }

    @Test
    fun aVeryLongLanguageTagDoesNotThrow() {
        // About 60 KB: MECP's own pattern, as a JDK regex, overflowed the stack near 9 KB.
        val tag = "@en" + "-ab".repeat(20_000)
        assertEquals(tag.drop(1), Mecp.parse("MECP/3/L04 $tag")!!.language)
        assertNull(Mecp.parse("MECP/3/L04 ${tag}-x")!!.language)
    }

    @Test
    fun theNewMedicalSupplyDropCode() {
        assertEquals("🚨 URGENT: Medical supply drop needed · At GPS coordinates · 39.7392, -104.9903",
            describe("MECP/1/M16 P05 39.7392,-104.9903"))
    }

    @Test
    fun foundInAChannelMessageWithItsSender() {
        val found = Mecp.find("Silver Eagle: MECP/0/M06 P05 39.7392,-104.9903")!!
        assertEquals("Silver Eagle", found.sender)
        assertEquals(-104.9903, found.message.longitude!!, 1e-9)
        assertNull(Mecp.find("Silver Eagle: hello MECP/0/M06"))
        assertNull(Mecp.find("Silver Eagle: good morning"))
    }

    @Test
    fun foundInADirectMessage() {
        val found = Mecp.find("MECP/3/L15")!!
        assertNull(found.sender)
        assertEquals("ℹ️ ROUTINE: Good morning / check-in", Mecp.describe(found.message))
    }

    @Test
    fun theTableHasEveryCode() {
        assertEquals(109, MecpCodes.CODES.size)
        assertEquals(12, MecpCodes.CATEGORIES.size)
        assertTrue(MecpCodes.CODES.keys.all { it[0] in MecpCodes.CATEGORIES })
    }
}
