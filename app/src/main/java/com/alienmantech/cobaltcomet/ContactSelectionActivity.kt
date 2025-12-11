package com.alienmantech.cobaltcomet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.NoContentView
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn
import com.alienmantech.cobaltcomet.utils.Utils

class ContactSelectionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SELECTED_ENTRIES = "selected_entries"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialSelection = Utils.decodePhoneEntries(intent.getStringExtra(EXTRA_SELECTED_ENTRIES))

        setContent {
            CobaltCometTheme {
                val contacts = remember { mutableStateOf<List<PhoneEntry>>(emptyList()) }

                LaunchedEffect(Unit) {
                    contacts.value = loadContacts(this@ContactSelectionActivity)
                }

                ContactSelectionScreen(
                    contacts = contacts.value,
                    initialSelection = initialSelection,
                    onSave = { selectedEntries ->
                        val intent = Intent().apply {
                            putExtra(EXTRA_SELECTED_ENTRIES, Utils.encodePhoneEntries(selectedEntries))
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun loadContacts(context: Context): List<PhoneEntry> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            logWarn("READ_CONTACTS permission not granted; unable to load contacts")
            return emptyList()
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val contacts = mutableListOf<PhoneEntry>()
        val seenNumbers = mutableSetOf<String>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val number = cursor.getString(numberIndex) ?: continue
                val cleanedNumber = number.replace("\\s".toRegex(), "")
                if (cleanedNumber.isBlank() || !seenNumbers.add(cleanedNumber)) {
                    continue
                }
                contacts.add(PhoneEntry(label = name, number = cleanedNumber))
            }
        } ?: logWarn("Unable to load contacts; cursor was null")

        return contacts
    }
}

@Composable
private fun ContactSelectionScreen(
    contacts: List<PhoneEntry>,
    initialSelection: List<PhoneEntry>,
    onSave: (List<PhoneEntry>) -> Unit,
    onCancel: () -> Unit
) {
    val selectedEntries = remember { mutableStateOf(initialSelection.toMutableSet()) }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (contacts.isEmpty()) {
                    NoContentView(
                        title = "No contacts found",
                        description = "Contacts with phone numbers will appear here when available.",
                        modifier = Modifier.weight(1f, fill = true)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = true)
                    ) {
                        items(contacts) { contact ->
                            ContactRow(
                                contact = contact,
                                isSelected = selectedEntries.value.contains(contact),
                                onToggle = { toggled ->
                                    val updated = selectedEntries.value.toMutableSet()
                                    if (updated.contains(toggled)) {
                                        updated.remove(toggled)
                                    } else {
                                        updated.add(toggled)
                                    }
                                    selectedEntries.value = updated
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) {
                        Text(text = "Cancel")
                    }
                    Button(onClick = { onSave(selectedEntries.value.toList()) }) {
                        Text(text = "Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: PhoneEntry,
    isSelected: Boolean,
    onToggle: (PhoneEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle(contact) })
        Column(modifier = Modifier.weight(1f)) {
            Text(text = contact.label, style = MaterialTheme.typography.bodyLarge)
            Text(text = contact.number, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
