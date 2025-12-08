package com.alienmantech.cobaltcomet

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.alienmantech.cobaltcomet.ui.theme.CobaltCometTheme
import com.alienmantech.cobaltcomet.utils.Utils
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

class FirebaseIdActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CobaltCometTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FirebaseIdScreen(contentPadding = innerPadding)
                }
            }
        }
    }
}

@Composable
fun FirebaseIdScreen(contentPadding: PaddingValues = PaddingValues(0.dp)) {
    var token by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Utils.loadFirebaseId()
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    token = task.result
                    errorMessage = null
                } else {
                    errorMessage = task.exception?.localizedMessage
                        ?: "Unable to fetch Firebase Cloud ID"
                }
            }
    }

    val qrBitmap = remember(token) {
        token?.let { generateQrCode(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(contentPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Firebase Cloud ID",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when {
            isLoading -> {
                CircularProgressIndicator()
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage ?: "Unknown error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        Utils.loadFirebaseId()
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    token = task.result
                                } else {
                                    errorMessage = task.exception?.localizedMessage
                                        ?: "Unable to fetch Firebase Cloud ID"
                                }
                            }
                    },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(text = "Retry")
                }
            }

            qrBitmap != null -> {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Firebase Cloud ID QR code",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                token?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            token != null -> {
                Text(text = token ?: "")
            }
        }
    }
}

private fun generateQrCode(data: String, size: Int = 800): androidx.compose.ui.graphics.ImageBitmap? {
    return try {
        val bitMatrix: BitMatrix = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.asImageBitmap()
    } catch (_: WriterException) {
        null
    }
}

@Preview(showBackground = true)
@Composable
fun FirebaseIdScreenPreview() {
    CobaltCometTheme {
        FirebaseIdScreen()
    }
}
