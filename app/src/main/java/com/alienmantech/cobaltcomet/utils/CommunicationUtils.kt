package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.telephony.SmsManager
import android.text.TextUtils
import com.alienmantech.cobaltcomet.models.MessageModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

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
                var navigationLaunched = false
                if (message.lat.isNotEmpty() && message.lng.isNotEmpty()) {
                    navigationLaunched =
                        Utils.launchGoogleMapsNavigation(context, message.lat, message.lng)
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

        fun encodeGeoMessage(lat: String, lng: String): String {
            val message = MessageModel()
            message.lat = lat
            message.lng = lng
            return SMS_PREFIX + message.toString()
        }

        fun sendFirebaseMessage(to: String, body: String, onComplete: (Boolean) -> Unit = {}) {
            val sanitizedBody = body.ifEmpty { "" }

            val payload = mapOf(
                "to" to to,
                "body" to sanitizedBody,
                "timestamp" to System.currentTimeMillis()
            )

            Firebase.firestore.collection("messages")
                .add(payload)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener {
                    Logger.logError("Failed to send message via Firebase", it)
                    onComplete(false)
                }
        }

        fun decodeMessage(text: String): MessageModel? {
            if (TextUtils.isEmpty(text)) {
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

            val message = MessageModel()
            message.fromJson(jsonPart)

            if (message.url.isEmpty() && !extraUrl.isNullOrEmpty()) {
                message.url = extraUrl
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