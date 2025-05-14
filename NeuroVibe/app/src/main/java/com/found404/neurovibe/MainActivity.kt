package com.found404.neurovibe

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.found404.neurovibe.data.EEGDataProcessor
import com.found404.neurovibe.ml.TFLiteModel
import mylibrary.mindrove.SensorData
import mylibrary.mindrove.ServerManager
import java.io.File
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatDelegate

private const val LOCAL_PERMISSION_REQUEST_CODE = 100

class MainActivity : AppCompatActivity() {

    private val acquiredData = mutableListOf<SensorData>()
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var voltage: UInt = 0u

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

        voltage = sensorData.voltage

        sensorDataText.postValue(dataString)
    }

    private val wifiSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isWifiSettingsOpen = false
        }

    private val sensorDataText = MutableLiveData("No data yet")
    private val networkStatus = MutableLiveData("Checking network status...")
    private val isAcquiring = MutableLiveData(false)
    private val showModelButtons = MutableLiveData(false)
    private val showEEGButton = MutableLiveData(true)
    private val totalSegments = 5
    private var isWifiSettingsOpen = false
    private var selectedModelFile: String? = null
    private var model: TFLiteModel? = null

    private lateinit var textNetworkStatus: TextView
    private lateinit var textSensorData: TextView
    private lateinit var textAcquiring: TextView
    private lateinit var textBatteryStatus: TextView
    private lateinit var logoImageView: ImageView
    private lateinit var galleryImageView: ImageView
    private lateinit var buttonStartEEG: Button
    private lateinit var buttonUseSmallModel: Button
    private lateinit var buttonUseBigModel: Button

    private val imageList = listOf(
        R.drawable.image1,
        R.drawable.image2,
        R.drawable.image3,
        R.drawable.image4,
        R.drawable.image5,
        R.drawable.image6,
        R.drawable.image7
    )
    private var currentImageIndex = -1
    private var completeRound = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        // TODO: Rimuovere questa riga in produzione
        // Imposta il tema chiaro a scopo di debug
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Impedisce all'applicazione di andare in modalità landscape
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        super.onCreate(savedInstanceState)

        // Carico il file di configurazione del layout
        setContentView(R.layout.activity_main)

        // Inizializzazione degli elementi base
        textNetworkStatus = findViewById(R.id.textNetworkStatus)
        textSensorData = findViewById(R.id.textSensorData)
        textAcquiring = findViewById(R.id.textAcquiring)
        textBatteryStatus = findViewById(R.id.textBatteryStatus)
        buttonStartEEG = findViewById(R.id.buttonStartEEG)
        buttonUseSmallModel = findViewById(R.id.buttonUseSmallModel)
        buttonUseBigModel = findViewById(R.id.buttonUseBigModel)
        galleryImageView = findViewById(R.id.galleryImageView)
        logoImageView = findViewById(R.id.logoImageView)

        val dir = this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        // Pulisco la cartella /storage/emulated/0/Android/data/com.found404.neurovibe/files/Download/
        dir?.let {
            if (it.exists() && it.isDirectory) {
                it.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        }

        // Handler per la connessione alla rete
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                val isNetworkAvailable = isNetworkAvailable()
                if (!isNetworkAvailable) {
                    networkStatus.value = "No network connection.\nPlease enable Wi-Fi."
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

        showEEGButton.observe(this) { show ->
            buttonStartEEG.visibility = if(show) View.VISIBLE else View.GONE
        }

        showModelButtons.observe(this) { show ->
            buttonUseSmallModel.visibility = if (show) View.VISIBLE else View.GONE
            buttonUseBigModel.visibility = if (show) View.VISIBLE else View.GONE
        }

        checkAndRequestPermissions()
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        serverManager.stop()
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
                Toast.makeText(this, "Permissions are required to start the app.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        wifiSettingsLauncher.launch(intent)
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun isConnectedToMindRove(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        val isWifiConnected = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        if (isWifiConnected) {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo.ssid
            return ssid == "\"MindRove_ARC_ae01ec\""
        }

        return false
    }

    @SuppressLint("SetTextI18n")
    private fun startServerManager() {
        showEEGButton.value = true
        serverManager.start()

        buttonStartEEG.setOnClickListener {
            // Controllo che il dispositivo sia connesso al casco
            if (!isConnectedToMindRove()) {
                Toast.makeText(this, "Connect to the headset's Wi-Fi network to get started.", Toast.LENGTH_LONG).show()

                // Apro automaticamente le impostazione del WiFi
                if (!isWifiSettingsOpen) {
                    openWifiSettings()
                    isWifiSettingsOpen = true
                }
                return@setOnClickListener
            }

            acquiredData.clear()

            buttonStartEEG.text = "Next Image"
            // Mostro stato della batteria del caschetto
            var batteryString = if(estimateBatteryPercentage(voltage) == 120) "Charging..." else "${estimateBatteryPercentage(voltage)} %"
            textBatteryStatus.text = getString(R.string.battery_status, batteryString)
            textBatteryStatus.visibility = View.VISIBLE

            buttonStartEEG.text = getString(R.string.next_image)
            val segmentDurationMs = 2000L
            var currentSegment = 1
            val segmentHandler = Handler(Looper.getMainLooper())
            val exporter = EEGDataProcessor(this)

            showEEGButton.value = false

            logoImageView.visibility = View.GONE
            textSensorData.visibility = View.VISIBLE
            textNetworkStatus.visibility = View.GONE

            currentImageIndex = if(currentImageIndex != -1) (currentImageIndex + 1) % imageList.size else 0
            if (currentImageIndex == 0) completeRound += 1
            galleryImageView.setImageResource(imageList[currentImageIndex])

            if (isAcquiring.value != true) {
                isAcquiring.value = true
                textAcquiring.visibility = View.VISIBLE
                textAcquiring.text = getString(R.string.inizio_acquisizione, totalSegments)


                val segmentRunnable = object : Runnable{
                    override fun run(){
                        currentSegment++
                        textAcquiring.text = getString(R.string.acquisizione, currentSegment, totalSegments)

                        val currentData = ArrayList(acquiredData)
                        acquiredData.clear()

                        // Esportazione dati in un file CSV
                        val rawfile = exporter.exportRawDataToCsv(currentData, currentSegment, currentImageIndex)
                        if(rawfile != null){
                            val featuresFile = File(rawfile.parent, "features_image_${currentImageIndex}.csv")
                            exporter.extractFeaturesToCsv(rawfile, featuresFile)
                        }

                        if(currentSegment <= totalSegments){
                            segmentHandler.postDelayed(this, segmentDurationMs)
                        } else {
                            isAcquiring.value = false
                            textAcquiring.text = getString(R.string.fine_acquisizione)
                            textSensorData.visibility = View.GONE

                            textSensorData.text = getString(R.string.fine_acquisizione)

                            val dir = this@MainActivity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            if (dir == null) {
                                Toast.makeText(this@MainActivity, "Unable to access the save folder.", Toast.LENGTH_LONG).show()
                                return
                            }
                            val featuresFile = File(dir, "features_image_${currentImageIndex}.csv")

                            showModelButtons.value = true

                            buttonUseSmallModel.setOnClickListener {
                                selectedModelFile = "Smallmodel100Neurons.tflite"
                                val predizioni = initializeModel(featuresFile)
                                val predictedClass = finalPrediction(predizioni)
                                Log.i("Predizione", "Predicted class: $predictedClass")

                                showModelButtons.value = false
                                showEEGButton.value = true

                                textSensorData.visibility = View.GONE
                                textNetworkStatus.visibility = View.VISIBLE
                            }

                            buttonUseBigModel.setOnClickListener {
                                selectedModelFile = "Bigmodel1000Neurons.tflite"
                                val predizioni = initializeModel(featuresFile)
                                val predictedClass = finalPrediction(predizioni)
                                Log.i("Predizione", "Predicted class: $predictedClass")

                                showModelButtons.value = false
                                showEEGButton.value = true

                                textSensorData.visibility = View.GONE
                                textNetworkStatus.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                segmentHandler.postDelayed(segmentRunnable, segmentDurationMs)

            }
        }
    }

    private fun initializeModel(fileName: File): List<Int> {
        val predictedClasses = mutableListOf<Int>()

        selectedModelFile?.let { modelFile ->
            try {
                model = TFLiteModel(this, modelFile)
                for(i in 1..totalSegments){
                    val inputData = model?.loadCsvInput(this, fileName, linesToSkip = i + completeRound * totalSegments)
                    if (inputData != null) {
                        val output = model?.predict(inputData)
                        output?.let {
                            val predictedClass = it.indices.maxByOrNull { idx -> it[idx] } ?: -1
                            predictedClasses.add(predictedClass)
                            Log.d("PREDIZIONE", "Row $i → Class $predictedClass → Output: ${it.joinToString()}")
                        }
                    } else {
                        Log.e("PREDIZIONE", "Errore nel caricamento dei dati di input alla riga $i")
                        Toast.makeText(this, "Error at row $i", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error in model initialization.", Toast.LENGTH_LONG).show()
            }
        }
        return predictedClasses
    }

    private fun finalPrediction(predictedClasses: List<Int>): Int{
        val predictedClass = predictedClasses
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: -1

        Toast.makeText(this, "Predicted class: $predictedClass", Toast.LENGTH_LONG).show()

        return predictedClass
    }

    private fun estimateBatteryPercentage(mv: UInt): Int {
        val table = listOf(
            4200 to 100,
            4100 to 90,
            4000 to 80,
            3900 to 75,
            3800 to 70,
            3700 to 65,
            3600 to 50,
            3500 to 45,
            3400 to 40,
            3300 to 30,
            3200 to 10,
            3000 to 0
        )

        for (i in 0 until table.size - 1) {
            val (v1, p1) = table[i]
            val (v2, p2) = table[i + 1]

            if (mv >= v2.toUInt() && mv <= v1.toUInt()) {
                // Interpolazione lineare tra i due punti
                val fraction = (mv - v2.toUInt()).toDouble() / (v1 - v2)
                val percent = p2 + fraction * (p1 - p2)
                return percent.toInt().coerceIn(0, 100)
            }
        }
        return if (mv >= 4200u) 120 else 0
    }
}
