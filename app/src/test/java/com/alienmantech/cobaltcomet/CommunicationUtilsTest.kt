package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.SendFailureReason
import com.alienmantech.cobaltcomet.utils.SendResult
import com.alienmantech.cobaltcomet.utils.TransportPreflight
import com.alienmantech.cobaltcomet.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationUtilsTest {
    @Test
    fun preferredNavigationDestination_usesCoordinatesWithoutLabel() {
        val message = MessageModel(
            lat = "37.7749",
            lng = "-122.4194",
            locationName = "Coffee Shop"
        )

        val destination = CommunicationUtils.preferredNavigationDestination(message)

        assertEquals("37.7749,-122.4194", destination)
    }

    @Test
    fun preferredNavigationDestination_fallsBackToLocationName() {
        val message = MessageModel(locationName = "Coffee Shop")

        val destination = CommunicationUtils.preferredNavigationDestination(message)

        assertEquals("Coffee Shop", destination)
    }

    @Test
    fun shouldPreferGoogleMapsUrl_whenOnlyMapsUrlExists() {
        val message = MessageModel(
            locationName = "Coffee Shop",
            url = "https://maps.app.goo.gl/example"
        )

        val shouldPreferUrl = CommunicationUtils.shouldPreferGoogleMapsUrl(message)

        assertTrue(shouldPreferUrl)
    }

    @Test
    fun encodeUrlMessage_returnsPureJsonPayload() {
        val encoded = CommunicationUtils.encodeUrlMessage(
            title = "Coffee Shop",
            text = "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194"
        )

        val message = MessageModel().apply { fromJson(encoded) }

        assertEquals("Coffee Shop", message.locationName)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194",
            message.url
        )
        assertEquals("37.7749", message.lat)
        assertEquals("-122.4194", message.lng)
        assertFalse(encoded.contains("CobaltComet"))
        assertFalse(encoded.contains("\n"))
    }

    @Test
    fun encodeGeoMessage_returnsPureJsonPayload() {
        val encoded = CommunicationUtils.encodeGeoMessage(
            lat = "37.4219983",
            lng = "-122.084",
            locationName = "Googleplex"
        )

        val message = MessageModel().apply { fromJson(encoded) }

        assertEquals("37.4219983", message.lat)
        assertEquals("-122.084", message.lng)
        assertEquals("Googleplex", message.locationName)
        assertFalse(encoded.contains("CobaltComet"))
    }

    @Test
    fun shouldInterceptMessage_acceptsValidJsonPayload() {
        val message = MessageModel(
            url = "https://maps.app.goo.gl/example",
            locationName = "Coffee Shop"
        )

        val shouldIntercept = CommunicationUtils.shouldInterceptMessage(message.toString())

        assertTrue(shouldIntercept)
    }

    @Test
    fun shouldInterceptMessage_rejectsPlainTextAndMalformedJson() {
        assertFalse(CommunicationUtils.shouldInterceptMessage("meet me there"))
        assertFalse(CommunicationUtils.shouldInterceptMessage("{\"url\":"))
    }

    @Test
    fun parseMessagePayload_decodesJsonOnlyUrlPayload() {
        val payload =
            "{\"url\":\"https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194\",\"locationName\":\"Coffee Shop\"}"

        val message = CommunicationUtils.parseMessagePayload(payload)

        assertEquals("Coffee Shop", message?.locationName)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194",
            message?.url
        )
    }

    @Test
    fun parseMessagePayload_decodesJsonOnlyGeoPayload() {
        val payload = "{\"lat\":\"37.4219983\",\"lng\":\"-122.084\",\"locationName\":\"Googleplex\"}"

        val message = CommunicationUtils.parseMessagePayload(payload)

        assertEquals("37.4219983", message?.lat)
        assertEquals("-122.084", message?.lng)
        assertEquals("Googleplex", message?.locationName)
    }

    @Test
    fun parseMessagePayload_rejectsLegacyPrefixedPayload() {
        val prefixedPayload = "CobaltComet{\"url\":\"https://maps.app.goo.gl/example\"}"

        assertNull(CommunicationUtils.parseMessagePayload(prefixedPayload))
    }

    @Test
    fun shouldLaunchMapsUrlWithoutPackage_forShortGoogleMapsLinks() {
        val shouldLaunchWithoutPackage =
            Utils.shouldLaunchMapsUrlWithoutPackage("https://maps.app.goo.gl/oKZjmrkguyw8YLcy7")

        assertTrue(shouldLaunchWithoutPackage)
    }

    @Test
    fun shouldFallbackToLocationName_isFalseWhenMapsUrlWasHandled() {
        val message = MessageModel(
            locationName = "Lowe's Home Improvement",
            url = "https://maps.app.goo.gl/oKZjmrkguyw8YLcy7"
        )

        val shouldFallback = CommunicationUtils.shouldFallbackToLocationName(
            message = message,
            navigationLaunched = false,
            handledByMapsUrl = true
        )

        assertFalse(shouldFallback)
    }

    @Test
    fun extractGoogleMapsNavigationQuery_prefersCoordinatesOverPlaceName() {
        val url =
            "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194"

        val destination = Utils.extractGoogleMapsNavigationQuery(url)

        assertEquals("37.7749,-122.4194", destination)
    }

    @Test
    fun extractGoogleMapsCoordinates_supportsNestedLinkQuery() {
        val url =
            "https://www.google.com/maps?link=https%3A%2F%2Fwww.google.com%2Fmaps%2Fsearch%2F%3Fapi%3D1%26query%3D37.7749%2C-122.4194"

        val destination = Utils.extractGoogleMapsCoordinates(url)

        assertEquals("37.7749,-122.4194", destination)
    }

    @Test
    fun extractGoogleMapsNavigationQuery_returnsLabelWhenCoordinatesAbsent() {
        val url = "https://www.google.com/maps/search/?api=1&query=Coffee+Shop"

        val destination = Utils.extractGoogleMapsNavigationQuery(url)

        assertEquals("Coffee Shop", destination)
    }

    @Test
    fun populateCoordinatesFromUrl_extractsCoordinatesFromGoogleMapsUrl() {
        val message = MessageModel(
            locationName = "Coffee Shop",
            url = "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194"
        )

        CommunicationUtils.populateCoordinatesFromUrl(message)

        assertEquals("Coffee Shop", message.locationName)
        assertEquals("37.7749", message.lat)
        assertEquals("-122.4194", message.lng)
        assertTrue(message.url.contains("google.com/maps"))
    }

    @Test
    fun populateCoordinatesFromUrl_leavesNonMapsUrlWithoutCoordinates() {
        val message = MessageModel(
            locationName = "Coffee Shop",
            url = "https://example.com/coffee"
        )

        CommunicationUtils.populateCoordinatesFromUrl(message)

        assertEquals("Coffee Shop", message.locationName)
        assertEquals("", message.lat)
        assertEquals("", message.lng)
        assertEquals("https://example.com/coffee", message.url)
    }

    @Test
    fun parseGeoUri_extractsCoordinatesAndLabel() {
        val parsed = Utils.parseGeoString("geo:0,0?q=37.4219983,-122.084(Googleplex)")

        assertEquals(Triple("37.4219983", "-122.084", "Googleplex"), parsed)
    }

    @Test
    fun parseGeoUri_fallsBackToQueryTextWhenCoordinatesAbsent() {
        val parsed = Utils.parseGeoString("geo:0,0?q=Coffee%20Shop")

        assertEquals(Triple("", "", "Coffee Shop"), parsed)
    }

    @Test
    fun encodeSharedViewIntent_supportsGeoUris() {
        val encoded =
            CommunicationUtils.encodeSharedViewIntent("geo:0,0?q=37.4219983,-122.084(Googleplex)")

        val message = MessageModel().apply { fromJson(encoded.orEmpty()) }

        assertEquals("37.4219983", message.lat)
        assertEquals("-122.084", message.lng)
        assertEquals("Googleplex", message.locationName)
    }

    @Test
    fun encodeSharedViewIntent_supportsGoogleNavigationUris() {
        val encoded = CommunicationUtils.encodeSharedViewIntent("google.navigation:q=Coffee%20Shop")

        val message = MessageModel().apply { fromJson(encoded.orEmpty()) }

        assertEquals("Coffee Shop", message.locationName)
        assertTrue(message.lat.isEmpty())
        assertTrue(message.lng.isEmpty())
    }

    @Test
    fun encodeSharedViewIntent_supportsHttpMapLinks() {
        val encoded = CommunicationUtils.encodeSharedViewIntent(
            "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194"
        )

        val message = MessageModel().apply { fromJson(encoded.orEmpty()) }

        assertTrue(message.url.contains("google.com/maps"))
        assertEquals("37.7749", message.lat)
        assertEquals("-122.4194", message.lng)
    }

    @Test
    fun encodeSharedViewIntent_rejectsUnsupportedSchemes() {
        val encoded = CommunicationUtils.encodeSharedViewIntent("ftp://example.com/file")

        assertNull(encoded)
    }

    @Test
    fun parseGoogleNavigationString_extractsDestination() {
        val destination = Utils.parseGoogleNavigationString("google.navigation:q=Coffee%20Shop")

        assertEquals("Coffee Shop", destination)
    }

    @Test
    fun decodeSavedMessages_returnsEmptyListForMalformedJson() {
        val decoded = Utils.decodeSavedMessages("{\"message\":")

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun limitSavedMessages_capsHistoryAtThirtyItems() {
        val messages = (1..35).map { index ->
            MessageModel(locationName = "Place $index", receivedAt = index.toLong())
        }

        val limited = Utils.limitSavedMessages(messages)

        assertEquals(30, limited.size)
        assertEquals("Place 1", limited.first().locationName)
        assertEquals("Place 30", limited.last().locationName)
    }

    @Test
    fun csvHelpers_roundTripLegacyPhoneEntryFormat() {
        val encoded = Utils.listToCsv(listOf("123", "456"))

        val decoded = Utils.csvToList(encoded)

        assertEquals(listOf("123", "456"), decoded)
    }

    @Test
    fun normalizePhoneNumber_removesWhitespaceOnly() {
        val normalized = Utils.normalizePhoneNumber("555 123 4567")

        assertEquals("5551234567", normalized)
    }

    @Test
    fun sanitizePhoneEntries_deduplicatesNormalizedValues() {
        val sanitized = Utils.sanitizePhoneEntries(
            listOf(
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
    fun transportPreflight_validateBase_rejectsEmptyBody() {
        val result = TransportPreflight.validateBase("5551234", "")

        assertEquals(
            SendResult.Failure(
                reason = SendFailureReason.EMPTY_MESSAGE,
                userMessage = "Cannot send an empty message"
            ),
            result
        )
    }

    @Test
    fun transportPreflight_validateSmsDestination_rejectsInvalidNumbers() {
        val result = TransportPreflight.validateSmsDestination("invalid-number")

        assertEquals(
            SendResult.Failure(
                reason = SendFailureReason.INVALID_DESTINATION,
                userMessage = "Recipient phone number is invalid"
            ),
            result
        )
    }

    @Test
    fun decodePhoneEntries_keepsPhoneEntries() {
        val raw = """
            [
              {"label":"Driver One","number":"5551234567","type":"PHONE"},
              {"label":"Driver Two","number":"5551234568","type":"PHONE"}
            ]
        """.trimIndent()

        val decoded = Utils.decodePhoneEntries(raw)

        assertEquals(
            listOf(
                PhoneEntry(label = "Driver One", number = "5551234567", type = ContactType.PHONE),
                PhoneEntry(label = "Driver Two", number = "5551234568", type = ContactType.PHONE)
            ),
            decoded
        )
    }

    @Test
    fun decodePhoneEntries_filtersLegacyBluetoothEntries() {
        val raw = """
            [
              {"label":"Driver One","number":"5551234567","type":"PHONE"},
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
    fun decodePhoneEntries_defaultsUnknownTypeToPhone() {
        val raw = """
            [
              {"label":"Driver One","number":"5551234567","type":"SATELLITE"}
            ]
        """.trimIndent()

        val decoded = Utils.decodePhoneEntries(raw)

        assertEquals(
            listOf(PhoneEntry(label = "Driver One", number = "5551234567", type = ContactType.PHONE)),
            decoded
        )
    }

    @Test
    fun decodePhoneEntries_fallsBackToLegacyCsv() {
        val decoded = Utils.decodePhoneEntries("123-456")

        assertEquals(
            listOf(
                PhoneEntry(label = "123", number = "123", type = ContactType.PHONE),
                PhoneEntry(label = "456", number = "456", type = ContactType.PHONE)
            ),
            decoded
        )
    }
}
