package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alienmantech.cobaltcomet.models.ContactType
import com.alienmantech.cobaltcomet.models.PhoneEntry
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
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
        if (intent.action.equals(Intent.ACTION_SEND)) {
            if (intent.type.equals("text/plain")) {
                val title = intent.getStringExtra(Intent.EXTRA_TITLE)
                val clipText = intent.getStringExtra(Intent.EXTRA_TEXT)
                // send encoded data and map link together
                sendMessage(entry, CommunicationUtils.encodeUrlMessage(title, clipText))
            }
        } else if (intent.action.equals(Intent.ACTION_VIEW)) {
            intent.data?.let { data ->
                if (data.scheme.equals("geo")) {
                    val (lat, lng, locationName) = parseGeoData(data)
                    if (lat.isNotEmpty() && lng.isNotEmpty()) {
                        sendMessage(entry, CommunicationUtils.encodeGeoMessage(lat, lng, locationName))
                    } else if (locationName.isNotEmpty()) {
                        sendMessage(entry, CommunicationUtils.encodeGeoMessage("", "", locationName))
                    }
                }
            }
        }
    }

    private fun parseGeoData(data: Uri): Triple<String, String, String> {
        val queryParameter = data.getQueryParameter("q")
        if (!queryParameter.isNullOrBlank()) {
            val decoded = Uri.decode(queryParameter)
            val labelStart = decoded.indexOf("(")
            val labelEnd = decoded.lastIndexOf(")")
            val hasLabel = labelStart != -1 && labelEnd > labelStart

            val coordinatePart = if (hasLabel) decoded.substring(0, labelStart) else decoded
            val label = if (hasLabel) decoded.substring(labelStart + 1, labelEnd) else ""

            val coords = coordinatePart.split(",")
            if (coords.size >= 2 && coords[0].isNotBlank() && coords[1].isNotBlank()) {
                return Triple(coords[0], coords[1], label)
            }

            if (coordinatePart.isNotBlank()) {
                return Triple("", "", coordinatePart)
            }
        }

        val schemeSpecificPart = data.schemeSpecificPart.substringBefore("?")
        val values = schemeSpecificPart.split(",")
        return if (values.size >= 2 && values[0].isNotBlank() && values[1].isNotBlank()) {
            Triple(values[0], values[1], "")
        } else {
            Triple("", "", "")
        }
    }

    private fun sendMessage(entry: PhoneEntry, message: String) {
        CommunicationUtils.sendMessage(this, entry, message)
    }

    private val shouldShowYelpErrorMessage: Boolean
        get() {
            if (intent.action.equals(Intent.ACTION_SEND)) {
                if (intent.type.equals("text/plain")) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let { clipText ->
                        if (Utils.isYelpShareLink(clipText)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

    private fun showYelpError() {
        showErrorMessage(message = "Yelp: Please use View Map -> Menu -> Open In Google Map.")
    }

    private fun showErrorMessage(message: String) {
        //TODO: show error message
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
        val subtitle = if (entry.type == ContactType.BLUETOOTH) {
            "Bluetooth • ${entry.number}"
        } else {
            entry.number
        }
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
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
