package com.example.digitalsignage

import android.net.Uri
import com.google.gson.annotations.SerializedName

data class DataClass(
    @SerializedName("date")
    val date: String? = "",
    @SerializedName("urls")
    val urls: List<String>? = listOf(),
    @SerializedName("startTime")
    val startTime: String? = "",
    @SerializedName("endTime")
    val endTime: String? = ""
)

data class UriMapper(
    val starTime: Long,
    val endTime: Long,
    val uriList: MutableList<Uri>? = null
)

data class PlayMapper(
    val startTime: Long,
    val endTime: Long,
    val uriList: MutableList<Uri>? = null,
    var isSlotCompleted: Boolean = true
)