package com.alienmantech.cobaltcomet.utils

sealed interface SendResult {
    data object Success : SendResult

    data class Failure(
        val reason: SendFailureReason,
        val userMessage: String
    ) : SendResult
}

enum class SendFailureReason {
    NO_RECIPIENT,
    EMPTY_MESSAGE,
    MISSING_PERMISSION,
    INVALID_DESTINATION,
    SEND_FAILED
}

val SendResult.isSuccess: Boolean
    get() = this is SendResult.Success
