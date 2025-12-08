package com.alienmantech.cobaltcomet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.alienmantech.cobaltcomet.models.StoredFirebaseMessage
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.Utils
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_SMS_PERMISSION = 1
        private const val REQUEST_DRAW_PERMISSION = 2
    }

    private var phoneNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureSmsPermissionOnStart()
        enableEdgeToEdge()
        setContent {
            CobaltCometTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    ) {
                        PhoneNumberScreen(
                            phoneNumber = phoneNumber,
                            onPhoneNumberChange = { updatedValue ->
                                phoneNumber = updatedValue
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ScannerLauncher(onLaunchScanner = {
                            startActivity(Intent(this@MainActivity, QrScannerActivity::class.java))
                        })

                        FirebaseIdScreen()

                        FirebaseMessagesScreen(modifier = Modifier.weight(1f, fill = true))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        phoneNumber = loadPhoneNumber()
    }

    override fun onPause() {
        super.onPause()

        savePhoneNumber(phoneNumber)
    }

    private fun ensureSmsPermissionOnStart() {
        if (!hasSmsPermission()) {
            requestSmsPermission()
            return
        }

        // SMS permission already granted, continue checking additional permissions.
        checkPermissions()
    }

    private fun loadPhoneNumber(): String {
        return Utils.loadPhoneNumbers(this)?.let {
            Utils.listToCsv(it)
        } ?: ""
    }

    private fun savePhoneNumber(phoneNumber: String) {
        Utils.savePhoneNumbers(this, Utils.csvToList(phoneNumber))
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
//        val dialog = DrawOnScreenPermissionDialog.newInstance(object :
//            DrawOnScreenPermissionDialog.OnResultListener {
//            override fun positiveClick() {
//                startActivityForResult(
//                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
//                    REQUEST_DRAW_PERMISSION
//                )
//            }
//        })
//        dialog.show(supportFragmentManager, "Draw-On-Screen-Dialog")
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

@Composable
fun PhoneNumberScreen(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Phone Number",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        TextField(
            value = phoneNumber,
            onValueChange = onPhoneNumberChange,
            modifier = Modifier.width(200.dp),
            placeholder = { Text(text ="1112223333") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            singleLine = true
        )
    }
}

@Composable
fun ScannerLauncher(onLaunchScanner: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Scan a Firebase ID token",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(onClick = onLaunchScanner) {
            Text(text = "Open QR Scanner")
        }
    }
}

@Composable
fun FirebaseIdScreen() {
    var token by remember { mutableStateOf<String?>(null) }

    token = Utils.loadFirebaseId(LocalContext.current)

    val qrBitmap = remember(token) {
        token?.let { Utils.generateQrCode(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Firebase Cloud ID",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when {
            qrBitmap != null -> {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Firebase Cloud ID QR code",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            else -> {
                Text(
                    text = "Unable to load QR code.",
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneNumberScreenPreview() {
    MaterialTheme {
        PhoneNumberScreen(phoneNumber = "555-1234", onPhoneNumberChange = {})
    }
}

@Composable
fun FirebaseMessagesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val messages = remember(context) {
        Utils.loadFirebaseMessages(context).sortedByDescending { it.timestamp }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Firebase Messages",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (messages.isEmpty()) {
            Text(
                text = "No messages received yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                items(messages) { message ->
                    FirebaseMessageRow(message)
                }
            }
        }
    }
}

@Composable
fun FirebaseMessageRow(message: StoredFirebaseMessage) {
    val formattedTimestamp = remember(message.timestamp) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (message.from.isNotBlank()) {
            Text(
                text = "From: ${message.from}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyLarge
        )

        Text(
            text = formattedTimestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}