package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.Utils

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val phoneNumbers = Utils.loadPhoneNumbers(this).orEmpty()

        if (phoneNumbers.size == 1) {
            handleIntent(phoneNumbers.first())
            finish()
            return
        }

        setContent {
            CobaltCometTheme {
                ShareReceiverScreen(
                    phoneNumbers = phoneNumbers,
                    showYelpError = shouldShowYelpErrorMessage,
                    onSelectNumber = { selectedNumber ->
                        handleIntent(selectedNumber)
                        finish()
                    }
                )
            }
        }
    }

    private fun handleIntent(to: String) {
        if (intent.action.equals(Intent.ACTION_SEND)) {
            if (intent.type.equals("text/plain")) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { clipText ->
                    // send encoded data and map link together
                    sendMessage(to, CommunicationUtils.encodeUrlMessage(clipText))
                }
            }
        } else if (intent.action.equals(Intent.ACTION_VIEW)) {
            intent.data?.let { data ->
                if (data.scheme.equals("geo")) {
                    val (lat, lng, locationName) = parseGeoData(data)
                    if (lat.isNotEmpty() && lng.isNotEmpty()) {
                        sendMessage(to, CommunicationUtils.encodeGeoMessage(lat, lng, locationName))
                    } else if (locationName.isNotEmpty()) {
                        sendMessage(to, CommunicationUtils.encodeGeoMessage("", "", locationName))
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

    private fun sendMessage(to: String, message: String) {
        CommunicationUtils.sendMessage(to, message)
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

@Composable
private fun ShareReceiverScreen(
    phoneNumbers: List<String>,
    showYelpError: Boolean,
    onSelectNumber: (String) -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select a phone number",
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
                    text = "No saved phone numbers.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(phoneNumbers) { number ->
                        PhoneNumberRow(number = number, onSelectNumber = onSelectNumber)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneNumberRow(
    number: String,
    onSelectNumber: (String) -> Unit
) {
    Text(
        text = number,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectNumber(number) }
            .padding(vertical = 12.dp, horizontal = 8.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun ShareReceiverScreenPreview() {
    CobaltCometTheme {
        ShareReceiverScreen(
            phoneNumbers = listOf("555-0101", "555-0102"),
            showYelpError = true,
            onSelectNumber = {}
        )
    }
}
