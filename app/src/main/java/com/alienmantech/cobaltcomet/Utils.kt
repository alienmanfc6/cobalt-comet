package com.alienmantech.cobaltcomet

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.text.TextUtils
import android.util.Log

class Utils {

    companion object {

        private const val PREF_FILE_NAME = "PrefFile"
        const val PREF_PHONE_NUMBER = "phone"

        fun getSavePref(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
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

        fun openInGoogleMaps(context: Context) {
            val lat = "37.5749"
            val lng = "-122.4194"

            val gmmIntentUri = Uri.parse("geo:$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")

            mapIntent.resolveActivity(context.packageManager)?.let {
                context.startActivity(mapIntent)
            }
        }

        fun openInGoogleMapsAlt(context: Context) {
            val lat = "37.7749"
            val lng = "-122.4194"

            val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lng")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")

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

        fun logInfo(message: String) {
            Log.i("CobaltComet", message)
        }

        fun logWarn(message: String) {
            Log.w("CobaltComet", message)
        }

        fun logError(message: String, e: Throwable) {
            Log.e("CobaltComet", message, e)
        }
    }
}