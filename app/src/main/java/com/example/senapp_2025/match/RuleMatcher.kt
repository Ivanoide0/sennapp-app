// file: src/main/java/com/senapp/match/RuleMatcher.kt
package com.senapp.match

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.senapp.model.Finger
import com.senapp.model.Rule
import com.senapp.model.SignSpec
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object RuleMatcher {

    private data class FingerJoints(val mcp: Int, val pip: Int, val dip: Int, val tip: Int)

    private val FINGER_JOINTS = mapOf(
        Finger.THUMB  to FingerJoints(1, 2, 3, 4),
        Finger.INDEX  to FingerJoints(5, 6, 7, 8),
        Finger.MIDDLE to FingerJoints(9, 10, 11, 12),
        Finger.RING   to FingerJoints(13, 14, 15, 16),
        Finger.PINKY  to FingerJoints(17, 18, 19, 20),
    )

    // Prioridades (L/Y/ILY > V/W/I > 1/puño/B/mano abierta)
    private val PRIORITY_GROUPS: List<Set<String>> = listOf(
        setOf("letra_l", "letra_y", "te_amo_ily"),
        setOf("letra_v", "letra_w", "letra_i"),
        setOf("numero_1", "punio_a", "letra_b_palma", "mano_abierta_5")
    )

    /** Devuelve labelEs (o id) del mejor SignSpec, con prioridad por grupos. */
    fun matchFirstLabel(hand: List<NormalizedLandmark>, signs: List<SignSpec>): String? {
        if (hand.size < 21) return null
        val ext = extensionScores(hand)

        for (group in PRIORITY_GROUPS) {
            var best: Pair<SignSpec, Float>? = null
            for (spec in signs) {
                if (spec.id !in group) continue
                val sc = matchScore(spec, ext, hand) ?: continue
                if (best == null || sc > best!!.second) best = spec to sc
            }
            // 🔽 umbrales un poco más bajos para facilitar L / Y / ILY y mano abierta
            val minScore = when (group) {
                PRIORITY_GROUPS[0] -> 0.58f
                PRIORITY_GROUPS[1] -> 0.62f
                else               -> 0.66f
            }
            if (best != null && best!!.second >= minScore) {
                return best!!.first.labelEs ?: best!!.first.id
            }
        }
        return null
    }

    /** Score [0..1] si cumple; null si alguna regla no pasa. */
    private fun matchScore(spec: SignSpec, extScores: Map<Finger, Float>, hand: List<NormalizedLandmark>): Float? {
        var score = 1f
        var any = false

        for (r in spec.rules) {
            val metric = r.metric.lowercase()
            val v: Float = when (metric) {
                "ext", "extension", "curl" -> {
                    val f = r.finger ?: return null
                    extScores[f] ?: return null
                }
                "angle_deg_thumb_index" -> angleDeg(hand, 4, 8)
                "angle_deg_thumb_pinky" -> angleDeg(hand, 4, 20)
                "angle_deg_index_middle" -> angleDeg(hand, 8, 12)
                "angle_deg_middle_ring"  -> angleDeg(hand, 12, 16)
                "angle_deg_ring_pinky"   -> angleDeg(hand, 16, 20)
                "pip_deg" -> { // ∠MCP–PIP–DIP
                    val f = r.finger ?: return null
                    val j = FINGER_JOINTS[f]!!
                    jointAngleDeg(hand[j.mcp], hand[j.pip], hand[j.dip])
                }
                else -> continue
            }
            any = true

            val okMin = (r.min == null) || (v >= r.min!!)
            val okMax = (r.max == null) || (v <= r.max!!)
            if (!okMin || !okMax) return null

            if (r.min != null && r.max != null) {
                val mid = (r.min!! + r.max!!) / 2f
                val half = max(1e-6f, (r.max!! - r.min!!) / 2f)
                val local = 1f - (abs(v - mid) / half)
                score *= local.coerceIn(0f, 1f)
            }
        }

        return if (any) score.coerceIn(0f, 1f) else null
    }

    /** Extensión [0..1] por dedo con refuerzo PIP/DIP y pulgar lateral. */
    fun extensionScores(hand: List<NormalizedLandmark>): Map<Finger, Float> {
        val wrist = hand[0]
        val midMcp = hand[9]
        val palm = distance(wrist, midMcp).coerceAtLeast(1e-4f)

        fun fingerExt(f: Finger): Float {
            val j = FINGER_JOINTS[f]!!
            val mcp = hand[j.mcp]; val pip = hand[j.pip]; val dip = hand[j.dip]; val tip = hand[j.tip]

            val far = distance(tip, mcp) / palm
            val farScore = normalize(far, 0.35f, 1.00f)

            val v1 = vec(mcp, pip); val v2 = vec(pip, tip)
            val cos = cosine(v1, v2)            // [-1..1]
            val straightScore = normalize(cos, 0.0f, 1.0f)

            val angPip = jointAngleDeg(mcp, pip, dip)     // 180° extendido
            val angDip = jointAngleDeg(pip, dip, tip)
            val pipScore = normalize(angPip, 120f, 180f)
            val dipScore = normalize(angDip, 120f, 180f)
            var s = 0.4f * farScore + 0.3f * straightScore + 0.3f * ((pipScore + dipScore) * 0.5f)

            if (f == Finger.THUMB) {
                val indexMcp = hand[5]
                val thumbLateral = distance(tip, indexMcp) / palm
                val lateralScore = normalize(thumbLateral, 0.25f, 0.90f)
                s = 0.5f * s + 0.5f * lateralScore
            }
            return s.coerceIn(0f, 1f)
        }

        return mapOf(
            Finger.THUMB  to fingerExt(Finger.THUMB),
            Finger.INDEX  to fingerExt(Finger.INDEX),
            Finger.MIDDLE to fingerExt(Finger.MIDDLE),
            Finger.RING   to fingerExt(Finger.RING),
            Finger.PINKY  to fingerExt(Finger.PINKY),
        )
    }

    private fun angleDeg(hand: List<NormalizedLandmark>, tipA: Int, tipB: Int): Float {
        val wrist = hand[0]
        val u = vec(wrist, hand[tipA])
        val v = vec(wrist, hand[tipB])
        return angleDegrees(u, v)
    }

    private fun jointAngleDeg(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
        val u = vec(b, a) // b->a
        val v = vec(b, c) // b->c
        return angleDegrees(u, v)
    }

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        val dz = (a.z() ?: 0f) - (b.z() ?: 0f)
        return sqrt(dx*dx + dy*dy + dz*dz)
    }

    private fun vec(a: NormalizedLandmark, b: NormalizedLandmark): FloatArray =
        floatArrayOf(b.x() - a.x(), b.y() - a.y(), (b.z() ?: 0f) - (a.z() ?: 0f))

    private fun dot(u: FloatArray, v: FloatArray) = u[0]*v[0] + u[1]*v[1] + u[2]*v[2]
    private fun norm(u: FloatArray) = sqrt(max(1e-12f, dot(u, u)))
    private fun cosine(u: FloatArray, v: FloatArray) = (dot(u, v) / (norm(u) * norm(v))).coerceIn(-1f, 1f)

    private fun angleDegrees(u: FloatArray, v: FloatArray): Float {
        val c = cosine(u, v).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c).toDouble()).toFloat()
    }

    private fun normalize(x: Float, lo: Float, hi: Float): Float = ((x - lo) / (hi - lo)).coerceIn(0f, 1f)
}
