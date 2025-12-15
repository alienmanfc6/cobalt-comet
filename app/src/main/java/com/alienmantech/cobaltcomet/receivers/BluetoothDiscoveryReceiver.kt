package com.alienmantech.cobaltcomet.receivers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alienmantech.cobaltcomet.services.BluetoothMessageService
import com.alienmantech.cobaltcomet.utils.Logger

class BluetoothDiscoveryReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val address = device?.address
        if (address.isNullOrEmpty()) {
            Logger.logInfo("Bluetooth discovery broadcast received without device")
            return
        }

        BluetoothMessageService.notifyDeviceDiscovered(context, address)
    }
}

