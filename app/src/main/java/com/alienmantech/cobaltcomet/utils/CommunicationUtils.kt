package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logInfo
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn
import com.alienmantech.cobaltcomet.utils.Logger.Companion.maskIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

class CommunicationUtils {
    companion object {
        private val backgroundActionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        fun sendMessage(context: Context, entry: PhoneEntry?, body: String): Boolean {
            if (entry == null) {
                Toast.makeText(context, "No recipient selected", Toast.LENGTH_SHORT).show()
                return false
            }

            if (!Utils.hasSmsPermissions(context)) {
                Toast.makeText(
                    context,
                    "SMS permission is required before sending messages.",
                    Toast.LENGTH_SHORT
                ).show()
                return false
            }

            TransportPreflight.validateBase(entry.number, body)?.let { failure ->
                Toast.makeText(context, failure.userMessage, Toast.LENGTH_SHORT).show()
                return false
            }

            val normalizedRecipient = TransportPreflight.normalizeSmsDestination(entry.number)
            TransportPreflight.validateSmsDestination(normalizedRecipient)?.let { failure ->
                Toast.makeText(context, failure.userMessage, Toast.LENGTH_SHORT).show()
                return false
            }

            val result = SmsCommunicationTransport().send(context, normalizedRecipient.orEmpty(), body)
            if (result is SendResult.Failure) {
                Toast.makeText(context, result.userMessage, Toast.LENGTH_SHORT).show()
            }
            return result.isSuccess
        }

        // decide if this is a message we want to try to process
        fun shouldInterceptMessage(message: String): Boolean {
            return parseMessagePayload(message) != null
        }

        // handle any incoming message
        fun handleIncomingMessage(context: Context, from: String, text: String) {
            logInfo("incoming message intercepted from=${maskIdentifier(from)}")

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

            if (preferMapsUrl && Utils.shouldLaunchMapsUrlWithoutPackage(message.url)) {
                launchInContextScope(context) {
                    val destination = Utils.resolveGoogleMapsNavigationDestination(message.url)
                    if (!destination.isNullOrEmpty()) {
                        Utils.launchGoogleMapsNavigation(context, destination)
                    } else if (!Utils.tryLaunchGoogleMapsFromUrl(context, message.url) &&
                        message.locationName.isNotEmpty()
                    ) {
                        Utils.launchGoogleMapsNavigation(context, message.locationName)
                    }
                }
                return
            }

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

            return message.toString()
        }

        fun encodeGeoMessage(lat: String, lng: String, locationName: String = ""): String {
            val message = MessageModel()
            message.lat = lat
            message.lng = lng
            message.locationName = locationName
            return message.toString()
        }

        internal fun encodeSharedViewIntent(uri: Uri): String? {
            return encodeSharedViewIntent(uri.toString())
        }

        internal fun encodeSharedViewIntent(rawUri: String): String? {
            val scheme = runCatching {
                java.net.URI(rawUri).scheme?.lowercase()
            }.getOrNull()

            return when (scheme) {
                "geo" -> {
                    val (lat, lng, locationName) = Utils.parseGeoString(rawUri)
                    when {
                        lat.isNotEmpty() && lng.isNotEmpty() ->
                            encodeGeoMessage(lat, lng, locationName)
                        locationName.isNotEmpty() ->
                            encodeGeoMessage("", "", locationName)
                        else -> null
                    }
                }
                "google.navigation" -> {
                    val destination = Utils.parseGoogleNavigationString(rawUri) ?: return null
                    encodeGeoMessage("", "", destination)
                }
                "http", "https" -> encodeUrlMessage(null, rawUri)
                else -> null
            }
        }

        fun decodeMessage(context: Context, text: String): MessageModel? {
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(context, "Received empty message", Toast.LENGTH_SHORT).show()
                return null
            }

            return parseMessagePayload(text) ?: run {
                logWarn("Unable to decode shared message from=${maskIdentifier(null)}")
                Toast.makeText(context, "Couldn't read shared message", Toast.LENGTH_SHORT).show()
                null
            }
        }

        internal fun parseMessagePayload(text: String): MessageModel? {
            if (text.isBlank()) {
                return null
            }

            return try {
                val tokener = JSONTokener(text)
                val json = tokener.nextValue() as? JSONObject ?: return null
                if (tokener.nextClean() != 0.toChar()) {
                    return null
                }
                MessageModel().apply { fromJson(json) }
            } catch (_: Exception) {
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

        private fun launchInContextScope(context: Context, block: suspend () -> Unit) {
            val scope = (context as? LifecycleOwner)?.lifecycleScope ?: backgroundActionScope
            scope.launch { block() }
        }

    }
}
