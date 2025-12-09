package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.telephony.SmsManager
import android.text.TextUtils
import android.widget.Toast
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.utils.Logger

class CommunicationUtils {
    companion object {
        const val SMS_PREFIX = "CobaltComet"

        fun sendMessage(context: Context, to: String?, body: String): Boolean {
            val recipient = to?.trim()
            if (recipient.isNullOrEmpty()) {
                Toast.makeText(context, "No phone number selected", Toast.LENGTH_SHORT).show()
                return false
            }

            return sendSms(context, recipient, body)
        }

        // decide if this is a message we want to try to process
        fun shouldInterceptMessage(message: String): Boolean {
            return (message.startsWith(SMS_PREFIX))
        }

        // handle any incoming message
        fun handleIncomingMessage(context: Context, from: String, text: String) {
            Logger.logInfo("handleIncomingMessage: $text")

            decodeMessage(context, text)?.let { message ->
                message.from = from
                message.receivedAt = System.currentTimeMillis()
                Utils.saveMessage(context, message)
                handleMessageAction(context, message)
            }
        }

        fun handleMessageAction(context: Context, message: MessageModel) {
            var navigationLaunched = false
            if (message.lat.isNotEmpty() && message.lng.isNotEmpty()) {
                val destination = if (message.locationName.isNotEmpty()) {
                    "${message.lat},${message.lng}(${message.locationName})"
                } else {
                    "${message.lat},${message.lng}"
                }
                navigationLaunched =
                    Utils.launchGoogleMapsNavigation(context, destination)
            } else if (message.locationName.isNotEmpty()) {
                navigationLaunched = Utils.launchGoogleMapsNavigation(context, message.locationName)
            }

            if (message.url.isNotEmpty()) {
                val handledByMaps = if (!navigationLaunched) {
                    Utils.tryLaunchGoogleMapsFromUrl(context, message.url)
                } else {
                    false
                }

                if (!handledByMaps) {
                    if (!navigationLaunched || !Utils.isGoogleMapsUrl(message.url)) {
                        Utils.launchWebBrowser(context, message.url)
                    }
                }
            }
        }

        fun encodeUrlMessage(title: String?, text: String?): String {
            val message = MessageModel()

            if (text?.contains("\n") == true) {
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

            if (message.locationName.isEmpty()) {
                message.locationName = message.textList.firstOrNull().orEmpty()
            }

            if (title?.isNotEmpty() == true) {
                message.locationName = title
            }

            return buildString {
                append(SMS_PREFIX)
                append(message.toString())

                // include the url as plain text to keep the map link in the same SMS message
                if (message.url.isNotEmpty()) {
                    append("\n")
                    append(message.url)
                }
            }
        }

        fun encodeGeoMessage(lat: String, lng: String, locationName: String = ""): String {
            val message = MessageModel()
            message.lat = lat
            message.lng = lng
            message.locationName = locationName
            return SMS_PREFIX + message.toString()
        }

        fun decodeMessage(context: Context, text: String): MessageModel? {
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(context, "Received empty message", Toast.LENGTH_SHORT).show()
                return null
            }

            val content = if (text.startsWith(SMS_PREFIX)) {
                text.replaceFirst(SMS_PREFIX, "")
            } else {
                text
            }

            val parts = content.split("\n", limit = 2)
            val jsonPart = parts.getOrNull(0) ?: return null
            val extraUrl = parts.getOrNull(1)

            return try {
                val message = MessageModel()
                message.fromJson(jsonPart)

                if (message.url.isEmpty() && !extraUrl.isNullOrEmpty()) {
                    message.url = extraUrl
                }

                message
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't read shared message", Toast.LENGTH_SHORT).show()
                null
            }
        }

        @Synchronized
        private fun sendSms(context: Context, to: String, body: String): Boolean {
            return try {
                // replace any returns with \n which is a carage return for text
                val sm = SmsManager.getDefault()
                //sm.sendTextMessage(to, null, body, null, null);
                val parts = sm.divideMessage(body)
                sm.sendMultipartTextMessage(to, null, parts, null, null)
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Message send failed", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }
}