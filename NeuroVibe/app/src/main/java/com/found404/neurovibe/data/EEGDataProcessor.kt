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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


class EEGDataProcessor(private val context: Context) {
    fun exportRawDataToCsv(data: List<SensorData>, currentSegment : Int, currentImage : Int): File? {
        val fileName = "eeg_data_${currentSegment}_image_${currentImage}.csv"

        // Intestazione delle colonne
        val header = listOf(
            "accX", "accY", "accZ",
            "gyroX", "gyroY", "gyroZ",
            "ch1", "ch2", "ch3", "ch4",
            "ch5", "ch6", "ch7", "ch8",
            "measurementNumber"
        ).joinToString(",")

        // Contenuto del file in formato CSV
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

        // Directory di destinazione
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        val file = File(dir, fileName)
        return try {
            // Scrittura su file
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

    // Normalizza il segnale (media 0, deviazione standard 1)
    private fun normalizeSignal(input: DoubleArray): DoubleArray {
        val mean = input.average()
        val stdDev = sqrt(input.map { (it - mean).pow(2) }.average())

        return input.map { (it - mean) / stdDev }.toDoubleArray()
    }

    // Applica un filtro FIR passa banda (con finestra di Hamming)
    private fun firBandpassFilter(
        input: DoubleArray,
        fs: Double,             // frequenza di campionamento
        lowCut: Double,         // frequenza di taglio inferiore
        highCut: Double,        // frequenza di taglio superiore
        order: Int              // ordine del filtro (numero coefficienti - 1)
    ): DoubleArray {
        val nyquist = fs / 2.0
        val low = lowCut / nyquist
        val high = highCut / nyquist
        val coeffs = DoubleArray(order + 1)

        val mid = order / 2.0

        // Calcolo dei coefficienti con finestra di Hamming
        for (n in 0..order) {
            val sincHigh =
                if ((n - mid) == 0.0) 2 * high else sin(2 * PI * high * (n - mid)) / (PI * (n - mid))
            val sincLow =
                if ((n - mid) == 0.0) 2 * low else sin(2 * PI * low * (n - mid)) / (PI * (n - mid))
            val window = 0.54 - 0.46 * cos(2 * PI * n / order) // Hamming
            coeffs[n] = (sincHigh - sincLow) * window
        }

        // Normalizzazione per mantenere il guadagno a 1
        val gain = coeffs.sum()
        for (i in coeffs.indices) {
            coeffs[i] /= gain
        }

        // Zero-padding ai bordi (pad a sinistra con M zeri)
        val paddedInput = DoubleArray(input.size + order) { i ->
            if (i < order) 0.0 else input[i - order]
        }

        // Convoluzione (applicazione filtro FIR)
        val output = DoubleArray(input.size)
        for (i in output.indices) {
            var acc = 0.0
            for (j in coeffs.indices) {
                acc += coeffs[j] * paddedInput[i + order - j]
            }
            output[i] = acc
        }

        return output
    }

    // Estrae feature statistiche dai canali EEG filtrati e le salva in un CSV
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

        // Parsing del file e raccolta dei dati dei canali EEG
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

        // Riordinamento dei canali secondo la mappa degli elettrodi
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

        // Calcolo delle statistiche per ogni canale filtrato
        val stats = reorderedChannels.map { raw ->
            val normalizedInput = normalizeSignal(raw.toDoubleArray())
            val filtered = firBandpassFilter(normalizedInput, 250.0, 1.0, 50.0, 50)
            DescriptiveStatistics(filtered)
        }

        // Estrazione delle feature: media, std, max, min, curtosi, entropia di Shannon
        val means = stats.map { it.mean }
        val stds = stats.map { it.standardDeviation }
        val maxs = stats.map { it.max }
        val mins = stats.map { it.min }
        val kurtoses = stats.map { it.kurtosis }
        val entropies = reorderedChannels.map { raw ->
            val normalizedInput = normalizeSignal(raw.toDoubleArray())
            val filtered = firBandpassFilter(normalizedInput, 250.0, 1.0, 50.0, 50)
            computeShannonEntropy(filtered.toList())
        }

        // Intestazione file features
        val header = listOf(
            "MeanF3","MeanFz","MeanF4","MeanC3","MeanC4","MeanP3","MeanPz","MeanP4",
            "StdF3","StdFz","StdF4","StdC3","StdC4","StdP3","StdPz","StdP4",
            "MaxF3","MaxFz","MaxF4","MaxC3","MaxC4","MaxP3","MaxPz","MaxP4",
            "MinF3","MinFz","MinF4","MinC3","MinC4","MinP3","MinPz","MinP4",
            "KurtF3","KurtFz","KurtF4","KurtC3","KurtC4","KurtP3","KurtPz","KurtP4",
            "EntrF3","EntrFz","EntrF4","EntrC3","EntrC4","EntrP3","EntrPz","EntrP4"
        )

        val row = means + stds + maxs + mins + kurtoses + entropies

        // Scrittura sul file in append
        val writeHeader = !outputFile.exists()
        FileWriter(outputFile, true).use { writer ->
            if(writeHeader){
                writer.appendLine(header.joinToString(","))
            }
            writer.appendLine(row.joinToString(","))
        }
        return outputFile
    }

    // Calcola l'entropia di Shannon di una sequenza
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