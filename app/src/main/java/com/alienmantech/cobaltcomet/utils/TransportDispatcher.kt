package com.alienmantech.cobaltcomet.utils

import android.content.Context

class TransportDispatcher(
    private val config: TransportConfig = TransportConfig()
) {

    private val smsTransport = SmsCommunicationTransport()
    private val bluetoothTransport = BluetoothCommunicationTransport()
    private val transports: Map<TransportType, CommunicationTransport> = mapOf(
        TransportType.SMS to smsTransport,
        TransportType.BLUETOOTH to bluetoothTransport
    )

    fun dispatch(context: Context, to: String, body: String): Boolean {
        return when (config.mode) {
            TransportMode.SMS -> sendWithFallback(context, to, body, TransportType.SMS, config.fallback)
            TransportMode.BLUETOOTH -> sendWithFallback(context, to, body, TransportType.BLUETOOTH, config.fallback)
            TransportMode.AUTO -> dispatchAuto(context, to, body)
        }
    }

    private fun dispatchAuto(context: Context, to: String, body: String): Boolean {
        val preferredOrder = if (bluetoothTransport.canHandle(to)) {
            listOf(TransportType.BLUETOOTH, TransportType.SMS)
        } else {
            listOf(TransportType.SMS, TransportType.BLUETOOTH)
        }

        val explicitFallback = config.fallback?.let { listOf(it) } ?: emptyList()
        val attempts = (preferredOrder + explicitFallback).distinct()

        for (transportType in attempts) {
            val transport = transports[transportType] ?: continue
            if (transport.send(context, to, body)) {
                return true
            }
        }

        return false
    }

    private fun sendWithFallback(
        context: Context,
        to: String,
        body: String,
        primary: TransportType,
        fallback: TransportType?
    ): Boolean {
        val primaryTransport = transports[primary]
        val primaryResult = primaryTransport?.send(context, to, body) ?: false
        if (primaryResult) {
            return true
        }

        val fallbackTransport = fallback?.let { transports[it] }
        return fallbackTransport?.send(context, to, body) ?: false
    }
}

data class TransportConfig(
    val mode: TransportMode = TransportMode.SMS,
    val fallback: TransportType? = null
)

enum class TransportMode {
    SMS,
    BLUETOOTH,
    AUTO
}

enum class TransportType {
    SMS,
    BLUETOOTH
}
