package com.alienmantech.cobaltcomet.utils

import android.content.Context
import android.widget.Toast
import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.PhoneEntry

class TransportDispatcher {

    private val smsTransport = SmsCommunicationTransport()
    private val bluetoothTransport = BluetoothCommunicationTransport()
    private val transports: Map<TransportType, CommunicationTransport> = mapOf(
        TransportType.SMS to smsTransport,
        TransportType.BLUETOOTH to bluetoothTransport
    )

    fun dispatch(context: Context, recipient: PhoneEntry, body: String): Boolean {
        if (!TransportPreflight.validateBase(context, recipient.number, body)) {
            return false
        }

        val transportType = when (recipient.type) {
            ContactType.BLUETOOTH -> TransportType.BLUETOOTH
            ContactType.PHONE -> TransportType.SMS
        }

        val transport = transports[transportType]
        val isValid = validateForTransport(context, recipient.number, transportType)

        return if (isValid && transport?.send(context, recipient.number, body) == true) {
            true
        } else {
            notifyFailure(context, transportType)
            false
        }
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
                "Bluetooth send failed.",
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

enum class TransportType {
    SMS,
    BLUETOOTH
}
