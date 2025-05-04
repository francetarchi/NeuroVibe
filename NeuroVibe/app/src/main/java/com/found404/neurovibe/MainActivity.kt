package com.found404.neurovibe

import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
// import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
// import androidx.compose.runtime.setValue
// import androidx.compose.runtime.mutableStateOf
// import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import mylibrary.mindrove.SensorData
import mylibrary.mindrove.ServerManager

class MainActivity : ComponentActivity() {
    private val serverManager = ServerManager { sensorData: SensorData ->
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
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var isServerManagerStarted = false
    private var isWifiSettingsOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handler = Handler(Looper.getMainLooper())
        runnable = Runnable {
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

                if (!isServerManagerStarted) {
                    serverManager.start()
                    isServerManagerStarted = true
                }
            }
            handler.postDelayed(runnable, 3000)
        }

        handler.post(runnable)

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val networkStatusValue by networkStatus.observeAsState("Checking network status...")
                val sensorDataTextValue by sensorDataText.observeAsState("No data yet")

                // var isPaused by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Network: $networkStatusValue")
                    Text(text = "Sensor Data: $sensorDataTextValue")
                    Spacer(modifier = Modifier.height(16.dp))

//                    Button(onClick = {
//                        if (isPaused) {
//                            serverManager.resume()
//                        } else {
//                            serverManager.pause()
//                        }
//                        isPaused = !isPaused
//                    }) {
//                        Text(text = if (isPaused) "Resume Server" else "Pause Server")
//                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        serverManager.stop()
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

    private val wifiSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isWifiSettingsOpen = false
        }

    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        wifiSettingsLauncher.launch(intent)
    }
}
