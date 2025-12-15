package com.alienmantech.cobaltcomet

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.NoContentView
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.TransportConfig
import com.alienmantech.cobaltcomet.utils.TransportMode
import com.alienmantech.cobaltcomet.utils.TransportType
import com.alienmantech.cobaltcomet.utils.Utils
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private const val MainRoute = "main"
private const val SetupRoute = "setup"

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_SMS_PERMISSION = 1
        private const val REQUEST_DRAW_PERMISSION = 2
        private const val REQUEST_CONTACTS_PERMISSION = 3
    }

    private var phoneEntries by mutableStateOf(listOf<PhoneEntry>())
    private var messages by mutableStateOf(listOf<MessageModel>())
    private var transportConfig by mutableStateOf(TransportConfig())
    private var bluetoothStatus by mutableStateOf(BluetoothStatus())

    private val contactSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(ContactSelectionActivity.EXTRA_SELECTED_ENTRIES)
                    ?.let { json ->
                        val selectedEntries = Utils.decodePhoneEntries(json)
                        phoneEntries = selectedEntries
                        savePhoneEntries(selectedEntries)
                    }
            }
        }

    private val bluetoothWizardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(BluetoothWizardActivity.EXTRA_PAIRED_ENTRY)
                    ?.let { json ->
                        val newEntry = Utils.decodePhoneEntries(json).firstOrNull()
                        if (newEntry != null) {
                            val merged = phoneEntries.toMutableList().apply {
                                removeAll { it.number == newEntry.number }
                                add(0, newEntry)
                            }
                            phoneEntries = merged
                            savePhoneEntries(merged)
                        }
                    }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureSmsPermissionOnStart()
        enableEdgeToEdge()
        setContent {
            CobaltCometTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = MainRoute,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    composable(MainRoute) {
                        MessageHistoryScreen(
                            messages = messages,
                            onMessageClick = { message ->
                                CommunicationUtils.handleMessageAction(
                                    this@MainActivity,
                                    message
                                )
                            },
                            onSettingsClick = { navController.navigate(SetupRoute) }
                        )
                    }
                    composable(SetupRoute) {
                        SetupScreen(
                            phoneEntries = phoneEntries,
                            onManageNumbers = { openContactSelection() },
                            onAddBluetoothContact = { openBluetoothWizard() },
                            transportConfig = transportConfig,
                            bluetoothStatus = bluetoothStatus,
                            onTransportModeChanged = { mode ->
                                transportConfig = transportConfig.copy(mode = mode)
                                CommunicationUtils.saveTransportConfig(
                                    this@MainActivity,
                                    transportConfig
                                )
                            },
                            onFallbackChanged = { fallback ->
                                transportConfig = transportConfig.copy(fallback = fallback)
                                CommunicationUtils.saveTransportConfig(
                                    this@MainActivity,
                                    transportConfig
                                )
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        phoneEntries = Utils.loadPhoneNumbers(this)
        messages = Utils.loadMessages(this)
        transportConfig = CommunicationUtils.loadTransportConfig(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothStatus = fetchBluetoothStatus()
        }
    }

    override fun onPause() {
        super.onPause()

        savePhoneEntries(phoneEntries)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun fetchBluetoothStatus(): BluetoothStatus {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return BluetoothStatus(supported = false, enabled = false)

        val paired = adapter.bondedDevices.firstOrNull()
        return BluetoothStatus(
            supported = true,
            enabled = adapter.isEnabled,
            pairedDeviceName = paired?.name,
            pairedDeviceAddress = paired?.address
        )
    }

    private fun ensureSmsPermissionOnStart() {
        if (!hasSmsPermission()) {
            requestSmsPermission()
            return
        }

        // SMS permission already granted, continue checking additional permissions.
        checkPermissions()
    }

    private fun savePhoneEntries(entries: List<PhoneEntry>) {
        Utils.savePhoneNumbers(this, entries)
    }

    private fun openContactSelection() {
        val intent = Intent(this, ContactSelectionActivity::class.java).apply {
            putExtra(
                ContactSelectionActivity.EXTRA_SELECTED_ENTRIES,
                Utils.encodePhoneEntries(phoneEntries)
            )
        }
        contactSelectionLauncher.launch(intent)
    }

    private fun openBluetoothWizard() {
        val intent = Intent(this, BluetoothWizardActivity::class.java)
        bluetoothWizardLauncher.launch(intent)
    }

    private fun checkPermissions() {
        // SMS
        if (!hasSmsPermission()) {
            requestSmsPermission()
            return // don't check the next one till this one is resolved
        }

        // Contacts
        if (!hasContactsPermission()) {
            requestContactsPermission()
            return // don't check the next one till this one is resolved
        }

        // Draw on apps
        if (!hasDrawOnScreenPermission()) {
            requestDrawOnScreenPermission()
            return // don't check the next one till this one is resolved
        }
    }

    private fun hasSmsPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun hasDrawOnScreenPermission(): Boolean {
        return (Settings.canDrawOverlays(this))
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS),
            REQUEST_SMS_PERMISSION
        )
    }

    private fun requestDrawOnScreenPermission() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(
                "This app needs permission to draw over other apps to show important information. " +
                        "Open settings to grant this permission?"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, REQUEST_DRAW_PERMISSION)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestContactsPermission() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.READ_CONTACTS),
            REQUEST_CONTACTS_PERMISSION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DRAW_PERMISSION) {
            // check permissions again, we may need to look for more
            checkPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SMS_PERMISSION || requestCode == REQUEST_CONTACTS_PERMISSION) {
            // check permissions again, we may need to look for more
            checkPermissions()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    phoneEntries: List<PhoneEntry>,
    onManageNumbers: () -> Unit,
    onAddBluetoothContact: () -> Unit,
    transportConfig: TransportConfig,
    bluetoothStatus: BluetoothStatus,
    onTransportModeChanged: (TransportMode) -> Unit,
    onFallbackChanged: (TransportType?) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            TransportControls(
                transportConfig = transportConfig,
                bluetoothStatus = bluetoothStatus,
                onTransportModeChanged = onTransportModeChanged,
                onFallbackChanged = onFallbackChanged
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Driver contacts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onManageNumbers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Select from contacts")
                }

                Button(
                    onClick = onAddBluetoothContact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(text = "Pair Bluetooth device")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (phoneEntries.isEmpty()) {
                    Text(
                        text = "No driver contacts selected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            phoneEntries.forEachIndexed { index, entry ->
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (entry.type == ContactType.BLUETOOTH) {
                                            "Bluetooth • ${entry.number}"
                                        } else {
                                            entry.number
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (index != phoneEntries.lastIndex) {
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageHistoryScreen(
    messages: List<MessageModel>,
    onMessageClick: (MessageModel) -> Unit,
    onSettingsClick: () -> Unit
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Saved Messages") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Setup"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                NoContentView(
                    title = "No saved messages",
                    description = "Messages you save will show up here for quick access.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = listState
                ) {
                    items(messages) { message ->
                        MessageCard(message = message, onMessageClick = onMessageClick)
                    }
                }
            }
        }
    }
}

@Composable
fun TransportControls(
    transportConfig: TransportConfig,
    bluetoothStatus: BluetoothStatus,
    onTransportModeChanged: (TransportMode) -> Unit,
    onFallbackChanged: (TransportType?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Message delivery",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose how messages are sent and set a fallback option.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ModeSelector(transportConfig.mode, onTransportModeChanged)

            FallbackSelector(
                mode = transportConfig.mode,
                selectedFallback = transportConfig.fallback,
                onFallbackChanged = onFallbackChanged
            )

            BluetoothStatusSummary(
                bluetoothStatus = bluetoothStatus,
                shouldHighlightIssues = usesBluetooth(transportConfig)
            )
        }
    }
}

@Composable
fun ModeSelector(
    selected: TransportMode,
    onTransportModeChanged: (TransportMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Mode",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChipRow(
                options = listOf(
                    TransportMode.SMS to "SMS only",
                    TransportMode.BLUETOOTH to "Bluetooth only",
                    TransportMode.AUTO to "Auto"
                ),
                selected = selected,
                onSelectionChanged = onTransportModeChanged
            )
            Text(
                text = when (selected) {
                    TransportMode.SMS -> "Messages will always send via SMS unless a fallback is set."
                    TransportMode.BLUETOOTH -> "Messages will send over the paired Bluetooth device when available."
                    TransportMode.AUTO -> "App will prefer Bluetooth when available and fall back to SMS."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun <T> FilterChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelectionChanged: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelectionChanged(value) },
                label = { Text(label) },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}

@Composable
fun FallbackSelector(
    mode: TransportMode,
    selectedFallback: TransportType?,
    onFallbackChanged: (TransportType?) -> Unit
) {
    val availableFallbacks = TransportType.values().filter { it != mode.primaryTransportType() }
    var isMenuOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Fallback",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Optional transport to try if the selected mode fails.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { isMenuOpen = true }) {
            Text(
                text = selectedFallback?.let { fallback ->
                    "Fallback: ${fallback.name.lowercase().replaceFirstChar { it.uppercase() }}"
                } ?: "No fallback selected"
            )
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text("No fallback") },
                onClick = {
                    onFallbackChanged(null)
                    isMenuOpen = false
                }
            )
            availableFallbacks.forEach { fallback ->
                DropdownMenuItem(
                    text = { Text(fallback.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onFallbackChanged(fallback)
                        isMenuOpen = false
                    }
                )
            }
        }
    }
}

@Composable
fun BluetoothStatusSummary(
    bluetoothStatus: BluetoothStatus,
    shouldHighlightIssues: Boolean
) {
    val statusMessage: Pair<String, Boolean> = when {
        !bluetoothStatus.supported -> "Bluetooth is not supported on this device." to true
        !bluetoothStatus.enabled -> "Bluetooth is turned off." to shouldHighlightIssues
        bluetoothStatus.pairedDeviceName == null -> "No paired Bluetooth devices detected." to shouldHighlightIssues
        else -> "Ready to use ${bluetoothStatus.pairedDeviceName} (${bluetoothStatus.pairedDeviceAddress})" to false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Bluetooth",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = statusMessage.first,
            style = MaterialTheme.typography.bodyMedium,
            color = if (statusMessage.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (bluetoothStatus.enabled && bluetoothStatus.pairedDeviceName != null) {
            AssistChip(
                onClick = {},
                enabled = false,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                label = {
                    Text("Paired: ${bluetoothStatus.pairedDeviceName} (${bluetoothStatus.pairedDeviceAddress})")
                }
            )
        }
    }
}

data class BluetoothStatus(
    val supported: Boolean = true,
    val enabled: Boolean = false,
    val pairedDeviceName: String? = null,
    val pairedDeviceAddress: String? = null
)

private fun TransportMode.primaryTransportType(): TransportType? {
    return when (this) {
        TransportMode.SMS -> TransportType.SMS
        TransportMode.BLUETOOTH -> TransportType.BLUETOOTH
        TransportMode.AUTO -> null
    }
}

private fun usesBluetooth(config: TransportConfig): Boolean {
    return config.mode == TransportMode.BLUETOOTH ||
            config.mode == TransportMode.AUTO ||
            config.fallback == TransportType.BLUETOOTH
}

@Composable
fun MessageCard(
    message: MessageModel,
    onMessageClick: (MessageModel) -> Unit
) {
    val contactName = Utils.getContactName(LocalContext.current, message.from)

    val title = when {
        message.locationName.isNotEmpty() -> message.locationName
        message.textList.isNotEmpty() -> message.textList.first()
        message.url.isNotEmpty() -> message.url
        else -> "Saved message"
    }

    val subtitle = when {
        message.lat.isNotEmpty() && message.lng.isNotEmpty() -> "${message.lat}, ${message.lng}"
        !contactName.isNullOrEmpty() -> "From: $contactName"
        message.from.isNotEmpty() -> "From: ${message.from}"
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMessageClick(message) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message.url.isNotEmpty()) {
                Text(
                    text = message.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetupScreenPreview() {
    CobaltCometTheme {
        SetupScreen(
            phoneEntries = listOf(
                PhoneEntry(label = "Driver One", number = "555-1234"),
                PhoneEntry(label = "Driver Two", number = "555-5678")
            ),
            onManageNumbers = {},
            onAddBluetoothContact = {},
            transportConfig = TransportConfig(
                mode = TransportMode.AUTO,
                fallback = TransportType.SMS
            ),
            bluetoothStatus = BluetoothStatus(
                supported = true,
                enabled = true,
                pairedDeviceName = "Truck Radio",
                pairedDeviceAddress = "00:11:22:33:44:55"
            ),
            onTransportModeChanged = {},
            onFallbackChanged = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MessageHistoryScreenPreview() {
    CobaltCometTheme {
        val mockMessages = listOf(
            MessageModel(
                locationName = "Coffee Shop",
                lat = "37.7749",
                lng = "-122.4194",
                url = "https://maps.app.goo.gl/example"
            ),
            MessageModel(
                textList = mutableListOf("Check out this place"),
                url = "https://example.com"
            )
        )
        MessageHistoryScreen(
            messages = mockMessages,
            onMessageClick = {},
            onSettingsClick = {}
        )
    }
}
