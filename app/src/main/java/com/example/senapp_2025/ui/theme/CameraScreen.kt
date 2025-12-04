// file: src/main/java/com/senapp/ui/CameraScreen.kt
package com.senapp.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.senapp.ml.HandDetector
import com.senapp.ml.computeCalibration
import com.senapp.match.RuleMatcher
import com.senapp.model.Finger
import com.senapp.model.SignSpec
import com.senapp.net.InterpretApi
import com.senapp.net.TokenDto
import kotlinx.coroutines.*
import java.util.concurrent.Executors

// =============================
// Estado compartido para overlay
// =============================
data class FrameInfo(val width: Int, val height: Int, val isFrontCamera: Boolean = false)
val currentFrameInfo = mutableStateOf<FrameInfo?>(null)
val currentResult = mutableStateOf<HandLandmarkerResult?>(null)

// =============================
// Origen del texto reconocido
// =============================
enum class InterpretSource { LOCAL, SERVER }

// =============================
// Debouncer simple HTTP
// =============================
class KtxDebouncer(
    private val coroutineScope: CoroutineScope,
    private val delayMs: Long = 600L
) {
    private var job: Job? = null
    fun submit(block: suspend () -> Unit) {
        job?.cancel()
        job = coroutineScope.launch(Dispatchers.IO) {
            delay(delayMs)
            block()
        }
    }
}

// =============================
// Parámetros de estabilidad
// =============================
private const val STABLE_FRAMES_THRESHOLD = 3
private const val RESEND_COOLDOWN_MS = 1500L

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Se requiere permiso de cámara")
                Spacer(modifier = Modifier.height(16.dp))
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    (context as androidx.activity.ComponentActivity),
                    Manifest.permission.CAMERA
                )
                if (shouldShowRationale) {
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Volver a intentar")
                    }
                } else {
                    Button(onClick = {
                        val intent = android.content.Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }) { Text("Abrir Ajustes") }
                }
            }
        }
    } else {
        CameraPreview(lifecycleOwner = lifecycleOwner)
    }
}

@Composable
fun CameraPreview(lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    val didServerSmokeTest = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val httpDebouncer = remember { KtxDebouncer(coroutineScope, delayMs = 600L) }

    var handDetector by remember { mutableStateOf<HandDetector?>(null) }

    // ===== Estado de UI =====
    val statusText = remember { mutableStateOf("buscando manos...") }
    val lastSeenMs = remember { mutableStateOf(0L) }

    // Texto reconocido + fuente (local/server)
    val recognizedText = remember { mutableStateOf("Aquí aparecerá el texto reconocido") }
    val recognizedSource = remember { mutableStateOf(InterpretSource.LOCAL) }

    // Hold para no volver a [local] inmediatamente después de respuesta del servidor
    val serverUiHoldUntilMs = remember { mutableStateOf(0L) }

    // ===== Estado de calibración =====
    var isCalibrated by remember { mutableStateOf(false) }
    var calibrationProgress by remember { mutableStateOf(0f) }
    var calibrationMessage by remember { mutableStateOf("Coloca tus manos dentro de las guías") }

    // Histeresis de gesto + cooldown de envíos
    val lastLabel = remember { mutableStateOf<String?>(null) }
    val stableCount = remember { mutableStateOf(0) }
    val lastSentLabel = remember { mutableStateOf<String?>(null) }
    val lastSentAt = remember { mutableStateOf(0L) }

    // Tamaño REAL del buffer de PREVIEW
    val previewBufferSize = remember { mutableStateOf<android.util.Size?>(null) }

    // Catálogo LOCAL de señas
    val signSpecs = remember { mutableStateOf<List<SignSpec>>(emptyList()) }

    // Inicializa detector + catálogo local + ping + smoke test
    LaunchedEffect(Unit) {
        val hd = HandDetector(context)
        hd.initialize("ml/hand_landmarker.task")
        handDetector = hd

        // Catálogo local (las 10 señas)
        signSpecs.value = com.senapp.data.LocalSignRules.static10()
        Log.d("CameraPreview", "LocalRules: cargadas ${signSpecs.value.size} señas")

        try {
            val ok = InterpretApi.ping()
            Log.i("CameraScreen", "SVR ping -> $ok  (${InterpretApi.BASE_URL})")

            // 🔥 Prueba de humo: forzar un POST una sola vez para ver [srv]
            if (ok && !didServerSmokeTest.value) {
                didServerSmokeTest.value = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val now = System.currentTimeMillis()
                        val res = InterpretApi.interpret(
                            listOf(
                                TokenDto(
                                    signId = "mano_abierta_5",
                                    tStart = now,
                                    tEnd = now + 200,
                                    conf = 0.5f
                                )
                            )
                        )
                        Log.i("CameraScreen", "SVR smoke test text='${res.text}'")
                        withContext(Dispatchers.Main) {
                            recognizedText.value = res.text.ifBlank { "ok servidor (sin texto)" }
                            recognizedSource.value = InterpretSource.SERVER
                            serverUiHoldUntilMs.value = System.currentTimeMillis() + 2500
                        }
                    } catch (e: Exception) {
                        Log.w("CameraScreen", "SVR smoke test failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("CameraScreen", "SVR ping error: ${e.message}")
        }
    }

    // Matching + histeresis + llamada al servidor (con métrica de debug)
    LaunchedEffect(currentResult.value) {
        val DEBUG_FORCE_POST = false
        val count = currentResult.value?.landmarks()?.size ?: 0
        val now = System.currentTimeMillis()

        if (count > 0) {

            // ========= BLOQUE DE CALIBRACIÓN =========
            if (!isCalibrated) {
                val landmarksPerHand = currentResult.value!!.landmarks()

                val leftHand = landmarksPerHand.getOrNull(0)
                val rightHand = landmarksPerHand.getOrNull(1)

                val leftPoints = leftHand?.map { lm -> lm.x() to lm.y() }
                val rightPoints = rightHand?.map { lm -> lm.x() to lm.y() }

                val calibration = computeCalibration(leftPoints, rightPoints)

                calibrationProgress = calibration.score
                calibrationMessage = calibration.message

                statusText.value = when {
                    leftHand == null || rightHand == null ->
                        "Coloca ambas manos dentro de las guías"
                    calibration.isCalibrated ->
                        "Calibración completa"
                    else ->
                        "Calibrando manos..."
                }

                if (calibration.isCalibrated) {
                    isCalibrated = true
                }

                // Mientras no esté calibrado, no hacemos match ni llamadas al server
                if (!isCalibrated) {
                    return@LaunchedEffect
                }
            }
            // ========= FIN BLOQUE CALIBRACIÓN =========

            val firstHand = currentResult.value!!.landmarks()[0]

            // ===== Debug de métricas =====
            run {
                fun vec(
                    a: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
                    b: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
                ) = floatArrayOf(b.x() - a.x(), b.y() - a.y(), (b.z() ?: 0f) - (a.z() ?: 0f))

                fun dot(u: FloatArray, v: FloatArray) = u[0] * v[0] + u[1] * v[1] + u[2] * v[2]

                fun norm(u: FloatArray) =
                    kotlin.math.sqrt(kotlin.math.max(1e-12f, dot(u, u)))

                fun cosine(u: FloatArray, v: FloatArray) =
                    (dot(u, v) / (norm(u) * norm(v))).coerceIn(-1f, 1f)

                fun angleDeg(u: FloatArray, v: FloatArray): Float {
                    val c = cosine(u, v).coerceIn(-1f, 1f)
                    return Math.toDegrees(kotlin.math.acos(c).toDouble()).toFloat()
                }

                fun ray(tA: Int, tB: Int): Float {
                    val w = firstHand[0]
                    val u = vec(w, firstHand[tA])
                    val v = vec(w, firstHand[tB])
                    return angleDeg(u, v)
                }

                val ext = RuleMatcher.extensionScores(firstHand)
                val aTI = ray(4, 8); val aTP = ray(4, 20)
                val aIM = ray(8, 12); val aMR = ray(12, 16); val aRP = ray(16, 20)
                Log.d(
                    "Metrics",
                    "ext T/I/M/R/P = ${"%.2f".format(ext[Finger.THUMB] ?: 0f)}/" +
                            "${"%.2f".format(ext[Finger.INDEX] ?: 0f)}/" +
                            "${"%.2f".format(ext[Finger.MIDDLE] ?: 0f)}/" +
                            "${"%.2f".format(ext[Finger.RING] ?: 0f)}/" +
                            "${"%.2f".format(ext[Finger.PINKY] ?: 0f)} | " +
                            "angles TI=$aTI TP=$aTP IM=$aIM MR=$aMR RP=$aRP"
                )
            }
            // ===============================================================

            val label = RuleMatcher.matchFirstLabel(firstHand, signSpecs.value)
            Log.d("Match", "label=$label stable=${stableCount.value} last=${lastLabel.value}")

            if (label != null) {
                // Histeresis
                if (lastLabel.value == label) stableCount.value += 1
                else {
                    lastLabel.value = label
                    stableCount.value = 1
                }

                // Mostrar "interpretando…" cuando se estabiliza por primera vez
                if (stableCount.value == STABLE_FRAMES_THRESHOLD &&
                    recognizedSource.value != InterpretSource.SERVER
                ) {
                    withContext(Dispatchers.Main) {
                        recognizedText.value = "interpretando…"
                        recognizedSource.value = InterpretSource.SERVER
                    }
                }

                // ¿Podemos enviar?
                val canSend = stableCount.value >= STABLE_FRAMES_THRESHOLD &&
                        (lastSentLabel.value != label ||
                                (now - lastSentAt.value) > RESEND_COOLDOWN_MS)

                if (canSend) {
                    lastSentLabel.value = label
                    lastSentAt.value = now
                    Log.d("Match", ">> ENVIANDO al server: $label")

                    httpDebouncer.submit {
                        try {
                            val startTs = System.currentTimeMillis()
                            val res = InterpretApi.interpret(
                                listOf(
                                    TokenDto(
                                        label,
                                        startTs,
                                        startTs + 200,
                                        0.95f
                                    )
                                )
                            )

                            Log.d("CameraPreview", "Interpret result for '$label' -> '${res.text}'")

                            withContext(Dispatchers.Main) {
                                val txt = res.text
                                // 🔹 Aunque venga vacío, mostramos algo para que sepas qué pasó
                                recognizedText.value =
                                    if (txt.isBlank())
                                        "[srv] sin resultado para '$label'"
                                    else
                                        txt

                                recognizedSource.value = InterpretSource.SERVER
                                serverUiHoldUntilMs.value = System.currentTimeMillis() + 2500
                            }
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Interpret error for '$label'", e)
                            withContext(Dispatchers.Main) {
                                recognizedText.value = "[error srv] ${e.message ?: "sin detalles"}"
                                recognizedSource.value = InterpretSource.LOCAL
                            }
                        }
                    }
                }

            } else {
                // (Modo prueba) Fuerza una llamada para validar red/servidor
                if (DEBUG_FORCE_POST && (now - lastSentAt.value) > 2000L) {
                    lastSentAt.value = now
                    Log.d("Match", "FORCE POST: mano_abierta_5")
                    httpDebouncer.submit {
                        try {
                            val res = InterpretApi.interpret(
                                listOf(
                                    TokenDto(
                                        "mano_abierta_5",
                                        System.currentTimeMillis(),
                                        System.currentTimeMillis() + 200,
                                        0.5f
                                    )
                                )
                            )
                            withContext(Dispatchers.Main) {
                                recognizedText.value =
                                    res.text.ifBlank { "[FORCE] sin texto" }
                                recognizedSource.value = InterpretSource.SERVER
                                serverUiHoldUntilMs.value =
                                    System.currentTimeMillis() + 2500
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                // Sin label → limpia si ya pasó el grace period
                if (now - lastSeenMs.value > 800 && now >= serverUiHoldUntilMs.value) {
                    withContext(Dispatchers.Main) {
                        recognizedText.value = "Aquí aparecerá el texto reconocido"
                        recognizedSource.value = InterpretSource.LOCAL
                    }
                }
                lastLabel.value = null
                stableCount.value = 0
            }
        } else {
            // ===== SIN MANOS DETECTADAS =====
            val sinceLastHands = now - lastSeenMs.value

            // Si ya estábamos calibrados y llevamos > 3s sin ver manos,
            // asumimos que terminó la conversación y reiniciamos la calibración.
            if (isCalibrated && sinceLastHands > 3000L) {
                Log.d("Calibration", "Reset por ausencia de manos (${sinceLastHands} ms)")
                isCalibrated = false
                calibrationProgress = 0f
                calibrationMessage = "Coloca tus manos dentro de las guías"
                statusText.value = "buscando manos..."
            } else if (!isCalibrated) {
                // Si ya estábamos en modo calibración, mantenemos el mensaje base
                calibrationProgress = 0f
                calibrationMessage = "Coloca tus manos dentro de las guías"
            }

            // Reset del texto reconocido (igual que antes)
            if (sinceLastHands > 800 && now >= serverUiHoldUntilMs.value) {
                withContext(Dispatchers.Main) {
                    recognizedText.value = "Aquí aparecerá el texto reconocido"
                    recognizedSource.value = InterpretSource.LOCAL
                }
            }

            lastLabel.value = null
            stableCount.value = 0
        }

        // Estado de manos (UI) cuando ya se está interpretando
        statusText.value = when {
            count >= 2 -> {
                lastSeenMs.value = now
                "dos manos detectadas"
            }

            count == 1 -> {
                lastSeenMs.value = now
                "una mano detectada"
            }

            now - lastSeenMs.value > 800 -> "buscando manos..."
            else -> statusText.value
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            handDetector?.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.93f),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // PREVIEW: FILL_CENTER + interceptar resolución real
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx: Context ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder()
                                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                                .setTargetResolution(android.util.Size(1280, 720))
                                .build()

                            val originalProvider = previewView.surfaceProvider
                            val interceptingProvider = Preview.SurfaceProvider { request ->
                                previewBufferSize.value = request.resolution
                                originalProvider.onSurfaceRequested(request)
                            }
                            preview.setSurfaceProvider(interceptingProvider)

                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                                .setTargetResolution(android.util.Size(1280, 720))
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also { analysis ->
                                    val minIntervalMs = 150L
                                    var lastTs = 0L
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val nowTs = System.currentTimeMillis()
                                        if (nowTs - lastTs < minIntervalMs) {
                                            imageProxy.close()
                                            return@setAnalyzer
                                        }
                                        lastTs = nowTs
                                        coroutineScope.launch(Dispatchers.Default) {
                                            try {
                                                processImageProxy(imageProxy, handDetector)
                                            } catch (e: Exception) {
                                                Log.e("CameraPreview", "Error en analyzer", e)
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }
                                }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalyzer
                                )
                            } catch (e: Exception) {
                                Log.e("CameraX", "Error iniciando cámara", e)
                            }

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // OVERLAY (usa tu OverlayView actual)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx: Context ->
                        OverlayView(ctx)
                    },
                    update = { overlayView: OverlayView ->
                        overlayView.setResults(currentResult.value)
                        val size = previewBufferSize.value
                        if (size != null) {
                            overlayView.setSourceInfo(
                                size.width, size.height, false,
                                OverlayView.ScaleMode.FILL
                            )
                        } else {
                            currentFrameInfo.value?.let { info ->
                                overlayView.setSourceInfo(
                                    info.width, info.height, info.isFrontCamera,
                                    OverlayView.ScaleMode.FILL
                                )
                            }
                        }
                    }
                )

                // Overlay de calibración (mientras no esté calibrado)
                if (!isCalibrated) {
                    HandCalibrationOverlay(
                        progress = calibrationProgress,
                        message = calibrationMessage,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Degradado inferior + estado
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(100.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x99000000))
                            )
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = statusText.value,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Recuadro para texto reconocido
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val prefix =
                    if (recognizedSource.value == InterpretSource.SERVER) "[srv]" else "[local]"
                Text(
                    text = "$prefix ${recognizedText.value}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
fun processImageProxy(
    imageProxy: ImageProxy,
    handDetector: HandDetector?
) {
    try {
        val width = imageProxy.width
        val height = imageProxy.height
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val bitmap: Bitmap = if (pixelStride == 4 && rowStride >= width * 4) {
            val srcWidth = rowStride / pixelStride
            val tmp = Bitmap.createBitmap(srcWidth, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            tmp.copyPixelsFromBuffer(buffer)
            if (srcWidth != width) Bitmap.createBitmap(tmp, 0, 0, width, height) else tmp
        } else {
            val naive = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            naive.copyPixelsFromBuffer(buffer)
            naive
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap

        currentFrameInfo.value = FrameInfo(rotated.width, rotated.height, isFrontCamera = false)

        val mpImage = BitmapImageBuilder(rotated).build()
        val ts = SystemClock.uptimeMillis()
        val result = handDetector?.detectForVideo(mpImage, ts)
        currentResult.value = result

    } catch (e: Exception) {
        Log.e("CameraX", "Error procesando frame (stride/convert)", e)
    } finally {
        imageProxy.close()
    }
}
