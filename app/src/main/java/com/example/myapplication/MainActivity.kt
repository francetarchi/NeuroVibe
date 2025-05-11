package com.example.myapplication

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.myapplication.databinding.ActivityMainBinding
import android.os.Build
import android.provider.MediaStore.Downloads
import android.view.View

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.text.append
import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
    fun sendcsv(view: View){
    //fun sendcsv(context: Context, fileName: String, serverUrl: String, onResult: (Boolean) -> Unit) {
        val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        //val file = File(downloadDirectory, fileName)
        var csvname =  "test.csv"
        //createCsvFile(this, csvname, listOf(listOf("Column1", "Column2"), listOf("Value1", "Value2"), listOf("Value3", "Value4")))
        val file = File("/storage/emulated/0/Android/data/com.example.myapplication/files/Download/", csvname)
        sendFileToServer(
            file, "http://192.168.1.1434:8080/upload",
             { isSuccess, message -> if(isSuccess) {println("Success")}
                else
                {println("Failed: ${message?:"Unknown error"}")} }
        ) 
        println("Clicked")
    }

    fun sendFileToServer(
        file: File,
        serverUrl: String,
        onResult: (isSuccess: Boolean, message: String?) -> Unit
    ) {
        if (!file.exists()) {
            onResult(false, "File not found: ${file.absolutePath}")
            return
        }

        if (!file.isFile) {
            onResult(false, "Provided path is not a file: ${file.absolutePath}")
            return
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
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                onResult(false, "Network request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        // Handle successful response
                        val responseBody = response.body?.string()
                        onResult(true, "File uploaded successfully. Server response: $responseBody")
                    } else {
                        // Handle unsuccessful response
                        val errorBody = response.body?.string()
                        onResult(false, "File upload failed. Server code: ${response.code}, Error: $errorBody")
                    }
                }
            }
        })
    }


    fun createCsvFile(
        context: Context,
        fileName: String,
        data: List<List<String>>
    ): File? {
        // Get the app-specific external Downloads directory
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        if (directory == null || !directory.exists() && !directory.mkdirs()) {
            println("Error: Could not get or create app-specific external Downloads directory.")
            return null
        }

        val file = File(directory, fileName)

        try {
            FileWriter(file).use { writer ->
                for (rowData in data) {
                    writer.append(rowData.joinToString(",")) // Join columns with a comma
                    writer.append("\n") // Newline for the next row
                }
                writer.flush()
                println("CSV file created successfully at: ${file.absolutePath}")
                return file
            }
        } catch (e: IOException) {
            e.printStackTrace()
            println("Error writing CSV file: ${e.message}")
            return null
        }
    }
    
}
