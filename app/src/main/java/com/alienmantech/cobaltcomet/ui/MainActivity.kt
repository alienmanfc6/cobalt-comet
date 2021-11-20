package com.alienmantech.cobaltcomet.ui

import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.alienmantech.cobaltcomet.R
import com.alienmantech.cobaltcomet.utils.Utils

class MainActivity : AppCompatActivity() {
    private lateinit var phoneNumberEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        phoneNumberEditText = findViewById(R.id.phone_number_edit_text)
    }

    override fun onResume() {
        super.onResume()

        loadPhoneNumber()
    }

    override fun onPause() {
        super.onPause()

        savePhoneNumber()
    }

    private fun loadPhoneNumber() {
        val pref = Utils.getSavePref(this)

        pref.getString(Utils.PREF_PHONE_NUMBER, null)?.let {
            phoneNumberEditText.setText(it)
        }
    }

    private fun savePhoneNumber() {
        val editor = Utils.getSavePref(this).edit()

        phoneNumberEditText.text?.toString()?.let {
            editor.putString(Utils.PREF_PHONE_NUMBER, it)
        }

        editor.apply()
    }
}