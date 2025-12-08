package com.alienmantech.cobaltcomet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alienmantech.cobaltcomet.utils.Utils
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.RemoteJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.SignedJWT
import java.net.URL
import java.util.Date

class QrScannerActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 2001
        private const val JWK_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private var isHandlingResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CobaltCometTheme {
                QrScannerScreen(
                    onBarcodeDetected = { token ->
                        if (isHandlingResult) return@QrScannerScreen

                        isHandlingResult = true
                        barcodeView.pause()
                        handleScanResult(token)
                    },
                    onBarcodeViewReady = { view ->
                        barcodeView = view
                        checkCameraPermissionAndStart()
                    },
                    requestPermission = { requestCameraPermission() },
                    hasPermission = { hasCameraPermission() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::barcodeView.isInitialized && !isHandlingResult && hasCameraPermission()) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startScanning()
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            finish()
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (hasCameraPermission()) {
            startScanning()
        } else {
            requestCameraPermission()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }

    private fun startScanning() {
        isHandlingResult = false
        if (::barcodeView.isInitialized) {
            barcodeView.resume()
        }
    }

    private fun handleScanResult(rawValue: String?) {
        val token = rawValue ?: run {
            finish()
            return
        }

        lifecycleScope.launch {
            val isValid = withContext(Dispatchers.IO) { validateFirebaseIdToken(token) }

            if (!isValid) {
                showInvalidTokenDialog()
                return@launch
            }

            promptForContactName(token)
        }
    }

    private suspend fun validateFirebaseIdToken(token: String): Boolean {
        return try {
            val signedJwt = SignedJWT.parse(token)
            val jwkSource = RemoteJWKSet<SecurityContext>(URL(JWK_URL))
            val selector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
            val key = selector.selectJWSKeys(signedJwt.header, null).firstOrNull() as? RSAKey ?: return false
            val verifier = RSASSAVerifier(key)

            if (!signedJwt.verify(verifier)) {
                return false
            }

            val claims = signedJwt.jwtClaimsSet
            val projectId = Firebase.app.options.projectId

            val issuer = "https://securetoken.google.com/$projectId"
            val isIssuerValid = claims.issuer == issuer
            val isAudienceValid = claims.audience.contains(projectId)
            val isNotExpired = claims.expirationTime?.after(Date()) == true

            isIssuerValid && isAudienceValid && isNotExpired
        } catch (e: Exception) {
            false
        }
    }

    private fun promptForContactName(token: String) {
        val input = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Save Contact")
            .setMessage("Enter a name for this Firebase ID token")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    saveContact(name, token)
                }
                dialog.dismiss()
                finish()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }

    private fun saveContact(name: String, token: String) {
        val currentContacts = Utils.loadQrContacts(this).toMutableMap()
        currentContacts[name] = token
        Utils.saveQrContacts(this, currentContacts)
    }

    private fun showInvalidTokenDialog() {
        AlertDialog.Builder(this)
            .setTitle("Invalid QR Code")
            .setMessage("The scanned code is not a valid Firebase ID token.")
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }
}

@Composable
fun QrScannerScreen(
    onBarcodeDetected: (String?) -> Unit,
    onBarcodeViewReady: (DecoratedBarcodeView) -> Unit,
    requestPermission: () -> Unit,
    hasPermission: () -> Boolean,
    modifier: Modifier = Modifier
) {
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }

    LaunchedEffect(hasPermission()) {
        if (!hasPermission()) {
            requestPermission()
        } else {
            barcodeView?.resume()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DecoratedBarcodeAndroidView(
            onBarcodeDetected = onBarcodeDetected,
            onBarcodeViewReady = {
                barcodeView = it
                onBarcodeViewReady(it)
            }
        )

        if (!hasPermission()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun DecoratedBarcodeAndroidView(
    onBarcodeDetected: (String?) -> Unit,
    onBarcodeViewReady: (DecoratedBarcodeView) -> Unit
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        onBarcodeDetected(result.text)
                    }

                    override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {
                        // no-op
                    }
                })
            }.also(onBarcodeViewReady)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true)
@Composable
private fun QrScannerScreenPreview() {
    CobaltCometTheme {
        QrScannerScreen(
            onBarcodeDetected = {},
            onBarcodeViewReady = {},
            requestPermission = {},
            hasPermission = { true }
        )
    }
}
