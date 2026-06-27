package com.vip.visionassist

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class BoundingBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var detections: List<YoloDetector.Detection> = emptyList()
    private var frameWidth = 320f
    private var frameHeight = 320f

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#4FC3F7")
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#884FC3F7")
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val textBgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC1565C0")
        isAntiAlias = true
    }

    fun updateDetections(
        newDetections: List<YoloDetector.Detection>,
        srcWidth: Int,
        srcHeight: Int
    ) {
        detections = newDetections
        frameWidth = srcWidth.toFloat()
        frameHeight = srcHeight.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        val scaleX = width / frameWidth
        val scaleY = height / frameHeight

        for (det in detections) {
            val left   = det.x1 * scaleX
            val top    = det.y1 * scaleY
            val right  = det.x2 * scaleX
            val bottom = det.y2 * scaleY

            // Draw box fill
            canvas.drawRect(left, top, right, bottom, fillPaint)
            // Draw box border
            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Draw label background
            val label = "${det.label} ${"%.0f".format(det.confidence * 100)}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize
            canvas.drawRoundRect(
                left, top - textHeight - 8f,
                left + textWidth + 16f, top,
                8f, 8f, textBgPaint
            )

            // Draw label text
            canvas.drawText(label, left + 8f, top - 8f, textPaint)
        }
    }
}