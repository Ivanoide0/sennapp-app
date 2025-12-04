package com.senapp.ml

data class HandBox(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

data class CalibrationResult(
    val score: Float,
    val isCalibrated: Boolean,
    val message: String
)

// Posiciones objetivo (en coordenadas normalizadas 0..1)
private const val TARGET_LEFT_X = 0.30f
private const val TARGET_RIGHT_X = 0.70f
private const val TARGET_CENTER_Y = 0.50f

// Tamaño objetivo (aprox. ancho relativo de cada mano)
private const val TARGET_WIDTH = 0.20f   // antes 0.25f, ahora un poco más pequeño

// Rango razonable de tamaño (para mensajes cerca/lejos)
private const val MIN_WIDTH = 0.12f      // si es menor: muy lejos
private const val MAX_WIDTH = 0.40f      // si es mayor: muy cerca

// Tolerancias (cuánto podemos desviarnos de la posición/tamaño)
private const val CENTER_TOLERANCE = 0.25f   // antes 0.15f
private const val SIZE_TOLERANCE   = 0.25f   // antes 0.12f

fun computeCalibration(
    leftHand: List<Pair<Float, Float>>?,
    rightHand: List<Pair<Float, Float>>?
): CalibrationResult {

    // Si faltan manos, ni siquiera empezamos
    if (leftHand == null || rightHand == null) {
        return CalibrationResult(
            score = 0f,
            isCalibrated = false,
            message = "Coloca tus dos manos dentro de las guías"
        )
    }

    val leftBox = leftHand.toHandBox()
    val rightBox = rightHand.toHandBox()

    // Scores de posición (centro X/Y) y tamaño
    val leftCenterXScore = centerScore(leftBox.centerX, TARGET_LEFT_X)
    val rightCenterXScore = centerScore(rightBox.centerX, TARGET_RIGHT_X)

    val leftCenterYScore = centerScore(leftBox.centerY, TARGET_CENTER_Y)
    val rightCenterYScore = centerScore(rightBox.centerY, TARGET_CENTER_Y)

    val leftSizeScore = sizeScore(leftBox.width)
    val rightSizeScore = sizeScore(rightBox.width)

    val allScores = listOf(
        leftCenterXScore, rightCenterXScore,
        leftCenterYScore, rightCenterYScore,
        leftSizeScore, rightSizeScore
    )

    val avgScore = allScores.map { it.coerceIn(0f, 1f) }.average().toFloat()

    // Promedios separados para decisiones de mensaje
    val centerAvg = listOf(
        leftCenterXScore, rightCenterXScore,
        leftCenterYScore, rightCenterYScore
    ).map { it.coerceIn(0f, 1f) }.average().toFloat()

    val sizeAvg = ((leftSizeScore + rightSizeScore) / 2f).coerceIn(0f, 1f)

    // Ancho real promedio (para saber si estás MUY lejos o MUY cerca)
    val widthAvg = (leftBox.width + rightBox.width) / 2f

    // Umbral para considerar la calibración completa
    val isCalibrated = avgScore >= 0.78f

    val message = when {
        // 1) Si ya estás bien: mostrar mensaje final
        isCalibrated ->
            "Calibración completa"

        // 2) Muy lejos (manos muy pequeñas en la imagen)
        widthAvg < MIN_WIDTH ->
            "Acércate un poco a la cámara"

        // 3) Muy cerca (manos enormes ocupan demasiado)
        widthAvg > MAX_WIDTH ->
            "Aléjate un poco de la cámara"

        // 4) Tamaño ok pero todavía no estás centrado en los cuadros
        sizeAvg > 0.80f && centerAvg < 0.80f ->
            "Alinea tus manos con las siluetas"

        // 5) Todo casi bien, solo falta mantenerlas quietas un momento
        else ->
            "Mantén las manos quietas para completar la calibración"
    }

    return CalibrationResult(
        score = avgScore.coerceIn(0f, 1f),
        isCalibrated = isCalibrated,
        message = message
    )
}

private fun List<Pair<Float, Float>>.toHandBox(): HandBox {
    val minX = minOf { it.first }
    val maxX = maxOf { it.first }
    val minY = minOf { it.second }
    val maxY = maxOf { it.second }

    return HandBox(
        centerX = (minX + maxX) / 2f,
        centerY = (minY + maxY) / 2f,
        width = (maxX - minX).coerceAtLeast(1e-3f),
        height = (maxY - minY).coerceAtLeast(1e-3f)
    )
}

private fun centerScore(value: Float, target: Float): Float =
    (1f - (kotlin.math.abs(value - target) / CENTER_TOLERANCE))
        .coerceIn(0f, 1f)

private fun sizeScore(width: Float): Float =
    (1f - (kotlin.math.abs(width - TARGET_WIDTH) / SIZE_TOLERANCE))
        .coerceIn(0f, 1f)
