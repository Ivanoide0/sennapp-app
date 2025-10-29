package com.senapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.senapp.net.InterpretApi
import com.senapp.ui.CameraScreen
import com.senapp.ui.theme.SENAPP_2025Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fuerza la base URL desde BuildConfig (definida en gradle)
        InterpretApi.BASE_URL = BuildConfig.INTERPRET_BASE_URL

        setContent {
            SENAPP_2025Theme {
                MainScreen()
            }
        }
    }
}


    /**
     * Si es emulador usa 10.0.2.2, si es dispositivo físico usa la IP LAN de tu PC + :8080
     * Cambia "192.168.18.46" por tu IPv4 real (ipconfig).
     */

@Composable
fun MainScreen() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasCameraPermission = true
        } else {
            val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                (context as ComponentActivity), Manifest.permission.CAMERA
            )
            permanentlyDenied = !shouldShowRationale
        }
    }

    when {
        hasCameraPermission -> {
            CameraScreen()
        }
        permanentlyDenied -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "El permiso de cámara fue denegado permanentemente.\n" +
                                "Actívalo en Configuración (cierra y vuelve a abrir la app después).",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }) { Text("Abrir Configuración") }
                }
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Se solicita permiso de cámara", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Dar permiso")
                    }
                }
            }
        }
    }
}
