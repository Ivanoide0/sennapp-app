// file: src/main/java/com/senapp/ml/HandDetector.kt
package com.senapp.ml

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandDetector(private val context: Context) {

    private var landmarker: HandLandmarker? = null

    fun initialize(assetPath: String = "ml/hand_landmarker.task") {
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(assetPath)
                    .build()
            )
            .setNumHands(2)
            // detectForVideo() requiere RunningMode.VIDEO
            .setRunningMode(RunningMode.VIDEO)
            .build()

        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detectForVideo(image: MPImage, timestampMs: Long): HandLandmarkerResult? {
        return landmarker?.detectForVideo(image, timestampMs)
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
