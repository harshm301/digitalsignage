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
import android.transition.Scene
import android.transition.Transition
import android.transition.TransitionManager
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
    private var DeviceId = "DeviceId"
    var timeSlotMap: MutableMap<Long, PlayMapper> = mutableMapOf<Long, PlayMapper>().toSortedMap()
    private lateinit var database: DatabaseReference
    private var defaultList = mutableListOf<String>()
    private var defaultUriMapper: MutableMap<Long, Uri?> = mutableMapOf()
    private var isDefaultBeingShown = false
    private var isCampaignBeignShown = false


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
                    Toast.makeText(this,"${totalItems-itemDownload} more item to download",Toast.LENGTH_SHORT).show()
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
        if (Paper.book().read(ISFTU, true) == true && Paper.book().contains(DeviceId).not()) {
            setUpDeviceId()
        } else {
            Paper.book().write(ISFTU, false)
            setUpPlayer()
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
                Log.d("Barcode", "Calls Function one time only")
                bindingActivity.progressCircular.isVisible = false
                startPlayingIdex(timeSlotMap.values.toList(), 0)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun startPlayingIdex(listFiles: List<PlayMapper>, i: Int) {
        if (i < listFiles.size) {
            currentTimeStamp = Instant.now().toEpochMilli()
            if (currentTimeStamp >= listFiles[i].startTime) {
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
                startPlayingIdex(listFiles, i.inc())
            }
        } else {
            showDefault(listFiles)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun showDefault(
        listFiles: List<PlayMapper>,
    ) {
        if(isDefaultBeingShown.not()) {
            isDefaultBeingShown = true
            val random = defaultUriMapper.values.toList().randomOrNull()
            if (random != null) {
                playImageorVideo(random)
                isDefaultBeingShown = false
                Log.d("Barcode", "When start from  default block : ${Date(currentTimeStamp)}")
                currentTimeStamp = Instant.now().toEpochMilli()
                startPlayingIdex(listFiles, 0)
            } else {
                bindingActivity.ImageView.setImageDrawable(resources.getDrawable(R.drawable.ic_launcher_background))
                currentTimeStamp = Instant.now().toEpochMilli()
                isDefaultBeingShown = false
                startPlayingIdex(listFiles, 0)
            }
        }
    }

    private suspend fun showCampaigs(listFiles: MutableList<Uri>, i: Int) {
        Log.d("Barcode", "sampaigs, ${i} ${listFiles}")
        if (i < listFiles.size && isCampaignBeignShown.not()) {
            isCampaignBeignShown = true
            playImageorVideo(listFiles[i])
            isCampaignBeignShown = false
            showCampaigs(listFiles,i.inc())
        }
    }

    private suspend fun playImageorVideo(uri: Uri){
        //val transition: Transition = CircularRevealTransition()
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
                videoView.start()
                val retriever = MediaMetadataRetriever();
                retriever.setDataSource(this@MainActivity,uri);
                val duration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLong()
                delay(duration ?: 0)
                retriever.release();
            } else {
               // transition.addTarget(bindingActivity.ImageView)
                //TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                videoView.isVisible = false
                ImageView.isVisible = true
                ImageView.setImageURI(uri)
                delay(5000L)
            }
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
                        bindingActivity.progressCircular.isVisible = true
                        cleanUpandReset()
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
                        lifecycleScope.launch(Dispatchers.IO) {
                            println("Barcode" +  Date().toInstant())
                            dataClassList.forEach { dataClass ->
                                totalItems += dataClass.urls!!.size + defaultList.size
                                dataClass.urls.forEach {
                                    onDownload(
                                        it.trim(),
                                        startTime = dataClass.startTime,
                                        endTime = dataClass.endTime,
                                    )
                                }
                            }
                            defaultList.forEach {
                                onDownload(it, isDeafaultDownload = true)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        //will do later
                    }
                })
        }
    }

    private fun cleanUpandReset() {
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


    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun onDownload(
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