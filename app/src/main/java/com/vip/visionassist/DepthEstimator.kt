package com.vip.visionassist

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer

class DepthEstimator(private val context: Context) {
    private var session: OrtSession? = null
    private val inputSize = 182
    private val env = OrtEnvironment.getEnvironment()

    init {
        try {
            session = ModelLoader.loadONNX(context, "depth_vits.onnx")
            Log.i("VIP", "DepthEstimator loaded")
        } catch (e: Exception) {
            Log.e("VIP", "DepthEstimator init error: ${e.message}")
        }
    }

    fun estimate(bitmap: Bitmap): Array<FloatArray>? {
        val sess = session ?: return null

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val floatArray = FloatArray(1 * 3 * inputSize * inputSize)

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Normalize with ImageNet mean/std (same as Depth Anything training)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std  = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (i in pixels.indices) {
            val r = (pixels[i] shr 16 and 0xFF) / 255.0f
            val g = (pixels[i] shr 8  and 0xFF) / 255.0f
            val b = (pixels[i]        and 0xFF) / 255.0f
            floatArray[0 * inputSize * inputSize + i] = (r - mean[0]) / std[0]
            floatArray[1 * inputSize * inputSize + i] = (g - mean[1]) / std[1]
            floatArray[2 * inputSize * inputSize + i] = (b - mean[2]) / std[2]
        }

        return try {
            val shape = longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArray), shape)
            val output = sess.run(mapOf("input" to tensor))
            val depthRaw = output[0].value as Array<*>

            // Output shape: [1, H, W] — extract the 2D depth map
            @Suppress("UNCHECKED_CAST")
            val depthMap = (depthRaw[0] as Array<FloatArray>)
            tensor.close()
            output.close()
            depthMap
        } catch (e: Exception) {
            Log.e("VIP", "Depth inference error: ${e.message}")
            null
        }
    }

    fun close() { session?.close() }
}