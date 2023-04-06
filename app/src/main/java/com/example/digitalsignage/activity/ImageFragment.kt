package com.example.digitalsignage.activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.digitalsignage.PlayEvent
import com.example.digitalsignage.R
import com.example.digitalsignage.activity.VideoFragment.Companion.PLAY_EVENT
import com.example.digitalsignage.checkRight
import com.example.digitalsignage.databinding.FragmentImageBinding
import com.example.digitalsignage.databinding.FragmentVideoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class ImageFragment : Fragment() {

    private lateinit var binding: FragmentImageBinding
    private lateinit var listener: onImageCompletedListener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentImageBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.listener = requireContext() as onImageCompletedListener
        val playEvent = arguments?.getParcelable<PlayEvent.PlayImage>(PLAY_EVENT)
        val showDefaultImage = arguments?.getBoolean(DEFAULT_IMAGE)
        if (showDefaultImage == true) {
            binding.ImageView.setImageResource(R.drawable.default_image)
        } else {
            lifecycleScope.launch(Dispatchers.Main) {
                binding.run {
                    ImageView.isVisible = true
                    ImageView.setImageURI(playEvent!!.uri)
                }
                delay(5000L)
                if (playEvent!!.isDefault.not()) {
                    listener.onImageListener(playEvent = playEvent)
                } else {
                    listener.onDefaultImageDispalyed(playEvent = playEvent)
                }
            }
        }
    }

    companion object {
        const val PLAY_EVENT = "PLAY_EVENT"
        const val DEFAULT_IMAGE = "DEFAULT_IMAGE"

        @JvmStatic
        fun newInstance(playEvent: PlayEvent.PlayImage? = null, showDefaultImage: Boolean = false) =
            ImageFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(PLAY_EVENT, playEvent)
                    putBoolean(DEFAULT_IMAGE, showDefaultImage)
                }
            }
    }
}

interface onImageCompletedListener {
    fun onImageListener(playEvent: PlayEvent.PlayImage)
    fun onDefaultImageDispalyed(playEvent: PlayEvent.PlayImage)
}