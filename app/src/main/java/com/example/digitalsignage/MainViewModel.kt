package com.example.digitalsignage

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.digitalsignage.db.AppDatabase
import com.example.digitalsignage.model.CampaignFile
import com.example.digitalsignage.model.CurrentCampaign
import com.example.digitalsignage.model.DefaultList
import com.example.digitalsignage.repository.SignageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val signageRepository = SignageRepository(AppDatabase.getDatabase(application))
    private val _playEvent: MutableLiveData<PlayEvent> = MutableLiveData<PlayEvent>()
    val playEvent: LiveData<PlayEvent> = _playEvent

    private val _campaignFiles = MutableLiveData<List<CampaignFile>>()
    val campaignFile: LiveData<List<CampaignFile>> = _campaignFiles

    private var uriList: MutableList<Uri> = mutableListOf()
    private var defaultUriList: MutableList<Uri> = mutableListOf()
    var isCurrentingPlaying = false

    init {
        fetchCampaignFiles()
    }

    private fun fetchCampaignFiles() {
        viewModelScope.launch {
            _campaignFiles.postValue(
                signageRepository.getAllCampaign()
            )
        }
    }

    suspend fun getAllCampaign() = signageRepository.getAllCampaign()

    suspend fun getAllDownloadedFiles() = signageRepository.getAlldefaultFiles()


    suspend fun playDefaultImages(context: Context, list: List<DefaultList>) {
        if (list.isEmpty()) {
            _playEvent.postValue(PlayEvent.showLocalDeaulftImages)
            return
        }
        defaultUriList.clear()
        list.forEach {
            defaultUriList.add(Uri.parse(it.fileUri))
        }
        defaultUriList.forEach {
            if (checkRight(context, it)) {
                val fileExt = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(it))
                if (fileExt == "mp4") {
                    // val File = File()
                    //transition.addTarget(bindingActivity.videoView)
                    //  TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                    // if (uri.toFile().exists()) {
                    _playEvent.postValue(PlayEvent.PlayVideo(uri = it))
                    val retriever = MediaMetadataRetriever();
                    retriever.setDataSource(context, it);
                    val duration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLong()
                    delay(duration ?: 0)
                    retriever.release();
                } else {
                    // transition.addTarget(bindingActivity.ImageView)
                    //TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                    _playEvent.postValue(PlayEvent.PlayImage(it))
                    delay(5000L)
                }
            }
        }
        _playEvent.postValue(PlayEvent.RestartCampaign)
    }

    suspend fun playImageAndVideo(context: Context, list: List<CampaignFile>, endTime: Long) {
        var currentTimeStamp = Instant.now().toEpochMilli()
        if (list.isEmpty() || endTime < currentTimeStamp) {
            _playEvent.postValue(PlayEvent.RestartCampaign)
            return
        }

        uriList.clear()
        list.forEach {
            uriList.add(Uri.parse(it.fileUri))
        }
        uriList.forEach {
            currentTimeStamp = Instant.now().toEpochMilli()
            if (endTime < currentTimeStamp) {
                _playEvent.postValue(PlayEvent.RestartCampaign)
                return
            }
            if (checkRight(context, it)) {
                val fileExt = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(it))
                if (fileExt == "mp4") {
                    // val File = File()
                    //transition.addTarget(bindingActivity.videoView)
                    //  TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                    // if (uri.toFile().exists()) {
                    _playEvent.postValue(PlayEvent.PlayVideo(uri = it))
                    val retriever = MediaMetadataRetriever();
                    retriever.setDataSource(context, it);
                    val duration =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLong()
                    delay(duration ?: 0)
                    retriever.release();
                } else {
                    // transition.addTarget(bindingActivity.ImageView)
                    //TransitionManager.beginDelayedTransition(bindingActivity.root, transition)
                    _playEvent.postValue(PlayEvent.PlayImage(it))
                    delay(5000L)
                }
            }
        }
        _playEvent.postValue(PlayEvent.RestartCampaign)
    }

    fun restartCampaign() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(5000L)
            _playEvent.postValue(PlayEvent.RestartCampaign)
        }
    }

    suspend fun deleteAllCampaign() {
        signageRepository.deleteCampaigns()
    }

    suspend fun deleteAllDownloadedFiles() {
        signageRepository.deleteAllDownloadFiles()
    }


    suspend fun updateCampaignDetails(file: CampaignFile) {
        signageRepository.updateCampaignFile(file)
    }

    suspend fun updateDefaultFileDetails(file: DefaultList?) {
        file?.let { signageRepository.updateDefaultFile(it) }
    }

    suspend fun fetchCurrentCampaign(startTime: String): List<CampaignFile> {
        return signageRepository.fetchCurrentCampaign(startTime)
    }

    suspend fun fetchDownloadedDefaultImages(): List<DefaultList> {
        return signageRepository.getAllDownloadedDefaultFiles()
    }
}

sealed class PlayEvent() {
    data class PlayImage(val uri: Uri) : PlayEvent()
    data class PlayVideo(val uri: Uri) : PlayEvent()
    object RestartCampaign : PlayEvent()

    object showLocalDeaulftImages : PlayEvent()
}