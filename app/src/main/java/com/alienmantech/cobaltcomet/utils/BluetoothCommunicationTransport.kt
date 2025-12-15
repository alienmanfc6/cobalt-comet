package com.alienmantech.cobaltcomet.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import java.io.OutputStream
import java.util.UUID

class BluetoothCommunicationTransport(
    private val uuid: UUID = DEFAULT_UUID
) : CommunicationTransport {

    override fun canHandle(target: String): Boolean {
        return BluetoothAdapter.checkBluetoothAddress(target)
    }

    @SuppressLint("MissingPermission")
    override fun send(context: Context, to: String, message: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(context, "Bluetooth is unavailable", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val device = adapter.getRemoteDevice(to)
            sendMessage(adapter, device, message)
        } catch (e: Exception) {
            Logger.logError("Bluetooth send failed", e)
            Toast.makeText(context, "Bluetooth send failed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendMessage(adapter: BluetoothAdapter, device: BluetoothDevice, message: String): Boolean {
        val socket = device.createRfcommSocketToServiceRecord(uuid)
        adapter.cancelDiscovery()
        return socket.use { btSocket ->
            btSocket.connect()
            btSocket.outputStream.use { stream ->
                writeMessage(stream, message)
            }
            true
        }
    }

    private fun writeMessage(stream: OutputStream, message: String) {
        val payload = message.toByteArray()
        stream.write(payload)
        stream.flush()
    }

    companion object {
        val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
