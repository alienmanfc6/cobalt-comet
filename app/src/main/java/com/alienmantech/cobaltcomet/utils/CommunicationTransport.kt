package com.alienmantech.cobaltcomet.utils

import android.content.Context

interface CommunicationTransport {
    fun canHandle(target: String): Boolean

    fun send(context: Context, to: String, message: String): Boolean
}
