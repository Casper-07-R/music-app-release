package com.koi.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.koi.backend.share.PlaylistShareCodec
import com.koi.ui.R
import com.koi.ui.theme.*
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerSheet(
    isImportingImage: Boolean = false,
    onQrCodeScanned: (String) -> Unit,
    onImportImageUri: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportImageUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var invalidQrHint by remember { mutableStateOf<String?>(null) }
    val scanLocked = remember { AtomicBoolean(false) }

    LaunchedEffect(invalidQrHint) {
        if (invalidQrHint != null) {
            delay(1800)
            invalidQrHint = null
            scanLocked.set(false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RogObsidian)
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                val barcodeScanner = remember {
                    BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )
                }

                DisposableEffect(Unit) {
                    onDispose {
                        runCatching { cameraProvider?.unbindAll() }
                        barcodeScanner.close()
                        cameraExecutor.shutdown()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            @Suppress("DEPRECATION")
                            val analysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 960))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analyzer ->
                                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !scanLocked.get()) {
                                            val inputImage = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                                    if (raw != null && scanLocked.compareAndSet(false, true)) {
                                                        if (PlaylistShareCodec.isKoiPlaylistPayload(raw)) {
                                                            onQrCodeScanned(raw)
                                                        } else {
                                                            invalidQrHint = "Not a Koi playlist QR code"
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            runCatching {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder Overlay with Animated Gold Scan Line
                ScannerViewfinderOverlay(modifier = Modifier.fillMaxSize())
            } else {
                // Camera Permission Fallback
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(RogSurfaceDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera permission needed",
                        fontSize = 18.sp,
                        fontFamily = CircularFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = RogTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allow camera access to scan a friend's Koi playlist QR, or import a QR image directly from your gallery.",
                        fontSize = 13.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = RogGoldPrimary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Enable Camera",
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogObsidian
                        )
                    }
                }
            }

            // Top Controls Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RogObsidian.copy(alpha = 0.68f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close Scanner",
                        tint = RogTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Scan Playlist QR",
                    fontSize = 16.sp,
                    fontFamily = CircularFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RogTextPrimary
                )

                if (hasCameraPermission && camera?.cameraInfo?.hasFlashUnit() == true) {
                    IconButton(
                        onClick = {
                            val next = !isTorchEnabled
                            isTorchEnabled = next
                            camera?.cameraControl?.enableTorch(next)
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(RogObsidian.copy(alpha = 0.68f))
                    ) {
                        Icon(
                            imageVector = if (isTorchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = "Toggle Flash",
                            tint = if (isTorchEnabled) RogGoldPrimary else RogTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // Bottom Hint & Import from Image Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (invalidQrHint != null) {
                    Surface(
                        color = Color(0xFFE53935).copy(alpha = 0.92f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = invalidQrHint!!,
                            fontSize = 13.sp,
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                } else if (hasCameraPermission) {
                    Text(
                        text = "Align a Koi playlist QR code inside the frame",
                        fontSize = 13.sp,
                        fontFamily = CircularFontFamily,
                        color = RogTextPrimary.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = {
                        if (!isImportingImage) {
                            imagePickerLauncher.launch("image/*")
                        }
                    },
                    enabled = !isImportingImage,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RogSurfaceDark),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImportingImage) {
                        CircularProgressIndicator(
                            color = RogGoldPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reading QR from image...",
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = RogTextPrimary,
                            fontSize = 14.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = RogGoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import QR from Image",
                            fontFamily = CircularFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = RogTextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerViewfinderOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "qr_scan_beam")
    val beamProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_beam_y"
    )

    Canvas(modifier = modifier) {
        val boxSize = (size.minDimension * 0.72f).coerceAtMost(300.dp.toPx())
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2f - 24.dp.toPx()
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerRadius = 22.dp.toPx()

        val cutout = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }
        clipPath(cutout, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.58f))
        }

        // Corner brackets
        val strokeW = 4.dp.toPx()
        val arm = 28.dp.toPx()
        val bracketColor = RogGoldPrimary

        // Top-Left
        drawLine(bracketColor, Offset(left, top + arm), Offset(left, top + cornerRadius / 2), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + cornerRadius / 2, top), Offset(left + arm, top), strokeW, StrokeCap.Round)
        // Top-Right
        drawLine(bracketColor, Offset(right - arm, top), Offset(right - cornerRadius / 2, top), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, top + cornerRadius / 2), Offset(right, top + arm), strokeW, StrokeCap.Round)
        // Bottom-Left
        drawLine(bracketColor, Offset(left, bottom - arm), Offset(left, bottom - cornerRadius / 2), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(left + cornerRadius / 2, bottom), Offset(left + arm, bottom), strokeW, StrokeCap.Round)
        // Bottom-Right
        drawLine(bracketColor, Offset(right - arm, bottom), Offset(right - cornerRadius / 2, bottom), strokeW, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, bottom - cornerRadius / 2), Offset(right, bottom - arm), strokeW, StrokeCap.Round)

        // Animated scan line
        val scanY = top + 16.dp.toPx() + (boxSize - 32.dp.toPx()) * beamProgress
        drawLine(
            color = RogGoldBright.copy(alpha = 0.85f),
            start = Offset(left + 16.dp.toPx(), scanY),
            end = Offset(right - 16.dp.toPx(), scanY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
