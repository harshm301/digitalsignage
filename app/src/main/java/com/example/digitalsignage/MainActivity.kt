package com.example.digitalsignage

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.digitalsignage.databinding.ActivityMainBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import io.paperdb.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import kotlin.collections.set


class MainActivity : AppCompatActivity() {
    private lateinit var bindingActivity: ActivityMainBinding
    var PERMISSION_ALL = 101
    private var itemDownload = 0
    private var totalItems = 0
    private var uriHashMap: MutableMap<Long, UriMapper> = mutableMapOf()
    private var ISFTU = "ISFTU"
    private var IS_DOWNLOAD_AGAIN = "IS_DOWNLOAD_AGAIN"
    private var DeviceId = "DeviceId"
    var timeSlotMap: MutableMap<Long, PlayMapper> = mutableMapOf<Long, PlayMapper>().toSortedMap()
    private lateinit var database: DatabaseReference
    private var defaultList = mutableListOf<String>()
    private var defaultUriMapper: MutableMap<Long, Uri?> = mutableMapOf()
    private var isDefaultBeingShown = false
    private var isCampaignBeignShown = false
    private var campaignRefreshed = false
    private var isVideoPlaying = true
    private var isLocalDefaultImageisShown = false


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
                    if (uri != null) {
                        if (uriHashMap.contains(id)) {
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
                        } else if (defaultUriMapper.contains(id)) {
                            defaultUriMapper[id] = uri
                        }
                        itemDownload++
                        Toast.makeText(
                            this,
                            "${totalItems - itemDownload} more item to download",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (itemDownload == totalItems) {
                            startPlaying()
                        }
                    }
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bindingActivity.root)
        try {
            FirebaseApp.initializeApp(this)
            hasPermissions(context = this, PERMISSIONS)
            database = FirebaseDatabase.getInstance().reference
            Paper.book().write(IS_DOWNLOAD_AGAIN, false)
            if (Paper.book().read(ISFTU, false) == false && Paper.book().contains(DeviceId).not()) {
                setUpDeviceId()
            } else {
                setUpPlayer()
            }
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setUpDeviceId() {
        val alert: AlertDialog.Builder = AlertDialog.Builder(this)
        alert.setTitle("Enter device id")
        alert.setMessage("you can only set device id once")
        alert.setCancelable(false)

// Set an EditText view to get user input

// Set an EditText view to get user input
        val input = EditText(this)
        alert.setView(input)

        alert.setPositiveButton("Ok", DialogInterface.OnClickListener { dialog, whichButton ->
            val value: Editable? = input.text
            pushDeviceIdToFirebase(value.toString())
            // Do something with value!
        })

        alert.setNegativeButton("Cancel",
            DialogInterface.OnClickListener { dialog, whichButton ->
                // Canceled.
                finish()
            })

        alert.show()
    }

    private fun pushDeviceIdToFirebase(toString: String) {
        val map = mapOf(
            toString to ""
        )
        Paper.book().write(DeviceId, toString)
        database.child("user").updateChildren(map)
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
        lifecycleScope.launch(Dispatchers.Main) {
            uriHashMap.forEach {
                val list: MutableList<Uri>? = timeSlotMap[it.value.starTime]?.uriList
                if (list == null) {
                    val mutableList = mutableListOf<Uri>()
                    it.value.uriList?.let { it1 -> mutableList.addAll(it1) }
                    timeSlotMap.put(
                        it.value.starTime,
                        PlayMapper(
                            startTime = it.value.starTime,
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

            bindingActivity.progressCircular.isVisible = false
            campaignRefreshed = true
            val listFiles = timeSlotMap.values.toList().sortedBy {
                it.startTime
            }.filterNot {
                it.endTime <= currentTimeStamp && it.startTime <= currentTimeStamp
            }.toMutableList()
            Log.d("Barcode", "Calls Function one time only ${listFiles.size}")
            isLocalDefaultImageisShown = false
            startPlayingIdex(listFiles)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun startPlayingIdex(listFiles: MutableList<PlayMapper>) {
        if(isLocalDefaultImageisShown.not()) {
            if (listFiles.isEmpty()) {
                showDefault()
            }
            currentTimeStamp = Instant.now().toEpochMilli()
            for (playMapper in listFiles) {
                if (currentTimeStamp >= playMapper.startTime && playMapper.endTime >= currentTimeStamp) {
                    playMapper.uriList?.let {
                        showCampaigs(it)
                    }
                    currentTimeStamp = Instant.now().toEpochMilli()
                    if (currentTimeStamp >= playMapper.endTime) {
                        listFiles.remove(playMapper)
                    }
                    break
                } else {
                    showDefault()
                }
            }
            startPlayingIdex(listFiles)
        }
        //  if (index == listFiles.size) {

        //   }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun showDefault() {
        if (defaultUriMapper.values.isEmpty() || isLocalDefaultImageisShown) {
            return
        }
        val list = defaultUriMapper.values.toList()
        list.forEach {
            currentTimeStamp = if (it != null) {
                playImageorVideo(it)
                Log.d("Barcode", "When start from  default block : ${Date(currentTimeStamp)}")
                Instant.now().toEpochMilli()
            } else {
                bindingActivity.ImageView.setImageDrawable(resources.getDrawable(R.mipmap.ic_launcher))
                Instant.now().toEpochMilli()
            }
        }
    }

    private suspend fun showCampaigs(listFiles: MutableList<Uri>) {
        Log.d("Barcode", "sampaigs, ${listFiles}")
        listFiles.forEach {
            playImageorVideo(it)
        }
    }

    private suspend fun playImageorVideo(uri: Uri) {
        //val transition: Transition = CircularRevealTransition()
        try {
            val fileExt = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(contentResolver.getType(uri))
            bindingActivity.run {
                if (fileExt == "mp4") {
                    // val File = File()
                    //transition.addTarget(bindingActivity.videoView)
                    //  TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                    ImageView.isVisible = false
                    videoView.isVisible = true
                    videoView.setVideoURI(uri)
                    isVideoPlaying = true
                    videoView.start()
                    videoView.setZOrderOnTop(true)
                    val retriever = MediaMetadataRetriever();
                    retriever.setDataSource(this@MainActivity, uri);
                    val duration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLong()
                    videoView.setOnErrorListener { mp, what, extra ->
                        Log.d("video", "setOnErrorListener ")

                        true
                    }
                    delay(duration ?: 0)
                    retriever.release();
                    isVideoPlaying = false
                } else {
                    // transition.addTarget(bindingActivity.ImageView)
                    //TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                    videoView.isVisible = false
                    ImageView.isVisible = true
                    ImageView.setImageURI(uri)
                    delay(5000L)
                }
            }
        } catch (e: Exception) {
            Log.d("Barcode", e.toString())
        }
    }


    @SuppressLint("HardwareIds")
    private fun setUpPlayer() {
        cleanUpandReset()
        val android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Android", "Android ID : " + android_id)
        val deviceId = Paper.book().read<String>(DeviceId)
        if (deviceId != null) {
            database.child("user").child(deviceId)
                .addValueEventListener(object : ValueEventListener {
                    @RequiresApi(Build.VERSION_CODES.O)
                    override fun onDataChange(snapshot: DataSnapshot) {
                        showDefaultLocalImage()
                        cleanUpandReset()
                        bindingActivity.progressCircular.isVisible = true
                        val dataClassList = mutableListOf<DataClass>()
                        snapshot.child("campaigns").children.forEach {
                            val dataClass = it.getValue(DataClass::class.java)
                            if (dataClass != null) {
                                dataClassList.add(
                                    dataClass
                                )
                            }
                        }
                        snapshot.child("list").children.forEach {
                            val list = it.value as String
                            defaultList.add(list)
                        }
                        println("Barcode" + Date().toInstant())
                        dataClassList.forEach { dataClass ->
                            totalItems += dataClass.urls!!.size
                            dataClass.urls.forEach {
                                onDownload(
                                    it.trim(),
                                    startTime = dataClass.startTime,
                                    endTime = dataClass.endTime,
                                )
                            }
                        }
                        totalItems += defaultList.size
                        defaultList.forEach {
                            onDownload(it, isDeafaultDownload = true)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        //will do later
                    }
                })
        }
    }

    private fun showDefaultLocalImage() {
        isLocalDefaultImageisShown = true
        bindingActivity.ImageView.isVisible = true
        bindingActivity.videoView.isVisible = false
        bindingActivity.ImageView.setImageDrawable(resources.getDrawable(R.mipmap.ic_launcher))
    }

    private fun cleanUpandReset() {
        if (isVideoPlaying.not()) {
            uriHashMap.clear()
            defaultUriMapper.clear()
            timeSlotMap.clear()
            defaultList.clear()
            val downloadFolder =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            for (tempFile in downloadFolder.listFiles()) {
                tempFile.delete()
            }
            uriHashMap.clear()
            timeSlotMap.clear()
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun onDownload(
        s: String,
        startTime: String? = null,
        endTime: String? = null,
        isDeafaultDownload: Boolean = false
    ) {
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
        if (isDeafaultDownload.not()) {
            uriHashMap[reference] = UriMapper(
                starTime = OffsetDateTime.parse(startTime).toInstant().toEpochMilli(),
                endTime = OffsetDateTime.parse(endTime).toInstant().toEpochMilli()
            )
        } else {
            defaultUriMapper[reference] = null
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

}