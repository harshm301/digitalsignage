package com.example.digitalsignage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel:ViewModel() {

    private val _playEvent:MutableLiveData<PlayEvent> = MutableLiveData<PlayEvent>()
    val playEvent:LiveData<PlayEvent> = _playEvent

    var isCurrentingPlaying = false

    fun playImageAndVideo(context: Context, list:List<Uri?>){
        if(list.isEmpty()){
            isCurrentingPlaying = false
            _playEvent.postValue(PlayEvent.RestartCampaign)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            isCurrentingPlaying = true
            list.forEach {
                it?.let {
                    val fileExt = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(context.contentResolver.getType(it!!))
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
            isCurrentingPlaying = false
            _playEvent.postValue(PlayEvent.RestartCampaign)
        }
    }

    fun restartCampaign(){
        _playEvent.postValue(PlayEvent.RestartCampaign)
    }
}

sealed class PlayEvent(){
    data class PlayImage(val uri: Uri):PlayEvent()
    data class PlayVideo(val uri: Uri):PlayEvent()
    object RestartCampaign:PlayEvent()
}