package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
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
                    val ssp = data.schemeSpecificPart.toString()
                    val query = data.query.toString()
                    val values = ssp
                        .replace(query, "")
                        .replace("?", "")
                        .split(",")
                    sendMessage(to, CommunicationUtils.encodeGeoMessage(values[0], values[1]))
                }
            }
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
