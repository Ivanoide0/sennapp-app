// file: src/main/java/com/senapp/data/LocalSignRules.kt
package com.senapp.data

import com.senapp.model.*
import com.senapp.match.*

object LocalSignRules {
    fun static10(): List<SignSpec> = listOf(

        // ILY (te amo): T, I, P extendidos; M/R flexionados.
        // Tus logs: TI ≈ 44–50°, P ~0.75–0.85, M/R muy bajos (flex).
        SignSpec(
            id = "te_amo_ily",
            labelEs = "te amo",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.THUMB,  min = 0.55f),
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.60f),
                Rule(metric = "ext", finger = Finger.PINKY,  min = 0.60f),

                // Centrales flexionados (mejor vía PIP)
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, max = 130f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 130f),

                // Encajado a tu mano para maximizar score
                Rule(metric = "angle_deg_thumb_index", min = 38f, max = 58f),

                // Centrales relativamente pegados (no hiperestricto)
                Rule(metric = "angle_deg_index_middle", max = 36f),
                Rule(metric = "angle_deg_middle_ring",  max = 24f)
            ),
            smoothingMs = 240
        ),

        // L: I y T extendidos; M/R/P flexionados.
        // En tus logs: TI ≈ 46–60°. IM ≈ 22–26°.
        SignSpec(
            id = "letra_l",
            labelEs = "L",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.75f),
                Rule(metric = "ext", finger = Finger.THUMB,  min = 0.55f),

                // Asegura que el resto esté flexionado
                Rule(metric = "ext", finger = Finger.MIDDLE, max = 0.40f),
                Rule(metric = "ext", finger = Finger.RING,   max = 0.40f),
                Rule(metric = "ext", finger = Finger.PINKY,  max = 0.40f),
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, max = 130f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 130f),
                Rule(metric = "pip_deg", finger = Finger.PINKY,  max = 130f),

                // Tiende a ~50° en tu mano, pero admite variación
                Rule(metric = "angle_deg_thumb_index", min = 26f, max = 65f),

                // Aquí relajamos el IM para tu mano (antes 18° era muy estricto)
                Rule(metric = "angle_deg_index_middle", max = 30f),
                Rule(metric = "angle_deg_middle_ring",  max = 22f),
                Rule(metric = "angle_deg_ring_pinky",   max = 22f)
            ),
            smoothingMs = 220
        ),

        // Y: T + P extendidos; I/M/R flexionados.
        // TP ≈ 68–75° en tus logs → encajamos 60–88°.
        SignSpec(
            id = "letra_y",
            labelEs = "Y",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.THUMB,  min = 0.55f),
                Rule(metric = "ext", finger = Finger.PINKY,  min = 0.65f),

                Rule(metric = "ext", finger = Finger.INDEX,  max = 0.35f),
                Rule(metric = "ext", finger = Finger.MIDDLE, max = 0.35f),
                Rule(metric = "ext", finger = Finger.RING,   max = 0.35f),
                Rule(metric = "pip_deg", finger = Finger.INDEX,  max = 130f),
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, max = 130f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 130f),

                Rule(metric = "angle_deg_thumb_pinky", min = 60f, max = 88f),

                // Centrales pegados razonable
                Rule(metric = "angle_deg_index_middle", max = 22f),
                Rule(metric = "angle_deg_middle_ring",  max = 22f)
            ),
            smoothingMs = 220
        ),

        // I
        SignSpec(
            id = "letra_i",
            labelEs = "I",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.PINKY,  min = 0.70f),
                Rule(metric = "pip_deg", finger = Finger.INDEX,  max = 120f),
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, max = 120f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 120f),
                Rule(metric = "ext", finger = Finger.THUMB,  max = 0.55f)
            ),
            smoothingMs = 260
        ),

        // V
        SignSpec(
            id = "letra_v",
            labelEs = "V",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.70f),
                Rule(metric = "ext", finger = Finger.MIDDLE, min = 0.70f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 120f),
                Rule(metric = "pip_deg", finger = Finger.PINKY,  max = 120f),
                Rule(metric = "ext", finger = Finger.THUMB,  max = 0.55f)
            ),
            smoothingMs = 230
        ),

        // W
        SignSpec(
            id = "letra_w",
            labelEs = "W",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.65f),
                Rule(metric = "ext", finger = Finger.MIDDLE, min = 0.65f),
                Rule(metric = "ext", finger = Finger.RING,   min = 0.65f),
                Rule(metric = "pip_deg", finger = Finger.PINKY,  max = 120f),
                Rule(metric = "ext", finger = Finger.THUMB,  max = 0.55f)
            ),
            smoothingMs = 260
        ),

        // B / palma abierta fuerte
        SignSpec(
            id = "letra_b_palma",
            labelEs = "B",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.70f),
                Rule(metric = "ext", finger = Finger.MIDDLE, min = 0.70f),
                Rule(metric = "ext", finger = Finger.RING,   min = 0.70f),
                Rule(metric = "ext", finger = Finger.PINKY,  min = 0.70f),
                Rule(metric = "pip_deg", finger = Finger.INDEX,  min = 160f),
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, min = 160f),
                Rule(metric = "pip_deg", finger = Finger.RING,   min = 160f),
                Rule(metric = "pip_deg", finger = Finger.PINKY,  min = 160f),
                Rule(metric = "ext", finger = Finger.THUMB,  max = 0.45f)
            ),
            smoothingMs = 240
        ),

        // Mano abierta (5) — compatible con tus separaciones reales.
        SignSpec(
            id = "mano_abierta_5",
            labelEs = "mano abierta",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.THUMB,  min = 0.68f),
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.74f),
                Rule(metric = "ext", finger = Finger.MIDDLE, min = 0.72f),
                Rule(metric = "ext", finger = Finger.RING,   min = 0.72f),
                Rule(metric = "ext", finger = Finger.PINKY,  min = 0.68f),

                // Tus ángulos mínimos observados (IM≈14, MR≈10, RP≈15)
                Rule(metric = "angle_deg_index_middle", min = 12f),
                Rule(metric = "angle_deg_middle_ring",  min = 10f),
                Rule(metric = "angle_deg_ring_pinky",   min = 12f)
            ),
            smoothingMs = 200
        ),

        // 1
        SignSpec(
            id = "numero_1",
            labelEs = "1",
            type = "static",
            rules = listOf(
                Rule(metric = "ext", finger = Finger.INDEX,  min = 0.75f),
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, max = 120f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 120f),
                Rule(metric = "pip_deg", finger = Finger.PINKY,  max = 120f),
                Rule(metric = "ext", finger = Finger.THUMB,  max = 0.55f)
            ),
            smoothingMs = 240
        ),

        // Puño (A)
        SignSpec(
            id = "punio_a",
            labelEs = "puño",
            type = "static",
            rules = listOf(
                Rule(metric = "pip_deg", finger = Finger.INDEX,  max = 95f),
                Rule(metric = "pip_deg", finger = Finger.MIDDLE, max = 95f),
                Rule(metric = "pip_deg", finger = Finger.RING,   max = 95f),
                Rule(metric = "pip_deg", finger = Finger.PINKY,  max = 95f),
                Rule(metric = "ext", finger = Finger.THUMB,  max = 0.40f)
            ),
            smoothingMs = 260
        )
    )
}
