package com.alienmantech.cobaltcomet.utils

object TransportPreflight {

    fun validateBase(to: String?, body: String): SendResult.Failure? {
        val recipient = to?.trim()
        if (recipient.isNullOrEmpty()) {
            return SendResult.Failure(
                reason = SendFailureReason.NO_RECIPIENT,
                userMessage = "No recipient selected"
            )
        }

        if (body.isBlank()) {
            return SendResult.Failure(
                reason = SendFailureReason.EMPTY_MESSAGE,
                userMessage = "Cannot send an empty message"
            )
        }

        return null
    }

    fun normalizeSmsDestination(to: String?): String? {
        return to?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Utils::normalizePhoneNumber)
            ?.takeIf(String::isNotEmpty)
    }

    fun validateSmsDestination(to: String?): SendResult.Failure? {
        val normalizedRecipient = normalizeSmsDestination(to)
        if (normalizedRecipient.isNullOrEmpty() || !Utils.isValidPhoneNumber(normalizedRecipient)) {
            return SendResult.Failure(
                reason = SendFailureReason.INVALID_DESTINATION,
                userMessage = "Recipient phone number is invalid"
            )
        }

        return null
    }
}
