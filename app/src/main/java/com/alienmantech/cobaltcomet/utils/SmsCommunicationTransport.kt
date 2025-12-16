package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.telephony.SmsManager
import android.text.TextUtils
import android.widget.Toast

class SmsCommunicationTransport : CommunicationTransport {
    override fun canHandle(target: String): Boolean {
        return !TextUtils.isEmpty(target)
    }

    @Synchronized
    override fun send(context: Context, to: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Message send failed", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
