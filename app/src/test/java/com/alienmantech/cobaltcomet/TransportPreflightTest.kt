package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.utils.SendFailureReason
import com.alienmantech.cobaltcomet.utils.SendResult
import com.alienmantech.cobaltcomet.utils.TransportPreflight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransportPreflightTest {
    @Test
    fun validateBase_rejectsMissingRecipient() {
        val result = TransportPreflight.validateBase("   ", "hello")

        assertEquals(
            SendResult.Failure(
                reason = SendFailureReason.NO_RECIPIENT,
                userMessage = "No recipient selected"
            ),
            result
        )
    }

    @Test
    fun validateBase_acceptsRecipientAndBody() {
        val result = TransportPreflight.validateBase("5551234567", "hello")

        assertNull(result)
    }

    @Test
    fun normalizeSmsDestination_trimsAndRemovesWhitespace() {
        val normalized = TransportPreflight.normalizeSmsDestination("  +1 555 123 4567  ")

        assertEquals("+15551234567", normalized)
    }

    @Test
    fun validateSmsDestination_acceptsValidNormalizedNumber() {
        val result = TransportPreflight.validateSmsDestination("  +1 555 123 4567  ")

        assertNull(result)
    }

    @Test
    fun validateSmsDestination_rejectsEmptyDestination() {
        val result = TransportPreflight.validateSmsDestination("   ")

        assertEquals(
            SendResult.Failure(
                reason = SendFailureReason.INVALID_DESTINATION,
                userMessage = "Recipient phone number is invalid"
            ),
            result
        )
    }
}
