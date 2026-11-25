package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.utils.SmsCommunicationTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCommunicationTransportTest {
    @Test
    fun canHandle_returnsTrueForNonEmptyTarget() {
        assertTrue(SmsCommunicationTransport().canHandle("5551234567"))
    }

    @Test
    fun canHandle_returnsFalseForEmptyTarget() {
        assertFalse(SmsCommunicationTransport().canHandle(""))
    }
}
