package com.alienmantech.cobaltcomet

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.models.MessageModel
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.NoContentView
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Utils

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_SMS_PERMISSION = 1
        private const val REQUEST_DRAW_PERMISSION = 2
        private const val REQUEST_CONTACTS_PERMISSION = 3
    }

    private var phoneEntries by mutableStateOf(listOf<PhoneEntry>())
    private var messages by mutableStateOf(listOf<MessageModel>())

    private val contactSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(ContactSelectionActivity.EXTRA_SELECTED_ENTRIES)?.let { json ->
                    val selectedEntries = Utils.decodePhoneEntries(json)
                    phoneEntries = selectedEntries
                    savePhoneEntries(selectedEntries)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureSmsPermissionOnStart()
        enableEdgeToEdge()
        setContent {
            CobaltCometTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        PhoneNumberScreen(
                            phoneEntries = phoneEntries,
                            onManageNumbers = { openContactSelection() },
                            messages = messages,
                            onMessageClick = { message ->
                                CommunicationUtils.handleMessageAction(this@MainActivity, message)
                            }
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
    }

    override fun onPause() {
        super.onPause()

        savePhoneEntries(phoneEntries)
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
            putExtra(ContactSelectionActivity.EXTRA_SELECTED_ENTRIES, Utils.encodePhoneEntries(phoneEntries))
        }
        contactSelectionLauncher.launch(intent)
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

@Composable
fun PhoneNumberScreen(
    phoneEntries: List<PhoneEntry>,
    onManageNumbers: () -> Unit,
    messages: List<MessageModel>,
    onMessageClick: (MessageModel) -> Unit
) {
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Driver phone numbers",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(onClick = onManageNumbers) {
                Text(text = "Select from contacts")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (phoneEntries.isEmpty()) {
                Text(
                    text = "No driver numbers selected.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    phoneEntries.forEach { entry ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = entry.label, style = MaterialTheme.typography.bodyLarge)
                            Text(text = entry.number, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Saved Messages",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (message.url.isNotEmpty()) {
                Text(text = message.url, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneNumberScreenPreview() {
    MaterialTheme {
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
        PhoneNumberScreen(
            phoneEntries = listOf(
                PhoneEntry(label = "Driver One", number = "555-1234"),
                PhoneEntry(label = "Driver Two", number = "555-5678")
            ),
            onManageNumbers = {},
            messages = mockMessages,
            onMessageClick = {}
        )
    }
}