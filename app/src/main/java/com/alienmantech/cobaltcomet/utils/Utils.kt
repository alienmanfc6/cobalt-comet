package com.alienmantech.cobaltcomet.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Patterns
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logError
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class Utils {
    companion object {

        private const val PREF_FILE_NAME = "PrefFile"
        private const val PREF_PHONE_NUMBER = "phone"
        private const val PREF_MESSAGES = "messages"
        private const val MAX_SAVED_MESSAGES = 30
        private const val MAPS_RESOLUTION_CONNECT_TIMEOUT_MS = 3000
        private const val MAPS_RESOLUTION_READ_TIMEOUT_MS = 3000
        private const val MAPS_RESOLUTION_MAX_REDIRECTS = 4

        private const val PHONE_NUMBER_DELIM = "-"

        fun getSavePref(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        }

        fun loadPhoneNumbers(context: Context): List<PhoneEntry> {
            return sanitizePhoneEntries(
                decodePhoneEntries(getSavePref(context).getString(PREF_PHONE_NUMBER, null))
            )
        }

        fun savePhoneNumbers(context: Context, phoneNumber: List<PhoneEntry>) {
            getSavePref(context).edit()
                .putString(PREF_PHONE_NUMBER, encodePhoneEntries(sanitizePhoneEntries(phoneNumber)))
                .apply()
        }

        fun encodePhoneEntries(entries: List<PhoneEntry>): String {
            val array = JSONArray()
            for (entry in entries) {
                array.put(entry.toJson())
            }
            return array.toString()
        }

        fun decodePhoneEntries(raw: String?): List<PhoneEntry> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }

            if (!raw.trim().startsWith("[")) {
                return csvToList(raw).map { number ->
                    PhoneEntry(label = number, number = number)
                }
            }

            try {
                val parsed = mutableListOf<PhoneEntry>()
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val jsonObject = array.optJSONObject(i) ?: continue
                    PhoneEntry.fromJson(jsonObject)?.let { parsed.add(it) }
                }
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            } catch (e: Exception) {
                logWarn("Unable to decode saved phone entries; falling back to legacy format", e)
            }

            // Fallback for legacy CSV data.
            return csvToList(raw).map { number ->
                PhoneEntry(label = number, number = number)
            }
        }

        fun getContactName(context: Context, phoneNumber: String?): String? {
            if (phoneNumber.isNullOrBlank()) {
                return null
            }

            if (!hasContactsPermission(context)) {
                return null
            }

            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            val lookupUri = Uri.withAppendedPath(uri, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            return context.contentResolver.query(lookupUri, projection, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }
        }

        fun loadMessages(context: Context): List<MessageModel> {
            return decodeSavedMessages(getSavePref(context).getString(PREF_MESSAGES, null))
        }

        fun saveMessage(context: Context, message: MessageModel) {
            val existing = loadMessages(context).toMutableList()
            existing.add(0, message)

            getSavePref(context).edit()
                .putString(PREF_MESSAGES, encodeSavedMessages(limitSavedMessages(existing)))
                .apply()
        }

        internal fun decodeSavedMessages(raw: String?): List<MessageModel> {
            if (raw.isNullOrBlank()) {
                return emptyList()
            }

            return try {
                val array = JSONArray(raw)
                val messages = mutableListOf<MessageModel>()
                for (i in 0 until array.length()) {
                    val jsonObject = array.optJSONObject(i) ?: continue
                    val message = MessageModel()
                    message.fromJson(jsonObject)
                    messages.add(message)
                }
                messages
            } catch (e: Exception) {
                logError("Unable to load saved messages", e)
                emptyList()
            }
        }

        internal fun encodeSavedMessages(messages: List<MessageModel>): String {
            val array = JSONArray()
            for (item in messages) {
                array.put(item.toJson())
            }
            return array.toString()
        }

        internal fun limitSavedMessages(messages: List<MessageModel>): List<MessageModel> {
            return messages.take(MAX_SAVED_MESSAGES)
        }

        fun parseUrl(text: String?): String? {
            if (text == null) {
                return null
            }
            val index = text.indexOf("http")
            if (index == -1) {
                return null
            }

            val after = text.substring(index)
            val endOfUrl = after.indexOf(" ")

            return if (endOfUrl != -1) {
                after.substring(0, endOfUrl)
            } else {
                after
            }
        }

        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private val COORDINATE_REGEX = Regex("(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")

        fun launchGoogleMaps(context: Context, lat: String, lng: String) {
            val gmmIntentUri = Uri.parse("geo:$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage(GOOGLE_MAPS_PACKAGE)
            mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            safeStartActivity(context, mapIntent)
        }

        fun launchGoogleMapsNavigation(context: Context, lat: String, lng: String): Boolean {
            return launchGoogleMapsNavigation(context, "$lat,$lng")
        }

        fun launchGoogleMapsNavigation(context: Context, destination: String): Boolean {
            val encodedDestination = Uri.encode(destination)
            val gmmIntentUri = Uri.parse("google.navigation:q=$encodedDestination")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage(GOOGLE_MAPS_PACKAGE)
            mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            return if (safeStartActivity(context, mapIntent)) {
                true
            } else {
                val fallbackUrl = "https://www.google.com/maps/dir/?api=1&destination=$encodedDestination"
                if (!launchGoogleMapsUrl(context, fallbackUrl)) {
                    launchWebBrowser(context, fallbackUrl)
                }
                true
            }
        }

        fun launchGoogleMapsUrl(context: Context, url: String): Boolean {
            return try {
                val uri = Uri.parse(url)
                val prefersUnpackagedIntent = shouldLaunchMapsUrlWithoutPackage(url)
                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (!prefersUnpackagedIntent) {
                        setPackage(GOOGLE_MAPS_PACKAGE)
                    }
                }

                if (safeStartActivity(context, mapIntent)) {
                    true
                } else if (!prefersUnpackagedIntent) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (safeStartActivity(context, fallbackIntent)) {
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                logError("Unable to launch Google Maps link", e)
                false
            }
        }

        fun tryLaunchGoogleMapsFromUrl(context: Context, url: String): Boolean {
            if (!isGoogleMapsUrl(url)) {
                return false
            }

            extractGoogleMapsNavigationQuery(url)?.let { destination ->
                launchGoogleMapsNavigation(context, destination)
                return true
            }

            if (launchGoogleMapsUrl(context, url)) {
                return true
            }

            launchWebBrowser(context, url)
            return true
        }

        suspend fun resolveGoogleMapsNavigationDestination(url: String): String? {
            return withContext(Dispatchers.IO) {
                val initialUri = runCatching { URI(url) }.getOrNull() ?: return@withContext null
                val initialScheme = initialUri.scheme?.lowercase(Locale.US)
                if (initialScheme != "http" && initialScheme != "https") {
                    return@withContext null
                }

                var currentUrl = url
                repeat(MAPS_RESOLUTION_MAX_REDIRECTS) {
                    val connection = (URL(currentUrl).openConnection() as? HttpURLConnection) ?: return@withContext null
                    try {
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = MAPS_RESOLUTION_CONNECT_TIMEOUT_MS
                        connection.readTimeout = MAPS_RESOLUTION_READ_TIMEOUT_MS
                        connection.requestMethod = "GET"
                        connection.connect()

                        val redirectLocation = connection.getHeaderField("Location")
                        if (connection.responseCode in 300..399 && !redirectLocation.isNullOrBlank()) {
                            currentUrl = URL(URL(currentUrl), redirectLocation).toString()
                            return@repeat
                        }

                        val resolvedUrl = connection.url?.toString().orEmpty().ifBlank { currentUrl }
                        extractGoogleMapsCoordinates(resolvedUrl)?.let { return@withContext it }
                        extractGoogleMapsNavigationQuery(resolvedUrl)?.let { return@withContext it }
                        return@withContext null
                    } catch (e: Exception) {
                        logWarn("Unable to resolve Google Maps short url: $currentUrl", e)
                        return@withContext null
                    } finally {
                        connection.disconnect()
                    }
                }

                extractGoogleMapsCoordinates(currentUrl) ?: extractGoogleMapsNavigationQuery(currentUrl)
            }
        }

        fun isGoogleMapsUrl(url: String): Boolean {
            return try {
                val uri = URI(url)
                val host = uri.host?.lowercase(Locale.US) ?: return false

                if (host == "maps.app.goo.gl") {
                    return true
                }

                if (host == "goo.gl" && uri.path?.startsWith("/maps") == true) {
                    return true
                }

                if (host.endsWith("google.com")) {
                    val path = uri.path ?: ""
                    return path.startsWith("/maps") || path.startsWith("/maps/dir") || path.startsWith("/maps/place")
                }

                false
            } catch (e: Exception) {
                logWarn("Unable to determine if url is Google Maps: $url")
                false
            }
        }

        internal fun shouldLaunchMapsUrlWithoutPackage(url: String): Boolean {
            return try {
                val uri = URI(url)
                val host = uri.host?.lowercase(Locale.US) ?: return false
                host == "maps.app.goo.gl" || (host == "goo.gl" && uri.path?.startsWith("/maps") == true)
            } catch (e: Exception) {
                false
            }
        }

        fun extractGoogleMapsCoordinates(url: String): String? {
            return try {
                val uri = URI(url)

                extractCoordinatesFromGoogleMapsUri(uri)?.let { return it }

                getQueryParameter(uri, "link")?.let { nested ->
                    val decoded = decodeUrlComponent(nested)
                    if (decoded != url && isGoogleMapsUrl(decoded)) {
                        extractGoogleMapsCoordinates(decoded)?.let { return it }
                    }
                }

                null
            } catch (e: Exception) {
                logWarn("Unable to parse coordinates from url: $url")
                null
            }
        }

        fun extractGoogleMapsNavigationQuery(url: String): String? {
            return try {
                val uri = URI(url)

                extractCoordinatesFromGoogleMapsUri(uri)?.let { return it }

                val candidateParameters = listOf("q", "query", "destination", "daddr", "ll")
                for (parameter in candidateParameters) {
                    val value = getQueryParameter(uri, parameter)
                    if (!value.isNullOrBlank()) {
                        extractLatLngFromText(value)?.let { return it }
                        return value
                    }
                }

                uri.rawPath?.let { path ->
                    extractLatLngFromText(path)?.let { return it }
                }

                uri.fragment?.let { fragment ->
                    extractLatLngFromText(fragment)?.let { return it }
                }

                getQueryParameter(uri, "link")?.let { nested ->
                    val decoded = decodeUrlComponent(nested)
                    if (decoded != url && isGoogleMapsUrl(decoded)) {
                        extractGoogleMapsNavigationQuery(decoded)?.let { return it }
                    }
                }

                null
            } catch (e: Exception) {
                logWarn("Unable to parse navigation destination from url: $url")
                null
            }
        }

        private fun extractCoordinatesFromGoogleMapsUri(uri: URI): String? {
            val coordinateParameters = listOf("ll", "q", "query", "destination", "daddr")
            for (parameter in coordinateParameters) {
                extractLatLngFromText(getQueryParameter(uri, parameter).orEmpty())?.let { return it }
            }

            uri.rawPath?.let { path ->
                extractLatLngFromText(path)?.let { return it }
            }

            uri.fragment?.let { fragment ->
                extractLatLngFromText(fragment)?.let { return it }
            }

            return null
        }

        private fun extractLatLngFromText(text: String): String? {
            val match = COORDINATE_REGEX.find(text)
            return if (match != null && match.groupValues.size >= 3) {
                "${match.groupValues[1]},${match.groupValues[2]}"
            } else {
                null
            }
        }

        private fun getQueryParameter(uri: URI, name: String): String? {
            val query = uri.rawQuery ?: return null
            return query.split("&").asSequence()
                .mapNotNull { segment ->
                    val index = segment.indexOf("=")
                    val key = if (index >= 0) segment.substring(0, index) else segment
                    if (decodeUrlComponent(key) != name) {
                        null
                    } else if (index >= 0) {
                        decodeUrlComponent(segment.substring(index + 1))
                    } else {
                        ""
                    }
                }
                .firstOrNull()
        }

        private fun decodeUrlComponent(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

        internal fun parseGoogleNavigationUri(data: Uri): String? {
            return parseGoogleNavigationString(data.toString())
        }

        internal fun parseGoogleNavigationString(raw: String): String? {
            val inlineQuery = raw.substringAfter("q=", missingDelimiterValue = "")
                .substringBefore("&")
                .takeIf(String::isNotBlank)
            val schemeSpecificDestination = raw.substringAfter("google.navigation:", missingDelimiterValue = "")
                .substringBefore("?")
                .takeIf(String::isNotBlank)

            val destination = inlineQuery ?: schemeSpecificDestination ?: return null
            return decodeUrlComponent(destination).trim().ifEmpty { null }
        }

        fun launchWebBrowser(context: Context, url: String) {
            try {
                if (!TextUtils.isEmpty(url)) {
                    val intent = Intent(Intent.ACTION_VIEW)

                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        intent.data = Uri.parse("http://$url")
                    } else {
                        intent.data = Uri.parse(url)
                    }

                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    safeStartActivity(context, intent)
                } else {
                    logWarn("Url is empty")
                }
            } catch (e: Exception) {
                logError("Unable to launch url link", e)
            }
        }

        fun isYelpShareLink(data: String): Boolean {
            return data.contains("://yelp")
        }

        internal fun parseGeoUri(data: Uri): Triple<String, String, String> {
            return parseGeoString(data.toString())
        }

        internal fun parseGeoString(raw: String): Triple<String, String, String> {
            val query = raw.substringAfter("?", "")
            val queryParameter = query.split("&")
                .firstOrNull { it.startsWith("q=") }
                ?.substringAfter("=")
            if (!queryParameter.isNullOrBlank()) {
                val decoded = decodeUrlComponent(queryParameter)
                val labelStart = decoded.indexOf("(")
                val labelEnd = decoded.lastIndexOf(")")
                val hasLabel = labelStart != -1 && labelEnd > labelStart

                val coordinatePart = if (hasLabel) decoded.substring(0, labelStart) else decoded
                val label = if (hasLabel) decoded.substring(labelStart + 1, labelEnd) else ""

                val coords = coordinatePart.split(",")
                if (coords.size >= 2 && coords[0].isNotBlank() && coords[1].isNotBlank()) {
                    return Triple(coords[0], coords[1], label)
                }

                if (coordinatePart.isNotBlank()) {
                    return Triple("", "", coordinatePart)
                }
            }

            val schemeSpecificPart = raw.substringAfter("geo:", "").substringBefore("?")
            val values = schemeSpecificPart.split(",")
            return if (values.size >= 2 && values[0].isNotBlank() && values[1].isNotBlank()) {
                Triple(values[0], values[1], "")
            } else {
                Triple("", "", "")
            }
        }

        fun csvToList(string: String): List<String> {
            return if (string.contains(PHONE_NUMBER_DELIM))
                string.split(PHONE_NUMBER_DELIM).toList()
            else listOf(string)
        }

        fun listToCsv(list: List<String>): String {
            val sb = StringBuilder()
            for (item in list) {
                if (sb.isNotEmpty()) {
                    sb.append(PHONE_NUMBER_DELIM)
                }
                sb.append(item)
            }
            return sb.toString()
        }

        fun normalizePhoneNumber(rawNumber: String): String {
            return rawNumber.replace("\\s".toRegex(), "")
        }

        fun isValidPhoneNumber(rawNumber: String): Boolean {
            if (rawNumber.isBlank()) {
                return false
            }

            val androidPattern = Patterns.PHONE
            if (androidPattern != null) {
                return androidPattern.matcher(rawNumber).matches()
            }

            val digitsOnly = rawNumber.filter(Char::isDigit)
            return digitsOnly.length >= 3 &&
                rawNumber.all { character ->
                    character.isDigit() || character == '+' || character == '-' ||
                        character == ' ' || character == '(' || character == ')' || character == '.'
                }
        }

        fun formatReceivedAt(timestamp: Long, nowMillis: Long = System.currentTimeMillis()): String? {
            if (timestamp <= 0) return null

            val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
            val receivedTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())

            val duration = Duration.between(receivedTime, now)
            val relativeText = when {
                duration.toMinutes() < 1 -> "Just now"
                duration.toMinutes() == 1L -> "1 minute ago"
                duration.toHours() < 1 -> "${duration.toMinutes()} minutes ago"
                duration.toHours() == 1L -> "1 hour ago"
                duration.toDays() < 1 -> "${duration.toHours()} hours ago"
                duration.toDays() == 1L -> "1 day ago"
                else -> "${duration.toDays()} days ago"
            }

            val dayOfWeek = receivedTime.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val dayOfMonth = formatDayWithSuffix(receivedTime.dayOfMonth)
            val timeFormatter = DateTimeFormatter.ofPattern("h:mma", Locale.getDefault())
            val timeText = receivedTime.format(timeFormatter).lowercase(Locale.getDefault())

            return "$relativeText - $dayOfWeek $dayOfMonth at $timeText"
        }

        private fun formatDayWithSuffix(day: Int): String {
            val suffix = when {
                day in 11..13 -> "th"
                day % 10 == 1 -> "st"
                day % 10 == 2 -> "nd"
                day % 10 == 3 -> "rd"
                else -> "th"
            }
            return "$day$suffix"
        }

        fun hasSmsPermissions(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasContactsPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        }

        internal fun sanitizePhoneEntries(entries: List<PhoneEntry>): List<PhoneEntry> {
            val seen = linkedSetOf<String>()
            return entries.filter { entry ->
                val normalizedNumber = normalizePhoneNumber(entry.number)
                if (entry.label.isBlank() || normalizedNumber.isBlank()) {
                    return@filter false
                }

                val key = "${entry.type.name}|${entry.label.trim()}|$normalizedNumber"
                seen.add(key)
            }.map { entry ->
                entry.copy(number = normalizePhoneNumber(entry.number))
            }
        }

        private fun safeStartActivity(context: Context, intent: Intent): Boolean {
            return try {
                if (intent.resolveActivity(context.packageManager) == null) {
                    false
                } else {
                    context.startActivity(intent)
                    true
                }
            } catch (e: Exception) {
                val target = intent.data?.toString().orEmpty().ifBlank { intent.action.orEmpty() }
                logError("Unable to launch activity for $target", e)
                false
            }
        }
    }
}
