package com.alienmantech.cobaltcomet.utils

import android.util.Log

class Logger {
    companion object {

        private const val LOG_TAG = "CobaltComet"

        fun logInfo(message: String) {
            safeLog { Log.i(LOG_TAG, message) }
        }

        fun logWarn(message: String) {
            safeLog { Log.w(LOG_TAG, message) }
        }

        fun logWarn(message: String, e: Throwable) {
            safeLog { Log.w(LOG_TAG, message, e) }
        }

        fun logError(message: String, e: Throwable) {
            safeLog { Log.e(LOG_TAG, message, e) }
        }

        fun maskIdentifier(value: String?): String {
            if (value.isNullOrBlank()) {
                return "unknown"
            }

            val trimmed = value.trim()
            return when {
                trimmed.length <= 4 -> "****"
                else -> "${"*".repeat(trimmed.length - 4)}${trimmed.takeLast(4)}"
            }
        }

        private inline fun safeLog(block: () -> Unit) {
            runCatching { block() }
        }
    }
}
