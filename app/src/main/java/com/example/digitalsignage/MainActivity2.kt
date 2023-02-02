package com.example.digitalsignage

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.net.toFile
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.digitalsignage.databinding.ActivityMain2Binding
import com.example.digitalsignage.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import io.paperdb.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class MainActivity2 : AppCompatActivity() {

    private lateinit var binding: ActivityMain2Binding
    val viewModel: MainViewModel by viewModels()
    private var ISFTU = "ISFTU"
    private var IS_DOWNLOAD_AGAIN = "IS_DOWNLOAD_AGAIN"
    private lateinit var database: DatabaseReference
    private var DeviceId = "DeviceId"
    private var currentFileList = mutableListOf<CampaignFile>()
    private var currentDownloadFileList = mutableListOf<DefaultList>()
    private var currentCampaign: CurrentCampaign? = null
    private var deviceId = Paper.book().read<String>(DeviceId)
    private var addedCampaignListener = false
    private var job: Job? = null

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = goAsync {
            val reference = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            CheckDwnloadStatus(this@MainActivity2, reference) { uri ->
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
            }
            addCampaignLisener()
        }
    }

    private fun addCampaignLisener() {
        if (addedCampaignListener.not()) {
            if (deviceId != null) {
                addedCampaignListener = true
                database.child("user").child(deviceId!!).child("current_campaign")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            currentCampaign = snapshot.getValue(CurrentCampaign::class.java)
                            startPlayingCampaigns()
                        }

                        override fun onCancelled(error: DatabaseError) {

                        }

                    })
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun startPlayingCampaigns() {
        job?.cancel()
        job = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentTimeStamp = Instant.now().toEpochMilli()
                currentCampaign?.let {
                    val startTime = OffsetDateTime.parse(it.startTime).toInstant().toEpochMilli()
                    val end = OffsetDateTime.parse(it.endTime).toInstant().toEpochMilli()
                    val dbList = viewModel.fetchCurrentCampaign(it)
                    if (startTime < currentTimeStamp && end > currentTimeStamp && dbList.isNotEmpty()) {
                        viewModel.playImageAndVideo(this@MainActivity2, dbList, end)
                    } else {
                        val list = viewModel.fetchDownloadedDefaultImages()
                        showDefaultImages(list)
                    }
                }
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity2, e.message, Toast.LENGTH_SHORT).show()
                }
                //showDefaultImages()
                // Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun showDefaultImages(dbList: List<DefaultList>) {
        viewModel.playDefaultImages(this, dbList)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        try {
            FirebaseApp.initializeApp(this)
            hasPermissions(activity = this, PERMISSIONS, PERMISSION_ALL)
            database = FirebaseDatabase.getInstance().reference
            Paper.book().write(IS_DOWNLOAD_AGAIN, false)
            //animBounce = AnimationUtils.loadAnimation(this,R.anim.bounce)
            if (Paper.book().read(ISFTU, false) == false && Paper.book().contains(DeviceId)
                    .not()
            ) {
                showAlertBox(this) {
                    pushDeviceIdToFirebase(it)
                }
            } else {

            }
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        observer()
    }

    private fun observer() {
        viewModel.campaignFile.observe(this) {
            setUpPlayer()
        }

        viewModel.playEvent.observe(this) {
            binding.run {
                when (it) {
                    is PlayEvent.PlayImage -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            videoView.stopPlayback()
                            videoView.isVisible = false
                            ImageView.isVisible = true
                            if (checkRight(this@MainActivity2, it.uri)) {
                                //ImageView.startAnimation(animBounce)
                                ImageView.setImageURI(it.uri)
                            } else {
                                //  showDefaultLocalImage()
                            }
                        }
                    }
                    is PlayEvent.PlayVideo -> {
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (checkRight(this@MainActivity2, it.uri)) {
                                videoView.stopPlayback()
                                ImageView.isVisible = false
                                videoView.isVisible = true
                                videoView.setVideoURI(it.uri)
                                videoView.start()
                                videoView.setZOrderOnTop(true)
                            }

                        }
                    }
                    PlayEvent.RestartCampaign -> {
                        startPlayingCampaigns()
                    }
                    PlayEvent.showLocalDeaulftImages -> {
                        showLocalImages()
                    }
                }
            }
        }
    }

    private fun showLocalImages(isFromClear: Boolean = false) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.ImageView.isVisible = true
            binding.ImageView.setImageDrawable(resources.getDrawable(R.mipmap.ic_launcher))
        }
        if (isFromClear.not()) viewModel.restartCampaign()
    }

    private fun pushDeviceIdToFirebase(toString: String) {
        val map = mapOf(
            toString to ""
        )
        Paper.book().write(DeviceId, toString)
        database.child("user").updateChildren(map).addOnSuccessListener {
            deviceId = deviceId ?: toString
            setUpPlayer()
        }
    }


    private fun setUpPlayer() {
        if (deviceId != null) {
            database.child("user").child(deviceId!!).child("campaign_file_list")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        println("Barcode Snapshot added")
                        lifecycleScope.launch(Dispatchers.IO) {
                            val dataClassList = mutableListOf<Campaigns>()
                            snapshot.children.forEach { dataSnapshot ->
                                dataSnapshot.getValue(Campaigns::class.java)?.let { item ->
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
                        //will do later
                    }
                })

            database.child("user").child(deviceId!!).child("default_list")
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
                            onDownload()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        //will do later
                    }
                })

            database.child("user").child(deviceId!!).child("clearCache")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val clear = snapshot.getValue(Boolean::class.java)
                            if (clear == true) {
                                clearEverything()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }

                })

        }

        database.child("footerInfo").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val footerInfo = snapshot.getValue(FooterInfo::class.java)
                displayFooterInfo(footerInfo)
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    private suspend fun clearEverything() {
        job?.cancel()
        val list = viewModel.getAllCampaign()
        val downloadedFiles = viewModel.getAllDownloadedFiles()
        list.forEach {
            it.fileUri?.let { string ->
                deleteFile(this, File(Uri.parse(string).path))
            }
        }
        downloadedFiles.forEach {
            it.fileUri?.let { string ->
                deleteFile(this, File(Uri.parse(string).path))
            }
        }
        viewModel.deleteAllCampaign()
        viewModel.deleteAllDownloadedFiles()
        showLocalImages(isFromClear = true)
    }

    private fun displayFooterInfo(footerInfo: FooterInfo?) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.run {
                footerInfo?.let {
                    copyright.text = it.copyright
                    mobilenumber.text = "mobile : ${it.mobile}"
                    mailId.text = "mail : ${it.mail}"
                    website.text = "website : ${it.website}"
                }
            }
        }

    }


    private suspend fun filterOutDefaultList(dataClassList: MutableList<String>) {
        val localList = viewModel.getAllDownloadedFiles().distinctBy { it.fileUrl }
        viewModel.deleteAllDownloadedFiles()
        val common = mutableListOf<DefaultList>()
        localList.forEach { default ->
            val file = dataClassList.find { it == default.fileUrl }
            if (file != null) {
                default.also {
                    it.fileUrl = file
                }
                common.add(default)
            }
        }

        val serverNewList = dataClassList.filter { default ->
            common.map { it.fileUrl }.toSet().contains(default).not()
        }

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
        var list = viewModel.getAllCampaign().distinctBy { it.fileUrl }
        viewModel.deleteAllCampaign()
        val common = mutableListOf<CampaignFile>()
        list.forEach { campaingFile ->
            val file = dataClassList.find { it.fileUrl == campaingFile.fileUrl }
            if (file != null) {
                campaingFile.also {
                    it.startTime = file.startTime
                    it.endTime = file.endTime
                    it.fileUrl = file.fileUrl
                    it.campaignId = file.campaignId
                    it.order = file.order
                }
                common.add(campaingFile)
            }
        }


        val serverNewList = dataClassList.filter { campaign ->
            common.map { it.fileUrl }.toSet().contains(campaign.fileUrl).not()
        }
        common.addAll(
            serverNewList.map {
                CampaignFile(
                    startTime = it.startTime,
                    endTime = it.endTime,
                    fileName = URLUtil.guessFileName(it.fileUrl, null, null),
                    campaignId = it.campaignId,
                    order = it.order,
                    fileUrl = it.fileUrl
                )
            }
        )
        common.forEach {
            viewModel.updateCampaignDetails(it)
        }
        currentFileList.clear()
        currentFileList.addAll(common)
    }

    private fun onDownload(
    ) {
        currentFileList.forEach {
            if (it.isDownloaded.not()) {
                it.also {
                    it.downloadRefrenceId = download(this, it.fileUrl)
                }
            }
        }

        currentDownloadFileList.forEach {
            if (it.isDownloaded.not()) {
                it.also {
                    it.downloadRefrenceId = download(this, it.fileUrl)
                }
            }
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