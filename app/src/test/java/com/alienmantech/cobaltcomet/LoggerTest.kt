package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.utils.Logger
import org.junit.Assert.assertEquals
import org.junit.Test

class LoggerTest {
    @Test
    fun maskIdentifier_returnsUnknownForNullAndBlankValues() {
        assertEquals("unknown", Logger.maskIdentifier(null))
        assertEquals("unknown", Logger.maskIdentifier("   "))
    }

    @Test
    fun maskIdentifier_masksShortIdentifiersCompletely() {
        assertEquals("****", Logger.maskIdentifier("1234"))
        assertEquals("****", Logger.maskIdentifier("123"))
    }

    @Test
    fun maskIdentifier_preservesLastFourCharactersForLongIdentifiers() {
        assertEquals("****5678", Logger.maskIdentifier("12345678"))
        assertEquals("******3456", Logger.maskIdentifier("  1234563456  "))
    }
}
