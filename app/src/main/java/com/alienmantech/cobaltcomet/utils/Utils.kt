package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import androidx.compose.ui.graphics.asImageBitmap
import com.alienmantech.cobaltcomet.models.StoredFirebaseMessage
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logError
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn
import com.google.firebase.messaging.FirebaseMessaging
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.util.Locale
import org.json.JSONArray

class Utils {
    companion object {

        private const val PREF_FILE_NAME = "PrefFile"
        private const val PREF_PHONE_NUMBER = "phone"
        private const val PREF_FIREBASE_ID = "firebase_id"
        private const val PREF_FIREBASE_MESSAGES = "firebase_messages"
        private const val PREF_QR_CONTACTS = "qr_contacts"

        private const val MAX_SAVED_FIREBASE_MESSAGES = 50

        private const val PHONE_NUMBER_DELIM = "-"

        fun getSavePref(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
        }

        fun loadPhoneNumbers(context: Context): List<String>? {
            return getSavePref(context).getString(PREF_PHONE_NUMBER, null)?.let {
                csvToList(it)
            }
        }

        fun savePhoneNumbers(context: Context, phoneNumber: List<String>) {
            getSavePref(context).edit()
                .putString(PREF_PHONE_NUMBER, listToCsv(phoneNumber))
                .apply()
        }

        fun loadFirebaseId(context: Context): String? {
            return getSavePref(context).getString(PREF_FIREBASE_ID, null)
        }

        fun saveFirebaseId(context: Context, firebaseId: String) {
            getSavePref(context).edit()
                .putString(PREF_FIREBASE_ID, firebaseId)
                .apply()
        }

        fun loadFirebaseMessages(context: Context): List<StoredFirebaseMessage> {
            val stored = getSavePref(context).getString(PREF_FIREBASE_MESSAGES, null) ?: return emptyList()

            return try {
                val jsonArray = JSONArray(stored)
                buildList {
                    for (i in 0 until jsonArray.length()) {
                        val message = StoredFirebaseMessage.fromJson(jsonArray.optJSONObject(i))
                        if (message != null) {
                            add(message)
                        }
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun saveFirebaseMessage(context: Context, message: StoredFirebaseMessage) {
            val messages = loadFirebaseMessages(context)
                .takeLast(MAX_SAVED_FIREBASE_MESSAGES - 1)
                .toMutableList()
            messages.add(message)

            val jsonArray = JSONArray()
            messages.forEach { jsonArray.put(it.toJson()) }

            getSavePref(context).edit()
                .putString(PREF_FIREBASE_MESSAGES, jsonArray.toString())
                .apply()
        }

        fun loadQrContacts(context: Context): Map<String, String> {
            val stored = getSavePref(context).getString(PREF_QR_CONTACTS, null) ?: return emptyMap()

            return try {
                val jsonObject = org.json.JSONObject(stored)
                jsonObject.keys().asSequence().associateWith { key -> jsonObject.getString(key) }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        fun saveQrContacts(context: Context, contacts: Map<String, String>) {
            val jsonObject = org.json.JSONObject()
            contacts.forEach { (name, token) ->
                jsonObject.put(name, token)
            }

            getSavePref(context).edit()
                .putString(PREF_QR_CONTACTS, jsonObject.toString())
                .apply()
        }

        fun parseUrl(text: String): String? {
            val index = text.indexOf("http")
            if (index == -1) {
                return null
            }

            val after = text.substring(index)
            val endOfUrl = after.indexOf(" ")

            return if (endOfUrl != -1) {
                text.substring(index, endOfUrl)
            } else {
                text.substring(index)
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

        fun generateQrCode(data: String, size: Int = 800): androidx.compose.ui.graphics.ImageBitmap? {
            return try {
                val bitMatrix: BitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    }
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                bitmap.asImageBitmap()
            } catch (_: WriterException) {
                null
            }
        }
    }
}