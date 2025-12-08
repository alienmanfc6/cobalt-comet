package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.telephony.SmsManager
import android.text.TextUtils
import com.alienmantech.cobaltcomet.models.MessageModel
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.ktx.Firebase
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

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
            Thread {
                val sanitizedBody = body.ifEmpty { "" }
                val isSuccess = try {
                    val accessToken = getFcmAccessToken() ?: return@Thread onComplete(false)
                    val projectId = Firebase.app.options.projectId ?: return@Thread onComplete(false)
                    val payload = buildFcmPayload(to, sanitizedBody)

                    val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Authorization", "Bearer $accessToken")
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        doOutput = true
                    }

                    connection.outputStream.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                    }

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        val errorBody = try {
                            connection.errorStream?.bufferedReader()?.readText()
                        } catch (_: Exception) {
                            null
                        }
                        Logger.logWarn("Failed to send message via Firebase FCM v1. Code: $responseCode. $errorBody")
                        false
                    } else {
                        true
                    }
                } catch (e: Exception) {
                    Logger.logError("Failed to send message via Firebase", e)
                    false
                }

                onComplete(isSuccess)
            }.start()
        }

        private fun buildFcmPayload(to: String, body: String): String {
            val dataPayload = JSONObject().apply {
                put("body", body)
                put("timestamp", System.currentTimeMillis().toString())

                val senderId = Utils.loadFirebaseId(Firebase.app.applicationContext)
                if (!senderId.isNullOrEmpty()) {
                    put("from", senderId)
                }
            }

            val messagePayload = JSONObject().apply {
                put("token", to)
                put("data", dataPayload)
            }

            return JSONObject().apply {
                put("message", messagePayload)
            }.toString()
        }

        private fun getFcmAccessToken(): String? {
            val credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
            if (credentialsPath.isNullOrEmpty()) {
                Logger.logWarn("GOOGLE_APPLICATION_CREDENTIALS is not set; cannot send FCM messages.")
                return null
            }

            return try {
                FileInputStream(credentialsPath).use { stream ->
                    val credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
                    credentials.refreshIfExpired()
                    credentials.accessToken.tokenValue
                }
            } catch (e: Exception) {
                Logger.logError("Unable to read Firebase credentials for FCM.", e)
                null
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