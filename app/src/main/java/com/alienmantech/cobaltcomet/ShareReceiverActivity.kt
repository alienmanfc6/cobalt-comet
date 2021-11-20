package com.alienmantech.cobaltcomet

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

        finish()
    }

    fun showYelpError() {
        errorMessageTextView.setText(R.string.share_error_message__yelp)

        errorTitleTextView.visibility = View.VISIBLE
        errorMessageTextView.visibility = View.VISIBLE
        errorButton.visibility = View.VISIBLE
    }
}