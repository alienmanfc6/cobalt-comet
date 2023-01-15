package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logError
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn

class Utils {

    companion object {

        private const val PREF_FILE_NAME = "PrefFile"
        private const val PREF_PHONE_NUMBER = "phone"

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

        fun launchGoogleMaps(context: Context, lat: String, lng: String) {
            val gmmIntentUri = Uri.parse("geo:$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            mapIntent.resolveActivity(context.packageManager)?.let {
                context.startActivity(mapIntent)
            }
        }

        fun launchGoogleMapsNavigation(context: Context, lat: String, lng: String) {
            val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            mapIntent.resolveActivity(context.packageManager)?.let {
                context.startActivity(mapIntent)
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