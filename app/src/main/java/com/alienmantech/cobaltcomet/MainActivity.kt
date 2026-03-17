package com.alienmantech.cobaltcomet

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.NoContentView
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Utils

private const val MainRoute = "main"
private const val SetupRoute = "setup"

class MainActivity : ComponentActivity() {
    private var phoneEntries by mutableStateOf(listOf<PhoneEntry>())
    private var messages by mutableStateOf(listOf<MessageModel>())
    private var hasRequestedSmsPermission = false
    private var hasRequestedContactsPermission = false

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

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasSmsPermission()) {
                requestContactsPermissionIfNeeded()
            }
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshUiState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                CommunicationUtils.handleMessageAction(this@MainActivity, message)
                            },
                            onSettingsClick = { navController.navigate(SetupRoute) }
                        )
                    }
                    composable(SetupRoute) {
                        SetupScreen(
                            phoneEntries = phoneEntries,
                            onManageNumbers = { openContactSelection() },
                            onRemoveContact = { entry -> removePhoneEntry(entry) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
        refreshUiState()
        requestMissingPermissionsOnStartup()
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    override fun onPause() {
        super.onPause()
        savePhoneEntries(phoneEntries)
    }

    private fun refreshUiState() {
        phoneEntries = Utils.loadPhoneNumbers(this)
        messages = Utils.loadMessages(this)
    }

    private fun savePhoneEntries(entries: List<PhoneEntry>) {
        Utils.savePhoneNumbers(this, entries)
    }

    private fun removePhoneEntry(entry: PhoneEntry) {
        val updatedEntries = phoneEntries.filterNot { existing ->
            existing.label == entry.label &&
                existing.number == entry.number &&
                existing.type == entry.type
        }
        phoneEntries = updatedEntries
        savePhoneEntries(updatedEntries)
    }

    private fun openContactSelection() {
        if (!hasContactsPermission()) {
            requestContactsPermissionIfNeeded()
            Toast.makeText(
                this,
                "Contacts permission is required to select recipients.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(this, ContactSelectionActivity::class.java).apply {
            putExtra(
                ContactSelectionActivity.EXTRA_SELECTED_ENTRIES,
                Utils.encodePhoneEntries(phoneEntries)
            )
        }
        contactSelectionLauncher.launch(intent)
    }

    private fun requestMissingPermissionsOnStartup() {
        requestSmsPermissionIfNeeded()
        if (hasSmsPermission()) {
            requestContactsPermissionIfNeeded()
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        if (hasSmsPermission() || hasRequestedSmsPermission) {
            return
        }
        hasRequestedSmsPermission = true
        requestSmsPermission()
    }

    private fun requestContactsPermissionIfNeeded() {
        if (hasContactsPermission() || hasRequestedContactsPermission) {
            return
        }
        hasRequestedContactsPermission = true
        requestContactsPermission()
    }

    private fun hasSmsPermission(): Boolean {
        return Utils.hasSmsPermissions(this)
    }

    private fun hasContactsPermission(): Boolean {
        return Utils.hasContactsPermission(this)
    }

    private fun requestSmsPermission() {
        smsPermissionLauncher.launch(
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        )
    }

    private fun requestContactsPermission() {
        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    phoneEntries: List<PhoneEntry>,
    onManageNumbers: () -> Unit,
    onRemoveContact: (PhoneEntry) -> Unit,
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
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.clickable { onRemoveContact(entry) }
                                ) {
                                    Text(
                                        text = entry.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = entry.number,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Tap to remove",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (index != phoneEntries.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
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
fun MessageCard(
    message: MessageModel,
    onMessageClick: (MessageModel) -> Unit
) {
    val contactName = Utils.getContactName(LocalContext.current, message.from)
    val timestamp = Utils.formatReceivedAt(message.receivedAt)

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
            if (timestamp != null) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall,
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
            onRemoveContact = {},
            onBack = {}
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
