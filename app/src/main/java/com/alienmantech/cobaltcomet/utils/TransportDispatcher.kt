package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.widget.Toast

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
        if (!TransportPreflight.validateBase(context, to, body)) {
            return false
        }

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
            if (!validateForTransport(context, to, transportType)) {
                continue
            }

            if (transport.send(context, to, body)) {
                return true
            } else {
                notifyFailure(context, transportType)
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
        if (!validateForTransport(context, to, primary)) {
            return false
        }

        val primaryResult = primaryTransport?.send(context, to, body) ?: false
        if (primaryResult) {
            return true
        }

        notifyFailure(context, primary)

        val fallbackTransport = fallback?.let { transports[it] }
            ?: if (primary == TransportType.BLUETOOTH) smsTransport else null

        return fallbackTransport?.let { fallbackTransportInstance ->
            if (validateForTransport(context, to, TransportType.SMS)) {
                fallbackTransportInstance.send(context, to, body)
            } else {
                false
            }
        } ?: false
    }

    private fun validateForTransport(
        context: Context,
        to: String,
        transportType: TransportType
    ): Boolean {
        return when (transportType) {
            TransportType.BLUETOOTH -> TransportPreflight.validateBluetooth(context, to)
            TransportType.SMS -> true
        }
    }

    private fun notifyFailure(context: Context, transportType: TransportType) {
        when (transportType) {
            TransportType.BLUETOOTH -> Toast.makeText(
                context,
                "Bluetooth send failed. Trying SMS if available…",
                Toast.LENGTH_SHORT
            ).show()

            TransportType.SMS -> Toast.makeText(
                context,
                "SMS send failed. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
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
