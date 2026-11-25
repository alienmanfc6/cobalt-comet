package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.utils.Utils
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsAdditionalTest {
    private val originalLocale = Locale.getDefault()
    private val originalTimeZone = TimeZone.getDefault()

    @After
    fun restoreDefaults() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun isGoogleMapsUrl_recognizesSupportedHosts() {
        assertTrue(Utils.isGoogleMapsUrl("https://maps.app.goo.gl/example"))
        assertTrue(Utils.isGoogleMapsUrl("https://goo.gl/maps/example"))
        assertTrue(Utils.isGoogleMapsUrl("https://www.google.com/maps/search/?api=1&query=Coffee+Shop"))
    }

    @Test
    fun isGoogleMapsUrl_rejectsUnsupportedAndMalformedUrls() {
        assertFalse(Utils.isGoogleMapsUrl("https://www.google.com/search?q=coffee"))
        assertFalse(Utils.isGoogleMapsUrl("https://example.com/maps"))
        assertFalse(Utils.isGoogleMapsUrl("not a url"))
    }

    @Test
    fun extractGoogleMapsCoordinates_supportsDestinationParameterAndPathCoordinates() {
        val destinationCoordinates = Utils.extractGoogleMapsCoordinates(
            "https://www.google.com/maps/dir/?api=1&destination=37.4219983,-122.084"
        )
        val pathCoordinates = Utils.extractGoogleMapsCoordinates(
            "https://www.google.com/maps/place/Googleplex/@37.4219983,-122.084,17z"
        )

        assertEquals("37.4219983,-122.084", destinationCoordinates)
        assertEquals("37.4219983,-122.084", pathCoordinates)
    }

    @Test
    fun extractGoogleMapsCoordinates_returnsNullForMalformedUrl() {
        assertNull(Utils.extractGoogleMapsCoordinates("not a url"))
    }

    @Test
    fun extractGoogleMapsNavigationQuery_supportsDestinationAndFragmentCoordinates() {
        val destination = Utils.extractGoogleMapsNavigationQuery(
            "https://www.google.com/maps/dir/?api=1&destination=1600+Amphitheatre+Parkway"
        )
        val fragmentCoordinates = Utils.extractGoogleMapsNavigationQuery(
            "https://www.google.com/maps#37.4219983,-122.084"
        )

        assertEquals("1600 Amphitheatre Parkway", destination)
        assertEquals("37.4219983,-122.084", fragmentCoordinates)
    }

    @Test
    fun extractGoogleMapsNavigationQuery_returnsNullForMalformedUrl() {
        assertNull(Utils.extractGoogleMapsNavigationQuery("not a url"))
    }

    @Test
    fun shouldLaunchMapsUrlWithoutPackage_isFalseForRegularMapsUrls() {
        val shouldLaunchWithoutPackage =
            Utils.shouldLaunchMapsUrlWithoutPackage("https://www.google.com/maps/search/?api=1&query=Coffee+Shop")

        assertFalse(shouldLaunchWithoutPackage)
    }

    @Test
    fun parseGoogleNavigationString_supportsQueryParameterAndRejectsBlankPayload() {
        val queryDestination =
            Utils.parseGoogleNavigationString("google.navigation:q=Coffee%20Shop&mode=d")
        val blankDestination = Utils.parseGoogleNavigationString("google.navigation:")

        assertEquals("Coffee Shop", queryDestination)
        assertNull(blankDestination)
    }

    @Test
    fun parseGeoString_returnsEmptyTripleForBlankPayloadAndSupportsSchemeSpecificCoordinates() {
        val blankParsed = Utils.parseGeoString("geo:")
        val coordinatesParsed = Utils.parseGeoString("geo:47.6205,-122.3493?z=12")

        assertEquals(Triple("", "", ""), blankParsed)
        assertEquals(Triple("47.6205", "-122.3493", ""), coordinatesParsed)
    }

    @Test
    fun isYelpShareLink_detectsCustomSchemeLinks() {
        assertTrue(Utils.isYelpShareLink("share://yelp/biz/example"))
        assertFalse(Utils.isYelpShareLink("https://www.yelp.com/biz/example"))
    }

    @Test
    fun csvHelpers_handleSingleAndMultipleEntries() {
        assertEquals("123-456", Utils.listToCsv(listOf("123", "456")))
        assertEquals(listOf("789"), Utils.csvToList("789"))
    }

    @Test
    fun normalizePhoneNumber_keepsNonWhitespaceFormatting() {
        assertEquals("+1(555)123-4567", Utils.normalizePhoneNumber("+1 (555) 123-4567"))
    }

    @Test
    fun isValidPhoneNumber_acceptsPhoneLikeInputAndRejectsInvalidText() {
        assertTrue(Utils.isValidPhoneNumber("+1 555 123 4567"))
        assertFalse(Utils.isValidPhoneNumber(""))
        assertFalse(Utils.isValidPhoneNumber("call me maybe"))
    }

    @Test
    fun formatReceivedAt_returnsNullForNonPositiveTimestamp() {
        assertNull(Utils.formatReceivedAt(0))
    }

    @Test
    fun formatReceivedAt_formatsOneMinuteBoundaryWithDeterministicTimestamp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        val nowMillis = Instant.parse("2026-03-17T15:00:00Z").toEpochMilli()
        val timestamp = Instant.parse("2026-03-17T14:59:00Z").toEpochMilli()

        val formatted = Utils.formatReceivedAt(timestamp, nowMillis)

        assertEquals("1 minute ago - Tue 17th at 10:59am", formatted)
    }
}
