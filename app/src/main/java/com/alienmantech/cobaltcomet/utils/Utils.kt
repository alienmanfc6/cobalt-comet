package com.alienmantech.cobaltcomet.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logError
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn
import java.util.Locale
import org.json.JSONArray

class Utils {
    companion object {

        private const val PREF_FILE_NAME = "PrefFile"
        private const val PREF_PHONE_NUMBER = "phone"
        private const val PREF_MESSAGES = "messages"
        private const val MAX_SAVED_MESSAGES = 10

        private const val PHONE_NUMBER_DELIM = "-"

        fun getSavePref(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
        }

        fun loadPhoneNumbers(context: Context): List<PhoneEntry> {
            return decodePhoneEntries(getSavePref(context).getString(PREF_PHONE_NUMBER, null))
        }

        fun savePhoneNumbers(context: Context, phoneNumber: List<PhoneEntry>) {
            getSavePref(context).edit()
                .putString(PREF_PHONE_NUMBER, encodePhoneEntries(phoneNumber))
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
                logWarn("Unable to decode saved phone entries; falling back to legacy format")
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

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
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
            val saved = getSavePref(context).getString(PREF_MESSAGES, null) ?: return emptyList()
            return try {
                val array = JSONArray(saved)
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

        fun saveMessage(context: Context, message: MessageModel) {
            val existing = loadMessages(context).toMutableList()
            existing.add(0, message)

            val limited = existing.take(MAX_SAVED_MESSAGES)

            val array = JSONArray()
            for (item in limited) {
                array.put(item.toJson())
            }

            getSavePref(context).edit()
                .putString(PREF_MESSAGES, array.toString())
                .apply()
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
            mapIntent.resolveActivity(context.packageManager)?.let {
                context.startActivity(mapIntent)
            }
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

            return if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
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
                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                mapIntent.setPackage(GOOGLE_MAPS_PACKAGE)
                mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                if (mapIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mapIntent)
                    true
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

        fun isGoogleMapsUrl(url: String): Boolean {
            return try {
                val uri = Uri.parse(url)
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

        fun extractGoogleMapsNavigationQuery(url: String): String? {
            return try {
                val uri = Uri.parse(url)

                val candidateParameters = listOf("q", "query", "destination", "daddr", "ll")
                for (parameter in candidateParameters) {
                    val value = uri.getQueryParameter(parameter)
                    if (!value.isNullOrBlank()) {
                        return value
                    }
                }

                uri.encodedPath?.let { path ->
                    extractLatLngFromText(path)?.let { return it }
                }

                uri.fragment?.let { fragment ->
                    extractLatLngFromText(fragment)?.let { return it }
                }

                uri.getQueryParameter("link")?.let { nested ->
                    val decoded = Uri.decode(nested)
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

        private fun extractLatLngFromText(text: String): String? {
            val match = COORDINATE_REGEX.find(text)
            return if (match != null && match.groupValues.size >= 3) {
                "${match.groupValues[1]},${match.groupValues[2]}"
            } else {
                null
            }
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
                    context.startActivity(intent)
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
    }
}