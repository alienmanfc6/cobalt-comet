package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationUtilsAdditionalTest {
    @Test
    fun preferredNavigationDestination_returnsNullWhenNoDestinationDataExists() {
        assertNull(CommunicationUtils.preferredNavigationDestination(MessageModel()))
    }

    @Test
    fun hasGoogleMapsUrl_matchesMapsAndNonMapsUrls() {
        val mapsMessage = MessageModel(url = "https://www.google.com/maps/search/?api=1&query=Coffee+Shop")
        val nonMapsMessage = MessageModel(url = "https://example.com/coffee")

        assertTrue(CommunicationUtils.hasGoogleMapsUrl(mapsMessage))
        assertFalse(CommunicationUtils.hasGoogleMapsUrl(nonMapsMessage))
    }

    @Test
    fun encodeUrlMessage_supportsMultilineTextAndTitlePrecedence() {
        val encoded = CommunicationUtils.encodeUrlMessage(
            title = "Pinned Title",
            text = "Meet here\nhttps://example.com/coffee\nBring snacks"
        )

        val message = MessageModel().apply { fromJson(encoded) }

        assertEquals("Pinned Title", message.locationName)
        assertEquals(listOf("Meet here", "Bring snacks"), message.textList)
        assertEquals("https://example.com/coffee", message.url)
    }

    @Test
    fun encodeUrlMessage_preservesTextOnlyPayloadWithoutUrl() {
        val encoded = CommunicationUtils.encodeUrlMessage(
            title = null,
            text = "Meet at the front desk"
        )

        val message = MessageModel().apply { fromJson(encoded) }

        assertEquals("Meet at the front desk", message.locationName)
        assertEquals(listOf("Meet at the front desk"), message.textList)
        assertTrue(message.url.isEmpty())
    }

    @Test
    fun parseMessagePayload_rejectsBlankAndTrailingGarbage() {
        assertNull(CommunicationUtils.parseMessagePayload("   "))
        assertNull(CommunicationUtils.parseMessagePayload("{\"url\":\"https://example.com\"}trailing"))
    }

    @Test
    fun shouldFallbackToLocationName_isFalseWhenNavigationAlreadyLaunchedOrLocationMissing() {
        val mapsMessage = MessageModel(
            locationName = "Lowe's Home Improvement",
            url = "https://maps.app.goo.gl/oKZjmrkguyw8YLcy7"
        )
        val missingLocation = MessageModel(url = "https://maps.app.goo.gl/oKZjmrkguyw8YLcy7")

        assertFalse(
            CommunicationUtils.shouldFallbackToLocationName(
                message = mapsMessage,
                navigationLaunched = true,
                handledByMapsUrl = false
            )
        )
        assertFalse(
            CommunicationUtils.shouldFallbackToLocationName(
                message = missingLocation,
                navigationLaunched = false,
                handledByMapsUrl = false
            )
        )
    }

    @Test
    fun encodeSharedViewIntent_supportsPlainHttpUrls() {
        val encoded = CommunicationUtils.encodeSharedViewIntent("https://example.com/coffee")

        val message = MessageModel().apply { fromJson(encoded.orEmpty()) }

        assertEquals("https://example.com/coffee", message.url)
        assertEquals("", message.lat)
        assertEquals("", message.lng)
    }

    @Test
    fun encodeSharedViewIntent_rejectsInvalidGeoAndNavigationPayloads() {
        assertNull(CommunicationUtils.encodeSharedViewIntent("geo:"))
        assertNull(CommunicationUtils.encodeSharedViewIntent("google.navigation:"))
    }
}
