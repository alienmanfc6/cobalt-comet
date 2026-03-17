package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Utils
import org.junit.Assert.assertEquals
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

        assertTrue(!shouldFallback)
    }

    @Test
    fun extractGoogleMapsNavigationQuery_prefersCoordinatesOverPlaceName() {
        val url =
            "https://www.google.com/maps/search/?api=1&query=Coffee+Shop&ll=37.7749,-122.4194"

        val destination = Utils.extractGoogleMapsNavigationQuery(url)

        assertEquals("37.7749,-122.4194", destination)
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
}
