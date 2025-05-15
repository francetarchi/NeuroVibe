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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import kotlin.text.append
import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter

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
        var csvname =  "input.csv"

        val context : Context = this
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, csvname)
        try {
            // Scrittura su file
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write("MeanF3,MeanFz,MeanF4,MeanC3,MeanC4,MeanP3,MeanPz,MeanP4,StdF3,StdFz,StdF4,StdC3,StdC4,StdP3,StdPz,StdP4,MaxF3,MaxFz,MaxF4,MaxC3,MaxC4,MaxP3,MaxPz,MaxP4,MinF3,MinFz,MinF4,MinC3,MinC4,MinP3,MinPz,MinP4,KurtF3,KurtFz,KurtF4,KurtC3,KurtC4,KurtP3,KurtPz,KurtP4,EntrF3,EntrFz,EntrF4,EntrC3,EntrC4,EntrP3,EntrPz,EntrP4\n" +
                            "6.355109805087943E-5,0.007202566410071986,0.005223433141151553,0.005951835202054556,0.0017933956471452724,0.007202566410071986,0.008979916034338847,0.008979916034338847,1.6512381089343924,1.6893770912104769,1.68766444276026,1.6709582823195432,1.6788205468644521,1.6893770912104769,1.6601104458644327,1.6601104458644327,3.033024652832938,2.9307984202180295,2.1962367234443425,2.442273900003007,2.555793386773969,2.9307984202180295,2.2641515361316897,2.2641515361316897,-2.545103719656989,-3.078916995693976,-3.7082811579542474,-2.871566162215641,-2.868284192643797,-3.078916995693976,-3.1335466334223305,-3.1335466334223305,-1.1639234197978099,-0.8837711163584543,-0.48114870239559693,-1.4085520801204756,-1.3888440152669457,-0.8837711163584543,-1.1872912684816528,-1.1872912684816528,2.932088392843633,2.871811100167423,2.6264723010041764,2.91182098557638,2.941212229744598,2.871811100167423,2.8908549934996364,2.8908549934996364\n" +
                            "-3.648735127381108E-4,0.005372819206398886,0.004537493806545269,0.0014107643518175537,0.0012655617419797967,0.005372819206398886,0.0023518700350628518,0.0023518700350628518,1.6549607251877982,1.6903020397573727,1.6885397401530986,1.6819341368033887,1.680514802882623,1.6903020397573727,1.6787063270785374,1.6787063270785374,3.0083982635612085,2.585685582646071,2.152118791742056,2.4745641671040643,2.5666532679063687,2.585685582646071,2.3749963859807037,2.3749963859807037,-2.5265712079084963,-3.1716034934441772,-3.621072252709164,-2.6835881733040376,-2.611972657467185,-3.1716034934441772,-2.995446825330068,-2.995446825330068,-1.1496618055122008,-0.9135151890248023,-0.5377190232435134,-1.4314031556239073,-1.449742055233029,-0.9135151890248023,-1.286829607259477,-1.286829607259477,2.936891881456205,2.876846019408867,2.6871336909185612,2.9181340839410446,2.929862276889132,2.876846019408867,2.9095524064185683,2.9095524064185683\n" +
                            "-0.0025931841192273026,-0.0021471229386839413,-0.003703093119680291,9.813816853346598E-4,-8.774805056599461E-4,-0.0021471229386839413,0.0016502321727039815,0.0016502321727039815,1.6593702250673052,1.694496867387847,1.6932109220223064,1.6830594596893536,1.6842347317484276,1.694496867387847,1.6808115250331854,1.6808115250331854,3.0248344910737432,2.5271026244972994,2.1702018868560793,2.503538239987708,2.572682425632394,2.5271026244972994,2.4084321807852316,2.4084321807852316,-2.4938283515489177,-3.1522228072759915,-3.5952263142115313,-2.8132829911059125,-2.550939860555104,-3.1522228072759915,-3.1161817866931347,-3.1161817866931347,-1.1830923126962152,-0.9175433657374561,-0.5483052666832999,-1.4385083121773554,-1.4658212549035419,-0.9175433657374561,-1.3049018960472472,-1.3049018960472472,2.910798967080882,2.859067932531407,2.6998424593981296,2.9339198146999106,2.9157222983696496,2.859067932531407,2.9280395776112904,2.9280395776112904\n" +
                            "0.00137177161908326,-0.0038204702090840993,-0.003970541703099009,0.001475051266614477,5.285546590211672E-4,-0.0038204702090840993,9.702944247498724E-4,9.702944247498724E-4,1.6546809713445456,1.6860978137531322,1.6845300892593553,1.6767503915463629,1.6757306534969258,1.6860978137531322,1.6750077864345572,1.6750077864345572,3.0433662514316984,2.410669777611912,2.1758770061560253,2.5090016582554253,2.5939391782997347,2.410669777611912,2.4444884019021287,2.4444884019021287,-2.505268587429171,-3.223926725844478,-3.532754355140896,-2.7205746214927053,-2.4925061067961054,-3.223926725844478,-3.037390511150832,-3.037390511150832,-1.1375099383678908,-0.8851272661462963,-0.5563655384551653,-1.4374099881026143,-1.450992167651606,-0.8851272661462963,-1.3452361952090348,-1.3452361952090348,2.917730431442063,2.851173355360852,2.713414445121377,2.915178222605435,2.911903541305978,2.851173355360852,2.9337177879212697,2.9337177879212697\n" +
                            "9.666930537736685E-4,-0.005128118575503002,-0.005752221985181603,0.0030080160379739105,-0.001623593198135554,-0.005128118575503002,0.003840911195667819,0.003840911195667819,1.6382482660222524,1.6831593676868823,1.6860954305208835,1.6540256468466574,1.673801205929315,1.6831593676868823,1.6366946184216962,1.6366946184216962,3.0885917661404485,2.8267927020349233,2.3626655740610882,2.4726893704157207,2.5238830648730906,2.8267927020349233,2.3093222880301774,2.3093222880301774,-2.5192763777678584,-3.19687720382431,-3.729877079963591,-3.9672078250530074,-2.915840462825632,-3.19687720382431,-4.772340255816778,-4.772340255816778,-1.1442646893304058,-0.8459528395541316,-0.4967392424236823,-1.2635791867602475,-1.3647746830344418,-0.8459528395541316,-0.8717901404124344,-0.8717901404124344,2.9310164286942864,2.854530661281509,2.7559079515713907,2.781256808386672,2.9352130432663364,2.854530661281509,2.684736954799152,2.684736954799152")
                }
            }
            println("File creato: ${file.absolutePath}")
        } catch (e: Exception) {
            println("Errore nel salvataggio: ${e.message}")
            null
        }
        //createCsvFile(this, csvname, listOf(listOf("Column1", "Column2"), listOf("Value1", "Value2"), listOf("Value3", "Value4")))
        //val file = File(dir, csvname)
        sendFileToServer(
            "big",
            file, "http://192.168.157.63:8080/upload",
             { isSuccess, message -> if(isSuccess) {println(message)}
                else
                {println("Failed: ${message?:"Unknown error"}")} }
        ) 
        println("Clicked")
    }

    fun sendFileToServer(
        model_selected : String,
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
            .addFormDataPart("model", model_selected)
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
                        val gson = Gson()
                        val body_json = gson.fromJson<Map<String, Any>>(responseBody, object : TypeToken<Map<String, Any>>() {}.type)
                        val ret = body_json["result"]
                        onResult(true, "File uploaded successfully. Server response: $ret")
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
