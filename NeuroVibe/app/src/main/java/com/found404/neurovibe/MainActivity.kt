package com.found404.neurovibe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
//import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
//import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.found404.neurovibe.data.EEGDataProcessor
//import androidx.lifecycle.Observer
import com.found404.neurovibe.ml.TFLiteModel
import mylibrary.mindrove.SensorData
import mylibrary.mindrove.ServerManager
import java.io.File

private const val LOCAL_PERMISSION_REQUEST_CODE = 100

class MainActivity : AppCompatActivity() {

    private val acquiredData = mutableListOf<SensorData>()
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    private val serverManager = ServerManager { sensorData: SensorData ->
        if (isAcquiring.value == true) {
            acquiredData.add(sensorData)
            Log.d("Sensor Data", "Data acquired: $sensorData")
        }

        val dataString = """
            Acceleration X: ${sensorData.accelerationX}
            Acceleration Y: ${sensorData.accelerationY}
            Acceleration Z: ${sensorData.accelerationZ}
            Angular Rate X: ${sensorData.angularRateX}
            Angular Rate Y: ${sensorData.angularRateY}
            Angular Rate Z: ${sensorData.angularRateZ}
            Channel 1: ${sensorData.channel1}
            Channel 2: ${sensorData.channel2}
            Channel 3: ${sensorData.channel3}
            Channel 4: ${sensorData.channel4}
            Channel 5: ${sensorData.channel5}
            Channel 6: ${sensorData.channel6}
            Channel 7: ${sensorData.channel7}
            Channel 8: ${sensorData.channel8}
            Measurement #: ${sensorData.numberOfMeasurement}
        """.trimIndent()

        sensorDataText.postValue(dataString)
    }

    private val sensorDataText = MutableLiveData("No data yet")
    private val networkStatus = MutableLiveData("Checking network status...")
    private val isAcquiring = MutableLiveData(false)
    //private var isServerManagerStarted = false
    private var isWifiSettingsOpen = false
    private var selectedModelFile: String? = null
    private var model: TFLiteModel? = null

    private lateinit var textNetworkStatus: TextView
    private lateinit var textSensorData: TextView
    private lateinit var buttonStartEEG: Button
    private lateinit var buttonUseSmallModel: Button
    private lateinit var buttonUseBigModel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Carico il file di configurazione del layout
        setContentView(R.layout.activity_main)

        //Inizializzazione degli elementi base
        textNetworkStatus = findViewById(R.id.textNetworkStatus)
        textSensorData = findViewById(R.id.textSensorData)
        buttonStartEEG = findViewById(R.id.buttonStartEEG)
        buttonUseSmallModel = findViewById(R.id.buttonUseSmallModel)
        buttonUseBigModel = findViewById(R.id.buttonUseBigModel)


        buttonStartEEG.visibility = View.GONE
        buttonUseSmallModel.visibility = View.GONE
        buttonUseBigModel.visibility = View.GONE

        // Handler per la connessione alla rete
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                val isNetworkAvailable = isNetworkAvailable()
                if (!isNetworkAvailable) {
                    networkStatus.value = "No network connection. Please enable Wi-Fi."
                    if (!isWifiSettingsOpen) {
                        openWifiSettings()
                        isWifiSettingsOpen = true
                    }
                } else {
                    networkStatus.value = "Connected to the network."
                    isWifiSettingsOpen = false
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)

        // Observers
        sensorDataText.observe(this){
            if (isAcquiring.value == true) {
                textSensorData.visibility = View.VISIBLE
                textSensorData.text = it
            }
        }

        networkStatus.observe(this){
            textNetworkStatus.text = it
        }

        isAcquiring.observe(this){ acquiring ->
            if (!acquiring) {
                textSensorData.visibility = View.GONE
            }
        }

        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        serverManager.stop()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), LOCAL_PERMISSION_REQUEST_CODE)
        } else {
            startServerManager()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == LOCAL_PERMISSION_REQUEST_CODE){
            if(grantResults.all { it == PackageManager.PERMISSION_GRANTED }){
                startServerManager()
            } else {
                Toast.makeText(this, "I permessi sono necessari per avviare l'app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val wifiSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isWifiSettingsOpen = false
        }

    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        wifiSettingsLauncher.launch(intent)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun startServerManager() {

        val totalSegments = 5
        val segmentDurationMs = 2000L
        var currentSegment = 0
        val segmentHandler = Handler(Looper.getMainLooper())
        val exporter = EEGDataProcessor()

        acquiredData.clear()

        buttonStartEEG.setOnClickListener {
            if (isAcquiring.value != true) {
                isAcquiring.value = true
                textSensorData.visibility = View.VISIBLE
                textSensorData.text = getString(R.string.inizio_acquisizione, totalSegments)
                serverManager.start()

                val segmentRunnable = object : Runnable{
                    override fun run(){
                        currentSegment++
                        textSensorData.text = getString(R.string.acquisizione, currentSegment, totalSegments)

                        val currentData = ArrayList(acquiredData)
                        acquiredData.clear()

                        //esporta i dati in un file CSV
                        val rawfile = exporter.exportRawDataToCsv(currentData, currentSegment)
                        if(rawfile != null){
                            val featuresFile = File(rawfile.parent, "features_image_1.csv")
                            exporter.extractFeaturesToCsv(rawfile, featuresFile)
                        }

                        if(currentSegment < totalSegments){
                            segmentHandler.postDelayed(this, segmentDurationMs)
                        } else {
                            serverManager.stop()
                            isAcquiring.value = false
                            textSensorData.text = getString(R.string.fine_acquisizione)

                            buttonStartEEG.visibility = View.GONE
                            buttonUseSmallModel.visibility = View.VISIBLE
                            buttonUseBigModel.visibility = View.VISIBLE

                            val featuresFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "features_image_1.csv")

                            buttonUseSmallModel.setOnClickListener {
                                selectedModelFile = "Smallmodel100Neurons.tflite"
                                val predizioni = initializeModel(featuresFile)
                                val predictedClass = finalPrediction(predizioni)
                                Log.i("Predizione", "Predicted class: $predictedClass")
                            }

                            buttonUseBigModel.setOnClickListener {
                                selectedModelFile = "Bigmodel1000Neurons.tflite"
                                val predizioni = initializeModel(featuresFile)
                                val predictedClass = finalPrediction(predizioni)
                                Log.i("Predizione", "Predicted class: $predictedClass")
                            }
                        }
                    }
                }

                segmentHandler.postDelayed(segmentRunnable, segmentDurationMs)
            }
        }
            // Rendi visibili i pulsanti necessari
            buttonStartEEG.visibility = View.VISIBLE
        }
    private fun initializeModel(fileName: File): List<Int> {
        val predictedClasses = mutableListOf<Int>()

        selectedModelFile?.let { modelFile ->
            try {
                model = TFLiteModel(this, modelFile)
                for(i in 1..5){
                    val inputData = model?.loadCsvInput(this, fileName, linesToSkip = i)
                    if (inputData != null) {
                        val output = model?.predict(inputData)
                        output?.let {
                            val predictedClass = it.indices.maxByOrNull { idx -> it[idx] } ?: -1
                            predictedClasses.add(predictedClass)
                            Log.d("PREDIZIONE", "Riga $i → Classe $predictedClass → Output: ${it.joinToString()}")
                            Toast.makeText(this, "Riga $i → Classe $predictedClass", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("PREDIZIONE", "Errore nel caricamento dei dati di input alla riga $i")
                        Toast.makeText(this, "Errore alla riga $i", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Errore nell'inizializzazione del modello.", Toast.LENGTH_LONG).show()
            }
        }
        return predictedClasses
    }

    private fun finalPrediction(predictedClasses: List<Int>): Int{
        return predictedClasses
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: -1
    }
}
