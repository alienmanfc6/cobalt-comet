package com.alienmantech.cobaltcomet.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Logger

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            // get the SMS message passed in
            val bundle = intent.extras ?: return
            val pdus = bundle["pdus"] as Array<*>? ?: return
            val format = bundle.getString("format")

            var from = ""
            val messageBuilder = StringBuilder()

            val msgs = arrayOfNulls<SmsMessage>(pdus.size)
            for (i in msgs.indices) {
                // Android delivers each message part as a PDU byte array in "pdus".
                // On Marshmallow and above the radio technology used to encode the PDU
                // (3GPP for GSM/LTE or 3GPP2 for CDMA) is provided in the "format"
                // extra and must be forwarded to createFromPdu for correct decoding.
                // Older versions ignore the format flag and use the deprecated one-arg
                // overload instead, so we keep the branch for backward compatibility.
                msgs[i] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pdus[i] as ByteArray, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pdus[i] as ByteArray)
                }
                msgs[i]?.originatingAddress?.let {
                    if (it.isNotEmpty()) {
                        from = it
                    }
                }
                msgs[i]?.messageBody?.let {
                    if (it.isNotEmpty()) {
                        messageBuilder.append(it)
                    }
                }
            }

            val message = messageBuilder.toString()

            if (CommunicationUtils.shouldInterceptMessage(message)) {
                CommunicationUtils.handleIncomingMessage(context, from, message)
            }
        } catch (e: Exception) {
            Logger.logError("Unable to parse message.", e)
        }
    }
}