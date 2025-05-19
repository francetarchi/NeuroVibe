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
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


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
        }

    private val sensorDataText = MutableLiveData("No data yet")
    private val networkStatus = MutableLiveData("Checking network status...")
    private val isAcquiring = MutableLiveData(false)
    private val showEEGButton = MutableLiveData(true)
    private val showBackHomeButton = MutableLiveData(false)
    private val showModelButtons = MutableLiveData(false)
    private val totalSegments = 5
    private var currentImageIndex = -1
    private var completeRound = -1
    private var selectedModelFile: String? = null
    private var model: TFLiteModel? = null
    private var serverIpAddress = ""
    private var serverUrl = ""

    private lateinit var featuresFile: File
    private lateinit var textNetworkStatus: TextView
    private lateinit var textSensorData: TextView
    private lateinit var textDataAcquiring: TextView
    private lateinit var textBatteryStatus: TextView
    private lateinit var textEegResult: TextView
    private lateinit var textEegRealResult: TextView
    private lateinit var logoImageView: ImageView
    private lateinit var galleryImageView: ImageView
    private lateinit var iconHappyMango: ImageView
    private lateinit var iconSadMango: ImageView
    private lateinit var buttonStartEEG: Button
    private lateinit var buttonBackHome: Button
    private lateinit var buttonUseSmallModel: Button
    private lateinit var buttonUseBigModel: Button
    private lateinit var buttonUseSmallModelEdge: Button
    private lateinit var buttonUseBigModelEdge: Button
    private lateinit var layoutDataAcquiring: View
    private lateinit var layoutBatteryStatus: View
    private lateinit var layoutNetworkStatus: View
    private lateinit var layoutWaitingEdge: View
    private lateinit var layoutEegResult: View

    private val imageList = listOf(
        R.drawable.image1,
        R.drawable.image2,
        R.drawable.image3,
        R.drawable.image4,
        R.drawable.image5,
        R.drawable.image6,
        R.drawable.image7
    )


    /// CLASS INSTANCE MANAGEMENT ///
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Impedisce all'applicazione di andare in modalità landscape
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        super.onCreate(savedInstanceState)

        // Carico il file di configurazione del layout
        setContentView(R.layout.activity_main)

        // Inizializzazione degli elementi base
        textNetworkStatus = findViewById(R.id.textNetworkStatus)
        textSensorData = findViewById(R.id.textSensorData)
        textDataAcquiring = findViewById(R.id.textDataAcquiring)
        textBatteryStatus = findViewById(R.id.textBatteryStatus)
        textEegResult = findViewById(R.id.textEegResult)
        textEegRealResult = findViewById(R.id.textEegRealResult)
        iconHappyMango = findViewById(R.id.iconHappyMango)
        iconSadMango = findViewById(R.id.iconSadMango)
        galleryImageView = findViewById(R.id.galleryImageView)
        logoImageView = findViewById(R.id.logoImageView)
        buttonStartEEG = findViewById(R.id.buttonStartEEG)
        buttonBackHome = findViewById(R.id.buttonBackHome)
        buttonUseSmallModel = findViewById(R.id.buttonUseSmallModel)
        buttonUseBigModel = findViewById(R.id.buttonUseBigModel)
        buttonUseSmallModelEdge = findViewById(R.id.buttonUseSmallModelEdge)
        buttonUseBigModelEdge = findViewById(R.id.buttonUseBigModelEdge)
        layoutDataAcquiring = findViewById(R.id.layoutDataAcquiring)
        layoutBatteryStatus = findViewById(R.id.layoutBatteryStatus)
        layoutNetworkStatus = findViewById(R.id.layoutNetworkStatus)
        layoutWaitingEdge = findViewById(R.id.layoutWaitingEdge)
        layoutEegResult = findViewById(R.id.layoutEegResult)

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
                    val ssid = getWiFiSsid() ?: "unknown network"
                    networkStatus.value = "Connected to $ssid."
                }
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)

        // Observers
        sensorDataText.observe(this){
            if (isAcquiring.value == true) {
                textSensorData.text = it
            }
        }

        networkStatus.observe(this){
            textNetworkStatus.text = it
        }

        showEEGButton.observe(this) { show ->
            buttonStartEEG.visibility = if(show) View.VISIBLE else View.INVISIBLE
            buttonStartEEG.isEnabled = show

            layoutNetworkStatus.visibility = if(show) View.VISIBLE else View.INVISIBLE
        }

        showBackHomeButton.observe(this) { show ->
            buttonBackHome.visibility = if (show) View.VISIBLE else View.INVISIBLE
            buttonBackHome.isEnabled = show
        }

        showModelButtons.observe(this) { show ->
            buttonUseSmallModel.visibility = if (show) View.VISIBLE else View.INVISIBLE
            buttonUseBigModel.visibility = if (show) View.VISIBLE else View.INVISIBLE
            buttonUseSmallModelEdge.visibility = if (show) View.VISIBLE else View.INVISIBLE
            buttonUseBigModelEdge.visibility = if (show) View.VISIBLE else View.INVISIBLE
        }

        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        serverManager.stop()
    }


    /// PERMISSION REQUEST FUNCTIONS ///
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


    /// SETTING LISTENERS ///
    @SuppressLint("SetTextI18n")
    private fun startServerManager() {
        serverManager.start()

        // Listener sul bottone per tornare alla home
        buttonBackHome.setOnClickListener {
            // Cambio il testo del bottone principale
            buttonStartEEG.text = getString(R.string.start_eeg)

            // Modifico l'interfaccia
            interfaceToHome()
        }

        // Listener sul bottone per iniziare un nuovo EEG
        buttonStartEEG.setOnClickListener {
            // Controllo che il dispositivo sia connesso alla rete del Mindrove
            if (!isConnectedToMindRove()) {
                showWiFiConnectionDialog()
                return@setOnClickListener
            }

            // Setto lo stato della batteria del caschetto
            var batteryString = if(estimateBatteryPercentage(voltage) == 120) "Charging..." else "${estimateBatteryPercentage(voltage)} %"
            textBatteryStatus.text = getString(R.string.battery_status, batteryString)

            // Cambio il testo del bottone principale
            buttonStartEEG.text = getString(R.string.next_image)

            // Scelgo l'immagine da visualizzare
            currentImageIndex = if(currentImageIndex != -1) (currentImageIndex + 1) % imageList.size else 0
            if (currentImageIndex == 0) completeRound += 1
            galleryImageView.setImageResource(imageList[currentImageIndex])

            // Modifico l'interfaccia
            interfaceToPaintingShow()

            // Inizio l'acquisizione dei dati
            acquiredData.clear()
            acquireData()
        }

        // Listeners sui bottoni che chiamano i modelli in locale
        buttonUseSmallModel.setOnClickListener {
            selectedModelFile = "Smallmodel100Neurons.tflite"
            val predictions = initializeModel(featuresFile)
            val predictedClass = finalPrediction(predictions)
            setEegResults(predictedClass)
        }
        buttonUseBigModel.setOnClickListener {
            selectedModelFile = "Bigmodel1000Neurons.tflite"
            val predictions = initializeModel(featuresFile)
            val predictedClass = finalPrediction(predictions)
            setEegResults(predictedClass)
        }

        // Listeners sui bottoni che richiamano i modelli in remoto (edge server)
        buttonUseSmallModelEdge.setOnClickListener {
            showIpRequestDialog("small", featuresFile)
        }
        buttonUseBigModelEdge.setOnClickListener {
            showIpRequestDialog("big", featuresFile)
        }
    }


    /// CONNECTION TO MINDROVE FUNCTIONS ///
    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        wifiSettingsLauncher.launch(intent)
    }

    @Suppress("DEPRECATION")
    private fun getWiFiSsid(): String? {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        return wifiManager.connectionInfo.ssid
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
            return getWiFiSsid() == "\"MindRove_ARC_ae01ec\""
        }

        return false
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


    /// ACQUIRING EEG DATA ///
    private fun acquireData() {
        val segmentDurationMs = 2000L
        var currentSegment = 1
        val segmentHandler = Handler(Looper.getMainLooper())
        val exporter = EEGDataProcessor(this)

        if (isAcquiring.value != true) {
            isAcquiring.value = true
            textDataAcquiring.text = getString(R.string.inizio_acquisizione, totalSegments)

            val segmentRunnable = object : Runnable{
                override fun run(){
                    val numEeg = currentSegment
                    currentSegment++
                    textDataAcquiring.text = getString(R.string.acquisizione, currentSegment, totalSegments)

                    val currentData = ArrayList(acquiredData)
                    acquiredData.clear()

                    // Esportazione dati in un file CSV
                    val rawfile = exporter.exportRawDataToCsv(currentData, numEeg, currentImageIndex)
                    if(rawfile != null){
                        val featuresFile = File(rawfile.parent, "features_image_${currentImageIndex}.csv")
                        exporter.extractFeaturesToCsv(rawfile, featuresFile)
                    }

                    if(currentSegment <= totalSegments){
                        segmentHandler.postDelayed(this, segmentDurationMs)
                    } else {
                        isAcquiring.value = false
                        textDataAcquiring.text = getString(R.string.fine_acquisizione)

                        // Salvo i dati in un file CSV
                        val dir = this@MainActivity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        if (dir == null) {
                            Toast.makeText(this@MainActivity, "Unable to access the save folder.", Toast.LENGTH_LONG).show()
                            return
                        }
                        featuresFile = File(dir, "features_image_${currentImageIndex}.csv")

                        Log.i("FILE", "File path: ${featuresFile.absolutePath}")
                        Log.i("FILE", "dir: ${dir.absolutePath}")
                        Log.i("FILE", "currentImageIndex: $currentImageIndex")

                        // Modifico l'interfaccia
                        interfaceToModelChoice()
                    }
                }
            }
            segmentHandler.postDelayed(segmentRunnable, segmentDurationMs)
        }
    }


    /// WORK ON MODELS ///
    private fun initializeModel(fileName: File): List<Int> {
        val predictedClasses = mutableListOf<Int>()

        selectedModelFile?.let { modelFile ->
            try {
                model = TFLiteModel(this, modelFile)
                for(i in 1..totalSegments){
                    val inputData = model?.loadCsvInput(fileName, linesToSkip = i + completeRound * totalSegments)
                    if (inputData != null) {
                        val output = model?.predict(inputData)
                        output?.let {
                            val predictedClass = it.indices.maxByOrNull { idx -> it[idx] } ?: -1
                            predictedClasses.add(predictedClass)
                            Log.d("PREDICTION", "Row $i → Class $predictedClass → Output: ${it.joinToString()}")
                        }
                    } else {
                        Log.e("PREDICTION", "Error loading input data at row $i")
                        Toast.makeText(this, "Error at row $i", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MODEL", "Error in model initialization: ${e.message}")
                Toast.makeText(this, "Error in model initialization. ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        return predictedClasses
    }

    private fun finalPrediction(predictedClasses: List<Int>) : Int {
        val predictedClass = predictedClasses
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: -1

        Log.i("Prediction", "Predicted class: $predictedClass")
        return predictedClass
    }


    /// DISPLAYING EEG RESULT ///
    private fun setEegResults(predictedClass: Int?) {
        // Controllo che il risultato sia valido
        if (predictedClass == null || predictedClass < 0 || predictedClass > 1) {
            Log.e("PREDICTION", "Predicted class has an invalid value: $predictedClass.")
            handleInvalidPredictionError(predictedClass)
        }

        // Setto il risultato numerico (classe predetta)
        textEegRealResult.text = buildString { append("Predicted class: ").append(predictedClass) }

        // Scelgo l'icona e il testo principale da visualizzare
        if (predictedClass == 0) {
            // Classe 0 ==> Opera non gradita
            iconSadMango.visibility = View.VISIBLE
            iconHappyMango.visibility = View.GONE
            textEegResult.text = getString(R.string.painting_disliked)
        } else {
            // Classe 1 ==> Opera gradita
            iconHappyMango.visibility = View.VISIBLE
            iconSadMango.visibility = View.GONE
            textEegResult.text = getString(R.string.painting_liked)
        }

        // Modifico l'interfaccia
        interfaceToEegResults()
    }


    /// EDGE SERVER FUNCTIONS ///
    private fun callEdgeServer(modelToUse: String, csvFile: File) {
        sendFileToServer(modelToUse, csvFile)

        // Modifico l'interfaccia
        interfaceToWaitingScreen()
    }

    private fun sendFileToServer(
        modelSelected : String,
        file: File
    ) {
        if (!file.exists()) {
            Log.e("FILE", "File not found: ${file.absolutePath}")
        }

        if (!file.isFile) {
            Log.e("FILE", "Provided path is not a file: ${file.absolutePath}")
        }

        // Determine the MIME type (content type) of the file.
        // For a CSV, it's typically "text/csv". You might want to
        // use a utility function to determine MIME type based on file extension
        // for more general file sending.
        val mediaType = "text/csv".toMediaTypeOrNull()

        // Create the request body from the file
        val requestBody = file.asRequestBody(mediaType)

        // Create the multipart request body (useful if your server expects a form-data upload)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", modelSelected)
            .addFormDataPart(
                "file", // The name of the form field on the server side (e.g., "file")
                file.name, // Use the original file name
                requestBody
            )
            .build()

        // Create the HTTP POST request
        val request = Request.Builder()
            .url(serverUrl)
            .post(multipartBody) // Use multipartBody for form-data upload
            .build()

        val client = OkHttpClient()

        // Execute the request asynchronously
        Log.d("DEBUG", "IP address used for this request: $serverIpAddress")
        Log.d("DEBUG", "Server URL used for this request: $serverUrl")
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("EDGESERVER", "Network request failed: ${e.message}")

                    // Sul thread principale (solo dal thread principale posso modificare la UI)
                    runOnUiThread {
                        handleEdgeConnectionError(e.message.toString())
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.i("EDGESERVER", "Network request successful")

                    response.use {
                        if (response.isSuccessful) {
                            Log.i("EDGESERVER", "File uploaded successfully.")

                            // Handle successful response
                            val responseBody = response.body?.string()
                            val gson = Gson()
                            val bodyJson = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)

                            // Tentativo di cast a Double (safe cast: se non riesce, ret sarà null)
                            // Questo passaggio serve perché il valore viene preso dal body automaticamente come java.lang.Double.
                            val ret = bodyJson["result"] as? Double

                            // Converto a Int se il cast a Double è riuscito (altrimenti retInt sarà null)
                            val retInt: Int? = ret?.toInt()

                            Log.i("EDGESERVER", "Server response: $retInt")

                            runOnUiThread { setEegResults(retInt) }
                        } else {
                            // Handle unsuccessful response
                            val errorBody = response.body?.string()

                            Log.i("EDGESERVER", "File upload failed. Server code: ${response.code}, Error: $errorBody")

                            runOnUiThread { handleEdgeConnectionError("File upload failed. Server code: ${response.code}, Error: $errorBody") }
                        }
                    }
                }
            }
        )
    }


    /// INTERFACE CHANGES ///
    private fun interfaceToHome() {
        // Sposto verso l'alto i bottoni "Start EEG" e "Home"
        buttonStartEEG.animate().translationY(0f).setDuration(1000).start()
        buttonBackHome.animate().translationY(0f).setDuration(1000).start()

        // Nascondo alcuni elementi
        buttonBackHome.visibility = View.INVISIBLE
        layoutDataAcquiring.visibility = View.INVISIBLE
        layoutBatteryStatus.visibility = View.INVISIBLE
        layoutWaitingEdge.visibility = View.INVISIBLE
        layoutEegResult.visibility = View.INVISIBLE

        // Visualizzo altri elementi
        logoImageView.visibility = View.VISIBLE
    }

    private fun interfaceToHomeWithError() {
        // Sposto verso l'alto i bottoni "Start EEG" e "Home"
        buttonStartEEG.translationY = 0f
        buttonBackHome.translationY = 0f

        // Nascondo alcuni elementi
        layoutWaitingEdge.visibility = View.INVISIBLE
        layoutEegResult.visibility = View.INVISIBLE

        // Visualizzo altri elementi
        showEEGButton.value = true
        showBackHomeButton.value = true
    }

    private fun interfaceToPaintingShow() {
        // Sposto verso il basso i bottoni "Start EEG" e "Home" (se "Start EEG" è al centro della schermata)
        if (buttonStartEEG.translationY == 0f) {
            buttonStartEEG.translationY = 325f
            buttonBackHome.translationY = 325f
        }

        // Nascondo alcuni elementi
        showEEGButton.value = false
        showBackHomeButton.value = false
        logoImageView.visibility = View.INVISIBLE
        layoutNetworkStatus.visibility = View.INVISIBLE
        layoutEegResult.visibility = View.INVISIBLE

        // Visualizzo altri elementi
        layoutBatteryStatus.visibility = View.VISIBLE
        layoutDataAcquiring.visibility = View.VISIBLE
        layoutBatteryStatus.visibility = View.VISIBLE
        galleryImageView.visibility = View.VISIBLE
    }

    private fun interfaceToModelChoice() {
        // Nascondo alcuni elementi
        galleryImageView.visibility = View.INVISIBLE

        // Visualizzo altri elementi
        showModelButtons.value = true
    }

    private fun interfaceToWaitingScreen() {
        // Nascondo alcuni elementi
        showModelButtons.value = false

        // Visualizzo altri elementi
        layoutWaitingEdge.visibility = View.VISIBLE
    }

    private fun interfaceToEegResults() {
        // Sposto verso il basso i bottoni "Start EEG" e "Home"
        buttonStartEEG.translationY = 325f
        buttonBackHome.translationY = 325f

        // Nascondo alcuni elementi
        showModelButtons.value = false
        layoutWaitingEdge.visibility = View.INVISIBLE

        // Visualizzo altri elementi
        showEEGButton.value = true
        showBackHomeButton.value = true
        layoutEegResult.visibility = View.VISIBLE
    }


    /// ERROR HANDLERS ///
    private fun handleEdgeConnectionError(errMsg: String) {
        interfaceToHomeWithError()
        showEdgeConnectionErrorDialog(errMsg)
    }

    private fun handleInvalidPredictionError(prediction: Int?) {
        interfaceToHomeWithError()
        showInvalidPredictionErrorDialog(prediction)
    }


    /// DIALOGS ///
    // Dialog per la connessione alla rete WiFi del casco
    private fun showWiFiConnectionDialog() {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("Connect to Mindrove")
        builder.setMessage("Before starting EEG data receiving, you must connect your device to Mindrove WiFi network.")

        // Pulsante POSITIVO
        builder.setPositiveButton("Open WiFi settings") { dialog, which ->
            openWifiSettings()
            dialog.dismiss()
        }

        // Pulsante NEGATIVO
        builder.setNegativeButton("Cancel") { dialog, which ->
            dialog.dismiss()
        }

        // Creo l'alert e lo mostro a schermo
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    /// Dialog per richiedere l'indirizzo IP del server edge ///
    private fun showIpRequestDialog(modelToUse: String, csvFile: File) {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("Set IP Address")
        builder.setMessage("Set in the input below the IP address of the PC where the edge serve is currently running.\nYou can read the IP address on MindRove WiFi properties on the PC.\n")

        // Aggiungi un campo di input
        val input = EditText(this)
        input.hint = "IP Address"
        input.inputType = android.text.InputType.TYPE_CLASS_PHONE
        builder.setView(input)

        // Pulsante POSITIVO
        builder.setPositiveButton("Ok") { dialog, which ->
            serverIpAddress = input.text.toString()
            serverUrl = "http://$serverIpAddress:8080/upload"
            callEdgeServer(modelToUse, csvFile)

            dialog.dismiss()
        }

        // Pulsante NEGATIVO
        builder.setNegativeButton("Cancel") { dialog, whihch ->
            dialog.dismiss()
        }

        // Se non è il primo test che stiamo facendo, mostro i dati riguardo all'ultimo indirizzo IP utilizzato e permetto di riutilizzarlo
        if (serverUrl != "") {
            // Aggiungo una stringa al testo già settato
            builder.setMessage("Set in the input below the IP address of the PC where the edge serve is currently running.\nYou can read the IP address on MindRove WiFi properties on the PC.\n\nLast IP address used: \"$serverIpAddress\".\n")

            // Pulsante NEUTRO
            builder.setNeutralButton("Use last IP") { dialog, which ->
                callEdgeServer(modelToUse, csvFile)

                dialog.dismiss()
            }
        }

        // Creo l'alert e lo mostro a schermo
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    // Dialog per l'eventuale errore di connessione all'edge server
    private fun showEdgeConnectionErrorDialog(errMsg: String) {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("Connection Error")
        builder.setMessage("There was an error connecting to the edge server.\n\n$errMsg")

        // Pulsante POSITIVO
        builder.setPositiveButton("Ok") { dialog, which ->
            dialog.dismiss()
        }

        // Creo l'alert e lo mostro a schermo
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }

    // Dialog per gestire un valore non valido della classe predetta
    private fun showInvalidPredictionErrorDialog(prediction: Int?) {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("Invalid prediction")
        builder.setMessage("Predicted class has an invalid value: $prediction.")

        // Pulsante POSITIVO
        builder.setPositiveButton("Ok") { dialog, which ->
            dialog.dismiss()
        }

        // Creo l'alert e lo mostro a schermo
        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()
    }
}
