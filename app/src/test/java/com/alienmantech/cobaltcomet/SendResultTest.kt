package com.alienmantech.cobaltcomet

import com.alienmantech.cobaltcomet.utils.SendFailureReason
import com.alienmantech.cobaltcomet.utils.SendResult
import com.alienmantech.cobaltcomet.utils.isSuccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendResultTest {
    @Test
    fun isSuccess_returnsTrueForSuccess() {
        assertTrue(SendResult.Success.isSuccess)
    }

    @Test
    fun isSuccess_returnsFalseForFailure() {
        val result = SendResult.Failure(
            reason = SendFailureReason.SEND_FAILED,
            userMessage = "SMS send failed. Please try again."
        )

        assertFalse(result.isSuccess)
    }
}
