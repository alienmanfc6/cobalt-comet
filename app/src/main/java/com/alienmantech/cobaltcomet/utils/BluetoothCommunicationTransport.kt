package com.alienmantech.cobaltcomet.utils

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.alienmantech.cobaltcomet.services.BluetoothMessageService
import java.util.UUID

class BluetoothCommunicationTransport(
    private val uuid: UUID = DEFAULT_UUID
) : CommunicationTransport {

    override fun canHandle(target: String): Boolean {
        return BluetoothAdapter.checkBluetoothAddress(target)
    }

    override fun send(context: Context, to: String, message: String): Boolean {
        return try {
            BluetoothMessageService.enqueueMessage(context, to, message)
            true
        } catch (e: Exception) {
            Logger.logError("Bluetooth send failed", e)
            false
        }
    }

    companion object {
        val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
