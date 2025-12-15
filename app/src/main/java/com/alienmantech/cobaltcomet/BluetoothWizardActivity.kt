package com.alienmantech.cobaltcomet

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.Utils
import kotlinx.coroutines.launch

class BluetoothWizardActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PAIRED_ENTRY = "paired_entry"
    }

    private val discoveredDevices = mutableStateListOf<BluetoothDeviceInfo>()
    private var selectedDevice by mutableStateOf<BluetoothDeviceInfo?>(null)

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { addOrUpdateDevice(it) }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { addOrUpdateDevice(it) }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        refreshBondedDevices()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(discoveryReceiver, filter)

        setContent {
            CobaltCometTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Bluetooth wizard") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    WizardContent(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        devices = discoveredDevices,
                        snackbarHostState = snackbarHostState,
                        selectedDevice = selectedDevice,
                        onStartDiscovery = { startDiscovery() },
                        onSelectDevice = { device -> selectedDevice = device },
                        onSaveContact = { label -> saveSelection(label) }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDiscovery()
        unregisterReceiver(discoveryReceiver)
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        adapter.cancelDiscovery()
        adapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun stopDiscovery() {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun refreshBondedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val bonded = adapter.bondedDevices
            .map { device ->
                BluetoothDeviceInfo(
                    name = device.name.orEmpty(),
                    address = device.address,
                    isBonded = true
                )
            }
        discoveredDevices.clear()
        discoveredDevices.addAll(bonded)
    }

    @SuppressLint("MissingPermission")
    private fun addOrUpdateDevice(device: BluetoothDevice) {
        val existingIndex = discoveredDevices.indexOfFirst { it.address == device.address }
        val info = BluetoothDeviceInfo(
            name = device.name.orEmpty(),
            address = device.address,
            isBonded = device.bondState == BluetoothDevice.BOND_BONDED
        )

        if (existingIndex == -1) {
            discoveredDevices.add(info)
        } else {
            discoveredDevices[existingIndex] = info
        }
    }

    private fun saveSelection(label: String) {
        val device = selectedDevice ?: return
        val cleanedLabel = label.ifBlank { device.name.ifBlank { "Bluetooth contact" } }
        val entry = PhoneEntry(
            label = cleanedLabel,
            number = device.address,
            type = ContactType.BLUETOOTH
        )

        val current = Utils.loadPhoneNumbers(this).toMutableList()
        current.removeAll { it.number == entry.number }
        current.add(0, entry)
        Utils.savePhoneNumbers(this, current)

        val resultIntent = Intent().apply {
            putExtra(EXTRA_PAIRED_ENTRY, Utils.encodePhoneEntries(listOf(entry)))
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

@Composable
private fun WizardContent(
    modifier: Modifier = Modifier,
    devices: List<BluetoothDeviceInfo>,
    snackbarHostState: SnackbarHostState,
    selectedDevice: BluetoothDeviceInfo?,
    onStartDiscovery: () -> Unit,
    onSelectDevice: (BluetoothDeviceInfo) -> Unit,
    onSaveContact: (String) -> Unit
) {
    var step by remember { mutableStateOf(WizardStep.Prepare) }
    var label by remember { mutableStateOf(selectedDevice?.name.orEmpty()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedDevice) {
        if (selectedDevice != null) {
            label = selectedDevice.name
            step = WizardStep.SaveContact
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WizardHeader(step)

            when (step) {
                WizardStep.Prepare -> PrepareStep(onContinue = { step = WizardStep.Scan })
                WizardStep.Scan -> DeviceSelectionStep(
                    devices = devices,
                    onStartDiscovery = onStartDiscovery,
                    onSelectDevice = onSelectDevice
                )

                WizardStep.SaveContact -> SaveContactStep(
                    label = label,
                    onLabelChanged = { label = it },
                    device = selectedDevice,
                    onSave = {
                        if (selectedDevice == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Select a device to continue")
                            }
                        } else {
                            onSaveContact(label)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(step: WizardStep) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Pair a Bluetooth device",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = when (step) {
                WizardStep.Prepare -> "Turn on Bluetooth and get ready to scan."
                WizardStep.Scan -> "Choose the device you want to pair with."
                WizardStep.SaveContact -> "Save the paired device as a contact for sharing."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrepareStep(onContinue: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enable Bluetooth",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Make sure Bluetooth is turned on and the device you want to pair is discoverable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun DeviceSelectionStep(
    devices: List<BluetoothDeviceInfo>,
    onStartDiscovery: () -> Unit,
    onSelectDevice: (BluetoothDeviceInfo) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available devices",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Scan",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onStartDiscovery() }
            )
        }

        if (devices.isEmpty()) {
            Text(
                text = "No devices found yet. Tap Scan to search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceRow(device = device, onSelect = onSelectDevice)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BluetoothDeviceInfo, onSelect: (BluetoothDeviceInfo) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(device) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = device.name.ifBlank { "Unnamed device" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (device.isBonded) {
                Text(
                    text = "Paired",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SaveContactStep(
    label: String,
    onLabelChanged: (String) -> Unit,
    device: BluetoothDeviceInfo?,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Save device",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (device != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = device.name.ifBlank { "Unnamed device" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = onLabelChanged,
            label = { Text("Contact label") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), enabled = device != null) {
            Text("Save and finish")
        }
    }
}

private data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isBonded: Boolean
)

private enum class WizardStep {
    Prepare,
    Scan,
    SaveContact
}

@Preview(showBackground = true)
@Composable
private fun WizardPreview() {
    val mockDevices = listOf(
        BluetoothDeviceInfo(name = "Truck Radio", address = "00:11:22:33:44:55", isBonded = true),
        BluetoothDeviceInfo(name = "Cab Tablet", address = "AA:BB:CC:DD:EE:FF", isBonded = false)
    )
    CobaltCometTheme {
        WizardContent(
            devices = mockDevices,
            snackbarHostState = SnackbarHostState(),
            selectedDevice = mockDevices.first(),
            onStartDiscovery = {},
            onSelectDevice = {},
            onSaveContact = {}
        )
    }
}
