package com.example.digitalsignage

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.digitalsignage.db.AppDatabase
import com.example.digitalsignage.model.CampaignFile
import com.example.digitalsignage.model.DefaultList
import com.example.digitalsignage.model.ErrorBody
import com.example.digitalsignage.repository.ErrorService
import com.example.digitalsignage.repository.SignageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val signageRepository = SignageRepository(AppDatabase.getDatabase(application))

    private val _playEvent: MutableLiveData<PlayEvent> = MutableLiveData<PlayEvent>()
    val playEvent: LiveData<PlayEvent> = _playEvent

    private val currentCampaignList: MutableList<CampaignFile> = mutableListOf()
    private val currentDefaultList: MutableList<DefaultList> = mutableListOf()

    suspend fun getAllCampaign() = signageRepository.getAllCampaign()

    suspend fun getAllDownloadedFiles() = signageRepository.getAlldefaultFiles()

    fun playDefaultImages(context: Context, indexToPlay: Int) {
        if (currentDefaultList.isEmpty()) {
            _playEvent.postValue(PlayEvent.showLocalDeaulftImages)
            return
        }
        if (indexToPlay < currentDefaultList.size) {
            val uri = Uri.parse(currentDefaultList[indexToPlay].fileUri)
            if (checkRight(context, uri)) {
                val fileExt = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(uri))
                if (fileExt == "mp4") {
                    _playEvent.postValue(
                        PlayEvent.PlayVideo(
                            uri = uri,
                            indexToPlay.inc(),
                            isDefault = true
                        )
                    )
                } else {
                    _playEvent.postValue(
                        PlayEvent.PlayImage(
                            uri,
                            indexToPlay.inc(),
                            isDefault = true
                        )
                    )
                }
            }
        } else {
            restartCampaign()
        }
    }

    fun playImageAndVideo(context: Context, indexToPlay: Int) {
        Log.d("Barcode","${indexToPlay.toString()} ${currentCampaignList.size.toString()}")
        if (indexToPlay < currentCampaignList.size) {
            Log.d("Barcode", indexToPlay.toString())
            val itemtoPlay = currentCampaignList[indexToPlay]
            val uri = Uri.parse(itemtoPlay.fileUri)
            if (checkRight(context, uri)) {
                val fileExt = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(uri))
                if (fileExt == "mp4") {
                    _playEvent.postValue(
                        PlayEvent.PlayVideo(
                            uri = uri,
                            nextIndex = indexToPlay.inc()
                        )
                    )
                } else {
                    _playEvent.postValue(
                        PlayEvent.PlayImage(
                            uri = uri,
                            nextIndex = indexToPlay.inc()
                        )
                    )
                }
            }
        } else {
            restartCampaign()
        }
    }

    fun restartCampaign() {
        viewModelScope.launch(Dispatchers.IO) {
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
        val response = signageRepository.fetchCurrentCampaign(startTime)
        currentCampaignList.clear()
        currentCampaignList.addAll(response)
        return response
    }

    suspend fun fetchDownloadedDefaultImages(): List<DefaultList> {
        val response = signageRepository.getAllDownloadedDefaultFiles()
        currentDefaultList.clear()
        currentDefaultList.addAll(response)
        return response
    }

    fun reportError(deviceId: String, campaignId: String, exception: java.lang.Exception) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ErrorService.create().reportError(
                    ErrorBody(
                        deviceId = deviceId,
                        campaignId = campaignId,
                        errorCode = exception.message.toString(),
                        message = exception.message.toString(),
                        stacktrace = exception.stackTraceToString()
                    )
                )
                if (response.isSuccessful) {
                    _playEvent.postValue(PlayEvent.ErrorReported())
                } else {
                    _playEvent.postValue(
                        PlayEvent.ErrorReported(
                            response.errorBody().toString()
                        )
                    )
                }
            } catch (e: java.lang.Exception) {
                _playEvent.postValue(PlayEvent.ErrorReported(e.message.toString()))
            }
        }
    }
}

sealed class PlayEvent() {
    data class PlayImage(val uri: Uri, val nextIndex: Int, val isDefault: Boolean = false) :
        PlayEvent()

    data class PlayVideo(val uri: Uri, val nextIndex: Int, val isDefault: Boolean = false) :
        PlayEvent()

    object RestartCampaign : PlayEvent()

    object showLocalDeaulftImages : PlayEvent()

    data class ErrorReported(val messageString: String = "Error Reported Successfully") :
        PlayEvent()
}