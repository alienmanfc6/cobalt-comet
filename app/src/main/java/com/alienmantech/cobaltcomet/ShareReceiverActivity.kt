package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
        setContent {
            CobaltCometTheme {
                Text("Hello World")
            }
        }
    }

    private fun handleIntent(to: String) {
        if (intent.action.equals(Intent.ACTION_SEND)) {
            if (intent.type.equals("text/plain")) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { clipText ->
                    // send encoded data
                    sendMessage(to, CommunicationUtils.encodeUrlMessage(clipText))
                    // send just the url
                    Utils.parseUrl(clipText)?.let { url -> sendMessage(to, url) }
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