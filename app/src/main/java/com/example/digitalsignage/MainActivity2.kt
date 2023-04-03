package com.example.digitalsignage

import android.annotation.SuppressLint
import android.app.ActionBar
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.example.digitalsignage.databinding.ActivityMain2Binding
import com.example.digitalsignage.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import io.paperdb.Paper
import kotlinx.android.synthetic.main.activity_main2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity2 : AppCompatActivity() {

    private lateinit var binding: ActivityMain2Binding
    val viewModel: MainViewModel by viewModels()
    private lateinit var database: DatabaseReference
    private var DEVICE_ID = "DeviceId"
    private var currentFileList: MutableList<CampaignFile> = Collections.synchronizedList(
        mutableListOf()
    )
    private var currentDownloadFileList: MutableList<DefaultList> = Collections.synchronizedList(
        mutableListOf()
    )
    private var currentCampaign: String? = null
    private var deviceId = Paper.book().read<String>(DEVICE_ID)
    private var job: Job? = null
    private var campaignListener: ValueEventListener? = null
    private var campaignFileListener: ValueEventListener? = null
    private var defaultFileListener: ValueEventListener? = null
    private var clearFlagListener: ValueEventListener? = null

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = goAsync {
            val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            CheckDwnloadStatus(this@MainActivity2, reference, sucessfullyDownload = { uri ->
                currentFileList.apply {
                    val file = this.find {
                        it.downloadRefrenceId == reference
                    }
                    file?.let {
                        it.fileUri = uri.toString()
                        it.isDownloaded = true
                        viewModel.updateCampaignDetails(file)
                    }
                }
                currentDownloadFileList.apply {
                    val file = this.find {
                        it.downloadRefrenceId == reference
                    }
                    file?.let {
                        it.fileUri = uri.toString()
                        it.isDownloaded = true
                        viewModel.updateDefaultFileDetails(file)
                    }
                }
            }, failureInDownload = {
                reportExecptopn(exception = java.lang.Exception(it))
            })
        }
    }

    private fun addCampaignListener() {
        deviceId?.let {
            campaignListener?.let {
                database.removeEventListener(it)
            }
            campaignListener = database.child("user").child(it).child("currentCampaign")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        currentCampaign = snapshot.getValue(String::class.java)
                        startPlayingCampaigns()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        reportExecptopn(exception = error.toException())
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity2,
                                error.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                    }

                })
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun startPlayingCampaigns() {
        job?.cancel()
        job = lifecycleScope.launch(Dispatchers.IO) {
            try {
                currentCampaign?.let {
                    viewModel.fetchDownloadedDefaultImages()
                    val dbList =
                        viewModel.fetchCurrentCampaign(it).sortedBy { it.order }
                    if (dbList.isNotEmpty()) {
                        viewModel.playImageAndVideo(this@MainActivity2, indexToPlay = 0)
                    } else {
                        showDefaultImages()
                    }
                }
            } catch (e: Exception) {
                reportExecptopn(e)
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity2, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDefaultImages() {
        viewModel.playDefaultImages(this, 0)
    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            FirebaseApp.initializeApp(this)
            hasPermissions(activity = this, PERMISSIONS, PERMISSION_ALL)
            database = FirebaseDatabase.getInstance().reference
            if (Paper.book().contains(DEVICE_ID).not()) {
                showAlertBox(this) {
                    pushDeviceIdToFirebase(it)
                }
            } else {
                setUpPlayer()
                addCampaignListener()
                addClearFlagListener()
            }
        } catch (e: Exception) {
            reportExecptopn(e)
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        binding.versionNumber.text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        hideSystemUI()
        observer()

    }

    @SuppressLint("AppCompatMethod")
    private fun hideSystemUI() {
        val decorView: View = window.decorView
        val uiOptions: Int = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
        decorView.systemUiVisibility = uiOptions
        val actionBar: ActionBar? = actionBar
        actionBar?.hide()
    }

    private fun observer() {
        viewModel.playEvent.observe(this) { playEvent ->
            try {
                binding.run {
                    when (playEvent) {
                        is PlayEvent.PlayImage -> {
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (videoView.isPlaying) {
                                    videoView.stopPlayback()
                                    // simple.clearMediaItems()
                                }
                                ImageView.isVisible = false
                                val transition = animationList.random()
                                transition.duration = 1000
                                transition.addTarget(binding.ImageView)
                                TransitionManager.beginDelayedTransition(
                                    binding.root,
                                    transition
                                )
                                videoView.isVisible = false
                                ImageView.isVisible = true
                                if (checkRight(this@MainActivity2, playEvent.uri)) {
                                    ImageView.setImageURI(playEvent.uri)
                                }
                                delay(5000L)
                                if (playEvent.isDefault.not()) {
                                    viewModel.playImageAndVideo(
                                        this@MainActivity2,
                                        playEvent.nextIndex
                                    )
                                } else {
                                    viewModel.playDefaultImages(
                                        this@MainActivity2,
                                        playEvent.nextIndex
                                    )
                                }
                            }

                        }
                        is PlayEvent.PlayVideo -> {
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (checkRight(this@MainActivity2, playEvent.uri)) {
                                    videoView.isVisible = false
                                    ImageView.isVisible = false
                                    val transition = animationList.random()
                                    transition.duration = 1000
                                    transition.addTarget(videoView)
                                    TransitionManager.beginDelayedTransition(
                                        binding.root,
                                        transition
                                    )
                                    videoView.isVisible = true
                                    videoView.setVideoURI(playEvent.uri)
                                    videoView.start()
                                    videoView.setOnCompletionListener {
                                        if (playEvent.isDefault.not()) {
                                            viewModel.playImageAndVideo(
                                                this@MainActivity2,
                                                playEvent.nextIndex
                                            )
                                        } else {
                                            viewModel.playDefaultImages(
                                                this@MainActivity2,
                                                playEvent.nextIndex
                                            )
                                        }
                                    }
                                    videoView.setOnErrorListener { mediaPlayer, i, i2 ->
                                        if (playEvent.isDefault.not()) {
                                            viewModel.playImageAndVideo(
                                                this@MainActivity2,
                                                playEvent.nextIndex
                                            )
                                        } else {
                                            viewModel.playDefaultImages(
                                                this@MainActivity2,
                                                playEvent.nextIndex
                                            )
                                        }
                                        return@setOnErrorListener true
                                    }
                                }

                                /* mediaSource = ProgressiveMediaSource.Factory(
                                     dataSourceFactory
                                 ).createMediaSource(MediaItem.fromUri(playEvent.uri))
                                 simple.setMediaSource(mediaSource)
                                 simple.prepare()
                                 simple.playWhenReady = true
                                 simple.play()*/
                                /*simple.addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_ENDED) {
                                            if (playEvent.isDefault.not()) {
                                                viewModel.playImageAndVideo(
                                                    this@MainActivity2,
                                                    playEvent.nextIndex
                                                )
                                            } else {
                                                viewModel.playDefaultImages(
                                                    this@MainActivity2,
                                                    playEvent.nextIndex
                                                )
                                            }
                                        }
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        super.onPlayerError(error)
                                        reportExecptopn(error)
                                        Toast.makeText(
                                            this@MainActivity2,
                                            error.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        if (playEvent.isDefault.not()) {
                                            viewModel.playImageAndVideo(
                                                this@MainActivity2,
                                                playEvent.nextIndex
                                            )
                                        } else {
                                            viewModel.playDefaultImages(
                                                this@MainActivity2,
                                                playEvent.nextIndex
                                            )
                                        }
                                    }
                                })*/
                            }
                        }
                        PlayEvent.RestartCampaign -> {
                            startPlayingCampaigns()
                        }
                        PlayEvent.showLocalDeaulftImages -> {
                            showLocalImages()
                        }
                        is PlayEvent.ErrorReported -> {
                            lifecycleScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity2,
                                    playEvent.messageString,
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                reportExecptopn(e)
            }
        }
    }

    private fun showLocalImages(isFromClear: Boolean = false) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.ImageView.isVisible = true
            binding.ImageView.setImageDrawable(resources.getDrawable(R.drawable.default_image))
        }
        if (isFromClear.not()) viewModel.restartCampaign()
    }

    private fun pushDeviceIdToFirebase(toString: String) {
        Paper.book().write(DEVICE_ID, toString)
        database.child("user").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.hasChild(toString).not()) {
                    addDeviceId(toString)
                } else {
                    deviceId = deviceId ?: toString
                    setUpPlayer()
                    addCampaignListener()
                    addClearFlagListener()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                val deviceID = deviceId ?: ""
                val campaignID = currentCampaign ?: ""
                viewModel.reportError(
                    deviceId = deviceID,
                    campaignId = campaignID,
                    exception = java.lang.Exception(error.message)
                )
            }

        })

    }

    private fun addDeviceId(toString: String) {
        val map = mapOf(
            toString to ""
        )
        database.child("user").updateChildren(map).addOnSuccessListener {
            deviceId = deviceId ?: toString
            setUpPlayer()
            addCampaignListener()
            addClearFlagListener()
        }
    }


    private fun setUpPlayer() {
        deviceId?.let {
            campaignFileListener?.let {
                database.removeEventListener(it)
            }
            campaignFileListener = database.child("user").child(it).child("campaigns")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("Barcode Snapshot added")
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dataClassList = mutableListOf<Campaigns>()
                            snapshot.children.forEach { dataSnapshot ->
                                dataSnapshot.getValue(Campaigns::class.java)?.let { item ->
                                    item.campaignId = dataSnapshot.key.toString()
                                    dataClassList.add(
                                        item
                                    )
                                }
                            }
                            filterOutNewList(dataClassList)
                            onDownload()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        reportExecptopn(exception = error.toException())
                    }
                })

            defaultFileListener?.let {
                database.removeEventListener(it)
            }
            defaultFileListener = database.child("user").child(it).child("defaultAssets")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("Default Snapshot added")
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dataClassList = mutableListOf<String>()
                            snapshot.children.forEach { dataSnapshot ->
                                dataSnapshot.getValue(String::class.java)?.let { item ->
                                    dataClassList.add(
                                        item
                                    )
                                }
                            }
                            filterOutDefaultList(dataClassList)
                            onDownload(isFromDefault = true)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        reportExecptopn(exception = error.toException())
                    }
                })
        }

        database.child("footerInfo").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val footerInfo = snapshot.getValue(FooterInfo::class.java)
                displayFooterInfo(footerInfo)
            }

            override fun onCancelled(error: DatabaseError) {
                reportExecptopn(exception = error.toException())
            }
        })
    }

    private fun reportExecptopn(exception: java.lang.Exception) {
        val deviceID = deviceId ?: ""
        val campaignID = currentCampaign ?: ""
        viewModel.reportError(
            deviceId = deviceID,
            campaignId = campaignID,
            exception = exception
        )
    }

    private fun addClearFlagListener() {
        deviceId?.let { deviceId ->
            clearFlagListener?.let {
                database.removeEventListener(it)
            }
            clearFlagListener = database.child("user").child(deviceId).child("clearCache")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val clear = snapshot.getValue(Boolean::class.java)
                        if (clear == true) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                clearEverything()
                                database.child("user").child(deviceId).updateChildren(
                                    mapOf("clearCache" to false)
                                ).addOnSuccessListener {
                                    setUpPlayer()
                                    addCampaignListener()
                                }
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        reportExecptopn(exception = error.toException())
                    }

                })
        }
    }

    private suspend fun clearEverything() {
        job?.cancel()
        showLocalImages(isFromClear = true)
        val list = viewModel.getAllCampaign()
        val downloadedFiles = viewModel.getAllDownloadedFiles()
        viewModel.deleteAllCampaign()
        viewModel.deleteAllDownloadedFiles()
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        list.forEach {
            it.downloadRefrenceId?.let { it1 -> manager.remove(it1) }
            Log.d("Barcode", it.fileUri.toString())
            it.fileUri?.let { deleteFile(applicationContext, Uri.parse(it)) }
        }
        downloadedFiles.forEach {
            it.downloadRefrenceId?.let { it1 -> manager.remove(it1) }
            Log.d("Barcode", it.fileUri.toString())
            it.fileUri?.let { deleteFile(applicationContext, Uri.parse(it)) }
        }
    }

    private fun displayFooterInfo(footerInfo: FooterInfo?) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.run {
                footerInfo?.let {
                    mobilenumber.text = "${it.mobile}"
                }
            }
        }
    }


    private suspend fun filterOutDefaultList(dataClassList: MutableList<String>) {
        viewModel.deleteAllDownloadedFiles()
        val common = mutableListOf<DefaultList>()
        val serverNewList = dataClassList.distinctBy { it }
        common.addAll(
            serverNewList.map {
                DefaultList(
                    fileUrl = it
                )
            }
        )

        common.forEach {
            viewModel.updateDefaultFileDetails(it)
        }
        currentDownloadFileList.clear()
        currentDownloadFileList.addAll(common)
    }

    private suspend fun filterOutNewList(dataClassList: MutableList<Campaigns>) {
        try {
            val list = viewModel.getAllCampaign().distinctBy { it.fileUrl }
            viewModel.deleteAllCampaign()
            val common = mutableListOf<CampaignFile>()
            dataClassList.forEach { campaign ->
                if (campaign != null) {
                    try {
                        val startTime =
                            Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(campaign.startTime))
                        val endTime =
                            Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(campaign.endTime))

                        campaign.assets.forEach { fileUrl ->
                            if (fileUrl != null) {
                                val file = list.find { it.fileUrl == fileUrl.fileUrl }
                                if (file != null) {
                                    file.also {
                                        it.startTime = campaign.startTime
                                        it.endTime = campaign.endTime
                                        it.fileUrl = fileUrl.fileUrl
                                        it.order = fileUrl.order
                                        it.campaignId = campaign.campaignId
                                    }
                                    common.add(file)
                                } else {
                                    common.add(
                                        CampaignFile(
                                            startTime = campaign.startTime,
                                            endTime = campaign.endTime,
                                            fileName = URLUtil.guessFileName(
                                                fileUrl.fileUrl,
                                                null,
                                                null
                                            ),
                                            fileUrl = fileUrl.fileUrl,
                                            campaignId = campaign.campaignId,
                                            order = fileUrl.order
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        reportExecptopn(exception = e)
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity2, e.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            common.forEach {
                viewModel.updateCampaignDetails(it)
            }
            currentFileList.clear()
            currentFileList.addAll(common)
        } catch (e: Exception) {
            reportExecptopn(exception = e)
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity2, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onDownload(isFromDefault: Boolean = false) {
        try {
            if (isFromDefault.not()) {
                currentFileList.forEach {
                    if (it.isDownloaded.not()) {
                        it.also {
                            it.downloadRefrenceId = download(this, it.fileUrl)
                        }
                    }
                }
            } else {
                currentDownloadFileList.forEach {
                    if (it.isDownloaded.not()) {
                        it.also {
                            it.downloadRefrenceId = download(this, it.fileUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            reportExecptopn(exception = e)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    override fun onDestroy() {
        clearFlagListener?.let {
            database.removeEventListener(it)
        }
        campaignListener?.let {
            database.removeEventListener(it)
        }
        campaignFileListener?.let {
            database.removeEventListener(it)
        }
        defaultFileListener?.let {
            database.removeEventListener(it)
        }
        super.onDestroy()
    }

}