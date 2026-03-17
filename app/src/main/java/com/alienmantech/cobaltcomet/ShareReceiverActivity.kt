package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Logger.Companion.logWarn
import com.alienmantech.cobaltcomet.utils.Utils

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val phoneNumbers = Utils.loadPhoneNumbers(this)

        if (phoneNumbers.size == 1) {
            handleIntent(phoneNumbers.first())
            finish()
            return
        }

        setContent {
            CobaltCometTheme {
                ShareReceiverBottomSheet(
                    phoneNumbers = phoneNumbers,
                    showYelpError = shouldShowYelpErrorMessage,
                    onSelectNumber = { selectedNumber ->
                        handleIntent(selectedNumber)
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun handleIntent(entry: PhoneEntry) {
        when (intent.action) {
            Intent.ACTION_SEND -> handleSendIntent(entry)
            Intent.ACTION_VIEW -> handleViewIntent(entry)
            else -> logWarn("Unsupported share action=${intent.action}")
        }
    }

    private fun handleSendIntent(entry: PhoneEntry) {
        if (intent.type != "text/plain") {
            logWarn("Unsupported shared mimeType=${intent.type}")
            return
        }

        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
        val clipText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (title.isNullOrBlank() && clipText.isNullOrBlank()) {
            logWarn("Received empty ACTION_SEND payload")
            return
        }

        sendMessage(entry, CommunicationUtils.encodeUrlMessage(title, clipText))
    }

    private fun handleViewIntent(entry: PhoneEntry) {
        val data = intent.data
        if (data == null) {
            logWarn("Received ACTION_VIEW without data")
            return
        }

        val encodedMessage = CommunicationUtils.encodeSharedViewIntent(data)
        if (encodedMessage != null) {
            sendMessage(entry, encodedMessage)
        } else {
            logWarn("Unsupported or empty ACTION_VIEW uri=$data")
        }
    }

    private fun sendMessage(entry: PhoneEntry, message: String) {
        CommunicationUtils.sendMessage(this, entry, message)
    }

    private val shouldShowYelpErrorMessage: Boolean
        get() {
            if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { clipText ->
                    if (Utils.isYelpShareLink(clipText)) {
                        return true
                    }
                }
            }
            return false
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareReceiverBottomSheet(
    phoneNumbers: List<PhoneEntry>,
    showYelpError: Boolean,
    onSelectNumber: (PhoneEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ShareReceiverBackground()

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            ShareReceiverSheetContent(
                phoneNumbers = phoneNumbers,
                showYelpError = showYelpError,
                onSelectNumber = onSelectNumber,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun ShareReceiverSheetContent(
    phoneNumbers: List<PhoneEntry>,
    showYelpError: Boolean,
    onSelectNumber: (PhoneEntry) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select a contact",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (showYelpError) {
            Text(
                text = "Yelp: Please use View Map -> Menu -> Open In Google Map.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (phoneNumbers.isEmpty()) {
            Text(
                text = "No saved contacts.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(phoneNumbers) { entry ->
                    PhoneNumberRow(entry = entry, onSelectNumber = onSelectNumber)
                }
            }
        }

        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(text = "Cancel")
        }
    }
}

@Composable
private fun PhoneNumberRow(
    entry: PhoneEntry,
    onSelectNumber: (PhoneEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectNumber(entry) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text(text = entry.label, style = MaterialTheme.typography.bodyLarge)
        Text(text = entry.number, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ShareReceiverBackground() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShareReceiverSheetContentPreview() {
    CobaltCometTheme {
        ShareReceiverSheetContent(
            phoneNumbers = listOf(
                PhoneEntry(label = "Driver One", number = "555-0101"),
                PhoneEntry(label = "Driver Two", number = "555-0102"),
            ),
            showYelpError = true,
            onSelectNumber = {},
            onDismiss = {}
        )
    }
}
