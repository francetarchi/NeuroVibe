package com.found404.neurovibe

import android.content.pm.PackageManager
import android.location.LocationManager
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
import android.util.Log
import android.view.Menu
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.found404.neurovibe.databinding.ActivityMainBinding
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.found404.neurovibe.ml.TFLiteModelSmall

private val LOCAL_PERMISSION_REQUEST_CODE = 100

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


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun checkAndRequestPermissions(){
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if(missingPermissions.isNotEmpty()){
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), LOCAL_PERMISSION_REQUEST_CODE)
        } else {
            // All permissions are granted, proceed with your logic

            val model = TFLiteModelSmall(this)
            val input = model.loadCsvInput(this)

            input?.let {
                val prediction = model.predict(it)
                val predictedClass = prediction.indices.maxByOrNull { prediction[it] } ?: -1
                Log.i("Predizione", "Classe predetta: $predictedClass")
            }
        }
    }
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
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
        serverManager.stop()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == LOCAL_PERMISSION_REQUEST_CODE){
            if(grantResults.all{it == PackageManager.PERMISSION_GRANTED}) {

                val model = TFLiteModelSmall(this)
                val input = model.loadCsvInput(this)

                input?.let {
                    val prediction = model.predict(it)
                    val predictedClass = prediction.indices.maxByOrNull { prediction[it] } ?: -1
                    Log.i("Predizione", "Classe predetta: $predictedClass")
                }
            } else {
                Toast.makeText(this, "Permessi di localizzazione richiesti per connettersi al casco", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isLocationEnabled(): Boolean{
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
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
