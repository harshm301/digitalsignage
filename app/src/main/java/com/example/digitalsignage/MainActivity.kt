/*
package com.example.digitalsignage

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
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
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
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
    val timeSlotMap: MutableMap<Long, PlayMapper> = ConcurrentHashMap<Long, PlayMapper>()
    private lateinit var database: DatabaseReference
    private var defaultList = mutableListOf<String>()
    private var defaultUriMapper: MutableMap<Long, Uri> = ConcurrentHashMap<Long,Uri>()
    val viewModel: MainViewModel by viewModels()
    private var isLoopStarted = false
    private var isDownloaded = false
    private var campaignRefreshed = false
    private var isVideoPlaying = false
    private var isLocalDefaultImageisShown = false
    private var animBounce: Animation? = null

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



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingActivity = ActivityMainBinding.inflate(layoutInflater)
        setContentView(bindingActivity.root)
        try {
            FirebaseApp.initializeApp(this)
            hasPermissions(context = this, PERMISSIONS)
            database = FirebaseDatabase.getInstance().reference
            Paper.book().write(IS_DOWNLOAD_AGAIN, false)
            animBounce = AnimationUtils.loadAnimation(this,R.anim.bounce)
            if (Paper.book().read(ISFTU, false) == false && Paper.book().contains(DeviceId)
                    .not()
            ) {
                setUpDeviceId()
            } else {
                setUpPlayer()
            }
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        observer()
    }

    private fun observer() {
        viewModel.playEvent.observe(this) {
            bindingActivity.run {
                when (it) {
                    is PlayEvent.PlayImage -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            videoView.stopPlayback()
                            videoView.isVisible = false
                            ImageView.isVisible = true
                            if(checkRight(this@MainActivity,it.uri)) {
                                //ImageView.startAnimation(animBounce)
                               ImageView.setImageURI(it.uri)
                            }else{
                                showDefaultLocalImage()
                            }
                        }
                    }
                    is PlayEvent.PlayVideo -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (checkRight(this@MainActivity,it.uri)) {
                                videoView.stopPlayback()
                                ImageView.isVisible = false
                                videoView.isVisible = true
                                videoView.setVideoURI(it.uri)
                                isVideoPlaying = true
                                videoView.start()
                                videoView.setZOrderOnTop(true)
                            }else{
                                showDefaultLocalImage()
                            }

                          //  videoView.setOnErrorListener(MediaPlayer.OnErrorListener { p0, p1, p2 -> })
                        }
                    }
                    PlayEvent.RestartCampaign -> {
                        lifecycleScope.launch(Dispatchers.IO) {
                            startPlayingIdex()
                        }
                    }
                }
            }
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
        campaignRefreshed = true
        if (isLoopStarted.not()) {
            isLoopStarted = true
            //startPlayingIdex()
            //setAlarm(timeInMillis = timeSlotMap.keys)
        }
    }

    private fun setAlarm(timeInMillis: MutableList<DataClass>) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel()
        for(time in timeInMillis) {
            val intent = Intent(this, MyAlarm::class.java)
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            //pendingIntent
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC,
                OffsetDateTime.parse(time.startTime).toInstant().toEpochMilli(),
                pendingIntent
            )
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Alarm is set", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startPlayingIdex() {
        if(viewModel.isCurrentingPlaying){
            return
        }
        Log.d("Barcdoe","startPlayIndex")
        if (isLocalDefaultImageisShown.not()) {
            currentTimeStamp = Instant.now().toEpochMilli()
            val currentSlot = timeSlotMap.filter {
                currentTimeStamp >= it.value.startTime && it.value.endTime >= currentTimeStamp
            }
            for (keys in currentSlot.keys) {
                val playMapper = currentSlot[keys]
                if (currentTimeStamp >= playMapper!!.startTime && playMapper.endTime >= currentTimeStamp) {

                    if(playMapper.uriList==null){
                        viewModel.restartCampaign()
                        break
                    }else {
                        playMapper.uriList.let {
                            showCampaigs(it)
                        }
                        currentTimeStamp = Instant.now().toEpochMilli()
                        if (currentTimeStamp >= playMapper.endTime) {
                            timeSlotMap.remove(keys)
                        }
                        break
                    }
                } else {
                    showDefault()
                }
            }

            if (currentSlot.isEmpty()) {
                showDefault()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDefault() {
        if (viewModel.isCurrentingPlaying.not()) {
            val list = defaultUriMapper.values.toMutableList()
            viewModel.playImageAndVideo(this, list)
            Log.d("Barcode", "When start from  default block : ${Date(currentTimeStamp)}")
            Log.d("Barcode", list.toString())
            currentTimeStamp = Instant.now().toEpochMilli()
        }
    }

    private fun showCampaigs(listFiles: MutableList<Uri>) {
        Log.d("Barcode", "sampaigs, ${listFiles}")
        if (viewModel.isCurrentingPlaying.not()) {
            viewModel.playImageAndVideo(this, listFiles)
        }
    }


    @SuppressLint("HardwareIds")
    private fun setUpPlayer() {
        val android_id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Log.d("Android", "Android ID : " + android_id)
        val deviceId = Paper.book().read<String>(DeviceId)
        if (deviceId != null) {
            database.child("user").child(deviceId)
                .addValueEventListener(object : ValueEventListener {
                    @RequiresApi(Build.VERSION_CODES.O)
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("Barcode Snapshot added")
                        bindingActivity.progressCircular.isVisible = true
                        showDefaultLocalImage()
                        lifecycleScope.launch(Dispatchers.IO) {
                            cleanUpandReset()
                            val dataClassList = mutableListOf<DataClass>()
                            snapshot.child("campaigns").children.forEach {
                                it?.let {
                                    it.getValue(DataClass::class.java)
                                        ?.let { it1 -> dataClassList.add(it1) }
                                }
                            }
                            println("Barcode Snapshot added " + dataClassList.size)
                            snapshot.child("list").children.forEach {
                                it?.let {
                                    it.getValue(String::class.java)
                                        ?.let { it1 -> defaultList.add(it1) }
                                }
                            }
                            println("Barcode" + Date().toInstant())
                            setAlarm(dataClassList)
                            onDownload(dataClass = dataClassList, defaultUrl = defaultList)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        //will do later
                    }
                })
        }

        database.child("footerInfo").addValueEventListener(object :ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                val footerInfo = snapshot.getValue(FooterInfo::class.java)
                displayFooterInfo(footerInfo)
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })
    }

    private fun displayFooterInfo(footerInfo: FooterInfo?) {
        lifecycleScope.launch(Dispatchers.Main){
            bindingActivity.run {
                footerInfo?.let {
                    copyright.text = it.copyright
                    mobilenumber.text = "mobile : ${it.mobile}"
                    mailId.text = "mail : ${it.mail}"
                    website.text = "website : ${it.website}"
                }
            }
        }

    }

    private fun showDefaultLocalImage() {
        lifecycleScope.launch(Dispatchers.Main) {
            isDownloaded = false
            //isLocalDefaultImageisShown = true
            bindingActivity.ImageView.isVisible = true
            bindingActivity.videoView.stopPlayback()
            bindingActivity.videoView.isVisible = false
            bindingActivity.ImageView.setImageDrawable(resources.getDrawable(R.mipmap.ic_launcher))
        }
    }

    private fun cleanUpandReset() {
        //if (isVideoPlaying.not()) {
            uriHashMap.clear()
            defaultUriMapper.clear()
            timeSlotMap.clear()
            defaultList.clear()
            val downloadFolder =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadFolder.isDirectory) {
                val list = downloadFolder.list()
                for (file in list) {
                    File(downloadFolder, file).delete()
                }
            }
       //    }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun onDownload(
        dataClass: MutableList<DataClass>? = null,
        defaultUrl: List<String>? = null
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Picasso.with(this).load(s).into(picassoImageTarget(context = this, pathImage, "${imageName}.jpeg"));
            defaultUrl?.forEach {
                defaultUriMapper[download(it)] = Uri.EMPTY
            }
            dataClass?.forEach { dataClass ->
                totalItems += dataClass.urls?.size ?: 0
                dataClass.urls?.forEach {
                    val start = OffsetDateTime.parse(dataClass.startTime).toInstant().toEpochMilli()
                    val end = OffsetDateTime.parse(dataClass.endTime).toInstant().toEpochMilli()
                    uriHashMap[download(it)] = UriMapper(
                        starTime = start,
                        endTime = end
                    )
                    timeSlotMap[start] = PlayMapper(
                        startTime = start,
                        endTime = end,
                        uriList = null
                    )
                }
            }
            totalItems += defaultList.size
        }
    }

    private fun download(s: String): Long {
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
        return manager.enqueue(dmr)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    private class MyAlarm : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("Alarm Bell", "Alarm just fired")
        }
    }

}
*/
