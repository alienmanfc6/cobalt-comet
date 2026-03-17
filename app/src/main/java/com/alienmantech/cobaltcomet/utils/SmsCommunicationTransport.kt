package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.telephony.SmsManager
import android.text.TextUtils
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logError

class SmsCommunicationTransport {
    fun canHandle(target: String): Boolean {
        return !TextUtils.isEmpty(target)
    }

    @Synchronized
    fun send(context: Context, to: String, message: String): SendResult {
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
                ?: return SendResult.Failure(
                    reason = SendFailureReason.SEND_FAILED,
                    userMessage = "SMS is unavailable on this device."
                )
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            SendResult.Success
        } catch (e: Exception) {
            logError("SMS send failed", e)
            SendResult.Failure(
                reason = SendFailureReason.SEND_FAILED,
                userMessage = "SMS send failed. Please try again."
            )
        }
    }
}
