package com.alienmantech.cobaltcomet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alienmantech.cobaltcomet.utils.Utils
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

class QrScannerActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 2001
        private const val JWK_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
    }

    private lateinit var barcodeView: DecoratedBarcodeView
    private var isHandlingResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        barcodeView = findViewById(R.id.barcode_scanner)
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (isHandlingResult) return

                isHandlingResult = true
                barcodeView.pause()
                handleScanResult(result.text)
            }

            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {
                // no-op
            }
        })

        checkCameraPermissionAndStart()
    }

    override fun onResume() {
        super.onResume()
        if (::barcodeView.isInitialized && !isHandlingResult && hasCameraPermission()) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        barcodeView.pause()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startScanning() {
        isHandlingResult = false
        barcodeView.resume()
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
