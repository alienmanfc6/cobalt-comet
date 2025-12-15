package com.alienmantech.cobaltcomet.utils

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.widget.Toast

object TransportPreflight {
    fun validateBase(context: Context, to: String?, body: String): Boolean {
        val recipient = to?.trim()
        if (recipient.isNullOrEmpty()) {
            Toast.makeText(context, "No recipient selected", Toast.LENGTH_SHORT).show()
            return false
        }

        if (body.isBlank()) {
            Toast.makeText(context, "Cannot send an empty message", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    fun validateBluetooth(context: Context, target: String): Boolean {
        if (target.isBlank()) {
            Toast.makeText(context, "No recipient selected", Toast.LENGTH_SHORT).show()
            return false
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(context, "Bluetooth is unavailable", Toast.LENGTH_SHORT).show()
            return false
        }

        if (!BluetoothAdapter.checkBluetoothAddress(target)) {
            Toast.makeText(context, "Bluetooth address is invalid", Toast.LENGTH_SHORT).show()
            return false
        }

        if (adapter.bondedDevices.none { it.address == target }) {
            Toast.makeText(context, "No paired device matches the selected address", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }
}
