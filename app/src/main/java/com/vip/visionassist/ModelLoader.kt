package com.vip.visionassist

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.tensorflow.lite.Interpreter
import java.io.InputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object ModelLoader {

    fun loadTFLite(context: Context, fileName: String): Interpreter {
        val assetFileDescriptor = context.assets.openFd(fileName)
        val inputStream = assetFileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val mappedBuffer: MappedByteBuffer = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
        return Interpreter(mappedBuffer)
    }

    fun loadONNX(context: Context, fileName: String): OrtSession {
        val env = OrtEnvironment.getEnvironment()
        val inputStream: InputStream = context.assets.open(fileName)
        val modelBytes = inputStream.readBytes()
        return env.createSession(modelBytes)
    }
}
