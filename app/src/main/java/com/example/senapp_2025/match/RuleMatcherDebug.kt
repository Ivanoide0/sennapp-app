package com.senapp.match

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

object RuleMatcherDebug {
    private fun vec(a: NormalizedLandmark, b: NormalizedLandmark): FloatArray =
        floatArrayOf(b.x() - a.x(), b.y() - a.y(), (b.z() ?: 0f) - (a.z() ?: 0f))

    private fun dot(u: FloatArray, v: FloatArray) = u[0]*v[0] + u[1]*v[1] + u[2]*v[2]
    private fun norm(u: FloatArray) = sqrt(max(1e-12f, dot(u, u)))
    private fun cosine(u: FloatArray, v: FloatArray) = (dot(u, v) / (norm(u) * norm(v))).coerceIn(-1f, 1f)
    private fun angleDegFromWrist(hand: List<NormalizedLandmark>, tipA: Int, tipB: Int): Int {
        val wrist = hand[0]
        val u = vec(wrist, hand[tipA])
        val v = vec(wrist, hand[tipB])
        val c = cosine(u, v)
        return Math.toDegrees(acos(c).toDouble()).toInt()
    }

    fun angleTI(hand: List<NormalizedLandmark>): Int = angleDegFromWrist(hand, 4, 8)
    fun angleTP(hand: List<NormalizedLandmark>): Int = angleDegFromWrist(hand, 4, 20)
    fun angleIM(hand: List<NormalizedLandmark>): Int = angleDegFromWrist(hand, 8, 12)
    fun angleMR(hand: List<NormalizedLandmark>): Int = angleDegFromWrist(hand, 12, 16)
    fun angleRP(hand: List<NormalizedLandmark>): Int = angleDegFromWrist(hand, 16, 20)
}
