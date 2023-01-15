package com.alienmantech.cobaltcomet.utils

import android.util.Log

class Logger {

    companion object {

        private const val LOG_TAG = "CobaltComet"

        fun logInfo(message: String) {
            Log.i(LOG_TAG, message)
        }

        fun logWarn(message: String) {
            Log.w(LOG_TAG, message)
        }

        fun logError(message: String, e: Throwable) {
            Log.e(LOG_TAG, message, e)
        }
    }

}