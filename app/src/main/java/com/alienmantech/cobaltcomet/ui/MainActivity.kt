package com.alienmantech.cobaltcomet.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.R
import com.alienmantech.cobaltcomet.utils.Utils

class MainActivity : AppCompatActivity() {

    companion object {

        private const val REQUEST_SMS_PERMISSION = 1
        private const val REQUEST_DRAW_PERMISSION = 2
    }

    private lateinit var phoneNumberEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        phoneNumberEditText = findViewById(R.id.phone_number_edit_text)

        checkPermissions()
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

    private fun checkPermissions() {
        // SMS
        if (!hasSmsPermission()) {
            requestSmsPermission()
            return // don't check the next one till this one is resolved
        }

        // Draw on apps
        if (!hasDrawOnScreenPermission()) {
            requestDrawOnScreenPermission()
            return // don't check the next one till this one is resolved
        }
    }

    private fun hasSmsPermission(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED)
    }

    private fun hasDrawOnScreenPermission(): Boolean {
        return (Settings.canDrawOverlays(this))
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS),
            REQUEST_SMS_PERMISSION
        )
    }

    private fun requestDrawOnScreenPermission() {
        val dialog = DrawOnScreenPermissionDialog.newInstance(object :
            DrawOnScreenPermissionDialog.OnResultListener {
            override fun positiveClick() {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
                    REQUEST_DRAW_PERMISSION
                )
            }
        })
        dialog.show(supportFragmentManager, "Draw-On-Screen-Dialog")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_DRAW_PERMISSION) {
            // check permissions again, we may need to look for more
            checkPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SMS_PERMISSION) {
            // check permissions again, we may need to look for more
            checkPermissions()
        }
    }
}