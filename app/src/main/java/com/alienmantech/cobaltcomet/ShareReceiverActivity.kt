package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: 11/18/21 create sharedPref to store the phone number to send to
        val to = ""

        if (intent.action.equals(Intent.ACTION_SEND)) {
            if (intent.type.equals("text/plain")) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { clipText ->
                    // send encoded data
                    CommunicationUtils.sendMessage(to, CommunicationUtils.encodeMessage(clipText))
                    // send just the url
                    Utils.parseUrl(clipText)?.let { url -> CommunicationUtils.sendMessage(to, url) }
                }
            }
        }

        finish()
    }
}