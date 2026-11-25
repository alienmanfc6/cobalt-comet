package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.utils.Utils
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilsTest {
    private val originalLocale = Locale.getDefault()
    private val originalTimeZone = TimeZone.getDefault()

    @After
    fun restoreDefaults() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun encodeAndDecodePhoneEntries_roundTripsJsonEntries() {
        val entries = listOf(
            PhoneEntry(label = "Driver One", number = "5551234567"),
            PhoneEntry(label = "Driver Two", number = "+15551234568")
        )

        val encoded = Utils.encodePhoneEntries(entries)
        val decoded = Utils.decodePhoneEntries(encoded)

        assertEquals(entries, decoded)
    }

    @Test
    fun decodePhoneEntries_skipsInvalidAndUnsupportedEntries() {
        val raw = """
            [
              {"label":"Driver One","number":"5551234567","type":"PHONE"},
              {"label":"","number":"5551234568","type":"PHONE"},
              {"label":"Truck Radio","number":"AA:BB:CC:DD:EE:FF","type":"BLUETOOTH"}
            ]
        """.trimIndent()

        val decoded = Utils.decodePhoneEntries(raw)

        assertEquals(
            listOf(PhoneEntry(label = "Driver One", number = "5551234567", type = ContactType.PHONE)),
            decoded
        )
    }

    @Test
    fun sanitizePhoneEntries_removesBlankEntriesAndDeduplicatesNormalizedNumbers() {
        val sanitized = Utils.sanitizePhoneEntries(
            listOf(
                PhoneEntry(label = "", number = "5551234567"),
                PhoneEntry(label = "Driver One", number = " "),
                PhoneEntry(label = "Driver One", number = "555 123 4567"),
                PhoneEntry(label = "Driver One", number = "5551234567"),
                PhoneEntry(label = "Driver Two", number = "555 123 9999")
            )
        )

        assertEquals(
            listOf(
                PhoneEntry(label = "Driver One", number = "5551234567", type = ContactType.PHONE),
                PhoneEntry(label = "Driver Two", number = "5551239999", type = ContactType.PHONE)
            ),
            sanitized
        )
    }

    @Test
    fun encodeAndDecodeSavedMessages_roundTripsMessageContent() {
        val messages = listOf(
            MessageModel(
                textList = mutableListOf("Meet here"),
                url = "https://maps.app.goo.gl/example",
                locationName = "Coffee Shop",
                from = "5551234567",
                receivedAt = 1234L
            ),
            MessageModel(lat = "37.4219983", lng = "-122.084", locationName = "Googleplex")
        )

        val encoded = Utils.encodeSavedMessages(messages)
        val decoded = Utils.decodeSavedMessages(encoded)

        assertEquals(messages, decoded)
    }

    @Test
    fun parseUrl_extractsFirstUrlFromSurroundingText() {
        val parsed = Utils.parseUrl("Meet me here https://example.com/path later")

        assertEquals("https://example.com/path", parsed)
    }

    @Test
    fun parseUrl_returnsNullWhenUrlAbsent() {
        val parsed = Utils.parseUrl("Meet me there")

        assertNull(parsed)
    }

    @Test
    fun parseGoogleNavigationString_supportsSchemeSpecificDestination() {
        val destination = Utils.parseGoogleNavigationString("google.navigation:1600%20Amphitheatre%20Parkway")

        assertEquals("1600 Amphitheatre Parkway", destination)
    }

    @Test
    fun parseGeoString_supportsCoordinateOnlyUri() {
        val parsed = Utils.parseGeoString("geo:37.4219983,-122.084")

        assertEquals(Triple("37.4219983", "-122.084", ""), parsed)
    }

    @Test
    fun formatReceivedAt_formatsJustNowWithDeterministicTimestamp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        val nowMillis = Instant.parse("2026-03-17T15:00:00Z").toEpochMilli()
        val timestamp = Instant.parse("2026-03-17T14:59:30Z").toEpochMilli()

        val formatted = Utils.formatReceivedAt(timestamp, nowMillis)

        assertEquals("Just now - Tue 17th at 10:59am", formatted)
    }

    @Test
    fun formatReceivedAt_formatsMultiDayTimestampWithDeterministicTimestamp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        val nowMillis = Instant.parse("2026-03-17T15:00:00Z").toEpochMilli()
        val timestamp = Instant.parse("2026-03-14T13:05:00Z").toEpochMilli()

        val formatted = Utils.formatReceivedAt(timestamp, nowMillis)

        assertEquals("3 days ago - Sat 14th at 9:05am", formatted)
    }
}
