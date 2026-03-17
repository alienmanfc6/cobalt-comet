package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.text.TextUtils
import android.widget.Toast
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.utils.Logger

class CommunicationUtils {
    companion object {
        const val SMS_PREFIX = "CobaltComet"

        fun sendMessage(context: Context, entry: PhoneEntry?, body: String): Boolean {
            if (entry == null) {
                Toast.makeText(context, "No recipient selected", Toast.LENGTH_SHORT).show()
                return false
            }

            val dispatcher = TransportDispatcher()
            return dispatcher.dispatch(context, entry, body)
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
            val preferMapsUrl = shouldPreferGoogleMapsUrl(message)
            var handledByMapsUrl = false

            if (!preferMapsUrl) {
                preferredNavigationDestination(message)?.let { destination ->
                    navigationLaunched = Utils.launchGoogleMapsNavigation(context, destination)
                }
            }

            if (message.url.isNotEmpty()) {
                handledByMapsUrl = if (!navigationLaunched || preferMapsUrl) {
                    Utils.tryLaunchGoogleMapsFromUrl(context, message.url)
                } else {
                    false
                }

                if (!handledByMapsUrl) {
                    if (!navigationLaunched || !Utils.isGoogleMapsUrl(message.url)) {
                        Utils.launchWebBrowser(context, message.url)
                    }
                }
            }

            if (shouldFallbackToLocationName(message, navigationLaunched, handledByMapsUrl)) {
                Utils.launchGoogleMapsNavigation(context, message.locationName)
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

            populateCoordinatesFromUrl(message)

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

        internal fun preferredNavigationDestination(message: MessageModel): String? {
            return when {
                message.lat.isNotEmpty() && message.lng.isNotEmpty() -> "${message.lat},${message.lng}"
                message.locationName.isNotEmpty() -> message.locationName
                else -> null
            }
        }

        internal fun populateCoordinatesFromUrl(message: MessageModel) {
            if (message.url.isEmpty()) {
                return
            }

            Utils.extractGoogleMapsCoordinates(message.url)?.split(",")?.let { coordinates ->
                if (coordinates.size >= 2) {
                    message.lat = coordinates[0]
                    message.lng = coordinates[1]
                }
            }
        }

        internal fun shouldPreferGoogleMapsUrl(message: MessageModel): Boolean {
            return message.lat.isEmpty() &&
                message.lng.isEmpty() &&
                message.url.isNotEmpty() &&
                Utils.isGoogleMapsUrl(message.url)
        }

        internal fun shouldFallbackToLocationName(
            message: MessageModel,
            navigationLaunched: Boolean,
            handledByMapsUrl: Boolean
        ): Boolean {
            return shouldPreferGoogleMapsUrl(message) &&
                !navigationLaunched &&
                !handledByMapsUrl &&
                message.locationName.isNotEmpty()
        }

    }
}
