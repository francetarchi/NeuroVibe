package com.found404.neurovibe.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel


class TFLiteModel(context: Context, modelFileName : String) {
    private val interpreter: Interpreter
    init {
        val assetFileDescriptor = context.assets.openFd(modelFileName)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        interpreter = Interpreter(modelBuffer)
    }

    fun predict(input: FloatArray): FloatArray{
        require(input.size == 48) {"Il modello si aspetta 48 feature, ma ne hai passate ${input.size}"}

        val inputBuffer = ByteBuffer.allocate(4 * 48)
        inputBuffer.order(ByteOrder.nativeOrder())
        input.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(2) }
        interpreter.run(inputBuffer, output)

        return output[0]
    }

    fun loadCsvInput(file: File, linesToSkip: Int): FloatArray? {
        try {
            val reader = file.bufferedReader()
            repeat(linesToSkip) {reader.readLine()}
            val dataLine = reader.readLine() ?: return null
            val values = dataLine.split(",").map { it.trim().toFloat() }
            return values.toFloatArray()
        } catch (e: Exception){
            e.printStackTrace()
            return null
        }
    }
}