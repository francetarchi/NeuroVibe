package com.found404.neurovibe.data

import android.content.Context
import android.os.Environment
import android.util.Log
import mylibrary.mindrove.SensorData
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStreamWriter
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class EEGDataProcessor(private val context: Context) {
    fun exportRawDataToCsv(data: List<SensorData>, currentSegment : Int, currentImage : Int): File? {
        val fileName = "eeg_data_${currentSegment}_image_${currentImage}.csv"
        val header = listOf(
            "accX", "accY", "accZ",
            "gyroX", "gyroY", "gyroZ",
            "ch1", "ch2", "ch3", "ch4",
            "ch5", "ch6", "ch7", "ch8",
            "measurementNumber"
        ).joinToString(",")

        val fileContents = buildString {
            appendLine(header)
            data.forEach { sensor ->
                appendLine(
                    listOf(
                        sensor.accelerationX,
                        sensor.accelerationY,
                        sensor.accelerationZ,
                        sensor.angularRateX,
                        sensor.angularRateY,
                        sensor.angularRateZ,
                        sensor.channel1,
                        sensor.channel2,
                        sensor.channel3,
                        sensor.channel4,
                        sensor.channel5,
                        sensor.channel6,
                        sensor.channel7,
                        sensor.channel8,
                        sensor.numberOfMeasurement
                    ).joinToString(",")
                )
            }
        }

        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        val file = File(downloadDir, fileName)
        return try {
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write(fileContents)
                }
            }
            Log.d("NeuroVibe", "File creato: ${file.absolutePath}")
            file


        } catch (e: Exception) {
            Log.d("NeuroVibe", "Errore nel salvataggio: ${e.message}")
            null
        }
    }

    private fun bandpassFilter(
        input: DoubleArray,
        fs: Double, // frequenza di campionamento in Hz
        f1: Double, // frequenza di taglio bassa
        f2: Double  // frequenza di taglio alta
    ): DoubleArray {
        // Pre-warp
        val nyquist = fs / 2
        val low = f1 / nyquist
        val high = f2 / nyquist

        val q = 1.0 / (high - low)

        val omega = 2 * PI * sqrt(low * high)
        val alpha = sin(omega) / (2.0 * q)

        val b0 = alpha
        val b1 = 0.0
        val b2 = -alpha
        val a0 = 1 + alpha
        val a1 = -2 * cos(omega)
        val a2 = 1 - alpha

        // Normalizzazione
        val b = doubleArrayOf(b0 / a0, b1 / a0, b2 / a0)
        val a = doubleArrayOf(1.0, a1 / a0, a2 / a0)

        // Output
        val output = DoubleArray(input.size)
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0

        for (i in input.indices) {
            val x0 = input[i]
            val y0 = b[0]*x0 + b[1]*x1 + b[2]*x2 - a[1]*y1 - a[2]*y2

            output[i] = y0

            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }

        return output
    }

    fun extractFeaturesToCsv(inputFile: File, outputFile: File): File {
        val channelData = Array(8) { mutableListOf<Double>() }

        // -- Corrispondenze elettrodi --
        // -------- C3 <- C3 -------- ch2
        // -------- C4 <- C4 -------- ch5
        // -------- F3 <- C5 -------- ch1
        // -------- Fz <- C1 -------- ch3
        // -------- F4 <- C6 -------- ch6
        // -------- P3 <- C1 -------- ch3
        // -------- Pz <- C2 -------- ch4
        // -------- P4 <- C2 -------- ch4

        val lines = inputFile.readLines()
        lines.forEachIndexed { index, line ->
            if (index == 0) return@forEachIndexed // salto l'header del file
            val parts = line.split(",")

            for (i in 0 until 8) {
                val value = parts[6 + i].toDoubleOrNull()
                if (value != null) {
                    channelData[i].add(value)
                }
            }
        }

        // Parametri del filtro passa banda
        val fs = 250.0  // frequenza di campionamento
        val f1 = 0.5
        val f2 = 49.0

        val reorderedChannels = listOf(
            channelData[0], // F3 <- ch1
            channelData[2], // Fz <- ch3
            channelData[5], // F4 <- ch6
            channelData[1], // C3 <- ch2
            channelData[4], // C4 <- ch5
            channelData[2], // P3 <- ch3
            channelData[3], // Pz <- ch4
            channelData[3]  // P4 <- ch4
        )

        val stats = reorderedChannels.map { raw ->
            val filtered = bandpassFilter(raw.toDoubleArray(), fs, f1, f2)
            DescriptiveStatistics(filtered)
        }

        val means = stats.map { it.mean }
        val stds = stats.map { it.standardDeviation }
        val maxs = stats.map { it.max }
        val mins = stats.map { it.min }
        val kurtoses = stats.map { it.kurtosis }
        val entropies = reorderedChannels.map { raw ->
            val filtered = bandpassFilter(raw.toDoubleArray(), fs, f1, f2)
            computeShannonEntropy(filtered.toList())
        }

        val header = listOf(
            "MeanF3","MeanFz","MeanF4","MeanC3","MeanC4","MeanP3","MeanPz","MeanP4",
            "StdF3","StdFz","StdF4","StdC3","StdC4","StdP3","StdPz","StdP4",
            "MaxF3","MaxFz","MaxF4","MaxC3","MaxC4","MaxP3","MaxPz","MaxP4",
            "MinF3","MinFz","MinF4","MinC3","MinC4","MinP3","MinPz","MinP4",
            "KurtF3","KurtFz","KurtF4","KurtC3","KurtC4","KurtP3","KurtPz","KurtP4",
            "EntrF3","EntrFz","EntrF4","EntrC3","EntrC4","EntrP3","EntrPz","EntrP4"
        )

        val row = means + stds + maxs + mins + kurtoses + entropies

        val writeHeader = !outputFile.exists()
        FileWriter(outputFile, true).use { writer ->
            if(writeHeader){
                writer.appendLine(header.joinToString(","))
            }
            writer.appendLine(row.joinToString(","))
        }
        return outputFile
    }

    private fun computeShannonEntropy(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0

        val bins = 20
        val min = values.minOrNull()!!
        val max = values.maxOrNull()!!
        val binWidth = (max - min) / bins

        val histogram = IntArray(bins)

        values.forEach {
            val bin = ((it - min) / binWidth).toInt().coerceIn(0, bins - 1)
            histogram[bin]++
        }

        val total = values.size.toDouble()
        return histogram
            .filter { it > 0 }
            .map { it / total }
            .sumOf { -it * ln(it) }
    }
}