package com.alienmantech.cobaltcomet.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Logger
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        try {
            var from = ""
            val messageBuilder = StringBuilder()

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (messagePart in messages) {
                val decodedMessage = if (messagePart != null) {
                    messagePart
                } else {
                    continue
                }
                decodedMessage.originatingAddress?.let {
                    if (it.isNotEmpty()) {
                        from = it
                    }
                }
                decodedMessage.messageBody?.let {
                    if (it.isNotEmpty()) {
                        messageBuilder.append(it)
                    }
                }
            }

            val message = messageBuilder.toString()
            if (message.isBlank()) {
                logWarn("Ignoring empty SMS payload")
                return
            }

            if (CommunicationUtils.shouldInterceptMessage(message)) {
                CommunicationUtils.handleIncomingMessage(context, from, message)
            }
        } catch (e: Exception) {
            Logger.logError("Unable to parse message.", e)
        }
    }
}
