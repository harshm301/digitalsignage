package com.example.digitalsignage

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
import java.time.format.DateTimeFormatter
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
    private var currentCampaign: String? = null
    private var deviceId = Paper.book().read<String>(DeviceId)
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
        }
    }

    private fun addCampaignLisener() {
        deviceId?.let {
            database.child("user").child(it).child("currentCampaign")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        currentCampaign = snapshot.getValue(String::class.java)
                        startPlayingCampaigns()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity2, error.message, Toast.LENGTH_SHORT)
                                .show()
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
                    val list = viewModel.fetchDownloadedDefaultImages()
                    val dbList = viewModel.fetchCurrentCampaign(it).sortedBy { it.order }
                    if (dbList.isNotEmpty()) {
                        val currentTimeStamp = Instant.now().toEpochMilli()
                        val end = OffsetDateTime.parse(dbList[0].endTime).toInstant().toEpochMilli()
                        if (end > currentTimeStamp) {
                            viewModel.playImageAndVideo(this@MainActivity2, dbList, end)
                        } else {
                            showDefaultImages(list)
                        }
                    } else {
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
            }
        } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        addCampaignLisener()
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
            binding.ImageView.setImageDrawable(resources.getDrawable(R.drawable.default_image))
        }
        if (isFromClear.not()) viewModel.restartCampaign()
    }

    private fun pushDeviceIdToFirebase(toString: String) {
        Paper.book().write(DeviceId, toString)
        database.child("user").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.hasChild(toString).not()) {
                    addDeviceId(toString)
                } else {
                    deviceId = deviceId ?: toString
                    setUpPlayer()
                    addCampaignLisener()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
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
            addCampaignLisener()
        }
    }


    private fun setUpPlayer() {
        if (deviceId != null) {
            database.child("user").child(deviceId!!).child("campaigns")
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
                        //will do later
                    }
                })

            database.child("user").child(deviceId!!).child("defaultAssets")
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
        showLocalImages(isFromClear = true)
        val list = viewModel.getAllCampaign()
        val downloadedFiles = viewModel.getAllDownloadedFiles()
        viewModel.deleteAllCampaign()
        viewModel.deleteAllDownloadedFiles()
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        list.forEach {
            it.downloadRefrenceId?.let { it1 -> manager.remove(it1) }
            //Uri.parse(it.fileUri).path?.let { it1 -> File(it1).delete() }
            Log.d("Barcode",it.fileUri.toString())
            it.fileUri?.let {  deleteFile(applicationContext,Uri.parse(it)) }
        }
        downloadedFiles.forEach {
            it.downloadRefrenceId?.let { it1 -> manager.remove(it1) }
            //Uri.parse(it.fileUri).path?.let { it1 -> File(it1).delete() }
            Log.d("Barcode",it.fileUri.toString())
            it.fileUri?.let {  deleteFile(applicationContext,Uri.parse(it)) }
        }
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
    }

    private suspend fun onDownload(
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