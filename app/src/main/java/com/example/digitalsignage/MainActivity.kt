package com.example.digitalsignage

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.transition.Scene
import android.transition.Transition
import android.transition.TransitionManager
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.MediaController
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.digitalsignage.databinding.ActivityMainBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.set

data class DataClass(
    @SerializedName("date")
    val date: String? = "",
    @SerializedName("urls")
    val urls: List<String>? = listOf(),
    @SerializedName("startTime")
    val startTime: String? = "",
    @SerializedName("endTime")
    val endTime: String? = ""
)

data class UriMapper(
    val starTime: Long,
    val endTime: Long,
    val uriList: MutableList<Uri>? = null
)

data class PlayMapper(
    val starTime: Long,
    val endTime: Long,
    val uriList: MutableList<Uri>? = null,
    var isSlotCompleted: Boolean = true
)


class MainActivity : AppCompatActivity() {
    private lateinit var bindingActivity: ActivityMainBinding
    private var isDownloaded = false
    var PERMISSION_ALL = 101
    private var itemDownload = 0
    private var totalItems = 0
    private var uriHashMap: MutableMap<Long, UriMapper> = mutableMapOf()
    var timeSlotMap: MutableMap<Long, PlayMapper> = mutableMapOf<Long, PlayMapper>().toSortedMap()
    private lateinit var database: DatabaseReference
    private var slotAlreadyPassed = true

    @RequiresApi(Build.VERSION_CODES.O)
    private var currentTimeStamp = Instant.now().toEpochMilli()
    var PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context, intent: Intent) {
            val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            CheckDwnloadStatus(reference)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun CheckDwnloadStatus(id: Long) {
        // TODO Auto-generated method stub
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterById(id)
        val cursor: Cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val columnIndex: Int = cursor
                .getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status: Int = cursor.getInt(columnIndex)
            val columnReason: Int = cursor
                .getColumnIndex(DownloadManager.COLUMN_REASON)
            val reason: Int = cursor.getInt(columnReason)
            when (status) {
                DownloadManager.STATUS_FAILED -> {
                    var failedReason = ""
                    when (reason) {
                        DownloadManager.ERROR_CANNOT_RESUME -> failedReason = "ERROR_CANNOT_RESUME"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> failedReason =
                            "ERROR_DEVICE_NOT_FOUND"
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> failedReason =
                            "ERROR_FILE_ALREADY_EXISTS"
                        DownloadManager.ERROR_FILE_ERROR -> failedReason = "ERROR_FILE_ERROR"
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> failedReason =
                            "ERROR_HTTP_DATA_ERROR"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> failedReason =
                            "ERROR_INSUFFICIENT_SPACE"
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> failedReason =
                            "ERROR_TOO_MANY_REDIRECTS"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> failedReason =
                            "ERROR_UNHANDLED_HTTP_CODE"
                        DownloadManager.ERROR_UNKNOWN -> failedReason = "ERROR_UNKNOWN"
                    }
                    Toast.makeText(
                        this, "FAILED: $failedReason",
                        Toast.LENGTH_LONG
                    ).show()
                }
                DownloadManager.STATUS_PAUSED -> {
                    var pausedReason = ""
                    when (reason) {
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> pausedReason =
                            "PAUSED_QUEUED_FOR_WIFI"
                        DownloadManager.PAUSED_UNKNOWN -> pausedReason = "PAUSED_UNKNOWN"
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> pausedReason =
                            "PAUSED_WAITING_FOR_NETWORK"
                        DownloadManager.PAUSED_WAITING_TO_RETRY -> pausedReason =
                            "PAUSED_WAITING_TO_RETRY"
                    }
                    Toast.makeText(
                        this, "PAUSED: $pausedReason",
                        Toast.LENGTH_LONG
                    ).show()
                }
                DownloadManager.STATUS_PENDING -> Toast.makeText(this, "PENDING", Toast.LENGTH_LONG)
                    .show()
                DownloadManager.STATUS_RUNNING -> Toast.makeText(this, "RUNNING", Toast.LENGTH_LONG)
                    .show()
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d("Barcode", "Download Succesful")
                    val uri = downloadManager.getUriForDownloadedFile(id)
                    val uriMapper = uriHashMap[id]
                    if (uriMapper?.uriList == null) {
                        uriMapper?.copy(
                            uriList = mutableListOf(uri)
                        )?.let { uriHashMap.put(id, it) }
                    } else {
                        val list = uriMapper.uriList
                        list.add(uri)
                        uriHashMap.put(
                            id, uriMapper.copy(
                                uriList = list
                            )
                        )
                    }

                    itemDownload++
                    startPlaying()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bindingActivity.root)
        FirebaseApp.initializeApp(this)
        hasPermissions(context = this, PERMISSIONS)
        database = FirebaseDatabase.getInstance().reference
        setUpPlayer()
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_ALL)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_ALL) {
            grantResults.forEach {
                if (it != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                    return@onRequestPermissionsResult
                }
            }

        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun startPlaying() {
        if (itemDownload == totalItems) {
            isDownloaded = true
            lifecycleScope.launch(Dispatchers.Main) {
                uriHashMap.forEach {
                    val list: MutableList<Uri>? = timeSlotMap[it.value.starTime]?.uriList
                    if (list == null) {
                        val mutableList = mutableListOf<Uri>()
                        it.value.uriList?.let { it1 -> mutableList.addAll(it1) }
                        timeSlotMap.put(
                            it.value.starTime,
                            PlayMapper(
                                starTime = it.value.starTime,
                                endTime = it.value.endTime,
                                uriList = it.value.uriList
                            )
                        )
                    } else {
                        it.value.uriList?.let { it1 ->
                            timeSlotMap.get(it.value.starTime)?.uriList?.addAll(
                                it1
                            )
                        }
                    }
                }
                if (isDownloaded) {
                    Log.d("Barcode", "Calls Function one time only")
                    startPlayingIdex(timeSlotMap.values.toList(), 0)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startPlayingIdex(listFiles: List<PlayMapper>, i: Int) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (isDownloaded && i < listFiles.size) {
                currentTimeStamp = Instant.now().toEpochMilli()
                if (currentTimeStamp >= listFiles[i].starTime) {
                    currentTimeStamp = Instant.now().toEpochMilli()
                    if (listFiles[i].isSlotCompleted.not()) {
                        showCampaigs(listFiles[i].uriList!!, 0)
                    }
                    if (listFiles[i].endTime > currentTimeStamp) {
                        listFiles[i].isSlotCompleted = false
                        startPlayingIdex(listFiles, i)
                    } else {
                        listFiles[i].isSlotCompleted = true
                        startPlayingIdex(listFiles, i.inc())
                    }
                } else {
                    if (listFiles.size == i.inc()) {
                        showDefault(listFiles)
                    } else {
                        startPlayingIdex(listFiles, i.inc())
                    }
                }
            } else {
                showDefault(listFiles)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun showDefault(
        listFiles: List<PlayMapper>,
        shouldStartAgain: Boolean = true
    ) {
        bindingActivity.ImageView.isVisible = true
        bindingActivity.ImageView.setImageDrawable(resources.getDrawable(R.drawable.ic_launcher_background))
        currentTimeStamp = Instant.now().toEpochMilli()
        Log.d("Barcode", "When start from  default block : ${Date(currentTimeStamp)}")
        delay(5000L)
        if (shouldStartAgain) startPlayingIdex(listFiles, 0)
    }

    private suspend fun showCampaigs(listFiles: MutableList<Uri>, i: Int) {
        Log.d("Barcode", "sampaigs, ${i} ${listFiles}")
        if (i < listFiles.size) {
            val transition: Transition = CircularRevealTransition()
            val fileExt = MimeTypeMap.getFileExtensionFromUrl(listFiles[i].toString())
            bindingActivity.run {
                if (fileExt == "mp4") {
                    // val File = File()
                    transition.addTarget(bindingActivity.videoView)
                    transition.duration = 600
                    val scene = Scene(bindingActivity.root)
                    TransitionManager.go(scene, transition)
                    ImageView.isVisible = false
                    videoView.isVisible = true
                    videoView.setVideoURI(listFiles[i])
                    val mediaController = MediaController(this@MainActivity)
                    mediaController.setAnchorView(videoView)
                    videoView.setMediaController(mediaController)
                    videoView.setOnPreparedListener { p0 -> p0?.start() }
                    videoView.setOnCompletionListener { p0 ->
                        p0?.stop()
                        lifecycleScope.launch(Dispatchers.Main) {
                            showCampaigs(listFiles, i.inc())
                        }
                    }
                } else {
                    transition.addTarget(bindingActivity.ImageView)
                    transition.duration = 2000
                    val scene = Scene(bindingActivity.root)
                    TransitionManager.go(scene, transition)
                    videoView.isVisible = false
                    ImageView.isVisible = true
                    ImageView.setImageURI(listFiles[i])
                    delay(5000L)
                    showCampaigs(listFiles, i.inc())
                }
            }
        }
    }


    @SuppressLint("HardwareIds")
    private fun setUpPlayer() {
        cleanUpandReset()
        val android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Android", "Android ID : " + android_id)
        val data = database.child("user").child(android_id).child("campaigns")
            .addValueEventListener(object : ValueEventListener {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onDataChange(snapshot: DataSnapshot) {
                    cleanUpandReset()
                    val dataClassList = mutableListOf<DataClass>()
                    snapshot.children.forEach {
                        val dataClass = it.getValue(DataClass::class.java)
                        if (dataClass != null) {
                            dataClassList.add(
                                dataClass
                            )
                        }
                    }
                    if (dataClassList.isNotEmpty()) {
                        isDownloaded = false
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        System.out.println("Barcode" + Date().toInstant())
                        dataClassList.forEach { dataClass ->
                            totalItems += dataClass.urls!!.size
                            dataClass.urls.forEach {
                                dataClass.startTime?.let { it1 ->
                                    onDownload(
                                        it.trim(),
                                        it1,
                                        dataClass.endTime!!
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    //will do later
                }
            })


    }

    private fun cleanUpandReset() {
        val downloadFolder =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        for (tempFile in downloadFolder.listFiles()) {
            tempFile.delete()
        }
        uriHashMap.clear()
        timeSlotMap.clear()

    }


    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun onDownload(s: String, startTime: String, endTime: String) {
        // Picasso.with(this).load(s).into(picassoImageTarget(context = this, pathImage, "${imageName}.jpeg"));
        val dmr = DownloadManager.Request(Uri.parse(s))
        //Alternative if you don't know filename
        //Alternative if you don't know filename
        val fileName: String =
            URLUtil.guessFileName(s, null, MimeTypeMap.getFileExtensionFromUrl(s))
        dmr.setTitle(fileName)
        dmr.setDescription("Some descrition about file") //optional
        dmr.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        dmr.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        dmr.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val reference = manager.enqueue(dmr)
        uriHashMap[reference] = UriMapper(
            starTime = OffsetDateTime.parse(startTime).toInstant().toEpochMilli(),
            endTime = OffsetDateTime.parse(endTime).toInstant().toEpochMilli()
        )
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

}