package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.telephony.SmsManager
import android.text.TextUtils
import com.alienmantech.cobaltcomet.models.MessageModel

class CommunicationUtils {

    companion object {
        const val SMS_PREFIX = "CobaltComet"

        fun sendMessage(to: String?, body: String) {
            to?.let {
                sendSms(it, body)
            }
        }

        // decide if this is a message we want to try to process
        fun shouldInterceptMessage(message: String): Boolean {
            return (message.startsWith(SMS_PREFIX))
        }

        // handle any incoming message
        fun handleIncomingMessage(context: Context, from: String, text: String) {
            Logger.logInfo("handleIncomingMessage: $text")

            decodeMessage(text)?.let { message ->
                if (message.url.isNotEmpty()) {
                    Utils.launchWebBrowser(context, message.url)
                }
                if (message.lat.isNotEmpty() && message.lng.isNotEmpty()) {
                    Utils.launchGoogleMapsNavigation(context, message.lat, message.lng)
                }
            }
        }

        fun encodeUrlMessage(text: String): String {
            val message = MessageModel()

            if (text.contains("\n")) {
                val lineArray = text.split("\n")
                for (i in lineArray.indices) {
                    val url = Utils.parseUrl(lineArray[i])
                    if (url != null) {
                        message.url = url
                    } else {
                        message.addText(lineArray[i])
                    }
                }
            } else {
                val url = Utils.parseUrl(text)
                if (url != null) {
                    message.url = url
                } else {
                    message.addText(text)
                }
            }

            return SMS_PREFIX + message.toString()
        }

        fun encodeGeoMessage(lat: String, lng: String): String {
            val message = MessageModel()
            message.lat = lat
            message.lng = lng
            return SMS_PREFIX + message.toString()
        }

        fun decodeMessage(text: String): MessageModel? {
            if (TextUtils.isEmpty(text)) {
                return null
            }

            val message = MessageModel()
            if (text.startsWith(SMS_PREFIX)) {
                message.fromJson(text.replace(SMS_PREFIX, ""))
            } else {
                message.fromJson(text)
            }
            return message
        }

        @Synchronized
        private fun sendSms(to: String, body: String): Boolean {
            return try {
                // replace any returns with \n which is a carage return for text
                val sm = SmsManager.getDefault()
                //sm.sendTextMessage(to, null, body, null, null);
                val parts = sm.divideMessage(body)
                sm.sendMultipartTextMessage(to, null, parts, null, null)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}