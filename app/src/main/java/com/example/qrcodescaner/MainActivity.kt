package com.example.qrcodescaner

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qrcodescaner.ui.theme.QRCodeScanerTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val weight: Double
)

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var productDatabase: List<Product> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Загрузка данных из YAML-файла
        productDatabase = loadProductsFromYaml(this, "products.yaml")

        cameraExecutor = Executors.newSingleThreadExecutor()

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    Log.d("QRCodeScanner", "Permission granted")
                    setContent {
                        QRCodeScanerTheme {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                QRCodeScanner(
                                    modifier = Modifier.padding(innerPadding),
                                    productDatabase = productDatabase
                                )
                            }
                        }
                    }
                } else {
                    Log.e("QRCodeScanner", "Permission denied")
                }
            }

        requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun loadProductsFromYaml(context: MainActivity, fileName: String): List<Product> {
        val yaml = Yaml()
        val inputStream: InputStream = context.assets.open(fileName)
        val data: Map<String, List<Map<String, Any>>> = yaml.load(inputStream)
        val products = data["products"] ?: emptyList()
        return products.map { product ->
            Product(
                id = product["id"].toString(),
                name = product["name"].toString(),
                price = product["price"].toString().toDouble(),
                weight = product["weight"].toString().toDouble()
            )
        }
    }
}

@Composable
fun QRCodeScanner(modifier: Modifier = Modifier, productDatabase: List<Product>) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var scannedCode by remember { mutableStateOf<String?>(null) }
    var scannedProduct by remember { mutableStateOf<Product?>(null) }
    var message by remember { mutableStateOf<String?>(null) } // Для отображения сообщения

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                val previewView = PreviewView(context)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                val scanner = BarcodeScanning.getClient()
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.displayValue?.let { value ->
                                                scannedCode = value
                                                scannedProduct = productDatabase.find { it.id == value }

                                                // Если продукт не найден, выводим сообщение
                                                if (scannedProduct == null) {
                                                    message = "Информация не найдена"
                                                } else {
                                                    message = null // Сбрасываем сообщение
                                                }
                                                Log.d("QRCodeScanner", "QR Code: $value")
                                            }
                                        }
                                    }
                                    .addOnFailureListener {
                                        Log.e("QRCodeScanner", "QR Code scanning failed", it)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("QRCodeScanner", "Camera initialization failed", exc)
                    }
                }, ContextCompat.getMainExecutor(context))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Если сообщение установлено, отображаем его
        message?.let {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(text = it, color = Color.White, fontSize = 16.sp)
            }
        }

        // Если продукт найден, отображаем его данные
        scannedProduct?.let { product ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Column {
                    Text(text = "ID: ${product.id}", color = Color.White, fontSize = 16.sp)
                    Text(text = "Name: ${product.name}", color = Color.White, fontSize = 16.sp)
                    Text(text = "Price: ${product.price}", color = Color.White, fontSize = 16.sp)
                    Text(text = "Weight: ${product.weight}", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}