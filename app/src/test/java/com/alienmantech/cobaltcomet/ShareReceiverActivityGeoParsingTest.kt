package com.alienmantech.cobaltcomet

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareReceiverActivityGeoParsingTest {

    @Test
    fun parseGeoParts_prefersQCoordinatesWithLabel() {
        val result = parseGeoParts(
            schemeSpecificPart = "10.1,20.2",
            queryParameter = "40.4,50.5(Test Place)"
        )

        assertEquals(Triple("40.4", "50.5", "Test Place"), result)
    }

    @Test
    fun parseGeoParts_prefersQCoordinatesWithoutLabel() {
        val result = parseGeoParts(
            schemeSpecificPart = "10.1,20.2",
            queryParameter = "40.4,50.5"
        )

        assertEquals(Triple("40.4", "50.5", ""), result)
    }

    @Test
    fun parseGeoParts_keepsSchemeCoordinatesWhenQIsOnlyName() {
        val result = parseGeoParts(
            schemeSpecificPart = "10.1,20.2",
            queryParameter = "Place Name"
        )

        assertEquals(Triple("10.1", "20.2", "Place Name"), result)
    }

    @Test
    fun parseGeoParts_parsesPlainSchemeCoordinates() {
        val result = parseGeoParts(
            schemeSpecificPart = "10.1,20.2",
            queryParameter = null
        )

        assertEquals(Triple("10.1", "20.2", ""), result)
    }

    @Test
    fun parseGeoParts_keepsSchemeCoordinatesWithEncodedPlaceNameInQ() {
        val result = parseGeoParts(
            schemeSpecificPart = "10.1,20.2",
            queryParameter = "Place+Name"
        )

        assertEquals(Triple("10.1", "20.2", "Place Name"), result)
    }
}
