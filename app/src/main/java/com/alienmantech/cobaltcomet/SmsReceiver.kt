package com.alienmantech.cobaltcomet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            // get the SMS message passed in
            val bundle = intent.extras ?: return
            val pdus = bundle["pdus"] as Array<*>? ?: return

            var from = ""
            var message = ""

            val msgs = arrayOfNulls<SmsMessage>(pdus.size)
            for (i in msgs.indices) {
                msgs[i] = SmsMessage.createFromPdu(pdus[i] as ByteArray)
                msgs[i]?.originatingAddress?.let {
                    if (it.isNotEmpty()) {
                        from = it
                    }
                }
                msgs[i]?.messageBody?.let {
                    if (it.isNotEmpty()) {
                        message = it
                    }
                }
            }

            if (CommunicationUtils.shouldInterceptMessage(message)) {
                CommunicationUtils.handleIncomingMessage(context, from, message)
            }
        } catch (e: Exception) {
            Utils.logError("Unable to parse message.", e)
        }
    }
}