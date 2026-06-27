package com.vip.visionassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YoloDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val inputSize = 320
    private val numCandidates = 2100
    private val numElements = 84

    private val labels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    init {
        try {
            interpreter = ModelLoader.loadTFLite(context, "yolov8s_float32.tflite")
            Log.i("VIP", "YOLOv8s loaded successfully")
        } catch (e: Exception) {
            Log.e("VIP", "YoloDetector init error: ${e.message}")
        }
    }

    // Letterbox resize — preserves aspect ratio like YOLO does internally
    private fun letterboxBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.rgb(114, 114, 114)) // gray padding

        val scale = minOf(
            targetSize.toFloat() / bitmap.width,
            targetSize.toFloat() / bitmap.height
        )
        val scaledW = (bitmap.width * scale).toInt()
        val scaledH = (bitmap.height * scale).toInt()
        val offsetX = (targetSize - scaledW) / 2
        val offsetY = (targetSize - scaledH) / 2

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        canvas.drawBitmap(scaled, offsetX.toFloat(), offsetY.toFloat(), Paint())
        return result
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val tflite = interpreter ?: return emptyList()

        // Use letterbox instead of direct resize
        val letterboxed = letterboxBitmap(bitmap, inputSize)

        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        letterboxed.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            inputBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255.0f) // G
            inputBuffer.putFloat((pixel and 0xFF)           / 255.0f) // B
        }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(1 * numElements * numCandidates * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        return try {
            tflite.run(inputBuffer, outputBuffer)
            outputBuffer.rewind()
            val floatArray = FloatArray(numElements * numCandidates)
            outputBuffer.asFloatBuffer().get(floatArray)
            parseResults(floatArray)
        } catch (e: Exception) {
            Log.e("VIP", "Inference error: ${e.message}")
            emptyList()
        }
    }

    private fun parseResults(data: FloatArray): List<Detection> {
        val detections = mutableListOf<Detection>()
        val confidenceThreshold = 0.35f
        var frameMaxConf = 0f

        for (col in 0 until numCandidates) {
            var maxClassScore = 0f
            var classIdx = -1

            for (row in 4 until numElements) {
                val score = data[row * numCandidates + col]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classIdx = row - 4
                }
            }

            if (maxClassScore > frameMaxConf) frameMaxConf = maxClassScore

            if (maxClassScore > confidenceThreshold) {
                val cx = data[0 * numCandidates + col] * inputSize
                val cy = data[1 * numCandidates + col] * inputSize
                val w  = data[2 * numCandidates + col] * inputSize
                val h  = data[3 * numCandidates + col] * inputSize

                Log.d("VIP", "Detected: ${labels.getOrNull(classIdx)} conf=${"%.2f".format(maxClassScore)}")

                detections.add(Detection(
                    label = labels.getOrNull(classIdx) ?: "object",
                    confidence = maxClassScore,
                    x1 = cx - w / 2,
                    y1 = cy - h / 2,
                    x2 = cx + w / 2,
                    y2 = cy + h / 2
                ))
            }
        }

        Log.d("VIP", "Max Conf: ${"%.2f".format(frameMaxConf)} | Detections: ${detections.size}")
        return applyNMS(detections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<Detection>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)
            sorted.removeAll { iou(best, it) > 0.45f }
        }
        return selected
    }

    private fun iou(a: Detection, b: Detection): Float {
        val x1 = maxOf(a.x1, b.x1)
        val y1 = maxOf(a.y1, b.y1)
        val x2 = minOf(a.x2, b.x2)
        val y2 = minOf(a.y2, b.y2)
        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        return intersection / (areaA + areaB - intersection)
    }

    data class Detection(
        val label: String, val confidence: Float,
        val x1: Float, val y1: Float,
        val x2: Float, val y2: Float
    )

    fun close() { interpreter?.close() }
}