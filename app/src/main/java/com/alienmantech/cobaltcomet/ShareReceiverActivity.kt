package com.alienmantech.cobaltcomet

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var errorTitleTextView: TextView
    private lateinit var errorMessageTextView: TextView
    private lateinit var errorButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receiver)

        errorTitleTextView = findViewById(R.id.share_receiver_error_title)
        errorMessageTextView = findViewById(R.id.share_receiver_error_message)
        errorButton = findViewById(R.id.share_receiver_error_button)
        errorButton.setOnClickListener {
            finish()
        }

        handleIntent()
    }

    private fun handleIntent() {
        if (intent.action.equals(Intent.ACTION_SEND)) {
            if (intent.type.equals("text/plain")) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { clipText ->
                    if (Utils.isYelpShareLink(clipText)) {
                        showYelpError()
                        return // return so we don't close the activity
                    }

                    // send encoded data
                    sendMessage(CommunicationUtils.encodeMessage(clipText))
                    // send just the url
                    Utils.parseUrl(clipText)?.let { url -> sendMessage(url) }
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
                    sendMessage(CommunicationUtils.encodeGeoMessage(values[0], values[1]))
                }
            }
        }

        finish()
    }

    fun sendMessage(message: String) {
        val to = Utils.getSavePref(this).getString(Utils.PREF_PHONE_NUMBER, null)
        CommunicationUtils.sendMessage(to, message)
    }

    fun showYelpError() {
        errorMessageTextView.setText(R.string.share_error_message__yelp)

        errorTitleTextView.visibility = View.VISIBLE
        errorMessageTextView.visibility = View.VISIBLE
        errorButton.visibility = View.VISIBLE
    }
}