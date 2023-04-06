package com.example.digitalsignage.activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.digitalsignage.PlayEvent
import com.example.digitalsignage.R
import com.example.digitalsignage.databinding.FragmentVideoBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource


class VideoFragment : Fragment() {

    private lateinit var binding: FragmentVideoBinding
    private lateinit var listener: OnVideoCompleteListener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideoBinding.inflate(inflater)
        return binding.root
    }

    companion object {
        const val PLAY_EVENT = "PLAY_EVENT"
        fun newInstance(playEvent: PlayEvent.PlayVideo) =
            VideoFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(PLAY_EVENT, playEvent)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /* dataSourceFactory = DefaultDataSource.Factory(requireContext())
        simple = ExoPlayer.Builder(requireContext()).build()*/
        this.listener = requireContext() as OnVideoCompleteListener
        val playEvent = arguments?.getParcelable<PlayEvent.PlayVideo>(PLAY_EVENT)
        binding.run {
            videoView.isVisible = true
            videoView.setVideoURI(playEvent!!.uri)
            videoView.start()
            videoView.setOnCompletionListener {
                if (playEvent.isDefault) {
                    listener.defaultVideoCompleted(playEvent)
                } else {
                    listener.videoCompleted(playEvent)
                }
            }
            videoView.setOnErrorListener { mediaPlayer, i, i2 ->
                if (playEvent.isDefault) {
                    listener.defaultVideoCompleted(playEvent)
                } else {
                    listener.videoCompleted(playEvent)
                }
                return@setOnErrorListener true
            }
        }
    }

}

interface OnVideoCompleteListener {
    fun defaultVideoCompleted(playEvent: PlayEvent.PlayVideo)
    fun videoCompleted(playEvent: PlayEvent.PlayVideo)
}