// file: src/main/java/com/senapp/ui/OverlayView.kt
package com.senapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ScaleMode { FIT, FILL }

    private var results: HandLandmarkerResult? = null
    private var srcW: Int = 0
    private var srcH: Int = 0
    private var mirrorX: Boolean = false
    private var scaleMode: ScaleMode = ScaleMode.FILL // 👈 por defecto FILL

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 10f
        isAntiAlias = true
    }
    private val linePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val connections = arrayOf(
        intArrayOf(0,1), intArrayOf(1,2), intArrayOf(2,3), intArrayOf(3,4),
        intArrayOf(0,5), intArrayOf(5,6), intArrayOf(6,7), intArrayOf(7,8),
        intArrayOf(5,9), intArrayOf(9,10), intArrayOf(10,11), intArrayOf(11,12),
        intArrayOf(9,13), intArrayOf(13,14), intArrayOf(14,15), intArrayOf(15,16),
        intArrayOf(13,17), intArrayOf(17,18), intArrayOf(18,19), intArrayOf(19,20),
        intArrayOf(0,17)
    )

    fun setResults(result: HandLandmarkerResult?) {
        results = result
        invalidate()
    }

    /** width/height del frame YA ROTADO + si es frontal + modo de escala (FIT o FILL) */
    fun setSourceInfo(
        width: Int,
        height: Int,
        isFrontCamera: Boolean,
        mode: ScaleMode = ScaleMode.FILL
    ) {
        srcW = width
        srcH = height
        mirrorX = isFrontCamera
        scaleMode = mode
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val result = results ?: return
        if (srcW <= 0 || srcH <= 0) return

        // FIT_CENTER = min(...), FILL_CENTER = max(...)
        val scale = when (scaleMode) {
            ScaleMode.FIT -> min(width.toFloat() / srcW, height.toFloat() / srcH)
            ScaleMode.FILL -> max(width.toFloat() / srcW, height.toFloat() / srcH)
        }
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f

        for (hand in result.landmarks()) {
            for (c in connections) {
                val a = hand[c[0]]
                val b = hand[c[1]]
                val ax = (if (mirrorX) (1f - a.x()) else a.x()) * srcW * scale + dx
                val ay = a.y() * srcH * scale + dy
                val bx = (if (mirrorX) (1f - b.x()) else b.x()) * srcW * scale + dx
                val by = b.y() * srcH * scale + dy
                canvas.drawLine(ax, ay, bx, by, linePaint)
            }
            for (lm in hand) {
                val x = (if (mirrorX) (1f - lm.x()) else lm.x()) * srcW * scale + dx
                val y = lm.y() * srcH * scale + dy
                canvas.drawCircle(x, y, 6f, pointPaint)
            }
        }
    }
}
