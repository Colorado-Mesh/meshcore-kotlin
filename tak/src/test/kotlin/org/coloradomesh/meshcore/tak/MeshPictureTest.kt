package org.coloradomesh.meshcore.tak

import org.coloradomesh.meshcore.protocol.Channel
import org.coloradomesh.meshcore.protocol.ChannelMessage
import org.coloradomesh.meshcore.protocol.Contact
import org.coloradomesh.meshcore.protocol.DirectMessage
import org.coloradomesh.meshcore.protocol.Mecp
import org.coloradomesh.meshcore.protocol.Position
import org.coloradomesh.meshcore.protocol.SelfInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * How MeshPicture shows the mesh in a TAK app. These pin down the uids, callsigns and types that
 * ATAK (through the bridge) and WearTAK (through the plugin) both rely on, so a node, channel or
 * MECP message looks the same in either.
 */
class MeshPictureTest {
    private val now = 1_763_300_000L
    private val atak = ClientIdentity(uid = "ANDROID-abc", callsign = "BUD")

    private fun self(name: String = "Bud T") = SelfInfo(
        name = name,
        publicKeyHex = "f".repeat(64),
        latitude = 0.0,
        longitude = 0.0,
        frequencyMhz = 910.525,
        bandwidthKhz = 62.5,
        spreadingFactor = 7,
        codingRate = 5,
        txPowerDbm = 22,
    )

    private fun contact(
        keyChar: Char,
        type: Int = Contact.TYPE_COMPANION,
        name: String = "Ridge",
        latitude: Double = 39.74,
        longitude: Double = -104.99,
        lastAdvert: Long = now - 60,
    ) = Contact(
        publicKeyHex = keyChar.toString().repeat(64),
        type = type,
        name = name,
        lastAdvert = lastAdvert,
        latitude = latitude,
        longitude = longitude,
    )

    private fun picture(): MeshPicture = MeshPicture("Test App").also { it.setSelf(self()) }

    @Test
    fun showsNothingBeforeTheRadioIsRead() {
        val picture = MeshPicture("Test App")
        picture.setChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        picture.putContact(contact('a'))
        assertTrue(picture.markers(now).isEmpty())
        assertFalse(picture.hasRadio)
    }

    @Test
    fun eachChannelIsAReachableContactAtZeroZero() {
        val picture = picture()
        picture.setChannels(
            listOf(
                Channel(0, "Public", Channel.Kind.PUBLIC),
                Channel(1, "#denver", Channel.Kind.HASHTAG),
                Channel(2, "Team", Channel.Kind.PRIVATE),
            ),
        )
        val byUid = picture.markers(now).associateBy { it.uid }
        assertEquals("#Public", byUid.getValue("MESHCORE-CH-0").callsign)
        assertEquals("#denver", byUid.getValue("MESHCORE-CH-1").callsign)
        assertEquals("#🔒 Team", byUid.getValue("MESHCORE-CH-2").callsign)
        val publicChannel = byUid.getValue("MESHCORE-CH-0")
        assertEquals(0.0, publicChannel.latitude, 0.0)
        assertEquals(0.0, publicChannel.longitude, 0.0)
        assertEquals("*:-1:stcp", publicChannel.endpoint)
        assertEquals("MeshCore channel 0, linked through Test App", publicChannel.remarks)
        assertEquals(3, picture.channelCount)
    }

    @Test
    fun channelsThatShareANameGetTheirSlotNumber() {
        val picture = picture()
        picture.setChannels(listOf(Channel(2, "Ops"), Channel(3, "Ops")))
        val callsigns = picture.markers(now).map { it.callsign }.toSet()
        assertEquals(setOf("#🔒 Ops (2)", "#🔒 Ops (3)"), callsigns)
    }

    @Test
    fun aMadeUpChannelNameThatMatchesAnotherChannelLosesNoChannel() {
        val picture = picture()
        // Slots 0 and 1 share a name; slot 2 is already called what slot 0 would be called.
        picture.setChannels(listOf(Channel(0, "Ops"), Channel(1, "Ops"), Channel(2, "Ops (0)")))
        val channels = picture.markers(now).filter { it.uid.startsWith("MESHCORE-CH-") }
        assertEquals(setOf("MESHCORE-CH-0", "MESHCORE-CH-1", "MESHCORE-CH-2"), channels.map { it.uid }.toSet())
        assertEquals(3, channels.map { it.callsign }.toSet().size)
        assertNotNull(picture.chatLine(ChannelMessage(0, "Ridge: still here", now), atak))
    }

    @Test
    fun aChatEventFindsItsChannelByContactUidOrCallsign() {
        val picture = picture()
        picture.setChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC), Channel(1, "#denver", Channel.Kind.HASHTAG)))
        assertEquals(1, picture.channelFor("anything", "MESHCORE-CH-1")?.index)
        assertEquals(0, picture.channelFor("#Public", null)?.index)
        assertNull(picture.channelFor("#nowhere", "MESHCORE-CH-9"))
    }

    @Test
    fun nodesWithAPositionAreMarkersByType() {
        val picture = picture()
        picture.relayIconPath = "set/group/repeater.png"
        picture.putContact(contact('a', name = "Ridge"))
        picture.putContact(contact('b', type = Contact.TYPE_REPEATER, name = "Squaw Mtn"))
        picture.putContact(contact('c', type = Contact.TYPE_SENSOR, name = "Weather"))
        picture.putContact(contact('d', name = "Hidden", latitude = 0.0, longitude = 0.0))
        val byUid = picture.markers(now).associateBy { it.uid }

        val companion = byUid.getValue("MESHCORE-" + "a".repeat(16))
        assertEquals("a-f-G-U-C", companion.cotType)
        assertEquals("Ridge", companion.callsign)
        assertEquals(MeshPicture.DEFAULT_COMPANION_TEAM, companion.team)
        assertEquals("*:-1:stcp", companion.endpoint)

        val repeater = byUid.getValue("MESHCORE-" + "b".repeat(16))
        assertEquals("a-f-G-I", repeater.cotType)
        assertEquals("set/group/repeater.png", repeater.iconsetPath)
        assertNull(repeater.endpoint)

        assertEquals("a-f-G-E-S", byUid.getValue("MESHCORE-" + "c".repeat(16)).cotType)
        // No position, and no conversation yet: not on the map.
        assertFalse(byUid.containsKey("MESHCORE-" + "d".repeat(16)))
        assertEquals(3, picture.positionedCount)
    }

    @Test
    fun filtersLeaveOffHiddenTypesAndOldAdverts() {
        val picture = picture()
        picture.putContact(contact('a'))
        picture.putContact(contact('b', type = Contact.TYPE_REPEATER))
        picture.putContact(contact('c', lastAdvert = now - 7_200))
        picture.shownTypes = setOf(Contact.TYPE_COMPANION)
        picture.maxAgeSeconds = 3_600
        val uids = picture.markers(now).map { it.uid }.toSet()
        assertEquals(setOf("MESHCORE-" + "a".repeat(16)), uids)
    }

    @Test
    fun aCompanionWithNoPositionStaysReachableOnceTheyHaveTalked() {
        val picture = picture()
        val quiet = contact('e', name = "Quiet", latitude = 0.0, longitude = 0.0)
        picture.putContact(quiet)
        assertTrue(picture.noteTalkedTo(quiet, now))
        assertFalse(picture.noteTalkedTo(quiet, now))
        val marker = picture.markers(now).single { it.uid == picture.markerUid(quiet) }
        assertEquals(0.0, marker.latitude, 0.0)
        assertEquals("*:-1:stcp", marker.endpoint)
        assertEquals(quiet, picture.companionFor(picture.markerUid(quiet)))
    }

    @Test
    fun aChannelMessageIsALineFromTheChannelsContact() {
        val picture = picture()
        picture.setChannels(listOf(Channel(1, "#denver", Channel.Kind.HASHTAG)))
        val line = picture.chatLine(ChannelMessage(1, "Ridge: hello", now - 5), atak)!!
        assertEquals("MESHCORE-CH-1", line.senderUid)
        assertEquals("#denver", line.senderCallsign)
        assertEquals(atak.callsign, line.room)
        assertEquals(atak.uid, line.roomId)
        assertEquals("Ridge: hello", line.text)
        assertEquals(Instant.ofEpochSecond(now - 5), line.sentAt)
        assertNull(picture.chatLine(ChannelMessage(7, "lost", now), atak))
    }

    @Test
    fun aDirectMessageIsALineFromItsSender() {
        val picture = picture()
        val ridge = contact('a', name = "Ridge")
        picture.putContact(ridge)
        val known = picture.directLine(DirectMessage("a".repeat(12), "hi", now), atak)
        assertEquals(picture.markerUid(ridge), known.senderUid)
        assertEquals("Ridge", known.senderCallsign)
        val unknown = picture.directLine(DirectMessage("0123456789ab", "who?", now), atak)
        assertEquals("MESHCORE-0123456789ab", unknown.senderUid)
        assertEquals("01234567", unknown.senderCallsign)
    }

    @Test
    fun differentTextsGetDifferentMessageIdsEvenWithTheSameHashCode() {
        val picture = picture()
        picture.setChannels(listOf(Channel(1, "#denver", Channel.Kind.HASHTAG)))
        // "Aa" and "BB" have the same String.hashCode().
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val a = picture.directLine(DirectMessage("a".repeat(12), "Aa", now), atak)
        val b = picture.directLine(DirectMessage("a".repeat(12), "BB", now), atak)
        assertFalse(a.messageId == b.messageId)
        val ca = picture.chatLine(ChannelMessage(1, "Aa", now), atak)!!
        val cb = picture.chatLine(ChannelMessage(1, "BB", now), atak)!!
        assertFalse(ca.messageId == cb.messageId)
        // A retry of the same message keeps its id.
        assertEquals(a.messageId, picture.directLine(DirectMessage("a".repeat(12), "Aa", now), atak).messageId)
    }

    @Test
    fun noticesAtTheSameMomentGetDifferentMessageIds() {
        val picture = picture()
        picture.setChannels(listOf(Channel(1, "#denver", Channel.Kind.HASHTAG)))
        val ridge = contact('a')
        val at = Instant.ofEpochSecond(now)
        val first = picture.directNotice(ridge, atak, "Not delivered", at)
        val second = picture.directNotice(ridge, atak, "Radio disconnected", at)
        assertFalse(first.messageId == second.messageId)
        val third = picture.notice(1, atak, "Too long", at)!!
        val fourth = picture.notice(1, atak, "Not sent", at)!!
        assertFalse(third.messageId == fourth.messageId)
    }

    @Test
    fun channelTextLeavesRoomForTheRadiosName() {
        val picture = MeshPicture("Test App")
        picture.setSelf(self(name = "Bud"))
        // 160 bytes, less the name, ": " and one spare byte, but never more than 133.
        assertEquals(133, picture.maxTextBytes())
        picture.setSelf(self(name = "x".repeat(150)))
        assertEquals(20, picture.maxTextBytes())
    }

    @Test
    fun anMecpMessageWithAPositionIsASpotMarker() {
        val picture = picture()
        val message = Mecp.parse("MECP/0/M01 M07 2pax P05 48.6520,20.1305")
        assertNotNull(message)
        val place = picture.putMecp("Ridge", "on #emergency", message!!, now)
        assertNotNull(place)
        val marker = picture.markers(now).single { it.uid == place!!.uid }
        assertEquals(CotEvents.SPOT_TYPE, marker.cotType)
        assertTrue(marker.callsign.endsWith(" Ridge"))
        assertEquals(48.652, marker.latitude, 1e-9)
        assertEquals(0xFFFF0000.toInt(), marker.colorArgb)
        val remarks = marker.remarks!!
        assertTrue(remarks.contains("From Ridge on #emergency"))
        assertTrue(remarks.contains("through Test App"))
    }

    @Test
    fun anMecpMessageWithoutAPositionHasNoMarker() {
        val picture = picture()
        val message = Mecp.parse("MECP/3/D03")
        assertNotNull(message)
        assertNull(picture.putMecp("Ridge", "on #emergency", message!!, now))
    }

    @Test
    fun expiredMecpMarkersDoNotCrowdOutANewOne() {
        val picture = picture()
        // Fill the 50-marker limit with MAYDAYs, which last a day.
        for (i in 1..50) {
            val mayday = Mecp.parse("MECP/0/M01 48.6520,20.1305")
            assertNotNull(mayday)
            assertNotNull(picture.putMecp("Sender $i", "on #emergency", mayday!!, now))
        }
        // A day and an hour later, without the map being refreshed in between, a ROUTINE message arrives.
        val later = now + 25 * 3_600
        val routine = Mecp.parse("MECP/3/M01 48.6520,20.1305")
        assertNotNull(routine)
        val place = picture.putMecp("Ridge", "on #emergency", routine!!, later)
        assertNotNull(place)
        assertEquals(listOf(place!!.uid), picture.markers(later).map { it.uid })
    }

    @Test
    fun clearForgetsTheRadio() {
        val picture = picture()
        picture.setChannels(listOf(Channel(0, "Public", Channel.Kind.PUBLIC)))
        picture.putContact(contact('a'))
        picture.clear()
        assertFalse(picture.hasRadio)
        assertEquals(0, picture.contactCount)
        assertTrue(picture.markers(now).isEmpty())
    }

    @Test
    fun aTrackedCompanionWithNoAdvertPositionShowsAtItsNewerFix() {
        val picture = picture()
        val runner = contact('d', name = "Runner", latitude = 0.0, longitude = 0.0)
        picture.putContact(runner)
        assertFalse(picture.isShown(runner, now))
        picture.tracked = setOf(runner.publicKeyHex)
        picture.putFix(runner.publicKeyHex, Position(39.9, -105.1, null), now - 10)
        assertTrue(picture.isShown(runner, now))
        val marker = picture.markers(now).single { it.uid == picture.markerUid(runner) }
        assertEquals(39.9, marker.latitude, 1e-9)
    }

    @Test
    fun aMarkerTheLimitDropsAtOnceIsNotReported() {
        val picture = picture()
        val mayday = Mecp.parse("MECP/0/M01 48.6520,20.1305")!!
        for (i in 1..50) assertNotNull(picture.putMecp("Sender $i", "on #emergency", mayday, now))
        val routine = Mecp.parse("MECP/3/M01 48.6520,20.1305")!!
        assertNull(picture.putMecp("Ridge", "on #emergency", routine, now))
        assertEquals(50, picture.markers(now).size)
    }
}
