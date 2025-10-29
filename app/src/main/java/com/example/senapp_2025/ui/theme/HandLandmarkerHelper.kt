package com.senapp.ui

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.File
import java.io.FileOutputStream

class HandLandmarkerHelper(context: Context) {

    private var landmarker: HandLandmarker? = null

    init {
        try {
            val modelName = "hand_landmarker.task"

            // Ruta final donde MediaPipe podrá leerlo
            val destDir = File(context.filesDir, "com/example/senapp_2025/ml")
            if (!destDir.exists()) destDir.mkdirs()

            val modelFile = File(destDir, modelName)

            // Copiar desde ml/ del APK solo si no existe en filesDir/ml
            if (!modelFile.exists()) {
                val inputStream = context.assets.open("ml/$modelName")
                FileOutputStream(modelFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                Log.d("HandLandmarkerHelper", "Modelo copiado a: ${modelFile.absolutePath}")
            } else {
                Log.d("HandLandmarkerHelper", "Modelo ya existe en: ${modelFile.absolutePath}")
            }

            // Crear HandLandmarker con el archivo en filesDir/ml
            landmarker = HandLandmarker.createFromFile(context, modelFile.absolutePath)

        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Error cargando modelo", e)
        }
    }

    fun detect(mpImage: MPImage): HandLandmarkerResult? {
        return try {
            landmarker?.detect(mpImage)
        } catch (e: Exception) {
            Log.e("HandLandmarkerHelper", "Error detectando mano", e)
            null
        }
    }
}
