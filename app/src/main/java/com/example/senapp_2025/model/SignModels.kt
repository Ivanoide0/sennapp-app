// file: src/main/java/com/senapp/model/SignModels.kt
package com.senapp.model

enum class Finger { THUMB, INDEX, MIDDLE, RING, PINKY }

data class Rule(
    val metric: String,           // "extension" | "tip_sep" | "thumb_to_palm" | "tip_to_palm"
    val finger: Finger? = null,   // dedo principal (si aplica)
    val finger2: Finger? = null,  // segundo dedo (para métricas de pares, ej. tip_sep)
    val min: Float? = null,       // umbral mínimo
    val max: Float? = null        // umbral máximo
)

data class SignSpec(
    val id: String,
    val labelEs: String,
    val labelEn: String? = null,
    val type: String,             // "static" | "dynamic"
    val handedness: String? = null,
    val rules: List<Rule>,
    val smoothingMs: Int = 400,
    val sampleCount: Int = 1,
    val notes: String? = null
)

data class SignDb(
    val version: Int,
    val signs: List<SignSpec>
)
