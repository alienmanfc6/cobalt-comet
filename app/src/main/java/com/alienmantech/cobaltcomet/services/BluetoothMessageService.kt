package com.alienmantech.cobaltcomet.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import android.widget.Toast
import com.alienmantech.cobaltcomet.R
import com.alienmantech.cobaltcomet.utils.BluetoothCommunicationTransport
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class BluetoothMessageService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listeningJob: Job? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var currentDevice: BluetoothDevice? = null
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        maybeStartForeground()

        when (intent?.action) {
            ACTION_SEND_MESSAGE -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val message = intent.getStringExtra(EXTRA_MESSAGE)
                if (!address.isNullOrEmpty() && !message.isNullOrEmpty()) {
                    serviceScope.launch {
                        sendMessage(address, message)
                    }
                }
            }

            ACTION_DEVICE_DISCOVERED -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (!address.isNullOrEmpty()) {
                    serviceScope.launch {
                        ensureBonded(address)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        listeningJob?.cancel()
        closeSocket()
        serviceScope.cancel()
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendMessage(address: String, message: String) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Logger.logInfo("Bluetooth adapter unavailable; cannot send message")
            Toast.makeText(applicationContext, "Bluetooth is unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val device = adapter.getRemoteDevice(address)
        if (!ensureBonded(device)) {
            Logger.logError("Unable to bond with $address", Exception("Bonding failed"))
            Toast.makeText(applicationContext, "Failed to connect to paired device", Toast.LENGTH_SHORT).show()
            return
        }

        val socket = getOrCreateSocket(adapter, device) ?: return
        try {
            val payload = message.toByteArray()
            socket.outputStream.write(payload)
            socket.outputStream.flush()
            startListening(socket, device)
        } catch (e: Exception) {
            Logger.logError("Bluetooth write failed", e)
            Toast.makeText(applicationContext, "Bluetooth send failed", Toast.LENGTH_SHORT).show()
            closeSocket()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensureBonded(device: BluetoothDevice): Boolean {
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            return true
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(applicationContext, "Bluetooth is unavailable", Toast.LENGTH_SHORT).show()
            return false
        }
        adapter.startDiscovery()
        return try {
            device.createBond()
        } catch (e: Exception) {
            Logger.logError("Bonding failed", e)
            Toast.makeText(applicationContext, "Pairing with device failed", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private suspend fun ensureBonded(address: String): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            Toast.makeText(applicationContext, "Bluetooth is unavailable", Toast.LENGTH_SHORT).show()
            return false
        }
        val device = adapter.getRemoteDevice(address)
        return ensureBonded(device)
    }

    @SuppressLint("MissingPermission")
    private fun getOrCreateSocket(
        adapter: BluetoothAdapter,
        device: BluetoothDevice
    ): BluetoothSocket? {
        bluetoothSocket?.let { socket ->
            if (socket.isConnected && currentDevice?.address == device.address) {
                return socket
            }
        }

        adapter.cancelDiscovery()

        return try {
            val socket = device.createRfcommSocketToServiceRecord(
                BluetoothCommunicationTransport.DEFAULT_UUID
            )
            socket.connect()
            closeSocket()
            bluetoothSocket = socket
            currentDevice = device
            socket
        } catch (e: Exception) {
            Logger.logError("Bluetooth socket connection failed", e)
            Toast.makeText(applicationContext, "Could not connect to Bluetooth device", Toast.LENGTH_SHORT).show()
            closeSocket()
            null
        }
    }

    private fun startListening(socket: BluetoothSocket, device: BluetoothDevice) {
        if (listeningJob?.isActive == true) {
            return
        }

        listeningJob = serviceScope.launch {
            val buffer = ByteArray(1024)
            val input = socket.inputStream
            while (isActive && socket.isConnected) {
                try {
                    val read = input.read(buffer)
                    if (read <= 0) continue
                    val text = String(buffer, 0, read)
                    if (text.isNotEmpty()) {
                        CommunicationUtils.handleIncomingMessage(
                            applicationContext,
                            device.address,
                            text
                        )
                    }
                } catch (e: IOException) {
                    Logger.logError("Bluetooth read failed", e)
                    break
                }
            }

            closeSocket()
        }
    }

    private fun closeSocket() {
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {
            Logger.logError("Error closing Bluetooth socket", e)
        } finally {
            bluetoothSocket = null
            currentDevice = null
        }
    }

    private fun maybeStartForeground() {
        if (isForeground || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channelId = "${packageName}.bluetooth"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager?.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening for Bluetooth messages")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        isForeground = true
    }

    companion object {
        private const val ACTION_SEND_MESSAGE =
            "com.alienmantech.cobaltcomet.services.BluetoothMessageService.SEND"
        private const val ACTION_DEVICE_DISCOVERED =
            "com.alienmantech.cobaltcomet.services.BluetoothMessageService.DEVICE"
        private const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val EXTRA_MESSAGE = "message"
        private const val NOTIFICATION_ID = 1001

        fun enqueueMessage(context: Context, address: String, message: String) {
            val intent = Intent(context, BluetoothMessageService::class.java).apply {
                action = ACTION_SEND_MESSAGE
                putExtra(EXTRA_DEVICE_ADDRESS, address)
                putExtra(EXTRA_MESSAGE, message)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun notifyDeviceDiscovered(context: Context, address: String) {
            val intent = Intent(context, BluetoothMessageService::class.java).apply {
                action = ACTION_DEVICE_DISCOVERED
                putExtra(EXTRA_DEVICE_ADDRESS, address)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

