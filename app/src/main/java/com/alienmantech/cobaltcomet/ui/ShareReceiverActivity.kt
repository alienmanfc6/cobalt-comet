package com.alienmantech.cobaltcomet.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.alienmantech.cobaltcomet.R
import com.alienmantech.cobaltcomet.utils.CommunicationUtils
import com.alienmantech.cobaltcomet.utils.CommunicationUtils.Companion.sendMessage
import com.alienmantech.cobaltcomet.utils.Utils

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var pickerContainer: ViewGroup
    private lateinit var pickerListView: ListView
    private lateinit var errorContainer: ViewGroup
    private lateinit var errorTitleTextView: TextView
    private lateinit var errorMessageTextView: TextView
    private lateinit var errorButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receiver)

        initUI()

        if (shouldShowContactErrorMessage) {
            showNoContactError()
        } else if (shouldShowYelpErrorMessage) {
            showYelpError()
        } else {
            if (shouldShowContactPicker) {
                initPickerListView()
            } else {
                getPhoneNumberAtIndex(0)?.let { to ->
                    handleIntent(to)
                }

                finish()
            }
        }
    }

    private fun initUI() {
        pickerContainer = findViewById(R.id.share_receiver_picker_container)
        pickerListView = findViewById(R.id.share_receiver_picker_listview)
        pickerListView.setOnItemClickListener { _, _, position, _ ->
            onContactPickerItemSelected(position)
        }
        errorContainer = findViewById(R.id.share_receiver_error_container)
        errorTitleTextView = findViewById(R.id.share_receiver_error_title)
        errorMessageTextView = findViewById(R.id.share_receiver_error_message)
        errorButton = findViewById(R.id.share_receiver_error_button)
        errorButton.setOnClickListener {
            finish()
        }
    }

    private fun initPickerListView() {
        val phoneNumbers = Utils.loadPhoneNumbers(this) ?: listOf()

        pickerListView.adapter =
            ArrayAdapter(this, R.layout.share_receiver_contact_picker_list_item, phoneNumbers)
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

    private fun onContactPickerItemSelected(phoneNumberIndex: Int) {
        getPhoneNumberAtIndex(phoneNumberIndex)?.let { to ->
            handleIntent(to)
        }
    }

    private fun sendMessage(to: String, message: String) {
        CommunicationUtils.sendMessage(to, message)
    }

    private val shouldShowContactErrorMessage: Boolean
        get() {
            return phoneNumberCount == 0
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

    private val shouldShowContactPicker: Boolean
        get() {
            return phoneNumberCount > 1
        }

    private val phoneNumberCount: Int
        get() {
            return Utils.loadPhoneNumbers(this)?.size ?: 0
        }

    private fun getPhoneNumberAtIndex(index: Int): String? {
        val phoneNumbers = Utils.loadPhoneNumbers(this)
        return phoneNumbers?.get(index)
    }

    private fun showNoContactError() {
        showErrorMessage(
            title = R.string.share_error_title__no_contacts,
            message = R.string.share_error_message__no_contacts
        )
    }

    private fun showYelpError() {
        showErrorMessage(message = R.string.share_error_message__yelp)
    }

    private fun showErrorMessage(@StringRes title: Int? = null, @StringRes message: Int? = null) {
        errorTitleTextView.setText(title ?: R.string.share_error_title__default)
        errorMessageTextView.setText(message ?: R.string.share_error_message__default)

        pickerContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
    }
}